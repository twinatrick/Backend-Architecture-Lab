package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Common.AlarmMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlarmKafkaPublisherTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private AlarmKafkaPublisher alarmKafkaPublisher;

    @Test
    void publish_whenSerializationSucceeds_shouldSendToKafka() throws Exception {
        List<AlarmMessage> messages = List.of(createAlarmMessage("ERROR", "test message"));
        String json = "[{\"level\":\"ERROR\",\"message\":\"test message\"}]";
        when(objectMapper.writeValueAsString(messages)).thenReturn(json);

        alarmKafkaPublisher.publish(messages);

        verify(objectMapper, times(1)).writeValueAsString(messages);
        verify(kafkaTemplate, times(1)).send("socketSend", json);
    }

    @Test
    void publish_withMultipleMessages_shouldSerializeAllAndSend() throws Exception {
        List<AlarmMessage> messages = List.of(
                createAlarmMessage("ERROR", "msg1"),
                createAlarmMessage("WARN", "msg2"));
        String json = "[{\"level\":\"ERROR\",\"message\":\"msg1\"},{\"level\":\"WARN\",\"message\":\"msg2\"}]";
        when(objectMapper.writeValueAsString(messages)).thenReturn(json);

        alarmKafkaPublisher.publish(messages);

        verify(kafkaTemplate, times(1)).send("socketSend", json);
    }

    @Test
    void publish_whenSerializationFails_shouldThrowNullPointerException() throws Exception {
        List<AlarmMessage> messages = List.of(createAlarmMessage("ERROR", "test"));
        when(objectMapper.writeValueAsString(messages)).thenThrow(new JsonProcessingException("Cannot serialize") {});

        assertThrows(NullPointerException.class, () -> alarmKafkaPublisher.publish(messages));

        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }

    @Test
    void publish_whenObjectMapperReturnsNull_shouldThrowNullPointerException() throws Exception {
        List<AlarmMessage> messages = List.of(createAlarmMessage("WARN", "test"));
        when(objectMapper.writeValueAsString(messages)).thenReturn(null);

        assertThrows(NullPointerException.class, () -> alarmKafkaPublisher.publish(messages));

        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }

    @Test
    void publish_withEmptyList_shouldSerializeEmptyArrayAndSend() throws Exception {
        List<AlarmMessage> messages = List.of();
        String json = "[]";
        when(objectMapper.writeValueAsString(messages)).thenReturn(json);

        alarmKafkaPublisher.publish(messages);

        verify(kafkaTemplate, times(1)).send("socketSend", json);
    }

    private AlarmMessage createAlarmMessage(String level, String message) {
        AlarmMessage alarm = new AlarmMessage();
        alarm.setLevel(level);
        alarm.setMessage(message);
        return alarm;
    }
}
