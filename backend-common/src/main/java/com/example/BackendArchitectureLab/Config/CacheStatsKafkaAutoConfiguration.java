package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Vo.CacheStatsEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
public class CacheStatsKafkaAutoConfiguration {

    @Value("${spring.kafka.bootstrap-servers:}")
    private String configuredBootstrapServers;

    @Value("${app.in-docker:false}")
    private boolean inDocker;

    private String resolveBootstrapServers() {
        if (configuredBootstrapServers != null && !configuredBootstrapServers.isBlank()) {
            return configuredBootstrapServers;
        }
        return inDocker ? "kafka:29092" : "localhost:9092";
    }

    @Bean
    @ConditionalOnMissingBean(name = "cacheStatsKafkaTemplate")
    public KafkaTemplate<String, CacheStatsEvent> cacheStatsKafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, resolveBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 1500);
        ProducerFactory<String, CacheStatsEvent> factory = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(factory);
    }
}
