package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.payload.response.ChatMessageDto;
import com.bd.blooddonorfinder.payload.response.ChatMessagesResponse;
import com.bd.blooddonorfinder.payload.response.ChatSummaryDto;

import java.util.List;
import java.util.UUID;

/**
 * Chat lifecycle and messaging. This is a state machine of its own: opening a
 * conversation is explicit and idempotent, and does not happen as a side
 * effect of a blood-request transition. Every operation re-verifies that the
 * caller is a participant.
 */
public interface ChatService {

    /**
     * Opens the conversation attached to an accepted request, returning the
     * existing chat when one already exists. Either the requester or the
     * accepted donor may call it; the request must currently allow chat.
     * Creating the chat notifies and pushes to the other participant.
     */
    ChatSummaryDto openChat(UUID requestId, UUID callerId);

    /**
     * Closes the active chat for a request, if any. Called by the donation
     * state machine on completion so chat rows are never mutated outside this
     * service.
     */
    void closeChatForRequest(UUID requestId);

    List<ChatSummaryDto> getMyChats(UUID userId);

    /**
     * Keyset-paginated history. {@code afterEpochMs == null} returns the latest
     * page; otherwise all messages created at/after the cursor (ascending).
     */
    ChatMessagesResponse getMessages(UUID chatId, UUID callerId, Long afterEpochMs, Integer size);

    ChatMessageDto sendMessage(UUID chatId, UUID senderId, String body);

    /** Marks every counterpart message in the chat as read and pushes a receipt. */
    void markRead(UUID chatId, UUID callerId);
}
