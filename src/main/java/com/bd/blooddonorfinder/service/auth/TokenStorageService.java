package com.bd.blooddonorfinder.service.auth;

import com.bd.blooddonorfinder.utils.constants.SecurityConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TokenStorageService {
    private final StringRedisTemplate redisTemplate;

    public void whitelistAccessToken(String jti, String username, long ttlMillis) {
        String key = SecurityConstants.WHITELIST_ACCESS_PREFIX + jti;
        redisTemplate.opsForValue().set(key, username, ttlMillis, TimeUnit.MILLISECONDS);
        log.debug("Whitelisted access token jti={} for user={}, ttl={}ms", jti, username, ttlMillis);
    }

    public void whitelistRefreshToken(String jti, String username, long ttlMillis) {
        String key = SecurityConstants.WHITELIST_REFRESH_PREFIX + jti;
        redisTemplate.opsForValue().set(key, username, ttlMillis, TimeUnit.MILLISECONDS);
        log.debug("Whitelisted refresh token jti={} for user={}, ttl={}ms", jti, username, ttlMillis);
    }

    public boolean isAccessTokenWhitelisted(String jti) {
        String key = SecurityConstants.WHITELIST_ACCESS_PREFIX + jti;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public boolean isRefreshTokenWhitelisted(String jti) {
        String key = SecurityConstants.WHITELIST_REFRESH_PREFIX + jti;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void revokeAccessToken(String jti) {
        String key = SecurityConstants.WHITELIST_ACCESS_PREFIX + jti;
        redisTemplate.delete(key);
        log.debug("Revoked access token jti={}", jti);
    }

    public void revokeRefreshToken(String jti) {
        String key = SecurityConstants.WHITELIST_REFRESH_PREFIX + jti;
        redisTemplate.delete(key);
        log.debug("Revoked refresh token jti={}", jti);
    }

    public void revokeAllTokensForUser(String username) {
        var accessKeys  = redisTemplate.keys(SecurityConstants.WHITELIST_ACCESS_PREFIX + "*");
        var refreshKeys = redisTemplate.keys(SecurityConstants.WHITELIST_REFRESH_PREFIX + "*");

        revokeMatchingKeys(accessKeys, username);
        revokeMatchingKeys(refreshKeys, username);
        log.info("Revoked all tokens for user={}", username);
    }

    private void revokeMatchingKeys(Set<String> keys, String username) {
        if (keys == null) return;
        for (String key : keys) {
            String storedUsername = redisTemplate.opsForValue().get(key);
            if (username.equals(storedUsername)) {
                redisTemplate.delete(key);
            }
        }
    }


}
