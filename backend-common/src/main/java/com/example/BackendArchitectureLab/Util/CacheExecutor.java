package com.example.BackendArchitectureLab.Util;

import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * 顯式快取切面執行器 (Explicit Cache Aspect Executor)
 * 封裝 Spring CacheManager 操作，使同類別內部方法呼叫無需透過 @Lazy self 自我代理注入，
 * 即可完整觸發 CachePenetrationProtectionCache（布隆過濾器、Request Collapsing 與 Redisson 分散式鎖）。
 */
@Component
@RequiredArgsConstructor
public class CacheExecutor {

    private final CacheManager cacheManager;

    public <T> T executeCacheable(String cacheName, Object key, Supplier<T> valueLoader) {
        if (cacheManager == null || key == null) {
            return valueLoader.get();
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return valueLoader.get();
        }
        return cache.get(key, valueLoader::get);
    }

    public <T> CacheListWrapper<T> executeCacheableWrapper(
            String cacheName,
            Object key,
            Supplier<List<T>> listLoader
    ) {
        return executeCacheable(cacheName, key, () -> {
            List<T> data = listLoader.get();
            return new CacheListWrapper<>(data != null ? data : List.of());
        });
    }

    public <T> List<T> executeCacheableList(
            String cacheName,
            Object key,
            Supplier<List<T>> listLoader
    ) {
        CacheListWrapper<T> wrapper = executeCacheableWrapper(cacheName, key, listLoader);
        return wrapper != null && wrapper.getData() != null ? wrapper.getData() : List.of();
    }

    public void put(String cacheName, Object key, Object value) {
        if (cacheManager == null || key == null) {
            return;
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    public void evict(String cacheName, Object... keys) {
        if (cacheManager == null || keys == null || keys.length == 0) {
            return;
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            for (Object key : keys) {
                if (key != null) {
                    cache.evict(key);
                }
            }
        }
    }
}
