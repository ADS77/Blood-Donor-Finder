package com.bd.blooddonorfinder.security.filter;

import com.bd.blooddonorfinder.payload.response.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Per-IP rate limiter for the public API surface (auth and search endpoints).
 * Backed by Redis so limits hold across instances. Fails open when Redis is
 * unavailable so the limiter itself never takes the API down.
 */
@Slf4j
public class IpRateLimitFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "ratelimit:";
    private static final String BUCKET_AUTH = "auth";
    private static final String BUCKET_SEARCH = "search";

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final int authLimitPerMinute;
    private final int searchLimitPerMinute;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public IpRateLimitFilter(StringRedisTemplate redisTemplate,
                             boolean enabled,
                             int authLimitPerMinute,
                             int searchLimitPerMinute) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.authLimitPerMinute = authLimitPerMinute;
        this.searchLimitPerMinute = searchLimitPerMinute;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getServletPath();
        String bucket = bucketFor(path);
        if (bucket == null) {
            filterChain.doFilter(request, response);
            return;
        }

        int limit = BUCKET_AUTH.equals(bucket) ? authLimitPerMinute : searchLimitPerMinute;
        String clientIp = request.getRemoteAddr();

        try {
            String key = KEY_PREFIX + bucket + ":" + clientIp;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, 1, TimeUnit.MINUTES);
            }
            if (count != null && count > limit) {
                log.warn("Rate limit exceeded: ip={} bucket={} count={}/{}", clientIp, bucket, count, limit);
                sendTooManyRequests(response, limit);
                return;
            }
        } catch (Exception e) {
            // fail open: never let the limiter take the API down
            log.error("Rate limiter unavailable, failing open: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String bucketFor(String path) {
        if (path == null) return null;
        if (path.startsWith("/api/auth/")) return BUCKET_AUTH;
        if (path.startsWith("/api/search/")) return BUCKET_SEARCH;
        return null;
    }

    private void sendTooManyRequests(HttpServletResponse response, int limit) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        int status = org.springframework.http.HttpStatus.TOO_MANY_REQUESTS.value();
        response.setStatus(status);
        ApiErrorResponse errorResponse = new ApiErrorResponse();
        errorResponse.setStatus(status);
        errorResponse.setError("RATE_LIMITED");
        errorResponse.setMessage("Too many requests. Limit is " + limit + " per minute.");
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
