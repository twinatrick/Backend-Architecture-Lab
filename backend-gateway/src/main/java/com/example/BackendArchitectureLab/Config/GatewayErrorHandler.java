package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;

@Component
@Order(-2)
public class GatewayErrorHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (isConnectionRefused(ex)) {
            log.warn("上游服務無法連接: {}", ex.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

            ResponseType<Void> body = ResponseType.Fail("SERVICE_NOT_FOUND", "目標服務無法連接", 404);
            try {
                byte[] bytes = objectMapper.writeValueAsBytes(body);
                return exchange.getResponse()
                        .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
            } catch (Exception e) {
                return Mono.error(e);
            }
        }

        return Mono.empty();
    }

    private boolean isConnectionRefused(Throwable ex) {
        if (ex instanceof ConnectException) {
            return true;
        }
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
            if (cause instanceof ConnectException) {
                return true;
            }
        }
        String msg = ex.getMessage();
        if (msg != null && msg.contains("Connection refused")) {
            return true;
        }
        return false;
    }
}
