package com.bd.blooddonorfinder.payload.response;

import java.util.UUID;

/** Document metadata as returned by the document API. */
public record DocumentDto(
        UUID id,
        String docType,
        String originalFilename,
        String contentType,
        long sizeBytes,
        long createdAtEpochMs) {
}
