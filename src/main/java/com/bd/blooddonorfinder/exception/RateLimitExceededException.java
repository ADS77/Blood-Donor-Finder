package com.bd.blooddonorfinder.exception;

public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String msg) {
        this(msg, 0L);
    }

    public RateLimitExceededException(String msg, long retryAfterSeconds) {
        super(msg);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * @return seconds until the client may retry, or 0 when the caller does not
     * know the remaining window.
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
