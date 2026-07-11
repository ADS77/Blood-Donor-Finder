package com.bd.blooddonorfinder.search.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

public sealed interface DonorEvent
        permits DonorEvent.DonorCreated,
        DonorEvent.DonorUpdated,
        DonorEvent.AvailabilityChanged,
        DonorEvent.EligibilityChanged,
        DonorEvent.DonorDeleted,
        DonorEvent.LocationUpdated,
        DonorEvent.TrustUpdated {

    UUID donorId();
    Instant eventTimestamp();
    String eventType();

    // ── donor.created ────────────────────────────────────────────────────
    @JsonIgnoreProperties(ignoreUnknown = true)
    final
    record DonorCreated(
            UUID donorId,
            String bloodGroup,
            double lat,
            double lng,
            boolean isAvailable,
            boolean isEligible,
            boolean isVerified,
            float trustScore,
            int totalDonations,
            UUID orgId,
            Instant lastActiveAt,
            Instant eventTimestamp
    ) implements DonorEvent {
        public String eventType() { return "donor.created"; }
    }

    // ── donor.updated ────────────────────────────────────────────────────
    @JsonIgnoreProperties(ignoreUnknown = true)
    final
    record DonorUpdated(
            UUID donorId,
            String bloodGroup,
            double lat,
            double lng,
            boolean isAvailable,
            boolean isEligible,
            boolean isVerified,
            float trustScore,
            int totalDonations,
            UUID orgId,
            Instant lastActiveAt,
            Instant eventTimestamp
    ) implements DonorEvent {
        public String eventType() { return "donor.updated"; }
    }

    // ── donor.availability_changed ───────────────────────────────────────
    @JsonIgnoreProperties(ignoreUnknown = true)
    final
    record AvailabilityChanged(
            UUID donorId,
            String bloodGroup,
            boolean isAvailable,
            boolean isEligible,
            double lat,
            double lng,
            Instant eventTimestamp
    ) implements DonorEvent {
        public String eventType() { return "donor.availability_changed"; }
    }

    // ── donor.eligibility_changed ────────────────────────────────────────
    @JsonIgnoreProperties(ignoreUnknown = true)
    final
    record EligibilityChanged(
            UUID donorId,
            String bloodGroup,
            boolean isEligible,
            boolean isAvailable,
            double lat,
            double lng,
            Instant eventTimestamp
    ) implements DonorEvent {
        public String eventType() { return "donor.eligibility_changed"; }
    }

    // ── donor.deleted ────────────────────────────────────────────────────
    @JsonIgnoreProperties(ignoreUnknown = true)
    final
    record DonorDeleted(
            UUID donorId,
            String bloodGroup,
            Instant eventTimestamp
    ) implements DonorEvent {
        public String eventType() { return "donor.deleted"; }
    }

    // ── donor.location_updated ───────────────────────────────────────────
    @JsonIgnoreProperties(ignoreUnknown = true)
    final
    record LocationUpdated(
            UUID donorId,
            String bloodGroup,
            double lat,
            double lng,
            boolean isAvailable,
            boolean isEligible,
            Instant eventTimestamp
    ) implements DonorEvent {
        public String eventType() { return "donor.location_updated"; }
    }

    // ── donor.trust_updated ──────────────────────────────────────────────
    @JsonIgnoreProperties(ignoreUnknown = true)
    final
    record TrustUpdated(
            UUID donorId,
            float trustScore,
            float availabilityReliability,
            boolean hasUnresolvedAbuseReport,
            Instant eventTimestamp
    ) implements DonorEvent {
        public String eventType() { return "donor.trust_updated"; }
    }
}
