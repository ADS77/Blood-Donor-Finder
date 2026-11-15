package com.bd.blooddonorfinder.service.push;

import com.bd.blooddonorfinder.model.enums.NotificationType;

import java.util.Map;
import java.util.UUID;

/**
 * Push port (hexagonal). The notification center depends only on this —
 * FCM today, APNs direct / a third party later, without touching service
 * logic. Implementations MUST be non-blocking for the caller (async
 * dispatch) and MUST never propagate failures into the business flow.
 */
public interface PushSender {

    boolean isEnabled();

    /**
     * Queues a push for delivery. Never throws for provider-side issues;
     * delivery problems are logged and handled inside the implementation.
     *
     * @param referenceId entity to deep-link to (request id, chat id, ...)
     * @param payload     per-type content, mirrors the Notification payload
     */
    void send(UUID userId, NotificationType type, UUID referenceId, Map<String, Object> payload);
}
