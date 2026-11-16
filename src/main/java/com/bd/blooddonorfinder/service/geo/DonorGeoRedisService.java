package com.bd.blooddonorfinder.service.geo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Redis GEO fast path for nearby donor search. Every confirmed geo write
 * (device location update, geo enrichment) also records the donor's position
 * in a Redis GEO set; searches can then resolve "who is within R km" with one
 * GEOSEARCH round trip and hydrate the matching Elasticsearch documents by
 * id — no expanding-radius ES query fan-out.
 *
 * <p>Redis is strictly a cache: it is the caller's duty to fall back to the
 * Elasticsearch path when this set is empty (cold or flushed) or this service
 * errors. There is no user.deleted removal wiring yet — stale members are
 * bounded instead: ids whose ES document is gone are skipped by the caller,
 * and coordinates older than the freshness window are filtered out.</p>
 */
@Service
@Slf4j
public class DonorGeoRedisService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.search.geo-redis.enabled:false}")
    private boolean enabled;

    @Value("${app.search.geo-redis.key:geo:donors}")
    private String geoKey;

    /** Hard cap on members fetched per GEOSEARCH; searches paginate past this via ES anyway. */
    @Value("${app.search.geo-redis.max-results:500}")
    private int maxResults;

    public DonorGeoRedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Records/updates a donor's position. Redis failures are logged and
     * swallowed: the DB and Elasticsearch remain the source of truth, and a
     * missed write only costs one ES-radius search until the next update.
     */
    public void upsert(String userId, double latitude, double longitude) {
        if (!enabled) {
            return;
        }
        try {
            redisTemplate.opsForGeo().add(geoKey, new Point(longitude, latitude), userId);
        } catch (RuntimeException e) {
            log.warn("Redis GEO upsert failed for userId={} (fast path degraded): {}",
                    userId, e.getMessage());
        }
    }

    /**
     * Donor ids within radiusKm of (latitude, longitude), nearest first.
     * Returns null when Redis has no data at all (cold/flushed set) so the
     * caller knows to fall back to Elasticsearch; an empty list means the
     * set is alive but nobody is in range.
     */
    public List<String> searchNearest(double latitude, double longitude, double radiusKm) {
        if (!enabled) {
            return null;
        }
        try {
            // Membership probe first: a missing key means "no fast path data"
            Boolean keyExists = redisTemplate.hasKey(geoKey);
            if (keyExists == null || !keyExists) {
                return null;
            }

            var results = redisTemplate.opsForGeo().search(
                    geoKey,
                    GeoReference.fromCoordinate(longitude, latitude),
                    new Distance(radiusKm, Metrics.KILOMETERS),
                    RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                            .limit(maxResults)
                            .sortAscending());

            if (results == null || results.getContent().isEmpty()) {
                return List.of();
            }
            return results.getContent().stream()
                    .map(geoResult -> geoResult.getContent().getName())
                    .toList();
        } catch (RuntimeException e) {
            // Redis outages must never take donor search down
            log.warn("Redis GEO search failed (falling back to Elasticsearch): {}", e.getMessage());
            return null;
        }
    }
}
