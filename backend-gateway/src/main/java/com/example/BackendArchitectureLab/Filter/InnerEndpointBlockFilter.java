package com.example.BackendArchitectureLab.Filter;

import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class InnerEndpointBlockFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(InnerEndpointBlockFilter.class);

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isInnerPath(path)) {
            log.warn("阻擋對外訪問內部端點: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            ResponseType<Void> body = ResponseType.Fail("NOT_FOUND", "Not Found", 404);
            try {
                byte[] bytes = objectMapper.writeValueAsBytes(body);
                return exchange.getResponse()
                        .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
            } catch (Exception e) {
                return Mono.error(e);
            }
        }
        return chain.filter(exchange);
    }

    private boolean isInnerPath(String path) {
        return path.matches(".*/inner(/.*)?");
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
