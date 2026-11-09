package com.bd.blooddonorfinder.exception;

import com.bd.blooddonorfinder.exception.enums.ErrorCode;

public class UserNotVerifiedException extends AuthException{
    public UserNotVerifiedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
