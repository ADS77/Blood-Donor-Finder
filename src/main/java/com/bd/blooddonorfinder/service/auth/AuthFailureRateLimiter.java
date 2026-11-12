package com.bd.blooddonorfinder.service.auth;

import com.bd.blooddonorfinder.exception.RateLimitExceededException;
import com.bd.blooddonorfinder.utils.HashUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed rate limit for unsuccessful authentication requests.
 *
 * <p>Counters are scoped by operation, client IP and subject. For password
 * login the subject is the resolved user id (or a shared bucket for unknown
 * identifiers); for OTP verification the scope includes the OTP purpose and
 * the subject is the user id. Every failed request counts, including bad
 * credentials, unknown users, invalid/expired OTPs and unexpected errors.
 * Successful requests do not reset the counter: otherwise an attacker could
 * reset their budget by authenticating with their own account between
 * attempts.</p>
 */
@Service
public class AuthFailureRateLimiter {

    private static final String KEY_PREFIX = "auth:failure:";

    private final StringRedisTemplate redisTemplate;
    private final int maxAttempts;
    private final Duration window;

    public AuthFailureRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${security.auth.failure.max-attempts:10}") int maxAttempts,
            @Value("${security.auth.failure.window-minutes:5}") long windowMinutes) {
        this.redisTemplate = redisTemplate;
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    public void checkAllowed(String scope, String clientIp, String subject) {
        String key = generateKey(scope, clientIp, subject);
        if (current(key) >= maxAttempts) {
            throw new RateLimitExceededException(
                    "Too many unsuccessful authentication attempts. Try again later.",
                    secondsUntilReset(key));
        }
    }

    public void recordFailure(String scope, String clientIp, String subject) {
        String key = generateKey(scope, clientIp, subject);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }
    }

    private long current(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private long secondsUntilReset(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0L;
    }

    private String generateKey(String scope, String clientIp, String subject) {
        String normalizedScope = StringUtils.hasText(scope) ? scope.trim() : "unknown";
        String normalizedIp = StringUtils.hasText(clientIp) ? clientIp.trim() : "unknown";
        String normalizedSubject = StringUtils.hasText(subject) ? subject.trim() : "unknown";
        return KEY_PREFIX + HashUtils.sha256Hex(
                normalizedScope + "|" + normalizedIp + "|" + normalizedSubject);
    }
}
