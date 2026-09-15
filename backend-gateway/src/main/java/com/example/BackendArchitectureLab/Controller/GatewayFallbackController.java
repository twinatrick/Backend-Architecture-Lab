package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

@ApiControllerTag(name = "Gateway Fallback", description = "微服務降級與熔斷統一回退端點")
@RestController
public class GatewayFallbackController {

    private static final Logger log = LoggerFactory.getLogger(GatewayFallbackController.class);

    @ApiOperationOk(summary = "熔斷降級回退端點", description = "下游微服務不可用或超時被熔斷時的統一回退處理")
    @RequestMapping("/fallback")
    public Mono<ResponseEntity<ResponseType<Void>>> fallback(ServerWebExchange exchange) {
        Throwable exception = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);

        String errorType = "SERVICE_UNAVAILABLE";
        String message = "目標服務暫時無法提供服務，請稍後再試";

        if (exception != null) {
            log.warn("Gateway 路由觸發降級保護: exception={}", exception.toString());
            if (exception instanceof CallNotPermittedException) {
                errorType = "CIRCUIT_BREAKER_OPEN";
                message = "目標服務熔斷保護中，請稍後再試";
            } else if (exception instanceof TimeoutException) {
                errorType = "GATEWAY_TIMEOUT";
                message = "目標服務請求超時，已觸發熔斷保護";
            }
        } else {
            log.warn("Gateway 路由觸發降級保護 (無例外資訊)");
        }

        ResponseType<Void> body = ResponseType.Fail(errorType, message, HttpStatus.SERVICE_UNAVAILABLE.value());
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }
}
