package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.RateLimitExceededException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.kafka.model.events.UserLocationUpdatedEvent;
import com.bd.blooddonorfinder.kafka.producer.GenericKafkaEventProducer;
import com.bd.blooddonorfinder.model.common.GeoLocation;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.payload.request.LocationUpdateRequest;
import com.bd.blooddonorfinder.payload.response.LocationUpdateResponse;
import com.bd.blooddonorfinder.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Donor device pushes fresh coordinates here. Design goals:
 * <ul>
 *   <li><b>Freshness</b> — coords are stored with a {@code geoUpdatedAt} stamp that the
 *       search layer and the staleness worker rely on.</li>
 *   <li><b>Churn protection</b> — a Redis throttle caps updates per user, and a
 *       haversine dedupe turns GPS jitter / repeated app-opens into no-ops so
 *       Kafka and Elasticsearch don't drown in redundant writes.</li>
 *   <li><b>Consistency</b> — row is locked pessimistically (no lost update), and the
 *       Kafka event is published only after the DB transaction commits.</li>
 * </ul>
 */
@Service
@Slf4j
public class LocationIngestServiceImpl implements LocationIngestService {

    private static final String THROTTLE_KEY = "loc:throttle:%s";

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final GenericKafkaEventProducer eventProducer;

    @Value("${app.location.update.min-interval-seconds:60}")
    private long minIntervalSeconds;

    @Value("${app.location.dedupe.radius-meters:100}")
    private double dedupeRadiusMeters;

    @Value("${app.location.dedupe.window-hours:24}")
    private long dedupeWindowHours;

    public LocationIngestServiceImpl(UserRepository userRepository,
                                     StringRedisTemplate redisTemplate,
                                     GenericKafkaEventProducer eventProducer) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.eventProducer = eventProducer;
    }

    @Override
    @Transactional
    public LocationUpdateResponse updateLocation(UUID userId, LocationUpdateRequest request) {
        // pessimistic lock: concurrent device pushes for the same user are serialized
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.USER_NOT_FOUND, "User not found"));

        Instant now = Instant.now();

        // 1. movement dedupe — GPS jitter / app re-opens must not touch Kafka or ES
        if (isUnmoved(user, request.getLatitude(), request.getLongitude(), now)) {
            log.debug("Location update for userId={} ignored: moved < {} m within the last {} h",
                    userId, dedupeRadiusMeters, dedupeWindowHours);
            return LocationUpdateResponse.ofUnchanged();
        }

        // 2. per-user throttle — a misbehaving client can't flood the pipeline
        Boolean firstInWindow = redisTemplate.opsForValue()
                .setIfAbsent(THROTTLE_KEY.formatted(userId), "1", Duration.ofSeconds(minIntervalSeconds));
        if (!Boolean.TRUE.equals(firstInWindow)) {
            throw new RateLimitExceededException("Too many location updates, please retry later");
        }

        // 3. persist — device coordinates are authoritative, no geocoding round-trip
        GeoLocation geo = user.getGeoLocation();
        if (geo == null) {
            geo = new GeoLocation();
            geo.setCity("Unknown"); // city is NOT NULL; search keys off coordinates, not city
            user.setGeoLocation(geo);
        }
        geo.setLatitude(BigDecimal.valueOf(request.getLatitude()).setScale(6, RoundingMode.HALF_UP));
        geo.setLongitude(BigDecimal.valueOf(request.getLongitude()).setScale(6, RoundingMode.HALF_UP));
        geo.setGeoUpdatedAt(now);
        geo.setGeoReconfirmRequestedAt(null); // a confirmed update clears any pending reconfirmation
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userRepository.save(user);

        // 4. propagate to Elasticsearch via Kafka, only after the row is durably committed
        UserLocationUpdatedEvent event = UserLocationUpdatedEvent.of(user, now);
        publishAfterCommit(event);

        log.info("Location updated for userId={} lat={} lon={}", userId,
                request.getLatitude(), request.getLongitude());
        return LocationUpdateResponse.ofAccepted();
    }

    @Override
    public void propagateLocationChange(User user) {
        if (user.getGeoLocation() == null
                || user.getGeoLocation().getLatitude() == null
                || user.getGeoLocation().getLongitude() == null) {
            log.warn("propagateLocationChange called for userId={} without coordinates — skipping", user.getId());
            return;
        }
        Instant confirmedAt = user.getGeoLocation().getGeoUpdatedAt() != null
                ? user.getGeoLocation().getGeoUpdatedAt() : Instant.now();
        publishAfterCommit(UserLocationUpdatedEvent.of(user, confirmedAt));
        log.info("Location change propagated for userId={} (source=profile-update)", user.getId());
    }

    /**
     * True when the donor is still (essentially) where they were and that position
     * was confirmed recently — i.e. nothing new to learn from this update.
     */
    private boolean isUnmoved(User user, double lat, double lon, Instant now) {
        GeoLocation geo = user.getGeoLocation();
        if (geo == null || geo.getLatitude() == null || geo.getLongitude() == null) {
            return false;
        }
        Instant lastConfirmedAt = geo.getGeoUpdatedAt();
        if (lastConfirmedAt == null || lastConfirmedAt.isBefore(now.minus(Duration.ofHours(dedupeWindowHours)))) {
            // last confirmation is too old to trust — always treat as a real move
            return false;
        }
        double movedMeters = haversineMeters(
                geo.getLatitude().doubleValue(), geo.getLongitude().doubleValue(), lat, lon);
        return movedMeters < dedupeRadiusMeters;
    }

    /** Great-circle distance in metres. */
    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusM = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * earthRadiusM * Math.asin(Math.sqrt(a));
    }

    private void publishAfterCommit(UserLocationUpdatedEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventProducer.publishEvent(event);
                }
            });
        } else {
            eventProducer.publishEvent(event);
        }
    }
}
