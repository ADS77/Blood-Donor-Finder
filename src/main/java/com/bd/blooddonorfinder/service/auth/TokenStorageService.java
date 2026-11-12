package com.bd.blooddonorfinder.service.auth;

import com.bd.blooddonorfinder.utils.constants.SecurityConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Stores the allow-list (whitelist) of issued JWTs in Redis.
 *
 * <p>Each token is stored under its own key with a TTL equal to the remaining
 * token lifetime. In addition, every user has an index set containing the
 * {@code jti} values of their active tokens. The index allows all tokens for a
 * user to be revoked without scanning the entire Redis keyspace (which is both
 * expensive and unsafe on a clustered Redis).</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TokenStorageService {

    private final StringRedisTemplate redisTemplate;

    public void whitelistAccessToken(String jti, String userId, long ttlMillis) {
        whitelist(SecurityConstants.WHITELIST_ACCESS_PREFIX,
                SecurityConstants.USER_ACCESS_INDEX_PREFIX,
                jti, userId, ttlMillis);
    }

    public void whitelistRefreshToken(String jti, String userId, long ttlMillis) {
        whitelist(SecurityConstants.WHITELIST_REFRESH_PREFIX,
                SecurityConstants.USER_REFRESH_INDEX_PREFIX,
                jti, userId, ttlMillis);
    }

    public boolean isAccessTokenWhitelisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey(SecurityConstants.WHITELIST_ACCESS_PREFIX, jti)));
    }

    public boolean isRefreshTokenWhitelisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey(SecurityConstants.WHITELIST_REFRESH_PREFIX, jti)));
    }

    public void revokeAccessToken(String jti) {
        revoke(SecurityConstants.WHITELIST_ACCESS_PREFIX,
                SecurityConstants.USER_ACCESS_INDEX_PREFIX,
                jti);
    }

    public void revokeRefreshToken(String jti) {
        revoke(SecurityConstants.WHITELIST_REFRESH_PREFIX,
                SecurityConstants.USER_REFRESH_INDEX_PREFIX,
                jti);
    }

    /**
     * Atomically removes a refresh token from the whitelist.
     *
     * @return {@code true} if this caller was the first to consume the token;
     *         {@code false} if the token had already been rotated or revoked
     */
    public boolean consumeRefreshToken(String jti) {
        if (!StringUtils.hasText(jti)) {
            return false;
        }
        String key = tokenKey(SecurityConstants.WHITELIST_REFRESH_PREFIX, jti);
        String userId = redisTemplate.opsForValue().getAndDelete(key);
        if (StringUtils.hasText(userId)) {
            redisTemplate.opsForSet().remove(
                    indexKey(SecurityConstants.USER_REFRESH_INDEX_PREFIX, userId), jti);
        }
        return userId != null;
    }

    /**
     * Revokes every access and refresh token issued to the supplied user.
     *
     * @param userId stable user identifier stored alongside the token
     */
    public void revokeAllTokensForUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        revokeAll(SecurityConstants.WHITELIST_ACCESS_PREFIX,
                SecurityConstants.USER_ACCESS_INDEX_PREFIX,
                userId);
        revokeAll(SecurityConstants.WHITELIST_REFRESH_PREFIX,
                SecurityConstants.USER_REFRESH_INDEX_PREFIX,
                userId);
        log.info("Revoked all tokens for user={}", userId);
    }

    private void whitelist(String tokenPrefix,
                           String indexPrefix,
                           String jti,
                           String userId,
                           long ttlMillis) {
        if (!StringUtils.hasText(jti)) {
            throw new IllegalArgumentException("Token id (jti) must not be blank");
        }
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("User id must not be blank");
        }
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("Token TTL must be greater than zero");
        }

        Duration ttl = Duration.ofMillis(ttlMillis);
        String indexKey = indexKey(indexPrefix, userId);

        // Write the index first. If the token write then fails, the login fails
        // and no usable token is left behind.
        redisTemplate.opsForSet().add(indexKey, jti);
        extendIndexTtl(indexKey, ttl);
        redisTemplate.opsForValue().set(tokenKey(tokenPrefix, jti), userId, ttl);

        log.debug("Whitelisted token jti={} for user={}, ttl={}ms", jti, userId, ttlMillis);
    }

    private void revoke(String tokenPrefix, String indexPrefix, String jti) {
        if (!StringUtils.hasText(jti)) {
            return;
        }
        String key = tokenKey(tokenPrefix, jti);
        String userId = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);

        if (StringUtils.hasText(userId)) {
            redisTemplate.opsForSet().remove(indexKey(indexPrefix, userId), jti);
        }
        log.debug("Revoked token jti={}", jti);
    }

    private void revokeAll(String tokenPrefix, String indexPrefix, String userId) {
        String indexKey = indexKey(indexPrefix, userId);
        Set<String> jtis = redisTemplate.opsForSet().members(indexKey);

        if (jtis != null) {
            // Delete one key at a time: multi-key commands are not safe when
            // the keys live in different Redis Cluster slots.
            jtis.forEach(jti -> redisTemplate.delete(tokenKey(tokenPrefix, jti)));
        }
        redisTemplate.delete(indexKey);
    }

    /**
     * Keeps the per-user index alive at least as long as the longest-lived
     * token it contains. Refreshing the TTL to a shorter value would make the
     * index expire before older refresh tokens, causing {@link #revokeAllTokensForUser}
     * to miss tokens.
     */
    private void extendIndexTtl(String indexKey, Duration ttl) {
        Long currentTtlMillis = redisTemplate.getExpire(indexKey, TimeUnit.MILLISECONDS);
        if (currentTtlMillis == null || currentTtlMillis < 0 || currentTtlMillis < ttl.toMillis()) {
            redisTemplate.expire(indexKey, ttl);
        }
    }

    private String tokenKey(String prefix, String jti) {
        return prefix + jti;
    }

    private String indexKey(String prefix, String userId) {
        return prefix + userId;
    }
}
