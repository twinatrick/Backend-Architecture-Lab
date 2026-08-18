package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.CacheStatsEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaCacheStatsPublisherTest {

    @Mock
    private KafkaTemplate<String, CacheStatsEvent> kafkaTemplate;

    private KafkaCacheStatsPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaCacheStatsPublisher(kafkaTemplate);
    }

    @Test
    void publish_SendsEventToCacheStatsTopic() {
        publisher.publish("users", "id");

        verify(kafkaTemplate).send("cache-stats", new CacheStatsEvent("users", "id"));
    }
}