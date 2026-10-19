package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Service.CacheStatsPublisher;
import com.example.BackendArchitectureLab.Service.IBloomFilterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchRedisCacheOperationsTest {

    private static final String CACHE_NAME = "users";

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private RedisConnection connection;

    @Mock
    private RedisStringCommands stringCommands;

    @Mock
    private RedisKeyCommands keyCommands;

    @Mock
    private IBloomFilterService bloomFilterService;

    @Mock
    private CacheStatsPublisher statsPublisher;

    @Mock
    private RedisCache redisCache;

    private RedisCacheConfiguration configuration;
    private BatchRedisCacheOperations operations;

    @BeforeEach
    void setUp() {
        configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer()
                ))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer()
                ));
        when(redisCache.getCacheConfiguration()).thenReturn(configuration);
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.stringCommands()).thenReturn(stringCommands);
        when(connection.keyCommands()).thenReturn(keyCommands);
        operations = new BatchRedisCacheOperations(connectionFactory, bloomFilterService, statsPublisher);
    }

    @Test
    void getAll_UsesMgetAndSeparatesHitMissAndNullMarker() {
        String hitKey = UUID.randomUUID().toString();
        String missKey = UUID.randomUUID().toString();
        String knownMissingKey = UUID.randomUUID().toString();
        byte[] hitValue = "cached".getBytes(StandardCharsets.UTF_8);
        when(stringCommands.mGet(any(byte[][].class)))
                .thenReturn(
                        Arrays.asList(hitValue, null, null),
                        Arrays.asList(null, null, "NULL_MARKER".getBytes(StandardCharsets.UTF_8))
                );

        BatchCacheOperations.BatchReadResult result = operations.getAll(
                CACHE_NAME,
                redisCache,
                List.of(hitKey, missKey, knownMissingKey)
        );

        assertEquals(Map.of(hitKey, "cached"), result.hits());
        assertEquals(Set.of(missKey), result.misses());
        assertEquals(Set.of(knownMissingKey), result.knownMissing());
        verify(statsPublisher).publish(CACHE_NAME, "hits", 1);
        verify(statsPublisher).publish(CACHE_NAME, "misses", 1);
    }

    @Test
    void getAll_WhenPositiveValueAndNullMarkerCoexist_NullMarkerTakesPriority() {
        String key = UUID.randomUUID().toString();
        when(stringCommands.mGet(any(byte[][].class)))
                .thenReturn(
                        List.of("stale".getBytes(StandardCharsets.UTF_8)),
                        List.of("NULL_MARKER".getBytes(StandardCharsets.UTF_8))
                );

        BatchCacheOperations.BatchReadResult result = operations.getAll(
                CACHE_NAME,
                redisCache,
                List.of(key)
        );

        assertTrue(result.hits().isEmpty());
        assertTrue(result.misses().isEmpty());
        assertEquals(Set.of(key), result.knownMissing());
        verify(statsPublisher, never()).publish(CACHE_NAME, "hits", 1);
    }

    @Test
    void getAll_WhenRedisFails_ReturnsAllKeysAsMisses() {
        String firstKey = UUID.randomUUID().toString();
        String secondKey = UUID.randomUUID().toString();
        when(stringCommands.mGet(any(byte[][].class))).thenThrow(new RuntimeException("Redis unavailable"));

        BatchCacheOperations.BatchReadResult result = operations.getAll(
                CACHE_NAME,
                redisCache,
                List.of(firstKey, secondKey)
        );

        assertTrue(result.hits().isEmpty());
        assertEquals(Set.of(firstKey, secondKey), result.misses());
        assertTrue(result.knownMissing().isEmpty());
    }

    @Test
    void putAll_UsesConfiguredPrefixSerializerTtlAndPipeline() {
        String uuidKey = UUID.randomUUID().toString();
        String nonUuidKey = "byname:admin";

        operations.putAll(CACHE_NAME, redisCache, Map.of(uuidKey, "uuid-value", nonUuidKey, "name-value"));

        verify(connection).openPipeline();
        verify(connection).closePipeline();
        verify(bloomFilterService).addAll(CACHE_NAME, List.of(uuidKey));

        ArgumentCaptor<byte[]> keyCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> valueCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(stringCommands, times(2))
                .pSetEx(keyCaptor.capture(), anyLong(), valueCaptor.capture());

        Set<String> physicalKeys = keyCaptor.getAllValues().stream()
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .collect(Collectors.toSet());
        Set<String> serializedValues = valueCaptor.getAllValues().stream()
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .collect(Collectors.toSet());
        assertEquals(Set.of("users::" + uuidKey, "users::" + nonUuidKey), physicalKeys);
        assertEquals(Set.of("uuid-value", "name-value"), serializedValues);
    }

    @Test
    void putAll_WithNoUuidKeys_DoesNotTouchBloomFilter() {
        operations.putAll(CACHE_NAME, redisCache, Map.of("all", "value"));

        verify(bloomFilterService, never()).addAll(any(), any());
    }

    @Test
    void putNulls_WritesMarkerWithTtlAndDeletesPositiveValue() {
        String key = UUID.randomUUID().toString();

        operations.putNulls(CACHE_NAME, redisCache, List.of(key), Duration.ofMinutes(3));

        verify(stringCommands).pSetEx(
                argThat(bytes -> Arrays.equals(
                        bytes,
                        "null:users:".concat(key).getBytes(StandardCharsets.UTF_8)
                )),
                eq(Duration.ofMinutes(3).toMillis()),
                argThat(bytes -> Arrays.equals(bytes, "NULL_MARKER".getBytes(StandardCharsets.UTF_8)))
        );
        ArgumentCaptor<byte[]> deletedKey = ArgumentCaptor.forClass(byte[].class);
        verify(keyCommands).del(deletedKey.capture());
        assertEquals("users::" + key, new String(deletedKey.getValue(), StandardCharsets.UTF_8));
    }

}
