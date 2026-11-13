package com.bd.blooddonorfinder.exception;

/**
 * Raised when an email payload cannot be encrypted, decrypted or parsed.
 * Retrying such a message cannot succeed, so the consumer error handler
 * dead-letters it immediately instead of exhausting the retry budget.
 */
public class EmailPayloadException extends RuntimeException {

    public EmailPayloadException(String message) {
        super(message);
    }

    public EmailPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
