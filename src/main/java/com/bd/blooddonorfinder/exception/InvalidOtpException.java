package com.bd.blooddonorfinder.exception;

import com.bd.blooddonorfinder.exception.enums.ErrorCode;

public class InvalidOtpException extends AuthException{
    public InvalidOtpException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
