package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Service.IBloomFilterService;
import com.example.BackendArchitectureLab.Util.NullValue;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class CachePenetrationProtectionCache implements Cache {

    private static final Logger log = LoggerFactory.getLogger(CachePenetrationProtectionCache.class);

    private static final long LOCK_TRY_WAIT_MILLIS = 200;
    private static final long POLL_TIMEOUT_MILLIS = 10000;
    private static final long POLL_INTERVAL_MILLIS = 200;

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private static boolean isUuidKey(String key) {
        return key != null && UUID_PATTERN.matcher(key).matches();
    }

    private final String name;

    private final RedisCache delegate;

    private final StringRedisTemplate stringRedisTemplate;

    private final IBloomFilterService bloomFilterService;

    private final RedissonClient redissonClient;

    private final Duration nullValueTtl;

    private final Object[] stripes = new Object[256];
    {
        for (int i = 0; i < stripes.length; i++) {
            stripes[i] = new Object();
        }
    }

    private final ConcurrentHashMap<String, CompletableFuture<ValueWrapper>> activeRedisFetches = new ConcurrentHashMap<>();

    public CachePenetrationProtectionCache(String name, RedisCache delegate,
                                            StringRedisTemplate stringRedisTemplate,
                                            IBloomFilterService bloomFilterService,
                                            RedissonClient redissonClient,
                                            Duration nullValueTtl) {
        this.name = name;
        this.delegate = delegate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.bloomFilterService = bloomFilterService;
        this.redissonClient = redissonClient;
        this.nullValueTtl = nullValueTtl;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        String cacheKey = toCacheKey(key);

        if (hasNullMarker(cacheKey)) {
            return () -> null;
        }

        if (!bloomFilterMightContain(cacheKey)) {
            return null;
        }

        ValueWrapper result = delegate.get(key);
        if (result != null && result.get() instanceof NullValue) {
            return () -> null;
        }

        return result;
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        String cacheKey = toCacheKey(key);

        if (hasNullMarker(cacheKey)) {
            return null;
        }

        if (!bloomFilterMightContain(cacheKey)) {
            return null;
        }

        T result = delegate.get(key, type);
        if (result instanceof NullValue) {
            return null;
        }

        return result;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        String cacheKey = toCacheKey(key);

        if (hasNullMarker(cacheKey)) {
            return null;
        }

        // 1. 使用 Request Collapsing (請求合併) 查詢 Redis，確保 500 個併發只有 1 個會向 Redis 發送 GET 請求
        CompletableFuture<ValueWrapper> future;
        boolean isLeader = false;
        CompletableFuture<ValueWrapper> newFuture = new CompletableFuture<>();

        CompletableFuture<ValueWrapper> existing = activeRedisFetches.putIfAbsent(cacheKey, newFuture);
        if (existing == null) {
            future = newFuture;
            isLeader = true;
        } else {
            future = existing;
        }

        if (isLeader) {
            try {
                ValueWrapper val = delegate.get(key);
                future.complete(val);
            } catch (Exception e) {
                future.completeExceptionally(e);
            } finally {
                activeRedisFetches.remove(cacheKey);
            }
        }

        ValueWrapper cached;
        try {
            cached = future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("獲取合併快取異常: {}", e.toString());
            cached = null;
        }

        if (cached != null) {
            Object value = cached.get();
            if (value instanceof NullValue) {
                return null;
            }
            return (T) value;
        }

        // 2. 本地查無快取，嘗試非阻塞獲取 Redisson 分散式鎖 (0ms 等待，拿不到直接進入輪詢)
        String lockKey = "lock:cache:" + name + ":" + cacheKey;
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 3. 拿到分散式鎖的 Leader 執行緒去讀資料庫
        if (acquired) {
            try {
                // 雙重檢查 Redis (使用請求合併方式)
                CompletableFuture<ValueWrapper> checkFuture;
                boolean isCheckLeader = false;
                CompletableFuture<ValueWrapper> newCheckFuture = new CompletableFuture<>();

                CompletableFuture<ValueWrapper> existingCheck = activeRedisFetches.putIfAbsent(cacheKey, newCheckFuture);
                if (existingCheck == null) {
                    checkFuture = newCheckFuture;
                    isCheckLeader = true;
                } else {
                    checkFuture = existingCheck;
                }

                if (isCheckLeader) {
                    try {
                        ValueWrapper val = delegate.get(key);
                        checkFuture.complete(val);
                    } catch (Exception e) {
                        checkFuture.completeExceptionally(e);
                    } finally {
                        activeRedisFetches.remove(cacheKey);
                    }
                }

                ValueWrapper doubleCheckDist;
                try {
                    doubleCheckDist = checkFuture.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    doubleCheckDist = null;
                }

                if (doubleCheckDist != null) {
                    Object value = doubleCheckDist.get();
                    if (value instanceof NullValue) {
                        return null;
                    }
                    return (T) value;
                }

                T value = valueLoader.call();
                put(key, value);
                return value;
            } catch (Exception e) {
                log.warn("快取 [{}] key [{}] 載入資料庫異常: {}", name, key, e.toString());
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException(e);
            } finally {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (Exception e) {
                    log.warn("解鎖異常 [{}] key [{}]: {}", name, key, e.toString());
                }
            }
        }

        // 4. 未取得鎖的其餘執行緒，在輪詢時也透過 Request Collapsing 來集體查詢 Redis，拒絕併發風暴
        try {
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < POLL_TIMEOUT_MILLIS) {
                TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL_MILLIS);

                if (hasNullMarker(cacheKey)) {
                    return null;
                }

                CompletableFuture<ValueWrapper> pollFuture;
                boolean isPollLeader = false;
                CompletableFuture<ValueWrapper> newPollFuture = new CompletableFuture<>();

                CompletableFuture<ValueWrapper> existingPoll = activeRedisFetches.putIfAbsent(cacheKey, newPollFuture);
                if (existingPoll == null) {
                    pollFuture = newPollFuture;
                    isPollLeader = true;
                } else {
                    pollFuture = existingPoll;
                }

                if (isPollLeader) {
                    try {
                        ValueWrapper val = delegate.get(key);
                        pollFuture.complete(val);
                    } catch (Exception e) {
                        pollFuture.completeExceptionally(e);
                    } finally {
                        activeRedisFetches.remove(cacheKey);
                    }
                }

                ValueWrapper fallbackCheck;
                try {
                    fallbackCheck = pollFuture.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    fallbackCheck = null;
                }

                if (fallbackCheck != null) {
                    Object v = fallbackCheck.get();
                    if (v instanceof NullValue) {
                        return null;
                    }
                    return (T) v;
                }
            }

            log.warn("快取 [{}] key [{}] 輪詢超時 ({}ms)，降級直接載入資料庫", name, key, POLL_TIMEOUT_MILLIS);
            return valueLoader.call();
        } catch (Exception e) {
            log.warn("快取 [{}] key [{}] 輪詢/降級載入異常: {}", name, key, e.toString());
            try {
                return valueLoader.call();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    public void put(Object key, Object value) {
        String cacheKey = toCacheKey(key);

        if (value == null) {
            setNullMarker(cacheKey);
            delegate.evict(key);
            return;
        }

        deleteNullMarker(cacheKey);
        addToBloomFilter(cacheKey);
        delegate.put(key, value);
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        String cacheKey = toCacheKey(key);

        ValueWrapper existing = get(key);
        if (existing != null) {
            return existing;
        }

        put(key, value);
        return null;
    }

    @Override
    public void evict(Object key) {
        String cacheKey = toCacheKey(key);
        deleteNullMarker(cacheKey);
        delegate.evict(key);
    }

    @Override
    public void clear() {
        delegate.clear();
        try {
            Set<String> nullKeys = stringRedisTemplate.keys("null:" + name + ":*");
            if (nullKeys != null && !nullKeys.isEmpty()) {
                stringRedisTemplate.delete(nullKeys);
            }
        } catch (Exception e) {
            log.warn("清除 null marker 異常 [{}]: {}", name, e.toString());
        }
    }

    private boolean hasNullMarker(String cacheKey) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(nullKey(cacheKey)));
        } catch (Exception e) {
            log.warn("null marker 檢查異常 [{}] key [{}]: {}", name, cacheKey, e.toString());
            return false;
        }
    }

    private boolean bloomFilterMightContain(String cacheKey) {
        if (!isUuidKey(cacheKey)) {
            return true;
        }
        try {
            return bloomFilterService.mightContain(name, cacheKey);
        } catch (Exception e) {
            log.warn("BloomFilter 檢查異常 [{}] key [{}]: {}", name, cacheKey, e.toString());
            return true;
        }
    }

    private void setNullMarker(String cacheKey) {
        try {
            stringRedisTemplate.opsForValue().set(nullKey(cacheKey), "NULL_MARKER", nullValueTtl);
        } catch (Exception e) {
            log.warn("null marker 寫入異常 [{}] key [{}]: {}", name, cacheKey, e.toString());
        }
    }

    private void deleteNullMarker(String cacheKey) {
        try {
            stringRedisTemplate.delete(nullKey(cacheKey));
        } catch (Exception e) {
            log.warn("null marker 刪除異常 [{}] key [{}]: {}", name, cacheKey, e.toString());
        }
    }

    private void addToBloomFilter(String cacheKey) {
        if (!isUuidKey(cacheKey)) {
            return;
        }
        try {
            bloomFilterService.add(name, cacheKey);
        } catch (Exception e) {
            log.warn("BloomFilter 新增異常 [{}] key [{}]: {}", name, cacheKey, e.toString());
        }
    }

    private String nullKey(String key) {
        return "null:" + name + ":" + key;
    }

    private String toCacheKey(Object key) {
        if (key == null) {
            return "null";
        }
        return key.toString();
    }
}
