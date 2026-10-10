package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.CacheStatsEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void publish_WhenFutureCompletesExceptionally_HandlesGracefully() {
        CompletableFuture future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka error"));
        when(kafkaTemplate.send(eq("cache-stats"), any(CacheStatsEvent.class))).thenReturn(future);

        assertDoesNotThrow(() -> publisher.publish("users", "id"));
    }

    @Test
    void publish_WhenSendThrowsException_DoesNotThrow() {
        when(kafkaTemplate.send(eq("cache-stats"), any(CacheStatsEvent.class)))
                .thenThrow(new RuntimeException("Sync failure"));

        assertDoesNotThrow(() -> publisher.publish("users", "id"));
    }
}
