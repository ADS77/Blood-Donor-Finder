package com.bd.blooddonorfinder.security.exception;


import com.bd.blooddonorfinder.utils.constants.TokenErrorReason;

import javax.security.sasl.AuthenticationException;

public class InvalidJwtTokenException extends AuthenticationException {
    private final TokenErrorReason reason;
    public InvalidJwtTokenException(String msg,  TokenErrorReason reason) {
        super(msg);
        this.reason = reason;
    }

    public InvalidJwtTokenException(String message, Throwable cause, TokenErrorReason reason) {
        super(message, cause);
        this.reason = reason;
    }

    public TokenErrorReason getReason(){
        return reason;
    }
}

