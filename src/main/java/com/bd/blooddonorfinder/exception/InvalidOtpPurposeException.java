package com.bd.blooddonorfinder.exception;

import com.bd.blooddonorfinder.exception.enums.ErrorCode;

public class InvalidOtpPurposeException extends AuthException{
    public InvalidOtpPurposeException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
