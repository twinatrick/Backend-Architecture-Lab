package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Service.IBloomFilterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CacheAvalancheTest {

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
    private RLock lock;

    private CachePenetrationProtectionCache cache;

    private final String cacheName = "avalanche-test";
    private final String testKey = "test-key";
    private final String nullKey = "null:avalanche-test:test-key";
    private final Duration nullTtl = Duration.ofMinutes(5);
    private final String cachedValue = "cached-result";

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(bloomFilterService.mightContain(anyString(), anyString())).thenReturn(true);
        cache = new CachePenetrationProtectionCache(
                cacheName, delegate, stringRedisTemplate,
                bloomFilterService, redissonClient, nullTtl);
    }

    @Test
    void basicCachePenetration_NoNullMarker_CacheMiss_ReturnsNull() {
        when(stringRedisTemplate.hasKey(nullKey)).thenReturn(false);
        when(delegate.get(testKey)).thenReturn(null);

        Cache.ValueWrapper result = cache.get(testKey);

        assertNull(result);
        verify(delegate).get(testKey);
    }

    @Test
    void repeatedCachePenetration_NullMarkerExists_ReturnsNull() {
        when(stringRedisTemplate.hasKey(nullKey)).thenReturn(true);

        Cache.ValueWrapper first = cache.get(testKey);
        assertNotNull(first);
        assertNull(first.get());

        Cache.ValueWrapper second = cache.get(testKey);
        assertNotNull(second);
        assertNull(second.get());

        verify(delegate, never()).get(any());
    }

    @Test
    void cachePenetration_MultipleDifferentInvalidKeys_AllResultInCacheMiss() {
        int keyCount = 10;
        for (int i = 0; i < keyCount; i++) {
            String key = "invalid-" + i;
            String nk = "null:" + cacheName + ":" + key;
            when(stringRedisTemplate.hasKey(nk)).thenReturn(false);
        }

        for (int i = 0; i < keyCount; i++) {
            String key = "invalid-" + i;
            Cache.ValueWrapper result = cache.get(key);
            assertNull(result);
        }

        verify(delegate, times(keyCount)).get(any());
    }

    @Test
    void cachePenetration_ValidKeyNotInCache_ProceedsNormally() {
        when(stringRedisTemplate.hasKey(nullKey)).thenReturn(false);
        when(delegate.get(testKey)).thenReturn(null);

        Cache.ValueWrapper result = cache.get(testKey);
        assertNull(result);
        verify(delegate).get(testKey);
    }

    @Test
    void avalanche_LockAcquired_OnlyOneLoadsFromDb() throws Exception {
        when(stringRedisTemplate.hasKey(nullKey)).thenReturn(false);
        when(delegate.get(testKey)).thenReturn(null);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);

        AtomicInteger loadCount = new AtomicInteger(0);
        Callable<String> valueLoader = () -> {
            loadCount.incrementAndGet();
            return cachedValue;
        };

        String result = cache.get(testKey, valueLoader);
        assertEquals(cachedValue, result);
        assertEquals(1, loadCount.get());
        verify(delegate).put(testKey, cachedValue);
        verify(lock).unlock();
    }

    @Test
    void avalanche_LockTimeout_FallsBackToDirectLoad() throws Exception {
        when(stringRedisTemplate.hasKey(nullKey)).thenReturn(false);
        when(delegate.get(testKey)).thenReturn(null);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(false);

        AtomicInteger loadCount = new AtomicInteger(0);
        Callable<String> valueLoader = () -> {
            loadCount.incrementAndGet();
            return cachedValue;
        };

        String result = cache.get(testKey, valueLoader);
        assertEquals(cachedValue, result);
        assertEquals(1, loadCount.get());
        verify(delegate, never()).put(any(), any());
        verify(lock, never()).unlock();
    }

    @Test
    void avalanche_DoubleCheckAfterLock_PreventsDuplicateLoad() throws Exception {
        when(stringRedisTemplate.hasKey(nullKey)).thenReturn(false);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);

        when(delegate.get(testKey))
                .thenReturn(null)
                .thenReturn(() -> cachedValue);

        AtomicInteger loadCount = new AtomicInteger(0);
        Callable<String> valueLoader = () -> {
            loadCount.incrementAndGet();
            fail("valueLoader should not be called when double-check finds cached value");
            return null;
        };

        String result = cache.get(testKey, valueLoader);
        assertEquals(cachedValue, result);
        assertEquals(0, loadCount.get());
        verify(delegate, never()).put(any(), any());
        verify(lock).unlock();
    }

    @Test
    void avalanche_ConcurrentSameKey_OnlyOneLoadsFromDb() throws Exception {
        when(stringRedisTemplate.hasKey(nullKey)).thenReturn(false);
        when(redissonClient.getLock(anyString())).thenReturn(lock);

        when(delegate.get(testKey)).thenAnswer(invocation -> {
            TimeUnit.MILLISECONDS.sleep(5);
            return null;
        });

        doAnswer(invocation -> {
            when(delegate.get(testKey)).thenReturn(() -> cachedValue);
            return null;
        }).when(delegate).put(eq(testKey), any());

        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
            boolean acquired = lockState.compareAndSet(false, true);
            if (acquired) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(10);
            return false;
        });

        doAnswer(invocation -> {
            lockState.set(false);
            return null;
        }).when(lock).unlock();

        AtomicInteger loadCount = new AtomicInteger(0);
        int threadCount = 20;
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return cache.get(testKey, () -> {
                    loadCount.incrementAndGet();
                    TimeUnit.MILLISECONDS.sleep(10);
                    return cachedValue;
                });
            }));
        }

        readyLatch.await(1, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<String> future : futures) {
            assertEquals(cachedValue, future.get(3, TimeUnit.SECONDS));
        }

        assertTrue(loadCount.get() <= 2,
                "valueLoader 應被呼叫 1~2 次（鎖競爭），實際 " + loadCount.get());
        executor.shutdown();
    }

    private final AtomicBoolean lockState = new AtomicBoolean(false);

    @Test
    void avalanche_ConcurrentDifferentKeys_ParallelLoad() throws Exception {
        int keyCount = 5;
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(delegate.get(any())).thenReturn(null);

        AtomicInteger loadCount = new AtomicInteger(0);
        CountDownLatch readyLatch = new CountDownLatch(keyCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(keyCount);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < keyCount; i++) {
            String key = "key-" + i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return cache.get(key, () -> {
                    loadCount.incrementAndGet();
                    return "val-" + key;
                });
            }));
        }

        readyLatch.await(1, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<String> future : futures) {
            assertNotNull(future.get(3, TimeUnit.SECONDS));
        }

        assertEquals(keyCount, loadCount.get(),
                "不同 key 應各自載入，共 " + keyCount + " 次");
        executor.shutdown();
    }

    @Test
    void avalanche_NullValue_StoresNullMarker() throws Exception {
        when(stringRedisTemplate.hasKey(nullKey)).thenReturn(false);
        when(delegate.get(testKey)).thenReturn(null);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);

        AtomicInteger loadCount = new AtomicInteger(0);
        Callable<String> valueLoader = () -> {
            loadCount.incrementAndGet();
            return null;
        };

        String result = cache.get(testKey, valueLoader);
        assertNull(result);
        assertEquals(1, loadCount.get());
        verify(valueOps).set(eq(nullKey), eq("NULL_MARKER"), any(Duration.class));
        verify(delegate).evict(testKey);
    }

    @Test
    void avalanche_NullKeyConcurrent_MultipleThreads() throws Exception {
        when(stringRedisTemplate.hasKey(nullKey)).thenReturn(false);
        when(delegate.get(testKey)).thenReturn(null);

        int threadCount = 20;
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Cache.ValueWrapper>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return cache.get(testKey);
            }));
        }

        readyLatch.await(1, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Cache.ValueWrapper> future : futures) {
            assertNull(future.get(3, TimeUnit.SECONDS));
        }

        verify(delegate, times(threadCount)).get(any());
        executor.shutdown();
    }

    @Test
    void nullKeyParam_HandledGracefully() {
        when(stringRedisTemplate.hasKey("null:" + cacheName + ":null")).thenReturn(false);
        when(delegate.get(null)).thenReturn(null);

        Cache.ValueWrapper result = cache.get(null);

        assertNull(result);
        verify(delegate).get(null);
    }

    @Test
    void evict_RemovesNullMarkerAndCache() {
        String key = "evict-key";
        String nk = "null:" + cacheName + ":" + key;

        cache.evict(key);
        verify(stringRedisTemplate).delete(nk);
        verify(delegate).evict(key);
    }
}
