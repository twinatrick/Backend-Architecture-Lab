package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Common.AlarmMessage;
import com.example.BackendArchitectureLab.Service.IKafkaConsumerService;
import com.example.BackendArchitectureLab.WebSocket.AlarmWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KafkaConsumerService implements IKafkaConsumerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConsumerService.class);

    @KafkaListener(topics = "socketSend", containerFactory = "alarmMessageKafkaListenerContainerFactory")
    @Override
    public void listen(List<AlarmMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            LOGGER.warn("Received empty alarm message list");
            return;
        }
        LOGGER.debug("outSize: {}", messages.size());
        messages.forEach(AlarmWebSocket::broadcast);
    }

}
