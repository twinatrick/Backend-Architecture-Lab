package com.example.BackendArchitectureLab.Util;

import com.example.BackendArchitectureLab.Config.BatchCacheOperations;
import com.example.BackendArchitectureLab.Vo.BatchResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class BatchCacheHelper {

    private BatchCacheHelper() {
    }

    public static <ID, V> BatchResultVo<V> fetchBatchWithCache(
            CacheManager cacheManager,
            String cacheName,
            Collection<ID> requestedIds,
            Function<Collection<ID>, List<V>> dbBatchLoader,
            Function<V, ID> idExtractor
    ) {
        return fetchBatchWithCache(
                cacheManager,
                cacheName,
                requestedIds,
                Objects::toString,
                dbBatchLoader,
                idExtractor,
                Function.identity()
        );
    }

    @SuppressWarnings("unchecked")
    public static <ID, E, V> BatchResultVo<V> fetchBatchWithCache(
            CacheManager cacheManager,
            String cacheName,
            Collection<ID> requestedIds,
            Function<ID, String> keySerializer,
            Function<Collection<ID>, List<E>> dbBatchLoader,
            Function<E, ID> idExtractor,
            Function<E, V> entityToVoMapper
    ) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return BatchResultVo.empty(0);
        }

        Set<ID> uniqueIds = new LinkedHashSet<>();
        for (ID id : requestedIds) {
            if (id != null) {
                uniqueIds.add(id);
            }
        }

        if (uniqueIds.isEmpty()) {
            return BatchResultVo.empty(0);
        }

        long started = System.nanoTime();
        long loaderNanos = 0;
        long writeNanos = 0;
        Function<ID, String> serializer = keySerializer != null ? keySerializer : Objects::toString;
        Cache cache = resolveCache(cacheManager, cacheName);

        Map<String, V> foundMap = new LinkedHashMap<>();
        List<ID> missedIds = new ArrayList<>();
        Map<String, ID> idsByKey = new LinkedHashMap<>();
        for (ID id : uniqueIds) {
            idsByKey.put(serializer.apply(id), id);
        }

        if (cache instanceof BatchCacheOperations batchCache) {
            BatchCacheOperations.BatchReadResult readResult = batchCache.getAll(idsByKey.keySet());
            readResult.hits().forEach((key, value) -> foundMap.put(key, (V) value));
            for (String missedKey : readResult.misses()) {
                ID missedId = idsByKey.get(missedKey);
                if (missedId != null) {
                    missedIds.add(missedId);
                }
            }
        } else {
            for (Map.Entry<String, ID> entry : idsByKey.entrySet()) {
                String key = entry.getKey();
                boolean hit = false;
                if (cache != null) {
                    try {
                        Cache.ValueWrapper wrapper = cache.get(key);
                        if (wrapper != null && wrapper.get() != null) {
                            foundMap.put(key, (V) wrapper.get());
                            hit = true;
                        }
                    } catch (Exception e) {
                        log.warn("讀取快取 [{}] 鍵 [{}] 失敗，將從資料庫補查: {}", cacheName, key, e.getMessage());
                    }
                }
                if (!hit) {
                    missedIds.add(entry.getValue());
                }
            }
        }

        long readFinished = System.nanoTime();
        int cacheHits = foundMap.size();
        if (!missedIds.isEmpty() && dbBatchLoader != null) {
            try {
                long loaderStarted = System.nanoTime();
                List<E> loadedEntities = dbBatchLoader.apply(missedIds);
                loaderNanos = System.nanoTime() - loaderStarted;
                Map<String, V> valuesToCache = new LinkedHashMap<>();
                if (loadedEntities != null) {
                    for (E entity : loadedEntities) {
                        if (entity == null) {
                            continue;
                        }
                        ID entityId = idExtractor.apply(entity);
                        if (entityId == null) {
                            continue;
                        }
                        String key = serializer.apply(entityId);
                        if (!idsByKey.containsKey(key)) {
                            continue;
                        }
                        V vo = entityToVoMapper.apply(entity);
                        if (vo == null) {
                            continue;
                        }
                        foundMap.put(key, vo);

                        if (cache instanceof BatchCacheOperations) {
                            valuesToCache.put(key, vo);
                        } else if (cache != null) {
                            try {
                                cache.put(key, vo);
                            } catch (Exception e) {
                                log.warn("回填快取 [{}] 鍵 [{}] 失敗: {}", cacheName, key, e.getMessage());
                            }
                        }
                    }
                }
                if (cache instanceof BatchCacheOperations batchCache) {
                    long writeStarted = System.nanoTime();
                    try {
                        batchCache.putAll(valuesToCache);
                        Set<String> missingKeys = new LinkedHashSet<>();
                        for (ID missedId : missedIds) {
                            String key = serializer.apply(missedId);
                            if (!valuesToCache.containsKey(key)) {
                                missingKeys.add(key);
                            }
                        }
                        batchCache.putNulls(missingKeys);
                    } catch (Exception e) {
                        log.warn("批次回填快取 [{}] 失敗，仍回傳資料庫結果: {}", cacheName, e.getMessage());
                    } finally {
                        writeNanos = System.nanoTime() - writeStarted;
                    }
                }
            } catch (Exception e) {
                log.error("批次查詢資料庫失敗，快取名稱 [{}]: {}", cacheName, e.getMessage(), e);
                throw e;
            }
        }

        List<V> items = new ArrayList<>();
        Map<String, V> itemMap = new LinkedHashMap<>();
        List<String> missingIds = new ArrayList<>();

        for (ID id : uniqueIds) {
            String key = serializer.apply(id);
            V val = foundMap.get(key);
            if (val != null) {
                items.add(val);
                itemMap.put(key, val);
            } else {
                missingIds.add(key);
            }
        }

        log.debug("批次查詢耗時 cache={} requested={} hits={} dbRequested={} readMs={} loaderMs={} writeMs={} totalMs={}",
                cacheName, uniqueIds.size(), cacheHits, missedIds.size(),
                TimeUnit.NANOSECONDS.toMillis(readFinished - started), TimeUnit.NANOSECONDS.toMillis(loaderNanos),
                TimeUnit.NANOSECONDS.toMillis(writeNanos), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        return BatchResultVo.<V>builder()
                .items(items)
                .itemMap(itemMap)
                .missingIds(missingIds)
                .totalRequested(uniqueIds.size())
                .totalFound(items.size())
                .build();
    }

    private static Cache resolveCache(CacheManager cacheManager, String cacheName) {
        if (cacheManager == null || cacheName == null || cacheName.isBlank()) {
            return null;
        }
        try {
            return cacheManager.getCache(cacheName);
        } catch (Exception e) {
            log.warn("取得快取實例 [{}] 失敗: {}", cacheName, e.getMessage());
            return null;
        }
    }
}
