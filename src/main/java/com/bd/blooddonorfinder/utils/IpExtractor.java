package com.bd.blooddonorfinder.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the client IP address.
 *
 * <p>Do not read {@code X-Forwarded-For} or similar headers directly here: any
 * client can forge them. Trusted proxies are handled by Tomcat's
 * {@code RemoteIpValve}, which rewrites {@link HttpServletRequest#getRemoteAddr()}
 * only when the immediate peer is in the configured allow-list.</p>
 */
public final class IpExtractor {

    private IpExtractor() {
    }

    public static String extract(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
