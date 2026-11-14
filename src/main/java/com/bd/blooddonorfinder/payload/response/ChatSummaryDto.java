package com.bd.blooddonorfinder.payload.response;

import java.util.UUID;

/** Chat inbox entry for one side of a request-accept conversation. */
public record ChatSummaryDto(
        UUID chatId,
        UUID requestId,
        UUID counterpartId,
        String counterpartName,
        String lastMessagePreview,
        Long lastMessageAtEpochMs,
        long unreadCount,
        String status) {
}
