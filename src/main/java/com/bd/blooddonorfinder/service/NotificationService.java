package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.model.enums.NotificationType;
import com.bd.blooddonorfinder.payload.response.NotificationDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Facebook-style notification center: every product event becomes exactly one
 * persisted notification + realtime WS push. This service is the ONLY write
 * path — no component emails users for product events; the daily digest
 * worker is the sole email producer.
 */
public interface NotificationService {

    /**
     * Creates a notification and pushes it to the recipient's
     * {@code /user/queue/notifications} after the current transaction commits.
     *
     * @param referenceId entity to deep-link to (chat id, request id, ...)
     * @param payload     per-type content, e.g. {senderName, preview, action}
     */
    NotificationDto notify(UUID recipientId, NotificationType type, UUID referenceId, Map<String, Object> payload);

    /** Feed, newest first; pass {@code beforeEpochMs} for keyset pagination (load-more). */
    List<NotificationDto> getNotifications(UUID userId, Long beforeEpochMs, Integer size);

    long getUnreadCount(UUID userId);

    void markRead(UUID userId, UUID notificationId);

    /** Marks everything read; returns the number of updated rows. */
    int markAllRead(UUID userId);
}
