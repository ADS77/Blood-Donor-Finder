package com.bd.blooddonorfinder.payload.response;

import java.util.UUID;

/** A document as seen in the admin verification queue. */
public record DocumentReviewDto(
        UUID id,
        String docType,
        UUID ownerId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        long uploadedAtEpochMs,
        String verificationStatus) {
}
