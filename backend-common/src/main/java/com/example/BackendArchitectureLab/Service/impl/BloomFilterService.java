package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Config.BloomFilterProperties;
import com.example.BackendArchitectureLab.Service.IBloomFilterService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class BloomFilterService implements IBloomFilterService {

    private final RedissonClient redissonClient;

    private final BloomFilterProperties bloomFilterProperties;

    private final Map<String, RBloomFilter<String>> filters = new ConcurrentHashMap<>();

    public BloomFilterService(RedissonClient redissonClient, BloomFilterProperties bloomFilterProperties) {
        this.redissonClient = redissonClient;
        this.bloomFilterProperties = bloomFilterProperties;
    }

    @Override
    public boolean mightContain(String cacheName, String key) {
        RBloomFilter<String> filter = filters.get(cacheName);
        if (filter == null) {
            return true;
        }
        return filter.contains(key);
    }

    @Override
    public void add(String cacheName, String key) {
        RBloomFilter<String> filter = getOrCreate(cacheName);
        filter.add(key);
    }

    @Override
    public void addAll(String cacheName, Collection<String> keys) {
        RBloomFilter<String> filter = getOrCreate(cacheName);
        for (String key : keys) {
            filter.add(key);
        }
    }

    @Override
    public long count(String cacheName) {
        RBloomFilter<String> filter = getOrCreate(cacheName);
        return filter.count();
    }

    @Override
    public void clear(String cacheName) {
        RBloomFilter<String> filter = filters.remove(cacheName);
        if (filter != null) {
            filter.delete();
        }
    }

    public RBloomFilter<String> getOrCreate(String cacheName) {
        return filters.computeIfAbsent(cacheName, name -> {
            RBloomFilter<String> filter = redissonClient.getBloomFilter("bloom:" + name);
            if (!filter.isExists()) {
                long expectedInsertions = bloomFilterProperties.getExpectedInsertions(name);
                double falseProbability = bloomFilterProperties.getFalseProbability(name);
                filter.tryInit(expectedInsertions, falseProbability);
                log.info("初始化布隆過濾器 [bloom:{}] - 預期資料量: {}, 誤判率: {}",
                        name, expectedInsertions, falseProbability);
            }
            return filter;
        });
    }

    @PreDestroy
    public void destroy() {
        filters.clear();
    }
}
