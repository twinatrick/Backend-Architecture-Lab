package com.example.BackendArchitectureLab.WebSocket;

import com.example.BackendArchitectureLab.Vo.Common.AlarmMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmWebSocketHandler extends TextWebSocketHandler {

    private final AlarmReactiveSink alarmReactiveSink;
    private final ObjectMapper objectMapper;
    private final Map<String, Disposable> sessionSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Disposable> keepAliveSubscriptions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket 連線已建立: sessionId={}", session.getId());

        Disposable alarmSub = alarmReactiveSink.asFlux()
                .onBackpressureDrop(dropped -> log.warn("慢消費者連線 (sessionId={}) 觸發背壓，已拋棄過期告警: {}",
                        session.getId(), dropped.getMessage()))
                .subscribe(alarm -> sendAlarmMessage(session, alarm));
        sessionSubscriptions.put(session.getId(), alarmSub);

        Disposable keepAliveSub = Flux.interval(Duration.ofSeconds(30))
                .subscribe(tick -> sendPing(session));
        keepAliveSubscriptions.put(session.getId(), keepAliveSub);
    }

    private void sendAlarmMessage(WebSocketSession session, AlarmMessage alarm) {
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(alarm)));
                }
            }
        } catch (IOException e) {
            log.error("推播 WebSocket 告警失敗: sessionId={}", session.getId(), e);
        }
    }

    private void sendPing(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new PingMessage(ByteBuffer.wrap(new byte[]{0})));
                }
            }
        } catch (IOException e) {
            log.warn("發送 Ping 保活訊框失敗: sessionId={}", session.getId(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket 連線已關閉: sessionId={}, status={}", session.getId(), status);
        Disposable alarmSub = sessionSubscriptions.remove(session.getId());
        if (alarmSub != null && !alarmSub.isDisposed()) {
            alarmSub.dispose();
        }
        Disposable keepAliveSub = keepAliveSubscriptions.remove(session.getId());
        if (keepAliveSub != null && !keepAliveSub.isDisposed()) {
            keepAliveSub.dispose();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket 傳輸異常: sessionId={}", session.getId(), exception);
    }
}
