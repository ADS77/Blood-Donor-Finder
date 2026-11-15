package com.bd.blooddonorfinder.security.exception;

import com.bd.blooddonorfinder.utils.constants.TokenErrorReason;
import org.springframework.security.core.AuthenticationException;

/**
 * Unchecked authentication failure raised while validating a JWT. Extending
 * Spring's {@link AuthenticationException} lets it flow through the security
 * filter chain and STOMP interceptors without checked-exception plumbing.
 */
public class InvalidJwtTokenException extends AuthenticationException {

    private final TokenErrorReason reason;

    public InvalidJwtTokenException(String message, TokenErrorReason reason) {
        super(message);
        this.reason = reason;
    }

    public InvalidJwtTokenException(String message, Throwable cause, TokenErrorReason reason) {
        super(message, cause);
        this.reason = reason;
    }

    public TokenErrorReason getReason() {
        return reason;
    }
}
