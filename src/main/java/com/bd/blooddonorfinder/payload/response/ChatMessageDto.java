package com.bd.blooddonorfinder.payload.response;

import java.util.UUID;

/**
 * A chat message as delivered over WebSocket and REST backfill.
 * Timestamps are epoch-millis so clients can keep a simple numeric cursor.
 */
public record ChatMessageDto(
        UUID id,
        UUID chatId,
        UUID senderId,
        String body,
        long createdAtEpochMs,
        Long readAtEpochMs) {
}
