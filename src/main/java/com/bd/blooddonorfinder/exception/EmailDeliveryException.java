package com.bd.blooddonorfinder.exception;

/**
 * Raised when the SMTP transport rejects or fails to deliver a message.
 * The Kafka consumer retries this failure before dead-lettering the event.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
