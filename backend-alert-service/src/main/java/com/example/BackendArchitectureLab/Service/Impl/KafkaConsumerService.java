package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.Common.AlarmMessage;
import com.example.BackendArchitectureLab.Service.IKafkaConsumerService;
import com.example.BackendArchitectureLab.WebSocket.AlarmReactiveSink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService implements IKafkaConsumerService {

    private final AlarmReactiveSink alarmReactiveSink;

    @KafkaListener(topics = "socketSend", containerFactory = "alarmMessageKafkaListenerContainerFactory")
    @Override
    public void listen(List<AlarmMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            log.warn("Received empty alarm message list");
            return;
        }
        log.debug("outSize: {}", messages.size());
        messages.forEach(alarmReactiveSink::tryEmitNext);
    }

}
