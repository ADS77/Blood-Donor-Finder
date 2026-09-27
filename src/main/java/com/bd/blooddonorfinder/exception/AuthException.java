package com.bd.blooddonorfinder.exception;

public class AuthException extends RuntimeException{
    private final ErrorCode errorCode;

    public AuthException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
