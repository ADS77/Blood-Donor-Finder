package com.bd.blooddonorfinder.search.service;

import com.bd.blooddonorfinder.exception.RateLimitExceededException;
import com.bd.blooddonorfinder.search.model.DonorGeoEntry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedisGeoSearchService {
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${app.redis.search.rate.limit.per.minute}")
    private int rateLimitPerMinute;
    @Value("${app.search.max.raw.results}")
    private int maxRawResults;

    /**
     * Lua script for atomic rate-limit check-and-increment.
     * Returns current count after increment, or -1 if the key was just created
     * (meaning we must set TTL).
     *
     * KEYS[1] = rate limit key
     * ARGV[1] = max requests
     * ARGV[2] = TTL in seconds (60)
     *
     * Returns: current count (≤ max = allowed), or 999 if exceeded.
     */
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = RedisScript.of(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            if current > tonumber(ARGV[1]) then
                return 999
            end
            return current
            """,
            Long.class
    );

    @Timed(value = "geo.search.latency.redis",
            description = "Redis GEO hot-path search latency",
            percentiles = {0.5, 0.9, 0.99})
    public List<String> searchNearby(String bloodGroup, double lat, double lng,
                                     int radiusKm, String userId) {
        enforceRateLimit(userId);

        String geoKey = DonorGeoEntry.geoKey(bloodGroup);
        Point center  = new Point(lng, lat);  // Redis GEO: longitude first
        Distance radius = new Distance(radiusKm, Metrics.KILOMETERS);

        RedisGeoCommands.GeoSearchCommandArgs args = RedisGeoCommands.GeoSearchCommandArgs
                .newGeoSearchArgs()
                .includeDistance()
                .includeCoordinates()
                .sortAscending()
                .limit(maxRawResults);

        GeoResults<RedisGeoCommands.GeoLocation<String>> results;
        try {
            results =  redisTemplate.opsForGeo().search(
                    geoKey,
                    GeoReference.fromCoordinate(center),
                    radius,
                    args
            );
        } catch (Exception ex) {
            log.warn("Redis geo search failed for key={} radius={}km: {}",
                    geoKey, radiusKm, ex.getMessage());
            meterRegistry.counter("geo.redis.error", "blood_group", bloodGroup).increment();
            return List.of();
        }

        if (results == null || results.getContent().isEmpty()) {
            log.debug("Redis cache miss: key={} radius={}km", geoKey, radiusKm);
            checkForColdStart(bloodGroup);
            return List.of();
        }

        List<String> donorIds = results.getContent().stream()
                .map(r -> r.getContent().getName())
                .filter(Objects::nonNull)
                .toList();

        log.debug("Redis found {} donors for group={} radius={}km",
                donorIds.size(), bloodGroup, radiusKm);

        meterRegistry.gauge(
                "geo.search.result_count",
                List.of(io.micrometer.core.instrument.Tag.of("blood_group", bloodGroup)),
                donorIds.size()
        );

        return donorIds;
    }

    public boolean isDonorIndexed(String bloodGroup, String donorId) {
        try {
            List<Point> positions = redisTemplate.opsForGeo()
                    .position(DonorGeoEntry.geoKey(bloodGroup), donorId);
            return positions != null && !positions.isEmpty() && positions.get(0) != null;
        } catch (Exception ex) {
            log.warn("Could not verify geo index for donor {}: {}", donorId, ex.getMessage());
            return false;
        }
    }

    private void enforceRateLimit(String userId) {
        if (userId == null) return;

        String key = DonorGeoEntry.rateLimitKey(UUID.fromString(userId));
        Long result = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(key),
                String.valueOf(rateLimitPerMinute),
                "60"
        );

        if (result != null && result >= 999) {
            log.warn("Rate limit exceeded for user={}", userId);
            meterRegistry.counter("geo.search.rate_limited").increment();
            throw new RateLimitExceededException(userId);
        }
    }

    private void checkForColdStart(String bloodGroup) {
        String geoKey = DonorGeoEntry.geoKey(bloodGroup);
        Long size = redisTemplate.opsForZSet().size(geoKey);
        if (size == null || size == 0) {
            log.warn("COLD START detected: Redis geo set is empty for blood_group={}. " +
                    "ES fallback will be used. Consider running geo-index bootstrap.", bloodGroup);
            meterRegistry.counter("geo.redis.cold_start", "blood_group", bloodGroup).increment();
        }
    }
}
