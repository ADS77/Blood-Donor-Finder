package com.bd.blooddonorfinder.payload.response;

import java.util.List;
import java.util.UUID;

/**
 * Keyset-paginated chat history. {@code nextCursorEpochMs} is the highest
 * createdAt in this page — pass it as {@code after} to fetch the next delta.
 */
public record ChatMessagesResponse(
        List<ChatMessageDto> messages,
        Long nextCursorEpochMs,
        boolean hasMore) {

    public static ChatMessagesResponse of(List<ChatMessageDto> messages, Long nextCursor, boolean hasMore) {
        return new ChatMessagesResponse(messages, nextCursor, hasMore);
    }

    public static ChatMessagesResponse empty() {
        return new ChatMessagesResponse(List.of(), null, false);
    }
}
