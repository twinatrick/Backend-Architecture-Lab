package com.example.BackendArchitectureLab.WebSocket;

import com.example.BackendArchitectureLab.Event.AlarmEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Slf4j
@Component
public class AlarmReactiveSink {

    private final Sinks.Many<AlarmEvent> sink = Sinks.many().multicast().directBestEffort();

    public Sinks.EmitResult tryEmitNext(AlarmEvent message) {
        if (message == null) {
            return Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER;
        }
        Sinks.EmitResult result = sink.tryEmitNext(message);
        if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            log.warn("多播通道發送非致命狀態: {}", result);
        }
        return result;
    }

    public Flux<AlarmEvent> asFlux() {
        return sink.asFlux();
    }
}
