package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.payload.response.AcceptRequestResponse;
import com.bd.blooddonorfinder.payload.response.ChatMessageDto;
import com.bd.blooddonorfinder.payload.response.ChatMessagesResponse;
import com.bd.blooddonorfinder.payload.response.ChatSummaryDto;

import java.util.List;
import java.util.UUID;

/**
 * Request → accept → chat pipeline. A chat exists ONLY after a donor accepted
 * a blood request, and every operation re-verifies the caller is a participant.
 */
public interface ChatService {

    /**
     * Atomically accepts a PENDING blood request on behalf of a verified donor
     * and opens the (single) chat for the request. First donor wins.
     */
    AcceptRequestResponse acceptRequest(UUID requestId, UUID donorId);

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
