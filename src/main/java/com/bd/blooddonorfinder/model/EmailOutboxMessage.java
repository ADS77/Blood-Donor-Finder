package com.bd.blooddonorfinder.model;

import com.bd.blooddonorfinder.kafka.model.events.EmailNotificationEvent;
import com.bd.blooddonorfinder.model.enums.EmailOutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable record of an email notification that must be published to Kafka.
 *
 * <p>The row is written in the same database transaction as the business
 * change that produced it. A relay publishes it asynchronously, so a broker
 * outage can never roll back or lose the business operation, and a database
 * rollback can never emit an email for an operation that did not happen.</p>
 */
@Entity
@Table(name = "email_outbox")
@Getter
@Setter
@NoArgsConstructor
public class EmailOutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false, unique = true, length = 255)
    private String eventId;

    @Column(name = "aggregate_id", nullable = false, updatable = false, length = 255)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    @Column(name = "topic_name", nullable = false, updatable = false, length = 150)
    private String topicName;

    /**
     * Serialized {@link EmailNotificationEvent} envelope. The sensitive mail
     * content inside the envelope is encrypted before it reaches this column.
     */
    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EmailOutboxStatus status = EmailOutboxStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static EmailOutboxMessage pending(EmailNotificationEvent event, String payload) {
        EmailOutboxMessage message = new EmailOutboxMessage();
        message.setEventId(event.getEventId());
        message.setAggregateId(event.getAggregateId());
        message.setEventType(event.getEventType());
        message.setTopicName(event.getTopicName());
        message.setPayload(payload);
        message.setStatus(EmailOutboxStatus.PENDING);
        message.setAttempts(0);
        message.setNextAttemptAt(Instant.now());
        return message;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.nextAttemptAt == null) {
            this.nextAttemptAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
