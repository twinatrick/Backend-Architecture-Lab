package com.example.BackendArchitectureLab;

import com.example.BackendArchitectureLab.Config.CachePenetrationProtectionCacheManager;
import com.example.BackendArchitectureLab.TestSupport.BaseTestcontainersIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest(classes = TestSupportApp.class)
public class RedissonCacheProtectionIT extends BaseTestcontainersIntegrationTest {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private CachePenetrationProtectionCacheManager cacheManager;

    @Test
    @DisplayName("Redisson 分散式互斥鎖：高並發多執行緒爭搶下確保臨界區絕對互斥")
    void testRedissonDistributedMutexLockConcurrentContention() throws InterruptedException {
        int threadCount = 10;
        int incrementsPerThread = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger sharedCounter = new AtomicInteger(0);
        String lockKey = "test:distributed:lock";

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    RLock lock = redissonClient.getLock(lockKey);
                    for (int j = 0; j < incrementsPerThread; j++) {
                        lock.lock();
                        try {
                            int current = sharedCounter.get();
                            Thread.sleep(1); // 模擬臨界區運算
                            sharedCounter.set(current + 1);
                        } finally {
                            lock.unlock();
                        }
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(completed, "所有執行緒應在限時內完成執行");
        assertEquals(threadCount * incrementsPerThread, sharedCounter.get(), "鎖保護下的計數值必須完全正確無並發競態");
    }

    @Test
    @DisplayName("快取防擊穿 (Single-Flight Fetch)：高並發冷啟動查詢同一 Key，保證底層 Loader 僅執行一次")
    void testCachePenetrationProtectionWithConcurrentFetch() throws InterruptedException {
        Cache cache = cacheManager.getCache("users");
        assertNotNull(cache, "users 快取實例不可為空");

        String cacheKey = "user-test-concurrent-100";
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger dbQueryCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    String result = cache.get(cacheKey, () -> {
                        dbQueryCount.incrementAndGet();
                        Thread.sleep(100); // 模擬慢速 DB 查詢
                        return "UserData-100";
                    });
                    assertEquals("UserData-100", result);
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(completed, "並發快取讀取應全數完成");
        assertEquals(1, dbQueryCount.get(), "高並發下分散式鎖應確保 DB Loader 僅被調用 1 次");
    }

    @Test
    @DisplayName("快取防穿透 (Null Value Caching)：查詢不存在資料快取空物件，避免頻繁穿透至 DB")
    void testNullValueCachingProtection() {
        Cache cache = cacheManager.getCache("users");
        assertNotNull(cache);

        String nonExistentKey = "user-non-existent-99999";
        AtomicInteger dbQueryCount = new AtomicInteger(0);

        // 第一次查詢回傳 null
        String val1 = cache.get(nonExistentKey, () -> {
            dbQueryCount.incrementAndGet();
            return null;
        });
        assertNull(val1);
        assertEquals(1, dbQueryCount.get(), "初次查詢需呼叫 Loader");

        // 第二次查詢應直接命中 NullValue 標記，不調用 Loader
        String val2 = cache.get(nonExistentKey, () -> {
            dbQueryCount.incrementAndGet();
            return null;
        });
        assertNull(val2);
        assertEquals(1, dbQueryCount.get(), "命中空值快取後，Loader 不得重複執行");
    }
}
