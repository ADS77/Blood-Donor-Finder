package com.bd.blooddonorfinder.exception;

/**
 * Raised when an event could not be acknowledged by the Kafka broker.
 * The outbox relay treats this as a retryable publication failure.
 */
public class KafkaPublishException extends RuntimeException {

    public KafkaPublishException(String message) {
        super(message);
    }

    public KafkaPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
