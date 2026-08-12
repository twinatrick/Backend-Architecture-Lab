package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
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
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaCompensationConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, CompensationEvent> compensationProducerFactory(ObjectMapper objectMapper) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
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
        deserializer.addTrustedPackages("com.example.BackendArchitectureLab.Vo.Kafka");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean(name = "compensationKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, CompensationEvent> compensationKafkaListenerContainerFactory(
            ConsumerFactory<String, CompensationEvent> compensationConsumerFactory,
            KafkaTemplate<String, CompensationEvent> compensationKafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, CompensationEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(compensationConsumerFactory);
        // Consumer retry policy：FixedBackOff（1 秒間隔、最多 4 次退避 = 含首發共 5 次嘗試），
        // 超過後交由 DeadLetterPublishingRecoverer 轉發至 DLT 主題，並自動 commit offset 避免阻塞正常消費。
        // Permanent 錯誤（無法靠重試復原）不重試：Unsupported event version、Unsupported action、
        // eventId null（契約違反）——直接交由 DLT。
        org.springframework.kafka.listener.DeadLetterPublishingRecoverer recoverer =
                new org.springframework.kafka.listener.DeadLetterPublishingRecoverer(compensationKafkaTemplate);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 4L));
        errorHandler.addNotRetryableExceptions(
                com.example.BackendArchitectureLab.Exception.UnsupportedEventVersionException.class,
                com.example.BackendArchitectureLab.Exception.UnsupportedCompensationActionException.class,
                IllegalArgumentException.class);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
