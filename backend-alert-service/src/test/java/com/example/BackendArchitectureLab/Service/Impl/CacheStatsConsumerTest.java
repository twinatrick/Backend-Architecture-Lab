package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Event.CacheStatsEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheStatsConsumerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private CacheStatsConsumer consumer;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        consumer = new CacheStatsConsumer(stringRedisTemplate);
    }

    @Test
    void consume_AggregatedEvent_IncrementsByCount() {
        consumer.consume(new CacheStatsEvent("users", "hits", 100));

        verify(hashOperations).increment("cache:stats:users", "hits", 100);
    }

    @Test
    void consume_LegacyEventWithoutCount_IncrementsByOne() {
        consumer.consume(new CacheStatsEvent("users", "hits", 0));

        verify(hashOperations).increment("cache:stats:users", "hits", 1);
    }
}
