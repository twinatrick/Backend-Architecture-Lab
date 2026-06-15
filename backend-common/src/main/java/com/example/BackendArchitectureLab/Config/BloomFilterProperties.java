package com.example.BackendArchitectureLab.Config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "cache.bloom-filter")
public class BloomFilterProperties {

    private long defaultExpectedInsertions = 10000L;

    private double defaultFalseProbability = 0.001;

    private Map<String, BloomFilterConfig> overrides = new HashMap<>();

    public long getExpectedInsertions(String cacheName) {
        BloomFilterConfig config = overrides.get(cacheName);
        return config != null ? config.getExpectedInsertions() : defaultExpectedInsertions;
    }

    public double getFalseProbability(String cacheName) {
        BloomFilterConfig config = overrides.get(cacheName);
        return config != null ? config.getFalseProbability() : defaultFalseProbability;
    }

    @Data
    public static class BloomFilterConfig {
        private long expectedInsertions;
        private double falseProbability;
    }
}
