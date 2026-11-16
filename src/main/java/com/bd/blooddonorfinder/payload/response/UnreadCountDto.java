package com.bd.blooddonorfinder.payload.response;

/**
 * Bell-badge value with a single, unambiguous meaning: the number of unread
 * notification rows for the caller.
 *
 * <p>CHAT_MESSAGE notifications are marked read together with the chat they
 * belong to (see the chat read endpoint), while lifecycle notifications
 * (request accepted, donation completed, ...) are cleared only through the
 * notification read endpoints. One badge, one source of truth.</p>
 */
public record UnreadCountDto(long unreadCount) {
}
