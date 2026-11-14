package com.bd.blooddonorfinder.payload.response;

import java.util.Map;
import java.util.UUID;

/** A notification as delivered over WebSocket and the REST feed. */
public record NotificationDto(
        UUID id,
        String type,
        UUID referenceId,
        Map<String, Object> payload,
        long createdAtEpochMs,
        Long readAtEpochMs) {
}
