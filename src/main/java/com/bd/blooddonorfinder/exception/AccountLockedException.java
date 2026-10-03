package com.bd.blooddonorfinder.exception;

import com.bd.blooddonorfinder.exception.enums.ErrorCode;

public class AccountLockedException extends AuthException{
    public AccountLockedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
