package com.example.BackendArchitectureLab.Config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.serializer.SerializationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisConfigTest {

    @Mock
    private Cache cache;

    private CacheErrorHandler errorHandler;
    private final String testKey = "test-key";

    @BeforeEach
    void setUp() {
        RedisConfig redisConfig = new RedisConfig();
        errorHandler = redisConfig.errorHandler();
        when(cache.getName()).thenReturn("test-cache");
    }

    @Test
    void handleCacheGetError_SerializationException_EvictsAndReturns() {
        SerializationException exception = new SerializationException("Deserialization failed");

        assertDoesNotThrow(() ->
            errorHandler.handleCacheGetError(exception, cache, testKey)
        );

        verify(cache).evict(testKey);
    }

    @Test
    void handleCacheGetError_OtherException_Throws() {
        RuntimeException exception = new RuntimeException("Connection refused");

        assertThrows(RuntimeException.class, () ->
            errorHandler.handleCacheGetError(exception, cache, testKey)
        );

        verify(cache, never()).evict(any());
    }

    @Test
    void handleCachePutError_Throws() {
        RuntimeException exception = new RuntimeException("Put failed");

        assertThrows(RuntimeException.class, () ->
            errorHandler.handleCachePutError(exception, cache, testKey, "value")
        );
    }

    @Test
    void handleCacheEvictError_Throws() {
        RuntimeException exception = new RuntimeException("Evict failed");

        assertThrows(RuntimeException.class, () ->
            errorHandler.handleCacheEvictError(exception, cache, testKey)
        );
    }

    @Test
    void handleCacheClearError_Throws() {
        RuntimeException exception = new RuntimeException("Clear failed");

        assertThrows(RuntimeException.class, () ->
            errorHandler.handleCacheClearError(exception, cache)
        );
    }
}
