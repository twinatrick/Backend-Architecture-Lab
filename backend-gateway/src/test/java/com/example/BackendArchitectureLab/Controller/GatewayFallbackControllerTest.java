package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Vo.ResponseType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class GatewayFallbackControllerTest {

    private GatewayFallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new GatewayFallbackController();
    }

    @Test
    @DisplayName("Should return 503 SERVICE_UNAVAILABLE when no exception is attached")
    void testFallbackDefault() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/fallback").build());

        StepVerifier.create(controller.fallback(exchange))
                .assertNext(response -> {
                    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
                    ResponseType<Void> body = response.getBody();
                    assertNotNull(body);
                    assertEquals("SERVICE_UNAVAILABLE", body.getErrorType());
                    assertEquals(503, body.getCode());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return 503 CIRCUIT_BREAKER_OPEN when CallNotPermittedException is present")
    void testFallbackCircuitBreakerOpen() {
        CircuitBreaker cb = CircuitBreaker.of("test", CircuitBreakerConfig.ofDefaults());
        CallNotPermittedException exception = CallNotPermittedException.createCallNotPermittedException(cb);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/fallback").build());
        exchange.getAttributes().put(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR, exception);

        StepVerifier.create(controller.fallback(exchange))
                .assertNext(response -> {
                    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
                    ResponseType<Void> body = response.getBody();
                    assertNotNull(body);
                    assertEquals("CIRCUIT_BREAKER_OPEN", body.getErrorType());
                    assertEquals(503, body.getCode());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return 503 GATEWAY_TIMEOUT when TimeoutException is present")
    void testFallbackTimeout() {
        TimeoutException exception = new TimeoutException("Gateway timeout");

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/fallback").build());
        exchange.getAttributes().put(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR, exception);

        StepVerifier.create(controller.fallback(exchange))
                .assertNext(response -> {
                    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
                    ResponseType<Void> body = response.getBody();
                    assertNotNull(body);
                    assertEquals("GATEWAY_TIMEOUT", body.getErrorType());
                    assertEquals(503, body.getCode());
                })
                .verifyComplete();
    }
}
