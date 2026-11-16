package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.RateLimitExceededException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.kafka.model.events.ChatMessageSentEvent;
import com.bd.blooddonorfinder.kafka.producer.GenericKafkaEventProducer;
import com.bd.blooddonorfinder.model.BloodRequest;
import com.bd.blooddonorfinder.model.chat.Chat;
import com.bd.blooddonorfinder.model.chat.ChatMessage;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.ChatStatus;
import com.bd.blooddonorfinder.model.enums.NotificationType;
import com.bd.blooddonorfinder.model.enums.RequestStatus;
import com.bd.blooddonorfinder.payload.response.AcceptRequestResponse;
import com.bd.blooddonorfinder.payload.response.ChatMessageDto;
import com.bd.blooddonorfinder.payload.response.ChatMessagesResponse;
import com.bd.blooddonorfinder.payload.response.ChatSummaryDto;
import com.bd.blooddonorfinder.repository.BloodRequestRepository;
import com.bd.blooddonorfinder.repository.ChatMessageRepository;
import com.bd.blooddonorfinder.repository.ChatRepository;
import com.bd.blooddonorfinder.repository.UserRepository;
import com.bd.blooddonorfinder.service.chat.ChatPushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class ChatServiceImpl implements ChatService {

    private static final String SEND_RATE_KEY = "chat:rl:%s";
    private static final int MAX_PAGE_SIZE = 100;
    private static final String CHAT_EVENTS_DESTINATION = "/queue/chat-events";
    private static final String CHAT_MESSAGES_DESTINATION = "/queue/chat";

    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final BloodRequestRepository bloodRequestRepository;
    private final UserRepository userRepository;
    private final ChatPushService pushService;
    private final NotificationService notificationService;
    private final GenericKafkaEventProducer eventProducer;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.chat.send.rate-limit-per-minute:30}")
    private int sendRateLimitPerMinute;

    public ChatServiceImpl(ChatRepository chatRepository,
                           ChatMessageRepository chatMessageRepository,
                           BloodRequestRepository bloodRequestRepository,
                           UserRepository userRepository,
                           ChatPushService pushService,
                           NotificationService notificationService,
                           GenericKafkaEventProducer eventProducer,
                           StringRedisTemplate redisTemplate) {
        this.chatRepository = chatRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.bloodRequestRepository = bloodRequestRepository;
        this.userRepository = userRepository;
        this.pushService = pushService;
        this.notificationService = notificationService;
        this.eventProducer = eventProducer;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    public AcceptRequestResponse acceptRequest(UUID requestId, UUID donorId) {
        User donor = userRepository.findById(donorId)
                .orElseThrow(() -> new AuthException(ErrorCode.USER_NOT_FOUND, "User not found"));
        if (!Boolean.TRUE.equals(donor.getIsVerified())) {
            throw new AuthException(ErrorCode.USER_NOT_VERIFIED, "Only verified donors can accept requests");
        }
        BloodRequest request = bloodRequestRepository.findById(requestId)
                .orElseThrow(() -> new AuthException(ErrorCode.CHAT_NOT_FOUND, "Blood request not found"));
        if (request.getRequester().getId().equals(donorId)) {
            throw new AuthException(ErrorCode.CHAT_FORBIDDEN, "You cannot accept your own request");
        }

        // atomic first-donor-wins; concurrent accepters lose here
        int accepted = bloodRequestRepository.acceptAtomically(requestId, donor, Instant.now());
        if (accepted == 0) {
            throw new AuthException(ErrorCode.REQUEST_ALREADY_ACCEPTED,
                    "This request has already been accepted by another donor");
        }
        // The bulk update bypasses the persistence context; mutating the managed
        // entity below (acceptedDonor) would make Hibernate flush a full-row
        // UPDATE carrying the stale status=PENDING, silently reverting the
        // transition. Mirror the bulk update onto the managed copy so the flush
        // rewrites the same state instead of clobbering it.
        request.setStatus(RequestStatus.ACCEPTED);
        request.setAcceptedDonor(donor);
        request.setAcceptedAt(Instant.now());

        // idempotent on event-replay: a chat for the request may already exist
        Chat chat = chatRepository.findByRequestId(requestId)
                .orElseGet(() -> chatRepository.save(newChat(request, donor)));

        User requester = request.getRequester();
        // Facebook-style: notification row + realtime badge push — no email.
        // (acceptRequest is @Transactional; notify() registers post-commit delivery)
        notificationService.notify(requester.getId(), NotificationType.REQUEST_ACCEPTED, chat.getId(),
                Map.of(
                        "chatId", String.valueOf(chat.getId()),
                        "donorId", String.valueOf(donor.getId()),
                        "donorName", donor.getFirstName() != null ? donor.getFirstName() : ""
                ));

        // realtime: tell the requester a chat just opened (if they're online)
        pushService.pushAfterCommit(requester.getId(), CHAT_EVENTS_DESTINATION,
                new ChatEventPayload("CHAT_OPENED", chat.getId()));

        log.info("Request={} accepted by donor={} — chat={} opened", requestId, donorId, chat.getId());
        return new AcceptRequestResponse(requestId, chat.getId(),
                requester.getId(), donorId, RequestStatus.ACCEPTED.name());
    }

    private Chat newChat(BloodRequest request, User donor) {
        Chat chat = new Chat();
        chat.setRequest(request);
        chat.setRequester(request.getRequester());
        chat.setDonor(donor);
        return chat;
    }

    @Override
    public List<ChatSummaryDto> getMyChats(UUID userId) {
        List<Chat> chats = chatRepository.findMyChats(userId, PageRequest.of(0, 50));
        List<ChatSummaryDto> summaries = new ArrayList<>(chats.size());
        for (Chat chat : chats) {
            User counterpart = chat.getRequester().getId().equals(userId)
                    ? chat.getDonor() : chat.getRequester();
            ChatMessage last = chatMessageRepository
                    .findFirstByChatIdOrderByCreatedAtDesc(chat.getId()).orElse(null);
            summaries.add(new ChatSummaryDto(
                    chat.getId(),
                    chat.getRequest().getId(),
                    counterpart.getId(),
                    counterpart.getFirstName() + " " + counterpart.getLastName(),
                    last != null ? last.getBody() : null,
                    last != null ? toEpoch(last.getCreatedAt()) : null,
                    chatMessageRepository.countUnread(chat.getId(), userId),
                    chat.getStatus().name()));
        }
        return summaries;
    }

    @Override
    public ChatMessagesResponse getMessages(UUID chatId, UUID callerId, Long afterEpochMs, Integer size) {
        Chat chat = requireParticipant(chatId, callerId);
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : 50;

        List<ChatMessage> messages;
        boolean ascending = afterEpochMs != null;
        if (ascending) {
            messages = chatMessageRepository.findAfter(chat.getId(),
                    toLocalDateTime(afterEpochMs), PageRequest.of(0, pageSize));
        } else {
            // first load: latest page, returned oldest-first for display
            messages = chatMessageRepository.findLatest(chat.getId(), PageRequest.of(0, pageSize));
            Collections.reverse(messages);
        }

        List<ChatMessageDto> dtos = messages.stream().map(this::toDto).toList();
        Long cursor = messages.isEmpty()
                ? (ascending ? afterEpochMs : null)
                : toEpoch(messages.get(messages.size() - 1).getCreatedAt());
        boolean hasMore = messages.size() == pageSize;
        return ChatMessagesResponse.of(dtos, cursor, hasMore);
    }

    @Override
    @Transactional
    public ChatMessageDto sendMessage(UUID chatId, UUID senderId, String body) {
        Chat chat = requireParticipant(chatId, senderId);
        if (!ChatStatus.ACTIVE.equals(chat.getStatus())) {
            throw new AuthException(ErrorCode.CHAT_FORBIDDEN, "This chat is closed");
        }
        enforceSendRateLimit(senderId);

        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            throw new AuthException(ErrorCode.INVALID_REQUEST, "Message body can't be blank");
        }

        ChatMessage message = new ChatMessage();
        message.setChat(chat);
        message.setSender(chat.getDonor().getId().equals(senderId) ? chat.getDonor() : chat.getRequester());
        message.setBody(trimmed);
        message = chatMessageRepository.save(message);

        ChatMessageDto dto = toDto(message);
        UUID recipientId = chat.getRequester().getId().equals(senderId)
                ? chat.getDonor().getId() : chat.getRequester().getId();

        // realtime push + offline email both ride on durable events, post-commit
        pushService.pushAfterCommit(recipientId, CHAT_MESSAGES_DESTINATION, dto);
        publishAfterCommit(ChatMessageSentEvent.of(chat.getId(), message.getId(),
                senderId, message.getSender().getFirstName(), recipientId, preview(trimmed)));

        log.info("Chat message persisted: chatId={}, senderId={}, messageId={}",
                chatId, senderId, message.getId());
        return dto;
    }

    @Override
    @Transactional
    public void markRead(UUID chatId, UUID callerId) {
        Chat chat = requireParticipant(chatId, callerId);
        LocalDateTime readAt = LocalDateTime.now();
        int updated = chatMessageRepository.markAllRead(chat.getId(), callerId, readAt);
        UUID counterpartId = chat.getRequester().getId().equals(callerId)
                ? chat.getDonor().getId() : chat.getRequester().getId();
        if (updated > 0) {
            // read receipt to the sender
            pushService.pushAfterCommit(counterpartId, CHAT_EVENTS_DESTINATION,
                    new ChatEventPayload("MESSAGES_READ", chat.getId()));
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Chat requireParticipant(UUID chatId, UUID callerId) {
        Chat chat = chatRepository.findWithParticipantsById(chatId)
                .orElseThrow(() -> new AuthException(ErrorCode.CHAT_NOT_FOUND, "Chat not found"));
        if (!chat.hasParticipant(callerId)) {
            // IDOR guard: non-participants learn nothing, not even that the chat exists
            log.warn("Non-participant {} attempted access to chat {}", callerId, chatId);
            throw new AuthException(ErrorCode.CHAT_FORBIDDEN, "Chat not found");
        }
        return chat;
    }

    private void enforceSendRateLimit(UUID senderId) {
        String key = SEND_RATE_KEY.formatted(senderId);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(60));
        }
        if (count != null && count > sendRateLimitPerMinute) {
            throw new RateLimitExceededException("Too many messages, slow down a little");
        }
    }

    private void publishAfterCommit(ChatMessageSentEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventProducer.publishEvent(event);
                }
            });
        } else {
            eventProducer.publishEvent(event);
        }
    }

    private ChatMessageDto toDto(ChatMessage message) {
        return new ChatMessageDto(
                message.getId(),
                message.getChat().getId(),
                message.getSender().getId(),
                message.getBody(),
                toEpoch(message.getCreatedAt()),
                message.getReadAt() != null ? toEpoch(message.getReadAt()) : null);
    }

    private static String preview(String body) {
        return body.length() <= 120 ? body : body.substring(0, 117) + "...";
    }

    private static long toEpoch(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDateTime toLocalDateTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }

    /** Minimal WS event envelope for lifecycle notifications. */
    record ChatEventPayload(String type, UUID chatId) {
    }
}
