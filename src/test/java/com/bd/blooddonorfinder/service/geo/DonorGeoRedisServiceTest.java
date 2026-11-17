package com.bd.blooddonorfinder.service.geo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DonorGeoRedisServiceTest {

    private static final String KEY = "geo:donors";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private GeoOperations<String, String> geoOperations;

    private DonorGeoRedisService service;

    @BeforeEach
    void setUp() {
        service = new DonorGeoRedisService(redisTemplate);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "geoKey", KEY);
        ReflectionTestUtils.setField(service, "maxResults", 500);
    }

    @Test
    void upsert_disabledIsNoOp() {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.upsert("u1", 23.8, 90.4);

        verify(redisTemplate, never()).opsForGeo();
    }

    @Test
    void upsert_addsPointWhenEnabled() {
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);

        service.upsert("u1", 23.8, 90.4);

        verify(geoOperations).add(eq(KEY), any(org.springframework.data.geo.Point.class), eq("u1"));
    }

    @Test
    void upsert_swallowsRedisFailure() {
        when(redisTemplate.opsForGeo()).thenThrow(new IllegalStateException("cluster down"));

        // must not propagate — Redis is a cache, ES/DB are the source of truth
        service.upsert("u1", 23.8, 90.4);
    }

    @Test
    void searchNearest_disabledReturnsNull() {
        ReflectionTestUtils.setField(service, "enabled", false);

        assertNull(service.searchNearest(23.8, 90.4, 5.0));
    }

    @Test
    void searchNearest_missingKeyReturnsNullForFallback() {
        when(redisTemplate.hasKey(KEY)).thenReturn(false);

        assertNull(service.searchNearest(23.8, 90.4, 5.0));
    }

    @Test
    void searchNearest_returnsMembersNearestFirst() {
        when(redisTemplate.hasKey(KEY)).thenReturn(true);
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);

        GeoResult<RedisGeoCommands.GeoLocation<String>> far =
                new GeoResult<>(new RedisGeoCommands.GeoLocation<>("u2", null),
                        new org.springframework.data.geo.Distance(4.0,
                                org.springframework.data.geo.Metrics.KILOMETERS));
        GeoResult<RedisGeoCommands.GeoLocation<String>> near =
                new GeoResult<>(new RedisGeoCommands.GeoLocation<>("u1", null),
                        new org.springframework.data.geo.Distance(0.8,
                                org.springframework.data.geo.Metrics.KILOMETERS));
        when(geoOperations.search(eq(KEY), any(GeoReference.class), any(
                org.springframework.data.geo.Distance.class), any(
                RedisGeoCommands.GeoSearchCommandArgs.class)))
                .thenReturn(new GeoResults<>(List.of(near, far)));

        List<String> ids = service.searchNearest(23.8, 90.4, 5.0);

        assertEquals(List.of("u1", "u2"), ids);
    }

    @Test
    void searchNearest_redisErrorReturnsNullForFallback() {
        when(redisTemplate.hasKey(KEY)).thenThrow(new IllegalStateException("timeout"));

        assertNull(service.searchNearest(23.8, 90.4, 5.0));
    }
}
