package com.bd.blooddonorfinder.service.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Lightweight per-user online presence backed by Redis (shared by all nodes).
 * The key holds the owning WS session id so a stale disconnect event from
 * another session can't clobber live state. TTL expiry covers crashed nodes.
 */
@Service
@Slf4j
public class PresenceService {

    static final String PRESENCE_KEY = "ws:presence:%s";

    private final StringRedisTemplate redisTemplate;
    private final Duration presenceTtl;

    public PresenceService(StringRedisTemplate redisTemplate,
                           @Value("${app.chat.presence.ttl-seconds:300}") long presenceTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.presenceTtl = Duration.ofSeconds(presenceTtlSeconds);
    }

    public void connect(UUID userId, String sessionId) {
        redisTemplate.opsForValue().set(key(userId), sessionId, presenceTtl);
    }

    /** Refreshes TTL only when this session still owns the presence key. */
    public void refresh(UUID userId, String sessionId) {
        String key = key(userId);
        if (sessionId.equals(redisTemplate.opsForValue().get(key))) {
            redisTemplate.expire(key, presenceTtl);
        }
    }

    /** Removes presence only when this session still owns it (multi-device safe). */
    public void disconnect(UUID userId, String sessionId) {
        String key = key(userId);
        if (sessionId.equals(redisTemplate.opsForValue().get(key))) {
            redisTemplate.delete(key);
        }
    }

    public boolean isOnline(UUID userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(userId)));
    }

    private static String key(UUID userId) {
        return PRESENCE_KEY.formatted(userId);
    }
}
