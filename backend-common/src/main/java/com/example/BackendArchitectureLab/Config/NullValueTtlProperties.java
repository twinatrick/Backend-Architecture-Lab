package com.example.BackendArchitectureLab.Config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "cache.penetration")
public class NullValueTtlProperties {

    private Duration defaultNullTtl = Duration.ofMinutes(3);

    private Map<String, Duration> nullTtlOverrides = new HashMap<>();

    public Duration getNullTtl(String cacheName) {
        return nullTtlOverrides.getOrDefault(cacheName, defaultNullTtl);
    }
}
