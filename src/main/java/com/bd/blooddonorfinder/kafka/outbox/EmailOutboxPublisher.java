package com.bd.blooddonorfinder.kafka.outbox;

import com.bd.blooddonorfinder.exception.EmailPayloadException;
import com.bd.blooddonorfinder.kafka.model.events.EmailNotificationEvent;
import com.bd.blooddonorfinder.model.EmailOutboxMessage;
import com.bd.blooddonorfinder.model.enums.EmailNotificationType;
import com.bd.blooddonorfinder.payload.request.SendMailRequest;
import com.bd.blooddonorfinder.repository.EmailOutboxRepository;
import com.bd.blooddonorfinder.utils.EmailPayloadCipher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Writes an email notification to the transactional outbox.
 *
 * <p>This is the only component the business code calls. It participates in
 * the caller's transaction (propagation {@code REQUIRED}), so the outbox row
 * commits or rolls back with the business change. It never talks to SMTP.</p>
 */
@Service
@Slf4j
public class EmailOutboxPublisher {

    private final EmailOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final EmailPayloadCipher payloadCipher;

    public EmailOutboxPublisher(EmailOutboxRepository repository,
                                ObjectMapper objectMapper,
                                EmailPayloadCipher payloadCipher) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.payloadCipher = payloadCipher;
    }

    /**
     * Durably enqueues a notification and returns its event id.
     *
     * @throws IllegalArgumentException when the request contains content that
     *         cannot be represented in the event (attachments/inline images)
     * @throws EmailPayloadException when the request cannot be serialized or
     *         encrypted
     */
    @Transactional
    public String enqueue(SendMailRequest request, EmailNotificationType type, UUID aggregateId) {
        validate(request, type);

        try {
            String requestJson = objectMapper.writeValueAsString(request);
            String encryptedPayload = payloadCipher.encrypt(requestJson);
            String aggregate = aggregateId != null ? aggregateId.toString() : "unknown";

            EmailNotificationEvent event = EmailNotificationEvent.of(type, aggregate, encryptedPayload);
            String envelope = objectMapper.writeValueAsString(event);
            repository.save(EmailOutboxMessage.pending(event, envelope));

            log.info("Queued email notification: eventId={}, type={}, aggregateId={}",
                    event.getEventId(), type, aggregate);
            return event.getEventId();
        } catch (JsonProcessingException e) {
            throw new EmailPayloadException("Failed to serialize email notification", e);
        }
    }

    private void validate(SendMailRequest request, EmailNotificationType type) {
        if (request == null) {
            throw new IllegalArgumentException("Email request is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("Email notification type is required");
        }
        if (request.getMailTo() == null || request.getMailTo().isEmpty()) {
            throw new IllegalArgumentException("At least one email recipient is required");
        }
        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            throw new IllegalArgumentException("Attachments are not supported by the email outbox");
        }
        if (request.getInlineImages() != null && !request.getInlineImages().isEmpty()) {
            throw new IllegalArgumentException("Inline images are not supported by the email outbox");
        }
    }
}
