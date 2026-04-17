package com.bd.blooddonorfinder.utils.auth;

public enum TokenErrorReason {
    EXPIRED,
    MALFORMED,
    REVOKED,
    WRONG_TYPE,
    MISSING,
    SIGNATURE_INVALID
}
