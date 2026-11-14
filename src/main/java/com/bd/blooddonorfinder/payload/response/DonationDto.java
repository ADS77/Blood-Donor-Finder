package com.bd.blooddonorfinder.payload.response;

import java.util.UUID;

/** One completed donation (requester's perspective: request id; donor's: history). */
public record DonationDto(
        UUID id,
        UUID requestId,
        UUID donorId,
        UUID requesterId,
        String bloodGroup,
        long donatedAtEpochMs,
        Integer rating,
        String review) {
}
