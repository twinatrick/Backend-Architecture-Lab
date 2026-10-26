package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Service.ICacheStatsService;
import com.example.BackendArchitectureLab.Vo.CacheMetricsVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheStatsServiceImpl implements ICacheStatsService {

    private static final String STATS_KEY_PREFIX = "cache:stats:";
    private static final String STATS_KEY_PATTERN = "cache:stats:*";
    private static final long SNAPSHOT_TTL_NANOS = 1_000_000_000L;

    private final StringRedisTemplate stringRedisTemplate;

    private volatile Map<String, CacheMetricsVo> cachedSnapshot;
    private volatile long cachedAtNanos = 0L;

    @Override
    public Map<String, CacheMetricsVo> getCacheStats() {
        try {
            if (stringRedisTemplate.getConnectionFactory() != null) {
                long now = System.nanoTime();
                Map<String, CacheMetricsVo> snapshot = cachedSnapshot;
                if (snapshot != null && (now - cachedAtNanos) < SNAPSHOT_TTL_NANOS) {
                    return snapshot;
                }
                synchronized (this) {
                    now = System.nanoTime();
                    if (cachedSnapshot != null && (now - cachedAtNanos) < SNAPSHOT_TTL_NANOS) {
                        return cachedSnapshot;
                    }
                    Map<String, CacheMetricsVo> fresh = fetchStatsWithScanAndPipeline();
                    cachedSnapshot = fresh;
                    cachedAtNanos = System.nanoTime();
                    return fresh;
                }
            }
            return fetchStatsLegacy();
        } catch (Exception e) {
            log.warn("[CacheStats] 讀取 Redis 快取統計失敗: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, CacheMetricsVo> fetchStatsWithScanAndPipeline() {
        List<String> keys = stringRedisTemplate.execute((RedisConnection connection) -> {
            List<String> found = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions().match(STATS_KEY_PATTERN).count(200).build();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    found.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return found;
        });

        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Object> pipelinedResults = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String key : keys) {
                connection.hashCommands().hGetAll(key.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });

        Map<String, CacheMetricsVo> statsResult = new HashMap<>();
        for (int i = 0; i < keys.size() && i < pipelinedResults.size(); i++) {
            String key = keys.get(i);
            Object rawObj = pipelinedResults.get(i);
            if (rawObj instanceof Map<?, ?> rawMap && !rawMap.isEmpty()) {
                String cacheName = key.replace(STATS_KEY_PREFIX, "");
                statsResult.put(cacheName, CacheMetricsVo.fromRawStats(rawMap));
            }
        }
        return Collections.unmodifiableMap(statsResult);
    }

    private Map<String, CacheMetricsVo> fetchStatsLegacy() {
        Set<String> keys = stringRedisTemplate.keys(STATS_KEY_PATTERN);
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, CacheMetricsVo> statsResult = new HashMap<>();
        for (String key : keys) {
            String cacheName = key.replace(STATS_KEY_PREFIX, "");
            Map<Object, Object> rawStats = stringRedisTemplate.opsForHash().entries(key);
            if (!rawStats.isEmpty()) {
                statsResult.put(cacheName, CacheMetricsVo.fromRawStats(rawStats));
            }
        }
        return statsResult;
    }
}
