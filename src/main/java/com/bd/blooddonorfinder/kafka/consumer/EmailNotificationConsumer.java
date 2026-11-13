package com.bd.blooddonorfinder.kafka.consumer;

import com.bd.blooddonorfinder.exception.EmailDeliveryException;
import com.bd.blooddonorfinder.exception.EmailPayloadException;
import com.bd.blooddonorfinder.kafka.idempotency.ProcessedEventStore;
import com.bd.blooddonorfinder.kafka.model.events.EmailNotificationEvent;
import com.bd.blooddonorfinder.kafka.outbox.EmailOutboxDeliveryTracker;
import com.bd.blooddonorfinder.payload.request.SendMailRequest;
import com.bd.blooddonorfinder.service.MailService;
import com.bd.blooddonorfinder.utils.EmailPayloadCipher;
import com.bd.blooddonorfinder.utils.MailUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Consumes durable email notifications and performs the actual SMTP send.
 *
 * <p>This is the only notification path that talks to the mail transport. It
 * is deliberately idempotent: the processed-event marker is written only
 * <em>after</em> a successful send, so a redelivery caused by a crash between
 * SMTP and the marker is suppressed, while a failed send is retried by the
 * container error handler and finally dead-lettered.</p>
 */
@Component
@Slf4j
public class EmailNotificationConsumer {

    private final MailService mailService;
    private final ProcessedEventStore processedEventStore;
    private final EmailPayloadCipher payloadCipher;
    private final ObjectMapper objectMapper;
    private final EmailOutboxDeliveryTracker deliveryTracker;

    public EmailNotificationConsumer(MailService mailService,
                                     ProcessedEventStore processedEventStore,
                                     EmailPayloadCipher payloadCipher,
                                     ObjectMapper objectMapper,
                                     EmailOutboxDeliveryTracker deliveryTracker) {
        this.mailService = mailService;
        this.processedEventStore = processedEventStore;
        this.payloadCipher = payloadCipher;
        this.objectMapper = objectMapper;
        this.deliveryTracker = deliveryTracker;
    }

    @KafkaListener(
            topics = "${kafka.topics.email-notification}.${kafka.topic.version}",
            groupId = "${kafka.consumer.email.group-id}",
            containerFactory = "emailNotificationListenerContainerFactory"
    )
    public void onEmailNotification(EmailNotificationEvent event, Acknowledgment acknowledgment) {
        if (event == null || event.getEventId() == null) {
            throw new EmailPayloadException("Email notification event or event id is missing");
        }

        if (processedEventStore.isProcessed(event.getEventId())) {
            log.info("Skipping duplicate email notification: eventId={}", event.getEventId());
            acknowledgment.acknowledge();
            return;
        }

        SendMailRequest request = decode(event);

        boolean sent;
        try {
            sent = mailService.sendMail(request);
        } catch (RuntimeException e) {
            throw new EmailDeliveryException(
                    "Email transport failed for eventId=" + event.getEventId(), e);
        }

        if (!sent) {
            throw new EmailDeliveryException(
                    "Email transport rejected eventId=" + event.getEventId());
        }

        // Record delivery first: if this update fails we prefer a duplicate
        // send (at-least-once) over losing the notification permanently.
        deliveryTracker.markDelivered(event.getEventId());
        processedEventStore.markProcessedIfAbsent(
                event.getEventId(), event.getTopicName(), event.getAggregateId());
        acknowledgment.acknowledge();

        log.info("Email notification delivered: eventId={}, type={}, recipients={}",
                event.getEventId(), event.getNotificationType(), maskRecipients(request));
    }

    private SendMailRequest decode(EmailNotificationEvent event) {
        try {
            String requestJson = payloadCipher.decrypt(event.getPayload());
            return objectMapper.readValue(requestJson, SendMailRequest.class);
        } catch (Exception e) {
            throw new EmailPayloadException(
                    "Could not decode email payload for eventId=" + event.getEventId(), e);
        }
    }

    private String maskRecipients(SendMailRequest request) {
        if (request.getMailTo() == null || request.getMailTo().isEmpty()) {
            return "[]";
        }
        return request.getMailTo().stream()
                .map(MailUtils::maskEmail)
                .collect(Collectors.joining(","));
    }
}
