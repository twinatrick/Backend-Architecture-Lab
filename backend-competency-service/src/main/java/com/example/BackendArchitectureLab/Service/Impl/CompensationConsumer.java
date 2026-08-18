package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Service.CompensationEventProcessor;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * 補償事件 Kafka 消費入口：接收 transaction-compensation 事件後委派
 * {@link CompensationEventProcessor} 處理（原子領取 / 去重 / 重試 / 租約恢復 / 策略委派）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationConsumer {

    private final CompensationEventProcessor eventProcessor;

    @KafkaListener(topics = "transaction-compensation", containerFactory = "compensationKafkaListenerContainerFactory")
    public void handleCompensation(CompensationEvent event) {
        log.info("Received compensation event: action={}, status={}, transactionId={}, eventId={}, eventVersion={}",
                event.getAction(), event.getStatus(), event.getTransactionId(),
                event.getEventId(), event.getEventVersion());

        eventProcessor.process(event);
    }
}
