package com.bd.blooddonorfinder.exception;

import com.bd.blooddonorfinder.exception.enums.ErrorCode;

public class OtpExpiredException extends AuthException{
    public OtpExpiredException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
