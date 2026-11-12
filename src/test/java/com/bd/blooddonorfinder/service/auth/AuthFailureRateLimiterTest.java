package com.bd.blooddonorfinder.service.auth;

import com.bd.blooddonorfinder.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthFailureRateLimiterTest {

    private static final String SCOPE = "login";
    private static final String IP = "127.0.0.1";
    private static final String SUBJECT = "user:5d6c8c2e";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private AuthFailureRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new AuthFailureRateLimiter(redisTemplate, 10, 5);
    }

    @Test
    void checkAllowed_shouldAllowBelowLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("9");

        assertDoesNotThrow(() -> rateLimiter.checkAllowed(SCOPE, IP, SUBJECT));
    }

    @Test
    void checkAllowed_shouldRejectAtLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("10");

        assertThrows(RateLimitExceededException.class,
                () -> rateLimiter.checkAllowed(SCOPE, IP, SUBJECT));
    }

    @Test
    void checkAllowed_shouldCarryRetryAfterFromKeyTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("10");
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(42L);

        RateLimitExceededException exception = assertThrows(RateLimitExceededException.class,
                () -> rateLimiter.checkAllowed(SCOPE, IP, SUBJECT));

        assertEquals(42L, exception.getRetryAfterSeconds());
    }

    @Test
    void recordFailure_shouldSetExpiryOnFirstFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        rateLimiter.recordFailure(SCOPE, IP, SUBJECT);

        verify(redisTemplate).expire(anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void recordFailure_shouldNotResetExpiryOnLaterFailures() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(2L);

        rateLimiter.recordFailure(SCOPE, IP, SUBJECT);

        verify(redisTemplate, never()).expire(anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void keys_shouldBeScopedByScopeIpAndSubject() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        rateLimiter.recordFailure("login", "10.0.0.1", "user:a");
        rateLimiter.recordFailure("login", "10.0.0.1", "user:b");
        rateLimiter.recordFailure("login", "10.0.0.2", "user:a");
        rateLimiter.recordFailure("verify-otp:LOGIN", "10.0.0.1", "user:a");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, times(4)).increment(keyCaptor.capture());
        Set<String> distinctKeys = new HashSet<>(keyCaptor.getAllValues());
        assertEquals(4, distinctKeys.size());
        assertNotEquals("user:a", distinctKeys.iterator().next());
    }
}
