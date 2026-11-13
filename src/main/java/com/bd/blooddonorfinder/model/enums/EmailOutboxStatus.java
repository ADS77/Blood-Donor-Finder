package com.bd.blooddonorfinder.model.enums;

/**
 * Lifecycle of a durable email notification.
 *
 * <pre>
 * PENDING -> PROCESSING -> PUBLISHED -> DELIVERED
 *                |              |
 *                +-> PENDING    +-> FAILED
 *                +-> FAILED
 * </pre>
 *
 * <p>PENDING rows are waiting for the relay, PROCESSING rows are claimed by a
 * relay instance, PUBLISHED means Kafka acknowledged the event, DELIVERED
 * means the consumer handed the message to SMTP, and FAILED means publication
 * or delivery exhausted its retry budget.</p>
 */
public enum EmailOutboxStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    DELIVERED,
    FAILED
}
