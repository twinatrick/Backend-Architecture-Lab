package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Service.CacheStatsPublisher;
import com.example.BackendArchitectureLab.Vo.CacheStatsEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
@ConditionalOnBean(name = "cacheStatsKafkaTemplate")
public class KafkaCacheStatsPublisher implements CacheStatsPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaCacheStatsPublisher.class);

    private final KafkaTemplate<String, CacheStatsEvent> kafkaTemplate;

    @Override
    public void publish(String cacheName, String field) {
        try {
            CompletableFuture<?> future = kafkaTemplate.send("cache-stats", new CacheStatsEvent(cacheName, field));
            if (future != null) {
                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.debug("發送快取度量至 Kafka 失敗 [{}]: {}", cacheName, ex.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("發送快取度量至 Kafka 異常 [{}]: {}", cacheName, e.getMessage());
        }
    }
}

