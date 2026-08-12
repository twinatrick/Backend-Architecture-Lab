package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Service.ICacheStatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class CacheStatsServiceImpl implements ICacheStatsService {

    private static final Logger log = LoggerFactory.getLogger(CacheStatsServiceImpl.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Map<String, Map<Object, Object>> getCacheStats() {
        Map<String, Map<Object, Object>> result = new LinkedHashMap<>();
        try {
            Set<String> keys = stringRedisTemplate.keys("cache:stats:*");
            if (keys != null) {
                for (String key : keys) {
                    String cacheName = key.substring("cache:stats:".length());
                    Map<Object, Object> stats = stringRedisTemplate.opsForHash().entries(key);
                    if (!stats.isEmpty()) {
                        result.put(cacheName, stats);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("讀取快取統計異常: {}", e.toString());
        }
        return result;
    }
}
