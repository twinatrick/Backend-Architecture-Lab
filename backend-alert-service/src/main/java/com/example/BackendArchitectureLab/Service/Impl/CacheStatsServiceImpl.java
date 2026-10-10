package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Service.ICacheStatsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CacheStatsServiceImpl implements ICacheStatsService {

    private static final Logger log = LoggerFactory.getLogger(CacheStatsServiceImpl.class);

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Map<String, Map<Object, Object>> getCacheStats() {
        Map<String, Map<Object, Object>> result = new LinkedHashMap<>();
        try {
            Set<String> keys = stringRedisTemplate.keys("cache:stats:*");
            if (keys != null) {
                for (String key : keys) {
                    String cacheName = key.substring("cache:stats:".length());
                    Map<Object, Object> rawStats = stringRedisTemplate.opsForHash().entries(key);
                    if (rawStats != null && !rawStats.isEmpty()) {
                        Map<Object, Object> stats = new LinkedHashMap<>(rawStats);
                        enrichDerivedMetrics(stats);
                        result.put(cacheName, stats);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("讀取快取統計異常: {}", e.toString());
        }
        return result;
    }

    private void enrichDerivedMetrics(Map<Object, Object> stats) {
        long hits = parseMetric(stats, "hits", "hitCount");
        long misses = parseMetric(stats, "misses", "missCount");
        long bloomRejects = parseMetric(stats, "bloom_rejects", "bloomRejects");
        long nullHits = parseMetric(stats, "null_hits", "nullHits");

        long total = hits + misses + bloomRejects + nullHits;
        float hitRate = (total > 0L) ? ((float) hits / (float) total) : 0.0f;

        stats.put("total", total);
        stats.put("hitRate", hitRate);
    }

    private long parseMetric(Map<Object, Object> stats, String primaryKey, String fallbackKey) {
        Object val = stats.get(primaryKey);
        if (val == null && fallbackKey != null) {
            val = stats.get(fallbackKey);
        }
        if (val == null) {
            return 0L;
        }
        try {
            if (val instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(val.toString().trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
