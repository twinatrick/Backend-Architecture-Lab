package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Dto.Kafka.CompensationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaCompensationConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, CompensationEvent> compensationProducerFactory(ObjectMapper objectMapper) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new JsonSerializer<>(objectMapper));
    }

    @Bean
    public KafkaTemplate<String, CompensationEvent> compensationKafkaTemplate(
            ProducerFactory<String, CompensationEvent> compensationProducerFactory) {
        return new KafkaTemplate<>(compensationProducerFactory);
    }

    @Bean
    public ConsumerFactory<String, CompensationEvent> compensationConsumerFactory(ObjectMapper objectMapper) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "compensation-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<CompensationEvent> deserializer = new JsonDeserializer<>(
                CompensationEvent.class, objectMapper, false);
        deserializer.addTrustedPackages("com.example.BackendArchitectureLab.Dto.Kafka");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean(name = "compensationKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, CompensationEvent> compensationKafkaListenerContainerFactory(
            ConsumerFactory<String, CompensationEvent> compensationConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, CompensationEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(compensationConsumerFactory);
        factory.setCommonErrorHandler(new DefaultErrorHandler());
        return factory;
    }
}
