package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Service.CacheStatsPublisher;
import com.example.BackendArchitectureLab.Service.IBloomFilterService;
import com.example.BackendArchitectureLab.Util.NullValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCache;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CachePenetrationProtectionCacheTest {

    @Mock
    private RedisCache delegate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private IBloomFilterService bloomFilterService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private CacheStatsPublisher statsPublisher;

    private CachePenetrationProtectionCache cache;

    private final String cacheName = "test-cache";
    private final String testKey = "550e8400-e29b-41d4-a716-446655440000";
    private final String nullKey = "null:test-cache:550e8400-e29b-41d4-a716-446655440000";
    private final Duration nullTtl = Duration.ofMinutes(5);

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(bloomFilterService.mightContain(cacheName, testKey)).thenReturn(true);
        cache = new CachePenetrationProtectionCache(
                cacheName, delegate, stringRedisTemplate,
                bloomFilterService, redissonClient, nullTtl,
                statsPublisher);
    }

    @Test
    void get_WhenNullMarkerExists_ReturnsNull() {
        when(stringRedisTemplate.hasKey(nullKey)).thenReturn(true);

        Cache.ValueWrapper result = cache.get(testKey);

        assertNotNull(result);
        assertNull(result.get());
        verify(delegate, never()).get(any());
    }

    @Test
    void get_WhenBloomFilterSaysNo_ReturnsNullWithoutQueryingDelegate() {
        when(stringRedisTemplate.hasKey(nullKey)).thenReturn(false);
        when(bloomFilterService.mightContain(cacheName, testKey)).thenReturn(false);

        Cache.ValueWrapper result = cache.get(testKey);

        assertNull(result);
        verify(delegate, never()).get(any());
    }

    @Test
    void get_WhenCacheMiss_ReturnsNull() {
        when(stringRedisTemplate.hasKey(nullKey)).thenReturn(false);

        Cache.ValueWrapper result = cache.get(testKey);

        assertNull(result);
        verify(delegate).get(testKey);
    }

    @Test
    void get_WhenDelegateReturnsNullValue_ReturnsNull() {
        when(stringRedisTemplate.hasKey(nullKey)).thenReturn(false);
        when(delegate.get(testKey)).thenReturn(() -> NullValue.INSTANCE);

        Cache.ValueWrapper result = cache.get(testKey);

        assertNotNull(result);
        assertNull(result.get());
    }

    @Test
    void get_WhenDelegateReturnsActualValue_ReturnsValue() {
        String expectedValue = "real-value";
        when(stringRedisTemplate.hasKey(nullKey)).thenReturn(false);
        when(delegate.get(testKey)).thenReturn(() -> expectedValue);

        Cache.ValueWrapper result = cache.get(testKey);

        assertNotNull(result);
        assertEquals(expectedValue, result.get());
    }

    @Test
    void get_WhenHasKeyThrows_DegradesGracefully() {
        when(stringRedisTemplate.hasKey(nullKey)).thenThrow(new RuntimeException("Redis down"));

        Cache.ValueWrapper result = cache.get(testKey);

        assertNull(result);
        verify(delegate).get(testKey);
    }

    @Test
    void get_WhenBloomFilterThrows_DegradesGracefully() {
        when(stringRedisTemplate.hasKey(nullKey)).thenReturn(false);
        when(bloomFilterService.mightContain(cacheName, testKey)).thenThrow(new RuntimeException("BF down"));

        Cache.ValueWrapper result = cache.get(testKey);

        assertNull(result);
        verify(delegate).get(testKey);
    }

    @Test
    void get_WhenHasKeyThrowsAndDelegateReturnsValue_ReturnsValue() {
        String expectedValue = "real-value";
        when(stringRedisTemplate.hasKey(nullKey)).thenThrow(new RuntimeException("Redis down"));
        when(delegate.get(testKey)).thenReturn(() -> expectedValue);

        Cache.ValueWrapper result = cache.get(testKey);

        assertNotNull(result);
        assertEquals(expectedValue, result.get());
    }

    @Test
    void put_WhenSetNullMarkerThrows_StillEvictsDelegate() {
        doThrow(new RuntimeException("Redis down")).when(valueOps).set(eq(nullKey), anyString(), any(Duration.class));

        cache.put(testKey, null);

        verify(delegate).evict(testKey);
    }

    @Test
    void put_WhenBloomFilterAddThrows_StillPutsToDelegate() {
        doThrow(new RuntimeException("BF down")).when(bloomFilterService).add(cacheName, testKey);
        String value = "real-value";

        cache.put(testKey, value);

        verify(delegate).put(testKey, value);
    }

    @Test
    void put_WithNullValue_StoresNullMarkerAndEvictsDelegate() {
        cache.put(testKey, null);

        verify(valueOps).set(nullKey, "NULL_MARKER", nullTtl);
        verify(delegate).evict(testKey);
        verify(delegate, never()).put(any(), any());
        verify(bloomFilterService, never()).add(anyString(), anyString());
    }

    @Test
    void put_WithActualValue_AddsToBloomFilterAndDelegate() {
        String value = "real-value";

        cache.put(testKey, value);

        verify(stringRedisTemplate).delete(nullKey);
        verify(bloomFilterService).add(cacheName, testKey);
        verify(delegate).put(testKey, value);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void evict_ClearsNullMarkerAndDelegate() {
        cache.evict(testKey);

        verify(stringRedisTemplate).delete(nullKey);
        verify(delegate).evict(testKey);
    }

    @Test
    void evict_WhenDeleteThrows_StillEvictsDelegate() {
        doThrow(new RuntimeException("Redis down")).when(stringRedisTemplate).delete(nullKey);

        cache.evict(testKey);

        verify(delegate).evict(testKey);
    }

    @Test
    void getWithLoader_WhenBloomFilterSaysNo_ReturnsNullWithoutCallingLoader() throws Exception {
        when(stringRedisTemplate.hasKey(nullKey)).thenReturn(false);
        when(bloomFilterService.mightContain(cacheName, testKey)).thenReturn(false);
        Callable<String> loader = mock(Callable.class);

        Object result = cache.get(testKey, loader);

        assertNull(result);
        verify(loader, never()).call();
    }

    @Test
    void getWithLoader_WhenNullMarkerExists_ReturnsNullWithoutQuerying() throws Exception {
        when(stringRedisTemplate.hasKey(nullKey)).thenReturn(true);
        Callable<String> loader = mock(Callable.class);

        Object result = cache.get(testKey, loader);

        assertNull(result);
        verify(loader, never()).call();
    }

    @Test
    void getName_ReturnsCorrectName() {
        assertEquals(cacheName, cache.getName());
    }
}
