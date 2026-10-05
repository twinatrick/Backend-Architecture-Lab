package com.example.BackendArchitectureLab.WebSocket;

import com.example.BackendArchitectureLab.Vo.Common.AlarmMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class AlarmWebSocket {

    private static AlarmReactiveSink staticSink;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public AlarmWebSocket(AlarmReactiveSink alarmReactiveSink) {
        AlarmWebSocket.staticSink = alarmReactiveSink;
    }

    public static void broadcast(AlarmMessage alarmMessage) {
        if (staticSink != null && alarmMessage != null) {
            staticSink.tryEmitNext(alarmMessage);
        }
    }
}
