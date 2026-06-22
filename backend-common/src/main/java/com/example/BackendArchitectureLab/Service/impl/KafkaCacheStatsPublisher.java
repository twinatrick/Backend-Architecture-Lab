package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Config.CachePenetrationProtectionCacheManager;
import com.example.BackendArchitectureLab.Vo.CacheStatsEvent;
import com.example.BackendArchitectureLab.Service.CacheStatsPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
@ConditionalOnBean({CachePenetrationProtectionCacheManager.class, KafkaTemplate.class})
public class KafkaCacheStatsPublisher implements CacheStatsPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaCacheStatsPublisher.class);

    @Autowired
    private KafkaTemplate<String, CacheStatsEvent> kafkaTemplate;

    @Override
    public void publish(String cacheName, String field) {
        kafkaTemplate.send("cache-stats", new CacheStatsEvent(cacheName, field));
    }
}
