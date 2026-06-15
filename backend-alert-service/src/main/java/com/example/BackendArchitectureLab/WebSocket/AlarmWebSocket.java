package com.example.BackendArchitectureLab.WebSocket;

import com.example.BackendArchitectureLab.Dto.Vo.Common.AlarmMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.OnClose;
import lombok.extern.slf4j.Slf4j;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@ServerEndpoint("/ws/alarm")
public class AlarmWebSocket {
    private static final Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());
    private static final ObjectMapper objectMapper = new ObjectMapper();
    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
    }

    public static void localBroadcast(String jsonMessage) {
        for (Session session : sessions) {
            if (session.isOpen()) {
                session.getAsyncRemote().sendText(jsonMessage);
            }
        }
    }

    public static void broadcast(AlarmMessage alarmMessage) {
        try {
            String msg = objectMapper.writeValueAsString(alarmMessage);
            localBroadcast(msg);
        } catch (IOException e) {
            log.error("序列化告警訊息失敗", e);
        }
    }

}
