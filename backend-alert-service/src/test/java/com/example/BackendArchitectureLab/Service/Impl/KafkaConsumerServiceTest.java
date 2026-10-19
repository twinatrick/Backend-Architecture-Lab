package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Event.AlarmEvent;
import com.example.BackendArchitectureLab.WebSocket.AlarmReactiveSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerServiceTest {

    @Mock
    private AlarmReactiveSink alarmReactiveSink;

    @InjectMocks
    private KafkaConsumerService kafkaConsumerService;

    @Test
    void listen_whenMessagesIsNull_shouldLogWarningAndReturn() {
        kafkaConsumerService.listen(null);

        verifyNoInteractions(alarmReactiveSink);
    }

    @Test
    void listen_whenMessagesIsEmpty_shouldLogWarningAndReturn() {
        kafkaConsumerService.listen(List.of());

        verifyNoInteractions(alarmReactiveSink);
    }

    @Test
    void listen_whenMessagesIsNotEmpty_shouldBroadcastEachMessage() {
        AlarmEvent msg1 = new AlarmEvent();
        msg1.setLevel("ERROR");
        msg1.setMessage("alarm 1");

        AlarmEvent msg2 = new AlarmEvent();
        msg2.setLevel("WARN");
        msg2.setMessage("alarm 2");

        List<AlarmEvent> messages = List.of(msg1, msg2);

        kafkaConsumerService.listen(messages);

        verify(alarmReactiveSink, times(1)).tryEmitNext(msg1);
        verify(alarmReactiveSink, times(1)).tryEmitNext(msg2);
    }

    @Test
    void listen_whenSingleMessage_shouldBroadcastOnce() {
        AlarmEvent msg = new AlarmEvent();
        msg.setLevel("INFO");
        msg.setMessage("single alarm");

        kafkaConsumerService.listen(List.of(msg));

        verify(alarmReactiveSink, times(1)).tryEmitNext(msg);
    }
}
