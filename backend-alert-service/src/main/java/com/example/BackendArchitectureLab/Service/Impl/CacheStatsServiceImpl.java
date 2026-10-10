package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Service.ICacheStatsService;
import com.example.BackendArchitectureLab.Vo.CacheMetricsVo;
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
    public Map<String, CacheMetricsVo> getCacheStats() {
        Map<String, CacheMetricsVo> result = new LinkedHashMap<>();
        try {
            Set<String> keys = stringRedisTemplate.keys("cache:stats:*");
            if (keys != null) {
                for (String key : keys) {
                    String cacheName = key.substring("cache:stats:".length());
                    Map<Object, Object> rawStats = stringRedisTemplate.opsForHash().entries(key);
                    if (rawStats != null && !rawStats.isEmpty()) {
                        result.put(cacheName, CacheMetricsVo.fromRawStats(rawStats));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("讀取快取統計異常: {}", e.toString());
        }
        return result;
    }
}
