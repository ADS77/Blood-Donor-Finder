package com.bd.blooddonorfinder.model.enums;

public enum AuditEvent {
    REGISTER,
    LOGIN,
    LOGOUT,
    OTP_SENT,
    OTP_FAILED,
    OTP_VERIFIED,
    REFRESH_TOKEN_ROTATED,
    REFRESH_REUSE_ATTACK,
    ACCOUNT_LOCKED,
    ROLE_CHANGED,
    TOKEN_REVOKED,
    ACCOUNT_DEACTIVATED
}
