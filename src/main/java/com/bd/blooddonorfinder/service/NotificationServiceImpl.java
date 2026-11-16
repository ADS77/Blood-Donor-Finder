package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.NotificationType;
import com.bd.blooddonorfinder.model.notification.Notification;
import com.bd.blooddonorfinder.payload.response.NotificationDto;
import com.bd.blooddonorfinder.repository.NotificationRepository;
import com.bd.blooddonorfinder.repository.UserRepository;
import com.bd.blooddonorfinder.service.chat.ChatPushService;
import com.bd.blooddonorfinder.service.push.PushSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_PAGE_SIZE = 100;
    static final String NOTIFICATIONS_DESTINATION = "/queue/notifications";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ChatPushService pushService;
    private final PushSender pushSender;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   UserRepository userRepository,
                                   ChatPushService pushService,
                                   PushSender pushSender) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.pushService = pushService;
        this.pushSender = pushSender;
    }

    @Override
    public NotificationDto notify(UUID recipientId, NotificationType type, UUID referenceId,
                                  Map<String, Object> payload) {
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new AuthException(ErrorCode.USER_NOT_FOUND, "User not found"));

        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setReferenceId(referenceId);
        if (payload != null) {
            notification.setPayload(payload);
        }
        notification = notificationRepository.save(notification);

        NotificationDto dto = toDto(notification);
        // Captured by the post-commit callback below, so it must be effectively final.
        Map<String, Object> pushPayload = payload != null ? payload : notification.getPayload();
        // realtime delivery on every node; post-commit so the row is readable
        // when the client immediately fetches the feed
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    pushService.pushNow(recipientId, NOTIFICATIONS_DESTINATION, dto);
                    // mobile push: async inside the sender, never fails the flow
                    pushSender.send(recipientId, type, referenceId, pushPayload);
                }
            });
        } else {
            pushService.pushNow(recipientId, NOTIFICATIONS_DESTINATION, dto);
            pushSender.send(recipientId, type, referenceId, pushPayload);
        }
        return dto;
    }

    @Override
    public List<NotificationDto> getNotifications(UUID userId, Long beforeEpochMs, Integer size) {
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : 30;
        PageRequest page = PageRequest.of(0, pageSize);
        List<Notification> feedPage = beforeEpochMs == null
                ? notificationRepository.findFeed(userId, page)
                : notificationRepository.findFeedBefore(userId, toLocalDateTime(beforeEpochMs), page);
        return feedPage.stream().map(this::toDto).toList();
    }

    @Override
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countUnread(userId);
    }

    @Override
    public void markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AuthException(ErrorCode.CHAT_NOT_FOUND, "Notification not found"));
        if (!notification.getRecipient().getId().equals(userId)) {
            // IDOR guard: identical response for other people's notifications
            log.warn("User={} attempted to read notification={} owned by someone else", userId, notificationId);
            throw new AuthException(ErrorCode.CHAT_NOT_FOUND, "Notification not found");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
    }

    @Override
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId, LocalDateTime.now());
    }

    private NotificationDto toDto(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getType().name(),
                notification.getReferenceId(),
                notification.getPayload(),
                toEpoch(notification.getCreatedAt()),
                notification.getReadAt() != null ? toEpoch(notification.getReadAt()) : null);
    }

    private static long toEpoch(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDateTime toLocalDateTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }
}
