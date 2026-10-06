package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.Common.AlarmMessage;
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
        AlarmMessage msg1 = new AlarmMessage();
        msg1.setLevel("ERROR");
        msg1.setMessage("alarm 1");

        AlarmMessage msg2 = new AlarmMessage();
        msg2.setLevel("WARN");
        msg2.setMessage("alarm 2");

        List<AlarmMessage> messages = List.of(msg1, msg2);

        kafkaConsumerService.listen(messages);

        verify(alarmReactiveSink, times(1)).tryEmitNext(msg1);
        verify(alarmReactiveSink, times(1)).tryEmitNext(msg2);
    }

    @Test
    void listen_whenSingleMessage_shouldBroadcastOnce() {
        AlarmMessage msg = new AlarmMessage();
        msg.setLevel("INFO");
        msg.setMessage("single alarm");

        kafkaConsumerService.listen(List.of(msg));

        verify(alarmReactiveSink, times(1)).tryEmitNext(msg);
    }
}
