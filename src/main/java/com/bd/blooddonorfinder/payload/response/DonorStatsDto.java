package com.bd.blooddonorfinder.payload.response;

import java.util.UUID;

/** A donor's lifetime donation stats. */
public record DonorStatsDto(
        UUID donorId,
        long totalDonations,
        double averageRating,
        long ratingCount) {
}
