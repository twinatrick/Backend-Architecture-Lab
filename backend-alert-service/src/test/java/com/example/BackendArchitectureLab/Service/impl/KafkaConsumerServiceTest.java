package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Common.AlarmMessage;
import com.example.BackendArchitectureLab.WebSocket.AlarmWebSocket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KafkaConsumerServiceTest {

    @InjectMocks
    private KafkaConsumerService kafkaConsumerService;

    @Test
    void listen_whenMessagesIsNull_shouldLogWarningAndReturn() {
        kafkaConsumerService.listen(null);

        try (MockedStatic<AlarmWebSocket> mockedWebSocket = mockStatic(AlarmWebSocket.class)) {
            mockedWebSocket.verifyNoInteractions();
        }
    }

    @Test
    void listen_whenMessagesIsEmpty_shouldLogWarningAndReturn() {
        kafkaConsumerService.listen(List.of());

        try (MockedStatic<AlarmWebSocket> mockedWebSocket = mockStatic(AlarmWebSocket.class)) {
            mockedWebSocket.verifyNoInteractions();
        }
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

        try (MockedStatic<AlarmWebSocket> mockedWebSocket = mockStatic(AlarmWebSocket.class)) {
            kafkaConsumerService.listen(messages);

            mockedWebSocket.verify(() -> AlarmWebSocket.broadcast(msg1), times(1));
            mockedWebSocket.verify(() -> AlarmWebSocket.broadcast(msg2), times(1));
        }
    }

    @Test
    void listen_whenSingleMessage_shouldBroadcastOnce() {
        AlarmMessage msg = new AlarmMessage();
        msg.setLevel("INFO");
        msg.setMessage("single alarm");

        try (MockedStatic<AlarmWebSocket> mockedWebSocket = mockStatic(AlarmWebSocket.class)) {
            kafkaConsumerService.listen(List.of(msg));

            mockedWebSocket.verify(() -> AlarmWebSocket.broadcast(msg), times(1));
        }
    }
}
