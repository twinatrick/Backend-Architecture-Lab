package com.example.BackendArchitectureLab.Util;

import com.example.BackendArchitectureLab.Vo.BatchResultVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchCacheHelperTest {

    private CacheManager cacheManager;
    private final String cacheName = "testEntities";

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager(cacheName);
    }

    record MockEntity(UUID id, String name) {}

    @Test
    @DisplayName("傳入空或 null 集合時應回傳空的 BatchResultVo")
    void testFetchBatchWithCache_EmptyInput() {
        BatchResultVo<MockEntity> result = BatchCacheHelper.fetchBatchWithCache(
                cacheManager,
                cacheName,
                Collections.emptyList(),
                ids -> Collections.emptyList(),
                MockEntity::id
        );

        assertNotNull(result);
        assertEquals(0, result.getTotalRequested());
        assertEquals(0, result.getTotalFound());
        assertTrue(result.getItems().isEmpty());
        assertTrue(result.getMissingIds().isEmpty());
    }

    @Test
    @DisplayName("全部快取命中時不應調用資料庫加載器")
    void testFetchBatchWithCache_AllCacheHit() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        MockEntity entity1 = new MockEntity(id1, "Entity 1");
        MockEntity entity2 = new MockEntity(id2, "Entity 2");

        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache);
        cache.put(id1.toString(), entity1);
        cache.put(id2.toString(), entity2);

        AtomicInteger dbCallCount = new AtomicInteger(0);

        BatchResultVo<MockEntity> result = BatchCacheHelper.fetchBatchWithCache(
                cacheManager,
                cacheName,
                List.of(id1, id2),
                missedIds -> {
                    dbCallCount.incrementAndGet();
                    return Collections.emptyList();
                },
                MockEntity::id
        );

        assertEquals(0, dbCallCount.get(), "全快取命中不應調用 DB");
        assertEquals(2, result.getTotalRequested());
        assertEquals(2, result.getTotalFound());
        assertEquals(2, result.getItems().size());
        assertEquals(entity1, result.getItemMap().get(id1.toString()));
        assertEquals(entity2, result.getItemMap().get(id2.toString()));
        assertTrue(result.getMissingIds().isEmpty());
    }

    @Test
    @DisplayName("部分命中部分未命中時應正確查 DB 並回填快取")
    void testFetchBatchWithCache_PartialHitAndBackfill() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        MockEntity entity1 = new MockEntity(id1, "Hit 1");
        MockEntity entity2 = new MockEntity(id2, "DB 2");

        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache);
        cache.put(id1.toString(), entity1);

        AtomicInteger dbCallCount = new AtomicInteger(0);

        BatchResultVo<MockEntity> result = BatchCacheHelper.fetchBatchWithCache(
                cacheManager,
                cacheName,
                List.of(id1, id2, id3),
                missedIds -> {
                    dbCallCount.incrementAndGet();
                    assertEquals(2, missedIds.size());
                    assertTrue(missedIds.contains(id2));
                    assertTrue(missedIds.contains(id3));
                    return List.of(entity2); // id3 在 DB 也沒有
                },
                MockEntity::id
        );

        assertEquals(1, dbCallCount.get(), "未命中項目應發起 1 次 DB 批次查詢");
        assertEquals(3, result.getTotalRequested());
        assertEquals(2, result.getTotalFound());
        assertEquals(2, result.getItems().size());
        assertEquals(1, result.getMissingIds().size());
        assertTrue(result.getMissingIds().contains(id3.toString()));

        // 驗證 id2 有被正確回填至快取中
        Cache.ValueWrapper wrapper = cache.get(id2.toString());
        assertNotNull(wrapper);
        assertEquals(entity2, wrapper.get());
    }

    @Test
    @DisplayName("重複 ID 傳入時應自動去重並維持原順序")
    void testFetchBatchWithCache_DuplicateIdsDeduplication() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        MockEntity entity1 = new MockEntity(id1, "Entity 1");
        MockEntity entity2 = new MockEntity(id2, "Entity 2");

        BatchResultVo<MockEntity> result = BatchCacheHelper.fetchBatchWithCache(
                cacheManager,
                cacheName,
                Arrays.asList(id1, id2, id1, id2),
                missedIds -> List.of(entity1, entity2),
                MockEntity::id
        );

        assertEquals(2, result.getTotalRequested(), "重複傳入的 ID 應被去重");
        assertEquals(2, result.getTotalFound());
        assertEquals(2, result.getItems().size());
    }
}
