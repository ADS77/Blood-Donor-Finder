package com.bd.blooddonorfinder.search.model;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;
@Data
@Builder
public class DonorGeoEntry {

    private UUID donorId;
    private String bloodGroup;
    private double lat;
    private double lng;

    /** Redis key for this blood group's geo sorted set */
    public static String geoKey(String bloodGroup) {
        return "geo:donors:" + bloodGroup;
    }

    /** Redis key for availability flag (STRING "1"/"0", TTL 5min) */
    public static String availabilityKey(UUID donorId) {
        return "donor:availability:" + donorId;
    }

    /** Redis key for eligibility flag (STRING "1"/"0", TTL 1h — owned by Eligibility Svc) */
    public static String eligibilityKey(UUID donorId) {
        return "donor:eligible:" + donorId;
    }

    /** Redis key for rate-limit counter (TTL 1min) */
    public static String rateLimitKey(UUID userId) {
        return "search:rl:" + userId;
    }

    /** Redis key for Kafka deduplication (TTL 24h) */
    public static String kafkaProcessedKey(UUID donorId, String topic) {
        return "kafka:processed:" + donorId + ":" + topic;
    }

    /** Redis key for distributed pulse job lock (ShedLock) */
    public static String pulseJobLockKey() {
        return "shedlock:pulse-job";
    }

}
