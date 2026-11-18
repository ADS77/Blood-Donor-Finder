package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.kafka.producer.GenericKafkaEventProducer;
import com.bd.blooddonorfinder.model.BloodRequest;
import com.bd.blooddonorfinder.model.chat.Chat;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.ChatStatus;
import com.bd.blooddonorfinder.model.enums.NotificationType;
import com.bd.blooddonorfinder.model.enums.RequestStatus;
import com.bd.blooddonorfinder.payload.response.ChatSummaryDto;
import com.bd.blooddonorfinder.repository.BloodRequestRepository;
import com.bd.blooddonorfinder.repository.ChatMessageRepository;
import com.bd.blooddonorfinder.repository.ChatRepository;
import com.bd.blooddonorfinder.service.chat.ChatPushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Opening a chat is its own state machine: idempotent, participant-guarded,
 * and gated only by request eligibility — never a side effect of acceptance.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceOpenChatTest {

    private static final String CHAT_EVENTS_DESTINATION = "/queue/chat-events";

    @Mock
    private ChatRepository chatRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private BloodRequestRepository bloodRequestRepository;
    @Mock
    private ChatPushService pushService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private GenericKafkaEventProducer eventProducer;
    @Mock
    private StringRedisTemplate redisTemplate;

    private ChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChatServiceImpl(chatRepository, chatMessageRepository, bloodRequestRepository,
                pushService, notificationService, eventProducer, redisTemplate);
    }

    @Test
    void openChat_createsChatAndNotifiesCounterpart() {
        User requester = user("Requester");
        User donor = user("Donor");
        BloodRequest request = request(requester, donor, RequestStatus.ACCEPTED);
        Chat chat = chat(request, requester, donor);

        when(bloodRequestRepository.findWithParticipantsById(request.getId())).thenReturn(Optional.of(request));
        when(chatRepository.findByRequestId(request.getId()))
                .thenReturn(Optional.empty(), Optional.of(chat));
        when(chatRepository.insertIfAbsent(any(UUID.class), eq(request.getId()),
                eq(requester.getId()), eq(donor.getId()), any(LocalDateTime.class))).thenReturn(1);
        when(chatMessageRepository.findFirstByChatIdOrderByCreatedAtDesc(chat.getId()))
                .thenReturn(Optional.empty());
        when(chatMessageRepository.countUnread(chat.getId(), requester.getId())).thenReturn(0L);

        ChatSummaryDto summary = service.openChat(request.getId(), requester.getId());

        assertEquals(chat.getId(), summary.chatId());
        assertEquals(donor.getId(), summary.counterpartId());
        assertEquals(ChatStatus.ACTIVE.name(), summary.status());
        verify(notificationService).notify(eq(donor.getId()), eq(NotificationType.CHAT_OPENED),
                eq(chat.getId()), anyMap());
        verify(pushService).pushAfterCommit(eq(donor.getId()), eq(CHAT_EVENTS_DESTINATION), any());
    }

    @Test
    void openChat_isIdempotentForExistingChat() {
        User requester = user("Requester");
        User donor = user("Donor");
        BloodRequest request = request(requester, donor, RequestStatus.FULFILLED);
        Chat chat = chat(request, requester, donor);
        chat.setStatus(ChatStatus.CLOSED);

        when(bloodRequestRepository.findWithParticipantsById(request.getId())).thenReturn(Optional.of(request));
        when(chatRepository.findByRequestId(request.getId())).thenReturn(Optional.of(chat));
        when(chatMessageRepository.findFirstByChatIdOrderByCreatedAtDesc(chat.getId()))
                .thenReturn(Optional.empty());
        when(chatMessageRepository.countUnread(chat.getId(), donor.getId())).thenReturn(0L);

        ChatSummaryDto summary = service.openChat(request.getId(), donor.getId());

        assertEquals(chat.getId(), summary.chatId());
        assertEquals(ChatStatus.CLOSED.name(), summary.status());
        verifyNoInteractions(notificationService);
        verify(chatRepository, never()).insertIfAbsent(any(), any(), any(), any(), any());
    }

    @Test
    void openChat_rejectsRequestBeforeAcceptance() {
        User requester = user("Requester");
        BloodRequest request = request(requester, null, RequestStatus.PENDING);

        when(bloodRequestRepository.findWithParticipantsById(request.getId())).thenReturn(Optional.of(request));
        when(chatRepository.findByRequestId(request.getId())).thenReturn(Optional.empty());

        AuthException error = assertThrows(AuthException.class,
                () -> service.openChat(request.getId(), requester.getId()));

        assertEquals(ErrorCode.INVALID_REQUEST, error.getErrorCode());
        verify(chatRepository, never()).insertIfAbsent(any(), any(), any(), any(), any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void openChat_rejectsWhenRequestClosesDuringInsert() {
        User requester = user("Requester");
        User donor = user("Donor");
        BloodRequest request = request(requester, donor, RequestStatus.ACCEPTED);
        BloodRequest closed = request(requester, donor, RequestStatus.FULFILLED);

        when(bloodRequestRepository.findWithParticipantsById(request.getId())).thenReturn(Optional.of(request));
        when(chatRepository.findByRequestId(request.getId())).thenReturn(Optional.empty());
        when(chatRepository.insertIfAbsent(any(UUID.class), eq(request.getId()),
                eq(requester.getId()), eq(donor.getId()), any(LocalDateTime.class))).thenReturn(0);
        when(bloodRequestRepository.findById(request.getId())).thenReturn(Optional.of(closed));

        AuthException error = assertThrows(AuthException.class,
                () -> service.openChat(request.getId(), requester.getId()));

        assertEquals(ErrorCode.INVALID_REQUEST, error.getErrorCode());
        verifyNoInteractions(notificationService);
    }

    @Test
    void openChat_rejectsNonParticipantWithoutRevealingTheRequest() {
        User requester = user("Requester");
        User donor = user("Donor");
        BloodRequest request = request(requester, donor, RequestStatus.ACCEPTED);

        when(bloodRequestRepository.findWithParticipantsById(request.getId())).thenReturn(Optional.of(request));

        AuthException error = assertThrows(AuthException.class,
                () -> service.openChat(request.getId(), UUID.randomUUID()));

        assertEquals(ErrorCode.REQUEST_NOT_FOUND, error.getErrorCode());
        verifyNoInteractions(chatRepository, notificationService);
    }

    @Test
    void closeChatForRequest_closesOnlyActiveChats() {
        User requester = user("Requester");
        User donor = user("Donor");
        BloodRequest request = request(requester, donor, RequestStatus.FULFILLED);
        Chat chat = chat(request, requester, donor);

        when(chatRepository.findByRequestId(request.getId())).thenReturn(Optional.of(chat));

        service.closeChatForRequest(request.getId());

        assertEquals(ChatStatus.CLOSED, chat.getStatus());
        verify(chatRepository).save(chat);
    }

    private static BloodRequest request(User requester, User donor, RequestStatus status) {
        BloodRequest request = new BloodRequest();
        request.setId(UUID.randomUUID());
        request.setRequester(requester);
        request.setAcceptedDonor(donor);
        request.setStatus(status);
        return request;
    }

    private static Chat chat(BloodRequest request, User requester, User donor) {
        Chat chat = new Chat();
        chat.setId(UUID.randomUUID());
        chat.setRequest(request);
        chat.setRequester(requester);
        chat.setDonor(donor);
        chat.setStatus(ChatStatus.ACTIVE);
        return chat;
    }

    private static User user(String firstName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName(firstName);
        user.setLastName("User");
        return user;
    }
}
