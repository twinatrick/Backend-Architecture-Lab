package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Vo.CacheStatsEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CacheStatsKafkaAutoConfigurationTest {

    @Test
    void cacheStatsKafkaTemplate_BuildsKafkaTemplateWithProperties() {
        CacheStatsKafkaAutoConfiguration configuration = new CacheStatsKafkaAutoConfiguration();

        KafkaTemplate<String, CacheStatsEvent> template = configuration.cacheStatsKafkaTemplate();
        assertNotNull(template);
        assertNotNull(template.getProducerFactory());
    }
}
