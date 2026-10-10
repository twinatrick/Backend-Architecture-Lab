package com.example.BackendArchitectureLab.Service.Impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheStatsServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private CacheStatsServiceImpl cacheStatsService;

    @Test
    void getCacheStats_whenKeysExist_shouldReturnPopulatedMap() {
        when(stringRedisTemplate.keys("cache:stats:*")).thenReturn(Set.of("cache:stats:users", "cache:stats:projects"));
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("cache:stats:users")).thenReturn(Map.of("hitCount", "10", "missCount", "2"));
        when(hashOperations.entries("cache:stats:projects")).thenReturn(Map.of("hitCount", "5"));

        Map<String, Map<Object, Object>> result = cacheStatsService.getCacheStats();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("users"));
        assertTrue(result.containsKey("projects"));
        assertEquals("10", result.get("users").get("hitCount"));
        assertEquals("5", result.get("projects").get("hitCount"));
        assertEquals(12L, result.get("users").get("total"));
        assertEquals(10f / 12f, (Float) result.get("users").get("hitRate"), 0.0001f);
    }

    @Test
    void getCacheStats_shouldCalculateTotalAndHitRateAsFloat() {
        when(stringRedisTemplate.keys("cache:stats:*")).thenReturn(Set.of("cache:stats:jobPostings"));
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("cache:stats:jobPostings")).thenReturn(Map.of(
                "hits", "8",
                "misses", "2",
                "bloom_rejects", "1",
                "null_hits", "1"
        ));

        Map<String, Map<Object, Object>> result = cacheStatsService.getCacheStats();

        assertNotNull(result);
        assertTrue(result.containsKey("jobPostings"));
        Map<Object, Object> stats = result.get("jobPostings");
        assertEquals(12L, stats.get("total"));
        assertTrue(stats.get("hitRate") instanceof Float);
        Float hitRate = (Float) stats.get("hitRate");
        assertEquals(8f / 12f, hitRate, 0.0001f);
        assertTrue(hitRate >= 0.0f && hitRate <= 1.0f);
    }

    @Test
    void getCacheStats_whenKeysNullOrEmpty_shouldReturnEmptyMap() {
        when(stringRedisTemplate.keys("cache:stats:*")).thenReturn(null);

        Map<String, Map<Object, Object>> result = cacheStatsService.getCacheStats();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getCacheStats_whenHashEntriesEmpty_shouldNotIncludeInResult() {
        when(stringRedisTemplate.keys("cache:stats:*")).thenReturn(Set.of("cache:stats:emptyCache"));
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("cache:stats:emptyCache")).thenReturn(Collections.emptyMap());

        Map<String, Map<Object, Object>> result = cacheStatsService.getCacheStats();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getCacheStats_whenRedisThrowsException_shouldCatchAndReturnEmptyMap() {
        when(stringRedisTemplate.keys("cache:stats:*")).thenThrow(new RuntimeException("Redis connection error"));

        Map<String, Map<Object, Object>> result = cacheStatsService.getCacheStats();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
