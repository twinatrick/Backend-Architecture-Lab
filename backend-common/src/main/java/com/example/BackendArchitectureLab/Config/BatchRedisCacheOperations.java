package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Service.CacheStatsPublisher;
import com.example.BackendArchitectureLab.Service.IBloomFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class BatchRedisCacheOperations {

    private static final byte[] NULL_MARKER_VALUE = StringRedisSerializer.UTF_8.serialize("NULL_MARKER");

    private final RedisConnectionFactory connectionFactory;

    private final IBloomFilterService bloomFilterService;

    private final CacheStatsPublisher statsPublisher;

    public BatchCacheOperations.BatchReadResult getAll(
            String cacheName,
            RedisCache redisCache,
            Collection<String> keys
    ) {
        Set<String> uniqueKeys = new LinkedHashSet<>(keys);
        Map<String, Object> hits = new LinkedHashMap<>();
        Set<String> misses = new LinkedHashSet<>();
        Set<String> knownMissing = new LinkedHashSet<>();
        RedisCacheConfiguration configuration = redisCache.getCacheConfiguration();

        try {
            List<byte[]> values = multiGet(toPhysicalKeys(cacheName, configuration, uniqueKeys));
            int index = 0;
            for (String key : uniqueKeys) {
                byte[] value = values.get(index++);
                if (value == null) {
                    misses.add(key);
                    continue;
                }
                try {
                    hits.put(key, deserializeValue(configuration, value));
                } catch (RuntimeException e) {
                    log.warn("批次反序列化快取 [{}] 鍵 [{}] 失敗，將從資料庫補查: {}", cacheName, key, e.getMessage());
                    misses.add(key);
                }
            }
        } catch (RuntimeException e) {
            log.warn("批次讀取快取 [{}] 失敗，將全部從資料庫補查: {}", cacheName, e.getMessage());
            misses.addAll(uniqueKeys);
        }

        try {
            List<String> uniqueKeyList = new ArrayList<>(uniqueKeys);
            List<byte[]> markers = multiGet(toNullMarkerKeys(cacheName, uniqueKeyList));
            for (int i = 0; i < uniqueKeyList.size(); i++) {
                if (markers.get(i) != null) {
                    String key = uniqueKeyList.get(i);
                    hits.remove(key);
                    misses.remove(key);
                    knownMissing.add(key);
                }
            }
        } catch (RuntimeException e) {
            log.warn("批次讀取 null marker [{}] 失敗，保留正值快取並由資料庫確認未命中項目: {}", cacheName, e.getMessage());
        }

        publishStats(cacheName, "hits", hits.size());
        publishStats(cacheName, "misses", misses.size());
        return new BatchCacheOperations.BatchReadResult(hits, misses, knownMissing);
    }

    public void putAll(String cacheName, RedisCache redisCache, Map<String, ?> values) {
        if (values.isEmpty()) {
            return;
        }

        List<String> bloomKeys = values.keySet().stream()
                .filter(CachePenetrationProtectionCache::isUuidKey)
                .toList();
        if (!bloomKeys.isEmpty()) {
            try {
                bloomFilterService.addAll(cacheName, bloomKeys);
            } catch (RuntimeException e) {
                log.warn("批次更新 BloomFilter [{}] 失敗，繼續回填快取: {}", cacheName, e.getMessage());
            }
        }

        RedisCacheConfiguration configuration = redisCache.getCacheConfiguration();
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.openPipeline();
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                connection.keyCommands().del(nullMarkerKey(cacheName, key));
                writeValue(connection, configuration, cacheName, key, value);
            }
            connection.closePipeline();
        }
    }

    public void putNulls(
            String cacheName,
            RedisCache redisCache,
            Collection<String> keys,
            Duration nullValueTtl
    ) {
        if (keys.isEmpty()) {
            return;
        }

        RedisCacheConfiguration configuration = redisCache.getCacheConfiguration();
        long ttlMillis = Math.max(1, nullValueTtl.toMillis());
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.openPipeline();
            for (String key : keys) {
                connection.stringCommands().pSetEx(
                        nullMarkerKey(cacheName, key),
                        ttlMillis,
                        NULL_MARKER_VALUE
                );
                connection.keyCommands().del(physicalKey(cacheName, configuration, key));
            }
            connection.closePipeline();
        }
    }

    private List<byte[]> multiGet(List<byte[]> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        try (RedisConnection connection = connectionFactory.getConnection()) {
            List<byte[]> values = connection.stringCommands().mGet(keys.toArray(byte[][]::new));
            if (values == null) {
                return new ArrayList<>(Collections.nCopies(keys.size(), null));
            }
            return values;
        }
    }

    private List<byte[]> toPhysicalKeys(
            String cacheName,
            RedisCacheConfiguration configuration,
            Collection<String> keys
    ) {
        return keys.stream()
                .map(key -> physicalKey(cacheName, configuration, key))
                .toList();
    }

    private List<byte[]> toNullMarkerKeys(String cacheName, Collection<String> keys) {
        return keys.stream()
                .map(key -> nullMarkerKey(cacheName, key))
                .toList();
    }

    private byte[] physicalKey(
            String cacheName,
            RedisCacheConfiguration configuration,
            String logicalKey
    ) {
        String key = configuration.usePrefix()
                ? configuration.getKeyPrefixFor(cacheName) + logicalKey
                : logicalKey;
        return toByteArray(configuration.getKeySerializationPair().write(key));
    }

    private byte[] nullMarkerKey(String cacheName, String logicalKey) {
        return StringRedisSerializer.UTF_8.serialize("null:" + cacheName + ":" + logicalKey);
    }

    private Object deserializeValue(RedisCacheConfiguration configuration, byte[] value) {
        return configuration.getValueSerializationPair().read(ByteBuffer.wrap(value));
    }

    private void writeValue(
            RedisConnection connection,
            RedisCacheConfiguration configuration,
            String cacheName,
            String logicalKey,
            Object value
    ) {
        byte[] key = physicalKey(cacheName, configuration, logicalKey);
        byte[] serializedValue = toByteArray(configuration.getValueSerializationPair().write(value));
        Duration ttl = configuration.getTtlFunction().getTimeToLive(logicalKey, value);
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            connection.stringCommands().pSetEx(key, Math.max(1, ttl.toMillis()), serializedValue);
        } else {
            connection.stringCommands().set(key, serializedValue);
        }
    }

    private byte[] toByteArray(ByteBuffer byteBuffer) {
        ByteBuffer copy = byteBuffer.slice();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    private void publishStats(String cacheName, String field, long count) {
        if (count <= 0) {
            return;
        }
        try {
            statsPublisher.publish(cacheName, field, count);
        } catch (RuntimeException e) {
            log.warn("批次快取統計發送異常 [{}] field [{}]: {}", cacheName, field, e.getMessage());
        }
    }
}
