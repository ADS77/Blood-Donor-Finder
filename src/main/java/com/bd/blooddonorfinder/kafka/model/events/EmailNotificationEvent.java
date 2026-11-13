package com.bd.blooddonorfinder.kafka.model.events;

import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import com.bd.blooddonorfinder.kafka.model.topic.KafkaTopics;
import com.bd.blooddonorfinder.model.enums.EmailNotificationType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Kafka envelope for an email notification.
 *
 * <p>The envelope only carries non-sensitive metadata. The recipient, subject
 * and body live in {@link #payload}, which is AES-GCM encrypted JSON produced
 * by the outbox publisher. Keeping the envelope opaque means the OTP-bearing
 * messages are not readable from the outbox table, the Kafka topic, or a DLQ
 * without the application key.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class EmailNotificationEvent extends BaseEvent {

    private EmailNotificationType notificationType;

    /**
     * Base64 encoded, authenticated ciphertext of a serialized
     * {@code SendMailRequest}. Never log or expose this value.
     */
    private String payload;

    public EmailNotificationEvent(EmailNotificationType notificationType) {
        super(KafkaTopics.EMAIL_NOTIFICATION, "EmailNotificationEvent");
        this.notificationType = notificationType;
    }

    public static EmailNotificationEvent of(EmailNotificationType notificationType,
                                            String aggregateId,
                                            String encryptedPayload) {
        EmailNotificationEvent event = new EmailNotificationEvent(notificationType);
        event.setAggregateId(aggregateId);
        event.setPayload(encryptedPayload);
        return event;
    }
}
