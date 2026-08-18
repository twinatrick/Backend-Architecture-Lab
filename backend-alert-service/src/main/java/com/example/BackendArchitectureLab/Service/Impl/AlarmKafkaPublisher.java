package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.Common.AlarmMessage;
import com.example.BackendArchitectureLab.Service.IAlarmPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmKafkaPublisher implements IAlarmPublisher {

    private static final String TOPIC = "socketSend";
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private String toJsonString(List<AlarmMessage> message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.warn("告警訊息 JSON 序列化失敗: {}", e.toString());
            return null;
        }
    }


    @Override
    public void publish(List<AlarmMessage> message)  {
        log.debug("Sending message: {}", message.size());
            kafkaTemplate.send(TOPIC, Objects.requireNonNull(toJsonString(message)));

    }
}
