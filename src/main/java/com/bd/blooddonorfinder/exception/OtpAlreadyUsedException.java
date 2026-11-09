package com.bd.blooddonorfinder.exception;

import com.bd.blooddonorfinder.exception.enums.ErrorCode;

public class OtpAlreadyUsedException extends AuthException{
    public OtpAlreadyUsedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
