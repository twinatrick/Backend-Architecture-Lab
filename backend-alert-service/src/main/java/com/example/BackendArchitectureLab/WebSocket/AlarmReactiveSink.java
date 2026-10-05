package com.example.BackendArchitectureLab.WebSocket;

import com.example.BackendArchitectureLab.Vo.Common.AlarmMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Slf4j
@Component
public class AlarmReactiveSink {

    private final Sinks.Many<AlarmMessage> sink = Sinks.many().multicast().directBestEffort();

    public Sinks.EmitResult tryEmitNext(AlarmMessage message) {
        if (message == null) {
            return Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER;
        }
        Sinks.EmitResult result = sink.tryEmitNext(message);
        if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            log.warn("多播通道發送非致命狀態: {}", result);
        }
        return result;
    }

    public Flux<AlarmMessage> asFlux() {
        return sink.asFlux();
    }
}
