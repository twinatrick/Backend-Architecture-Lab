package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.CacheStatsEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CacheStatsConsumer {

    private static final Logger log = LoggerFactory.getLogger(CacheStatsConsumer.class);

    private final StringRedisTemplate stringRedisTemplate;

    @KafkaListener(topics = "cache-stats", containerFactory = "cacheStatsKafkaListenerContainerFactory")
    public void consume(CacheStatsEvent event) {
        try {
            stringRedisTemplate.opsForHash().increment(
                "cache:stats:" + event.getCacheName(),
                event.getField(),
                1
            );
        } catch (Exception e) {
            log.warn("寫入快取統計異常: {}", e.toString());
        }
    }
}
