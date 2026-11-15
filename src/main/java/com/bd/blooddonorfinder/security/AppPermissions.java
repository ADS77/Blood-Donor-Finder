package com.bd.blooddonorfinder.security;

public final class AppPermissions {
    private AppPermissions(){}

    public static final String DONOR_READ = "donor:read";
    public static final String DONOR_CREATE = "donor:create";
    public static final String DONOR_UPDATE = "donor:update";
    public static final String DONOR_DELETE = "donor:delete";
    public static final String ORG_MANAGE = "org:manage";
    public static final String ORG_READ = "org:read";
    public static final String ORG_CREATE = "org:create";
    public static final String ORG_UPDATE = "org:update";
    public static final String ORG_DELETE = "org:delete";
    public static final String SUPER_ADMIN = "super:admin";
    public static final String ROLE_PREFIX = "ROLE_";
    public static final String ROLE_DONOR = "ROLE_DONOR";
    public static final String ROLE_ORG_ADMIN = "ROLE_ORG_ADMIN";
    public static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    /**
     * Legacy constant — the roles table seeds {@code ROLE_SYSTEM_ADMIN}, not
     * {@code ROLE_SUPER_ADMIN}. Kept only for backward-compatible admin
     * seeding; never use it for new @PreAuthorize checks.
     */
    public static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";
}
