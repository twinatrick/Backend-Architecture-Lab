package com.example.BackendArchitectureLab.Util;

import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheExecutorTest {

    private CacheManager cacheManager;
    private CacheExecutor cacheExecutor;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager("users", "roles", "items");
        cacheExecutor = new CacheExecutor(cacheManager);
    }

    @Test
    @DisplayName("executeCacheable 應於快取未命中時執行 loader 並快取結果")
    void executeCacheable_ShouldCacheAndReturnLoaderResult() {
        AtomicInteger counter = new AtomicInteger();
        String first = cacheExecutor.executeCacheable("users", "k1", () -> {
            counter.incrementAndGet();
            return "v1";
        });
        String second = cacheExecutor.executeCacheable("users", "k1", () -> {
            counter.incrementAndGet();
            return "v2";
        });

        assertEquals("v1", first);
        assertEquals("v1", second);
        assertEquals(1, counter.get());
    }

    @Test
    @DisplayName("executeCacheable 當 Cache 不存在時應直接執行 loader")
    void executeCacheable_WhenCacheNull_ShouldFallbackToLoader() {
        CacheManager emptyManager = mock(CacheManager.class);
        when(emptyManager.getCache("missing")).thenReturn(null);
        CacheExecutor fallbackExecutor = new CacheExecutor(emptyManager);

        String result = fallbackExecutor.executeCacheable("missing", "k1", () -> "direct");
        assertEquals("direct", result);
    }

    @Test
    @DisplayName("executeCacheableList 應自動包裝並解包 CacheListWrapper")
    void executeCacheableList_ShouldWrapAndUnwrapList() {
        AtomicInteger counter = new AtomicInteger();
        List<String> first = cacheExecutor.executeCacheableList("items", "all", () -> {
            counter.incrementAndGet();
            return List.of("a", "b");
        });
        List<String> second = cacheExecutor.executeCacheableList("items", "all", () -> {
            counter.incrementAndGet();
            return List.of("c");
        });

        assertEquals(List.of("a", "b"), first);
        assertEquals(List.of("a", "b"), second);
        assertEquals(1, counter.get());
    }

    @Test
    @DisplayName("executeCacheableWrapper 應回傳非空 CacheListWrapper")
    void executeCacheableWrapper_ShouldReturnWrapper() {
        CacheListWrapper<String> wrapper = cacheExecutor.executeCacheableWrapper("items", "empty", () -> null);
        assertNotNull(wrapper);
        assertTrue(wrapper.getData().isEmpty());
    }

    @Test
    @DisplayName("put 與 evict 應正確寫入與清除快取鍵值")
    void putAndEvict_ShouldUpdateAndClearCache() {
        cacheExecutor.put("roles", "r1", "admin");
        cacheExecutor.put("roles", "r2", "user");

        Cache cache = cacheManager.getCache("roles");
        assertNotNull(cache);
        assertEquals("admin", cache.get("r1", String.class));
        assertEquals("user", cache.get("r2", String.class));

        cacheExecutor.evict("roles", "r1", "r2");
        assertNull(cache.get("r1"));
        assertNull(cache.get("r2"));
    }
}
