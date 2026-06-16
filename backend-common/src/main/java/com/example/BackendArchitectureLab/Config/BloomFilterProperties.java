package com.example.BackendArchitectureLab.Config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "cache.bloom-filter")
public class BloomFilterProperties {

    private long defaultExpectedInsertions = 10000L;

    private double defaultFalseProbability = 0.001;

    private List<String> entities = new ArrayList<>();

    private Map<String, BloomFilterConfig> overrides = new HashMap<>();

    public Map<String, String> getEntityCacheMap() {
        Map<String, String> entityCacheMap = new HashMap<>();
        for (String entity : entities) {
            String[] parts = entity.split(":", 2);
            if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                entityCacheMap.put(parts[0].trim(), parts[1].trim());
            }
        }
        return entityCacheMap;
    }

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
