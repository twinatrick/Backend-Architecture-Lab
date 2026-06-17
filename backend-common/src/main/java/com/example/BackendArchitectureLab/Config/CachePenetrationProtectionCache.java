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

        ValueWrapper cached = delegate.get(key);
        if (cached != null) {
            Object value = cached.get();
            if (value instanceof NullValue) {
                return null;
            }
            return (T) value;
        }

        String lockKey = "lock:cache:" + name + ":" + cacheKey;
        int index = (lockKey.hashCode() & Integer.MAX_VALUE) % stripes.length;
        Object stripeLock = stripes[index];

        synchronized (stripeLock) {
            ValueWrapper doubleCheck = delegate.get(key);
            if (doubleCheck != null) {
                Object value = doubleCheck.get();
                if (value instanceof NullValue) {
                    return null;
                }
                return (T) value;
            }

            RLock lock = redissonClient.getLock(lockKey);
            try {
                if (lock.tryLock(LOCK_TRY_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                    try {
                        ValueWrapper doubleCheckDist = delegate.get(key);
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
                    } finally {
                        try {
                            lock.unlock();
                        } catch (Exception e) {
                            log.warn("解鎖異常 [{}] key [{}]: {}", name, key, e.toString());
                        }
                    }
                }

                // 獲取鎖失敗，進入無鎖輪詢機制 (Lock-free Polling)
                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < POLL_TIMEOUT_MILLIS) {
                    TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL_MILLIS);

                    if (hasNullMarker(cacheKey)) {
                        return null;
                    }

                    ValueWrapper fallbackCheck = delegate.get(key);
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
                log.warn("快取 [{}] key [{}] 鎖/輪詢異常，降級直接載入: {}", name, key, e.toString());
                try {
                    return valueLoader.call();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
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
