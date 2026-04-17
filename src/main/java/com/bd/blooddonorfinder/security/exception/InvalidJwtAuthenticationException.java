package com.bd.blooddonorfinder.security.exception;

import org.springframework.security.core.AuthenticationException;

public class InvalidJwtAuthenticationException extends AuthenticationException {
    public InvalidJwtAuthenticationException(String message, Exception ex){
        super(message);
    }
}
