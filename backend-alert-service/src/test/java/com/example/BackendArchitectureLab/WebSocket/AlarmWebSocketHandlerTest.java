package com.example.BackendArchitectureLab.WebSocket;

import com.example.BackendArchitectureLab.Vo.Common.AlarmMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlarmWebSocketHandlerTest {

    private AlarmReactiveSink alarmReactiveSink;
    private ObjectMapper objectMapper;
    private AlarmWebSocketHandler handler;

    @Mock
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        alarmReactiveSink = new AlarmReactiveSink();
        objectMapper = new ObjectMapper();
        handler = new AlarmWebSocketHandler(alarmReactiveSink, objectMapper);
    }

    @Test
    void alarmReactiveSink_shouldEmitAndDeliverMessages() {
        AlarmMessage message = new AlarmMessage();
        message.setLevel("CRITICAL");
        message.setMessage("水位超標");

        StepVerifier.create(alarmReactiveSink.asFlux())
                .then(() -> alarmReactiveSink.tryEmitNext(message))
                .expectNextMatches(m -> "CRITICAL".equals(m.getLevel()) && "水位超標".equals(m.getMessage()))
                .thenCancel()
                .verify();
    }

    @Test
    void afterConnectionEstablished_whenMessageEmitted_shouldSendTextMessage() throws IOException {
        when(session.getId()).thenReturn("test-session-1");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);

        AlarmMessage message = new AlarmMessage();
        message.setLevel("WARN");
        message.setMessage("水流過快");

        alarmReactiveSink.tryEmitNext(message);

        verify(session, timeout(1000).atLeastOnce()).sendMessage(any(TextMessage.class));

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
    }

    @Test
    void afterConnectionClosed_shouldCleanupSubscriptions() throws Exception {
        when(session.getId()).thenReturn("test-session-2");

        handler.afterConnectionEstablished(session);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        // Subsequent message should not attempt to send to closed session
        AlarmMessage message = new AlarmMessage();
        message.setLevel("INFO");
        alarmReactiveSink.tryEmitNext(message);

        verify(session, never()).sendMessage(any());
    }

    @Test
    void alarmWebSocket_broadcast_shouldEmitToSink() {
        AlarmWebSocket alarmWebSocket = new AlarmWebSocket(alarmReactiveSink);
        assertNotNull(alarmWebSocket);

        StepVerifier.create(alarmReactiveSink.asFlux())
                .then(() -> {
                    AlarmMessage message = new AlarmMessage();
                    message.setLevel("EMERGENCY");
                    message.setMessage("測試緊急告警");
                    AlarmWebSocket.broadcast(message);
                })
                .expectNextMatches(m -> "EMERGENCY".equals(m.getLevel()))
                .thenCancel()
                .verify();
    }
}
