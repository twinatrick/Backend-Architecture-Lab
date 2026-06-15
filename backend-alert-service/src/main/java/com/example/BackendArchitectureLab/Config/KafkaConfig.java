package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Dto.Vo.Common.AlarmMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class KafkaConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConfig.class);

    @Value("${spring.kafka.bootstrap-servers:}")
    private String configuredBootstrapServers;

    @Value("${app.in-docker:false}")
    private boolean inDocker;

    @Value("${kafka.consumer.group-id:myGroup}")
    private String groupId;

    @Bean
    public ProducerFactory<String, String> alarmProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, resolveBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> alarmKafkaTemplate(ProducerFactory<String, String> alarmProducerFactory) {
        return new KafkaTemplate<>(alarmProducerFactory);
    }

    @Bean
    public ConsumerFactory<String, List<AlarmMessage>> alarmMessageConsumerFactory(ObjectMapper objectMapper) {
        String bootstrapServers = resolveBootstrapServers();

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<List<AlarmMessage>> deserializer = new JsonDeserializer<>(
                new TypeReference<>() {},
                objectMapper,
                false
        );
        deserializer.addTrustedPackages("com.example.BackendArchitectureLab.Util");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean(name = "alarmMessageKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, List<AlarmMessage>> alarmMessageKafkaListenerContainerFactory(
            ConsumerFactory<String, List<AlarmMessage>> alarmMessageConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, List<AlarmMessage>> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(alarmMessageConsumerFactory);
        factory.setCommonErrorHandler(new DefaultErrorHandler());
        return factory;
    }

    private String resolveBootstrapServers() {
        if (configuredBootstrapServers != null && !configuredBootstrapServers.isBlank()) {
            LOGGER.info("Kafka bootstrap servers from Config: {}", configuredBootstrapServers);
            return configuredBootstrapServers;
        }

        String resolved = inDocker ? "kafka:9092" : "localhost:9092";
        LOGGER.info("Kafka bootstrap servers resolved by APP_IN_DOCKER({}): {}", inDocker, resolved);
        return resolved;
    }
}
