package com.bd.blooddonorfinder.utils.constants;

public final class SecurityConstants {

    private SecurityConstants() {}

    // ── JWT Headers & Prefixes ──
    public static final String TOKEN_HEADER        = "Authorization";
    public static final String TOKEN_PREFIX         = "Bearer ";
    public static final String TOKEN_TYPE           = "token_type";
    public static final String ACCESS_TOKEN         = "ACCESS_TOKEN";
    public static final String REFRESH_TOKEN        = "REFRESH_TOKEN";

    // ── Redis Key Prefixes ──
    public static final String WHITELIST_ACCESS_PREFIX  = "auth:whitelist:access:";
    public static final String WHITELIST_REFRESH_PREFIX = "auth:whitelist:refresh:";
    public static final String BLACKLIST_PREFIX         = "auth:blacklist:";

    // ── Claim Keys ──
    public static final String CLAIM_ROLES     = "roles";
    public static final String CLAIM_USER_ID   = "uid";
    public static final String CLAIM_TOKEN_ID  = "jti";
}
