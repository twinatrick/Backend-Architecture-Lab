package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.WebSocket.AlarmWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AlarmWebSocketHandler alarmWebSocketHandler;
    private final String allowedOrigins;

    public WebSocketConfig(
            AlarmWebSocketHandler alarmWebSocketHandler,
            @Value("${app.websocket.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:8000}") String allowedOrigins) {
        this.alarmWebSocketHandler = alarmWebSocketHandler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        registry.addHandler(alarmWebSocketHandler, "/ws/alarm")
                .setAllowedOrigins(origins);
    }
}
