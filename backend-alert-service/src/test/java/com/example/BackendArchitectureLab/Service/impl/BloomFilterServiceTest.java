package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Config.BloomFilterProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BloomFilterServiceTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBloomFilter<Object> bloomFilter;

    @Spy
    private BloomFilterProperties bloomFilterProperties = new BloomFilterProperties();

    @InjectMocks
    private BloomFilterService bloomFilterService;

    @Test
    void mightContain_WhenFilterSaysYes_ReturnsTrue() {
        when(redissonClient.getBloomFilter(anyString())).thenReturn(bloomFilter);
        when(bloomFilter.isExists()).thenReturn(true);
        when(bloomFilter.contains("existing-key")).thenReturn(true);

        bloomFilterService.count("test-cache");
        assertTrue(bloomFilterService.mightContain("test-cache", "existing-key"));
    }

    @Test
    void mightContain_WhenFilterSaysNo_ReturnsFalse() {
        when(redissonClient.getBloomFilter(anyString())).thenReturn(bloomFilter);
        when(bloomFilter.isExists()).thenReturn(true);
        when(bloomFilter.contains("unknown-key")).thenReturn(false);

        bloomFilterService.count("test-cache");
        assertFalse(bloomFilterService.mightContain("test-cache", "unknown-key"));
    }

    @Test
    void add_DelegatesToFilter() {
        when(redissonClient.getBloomFilter(anyString())).thenReturn(bloomFilter);
        when(bloomFilter.isExists()).thenReturn(true);

        bloomFilterService.add("test-cache", "new-key");

        verify(bloomFilter).add("new-key");
    }

    @Test
    void addAll_DelegatesToFilter() {
        when(redissonClient.getBloomFilter(anyString())).thenReturn(bloomFilter);
        when(bloomFilter.isExists()).thenReturn(true);

        bloomFilterService.addAll("test-cache", java.util.List.of("key1", "key2", "key3"));

        verify(bloomFilter).add("key1");
        verify(bloomFilter).add("key2");
        verify(bloomFilter).add("key3");
    }

    @Test
    void getOrCreate_InitializesNewFilter() {
        when(redissonClient.getBloomFilter("bloom:new-cache")).thenReturn(bloomFilter);
        when(bloomFilter.isExists()).thenReturn(false);

        bloomFilterService.count("new-cache");

        verify(bloomFilter).tryInit(10000L, 0.001);
    }

    @Test
    void getOrCreate_ReusesExistingFilter() {
        when(redissonClient.getBloomFilter("bloom:existing-cache")).thenReturn(bloomFilter);
        when(bloomFilter.isExists()).thenReturn(true);

        bloomFilterService.count("existing-cache");
        bloomFilterService.count("existing-cache");

        verify(bloomFilter, never()).tryInit(anyLong(), anyDouble());
    }

    @Test
    void getOrCreate_WithCustomProperties_UsesCustomValues() {
        when(redissonClient.getBloomFilter("bloom:custom-cache")).thenReturn(bloomFilter);
        when(bloomFilter.isExists()).thenReturn(false);

        bloomFilterProperties.setDefaultExpectedInsertions(50000);

        bloomFilterService.count("custom-cache");

        verify(bloomFilter).tryInit(50000L, 0.001);
    }

    @Test
    void getOrCreate_WithOverride_UsesOverrideValues() {
        BloomFilterProperties.BloomFilterConfig override = new BloomFilterProperties.BloomFilterConfig();
        override.setExpectedInsertions(100000);
        override.setFalseProbability(0.0001);
        bloomFilterProperties.getOverrides().put("large-cache", override);

        when(redissonClient.getBloomFilter("bloom:large-cache")).thenReturn(bloomFilter);
        when(bloomFilter.isExists()).thenReturn(false);

        bloomFilterService.count("large-cache");

        verify(bloomFilter).tryInit(100000L, 0.0001);
    }
}
