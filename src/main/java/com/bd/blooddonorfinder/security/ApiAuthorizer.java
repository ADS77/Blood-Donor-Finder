package com.bd.blooddonorfinder.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Authorization entry point referenced from {@code @PreAuthorize} expressions
 * as {@code @authz.*}.
 *
 * <p>Deliberately a plain class (no Spring Security machinery inside) so the
 * rules are unit-testable without a Spring context. Every check accepts the
 * standard {@link Authentication} object.
 *
 * <p>Authority strings are raw role names as seeded in the {@code roles} table
 * ({@code ROLE_DONOR}, {@code ROLE_ORG_ADMIN}, {@code ROLE_SYSTEM_ADMIN}) —
 * see {@code GrantedAuthorityDefaults("")} in SecurityConfig.
 */
@Component("authz")
public class ApiAuthorizer {

    /**
     * True only for a real, authenticated user backed by {@link UserPrincipal}.
     * Anonymous tokens and unauthenticated markers are rejected here even if a
     * filter chain misconfiguration ever lets them through.
     */
    public boolean isAuthenticated(Authentication authentication) {
        return isRealUser(authentication);
    }

    /**
     * True when the caller holds at least one of the given roles.
     * Example: {@code @PreAuthorize("@authz.hasAnyRole(authentication, 'ROLE_DONOR')")}.
     */
    public boolean hasAnyRole(Authentication authentication, String... roles) {
        if (!isRealUser(authentication) || roles == null || roles.length == 0) {
            return false;
        }
        Set<String> wanted = new HashSet<>(Arrays.asList(roles));
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (wanted.contains(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRealUser(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal;
    }
}
