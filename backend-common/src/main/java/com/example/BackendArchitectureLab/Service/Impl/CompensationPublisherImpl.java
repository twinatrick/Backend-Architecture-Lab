package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Service.ICompensationPublisher;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * CompensationPublisherImpl - 交易補償事件發佈實作，
 * 僅在有 compensationKafkaTemplate bean 的服務（alert-service / competency-service）中建立。
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
@ConditionalOnBean(name = "compensationKafkaTemplate")
public class CompensationPublisherImpl implements ICompensationPublisher {

    private static final String TOPIC = "transaction-compensation";
    private static final int EVENT_VERSION = 1;

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Autowired
    private KafkaTemplate<String, CompensationEvent> compensationKafkaTemplate;

    @Override
    public void publish(CompensationEvent event) {
        try {
            compensationKafkaTemplate.send(TOPIC, event.getTransactionId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("補償事件非同步發佈失敗: transactionId={}, action={}, status={}, cause={}",
                                    event.getTransactionId(), event.getAction(), event.getStatus(), ex.toString());
                        } else {
                            log.debug("補償事件已發佈: transactionId={}, action={}, status={}",
                                    event.getTransactionId(), event.getAction(), event.getStatus());
                        }
                    });
        } catch (Exception e) {
            log.warn("補償事件發佈失敗(同步): transactionId={}, cause={}", event.getTransactionId(), e.toString());
        }
    }

    @Override
    public void publishTransactionStarted(UUID transactionId, CompensationAction action, Map<String, Object> state) {
        publish(CompensationEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion(EVENT_VERSION)
                .transactionId(transactionId)
                .serviceName(serviceName)
                .action(action)
                .status(CompensationStatus.TRANSACTION_STARTED)
                .beforeState(state)
                .timestamp(Instant.now())
                .build());
    }

    @Override
    public void publishCommitted(UUID transactionId, CompensationAction action, Map<String, Object> state) {
        publish(CompensationEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion(EVENT_VERSION)
                .transactionId(transactionId)
                .serviceName(serviceName)
                .action(action)
                .status(CompensationStatus.COMMITTED)
                .beforeState(state)
                .timestamp(Instant.now())
                .build());
    }

    @Override
    public void publishFailed(UUID transactionId, CompensationAction action, Map<String, Object> state, String errorMessage) {
        publish(CompensationEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion(EVENT_VERSION)
                .transactionId(transactionId)
                .serviceName(serviceName)
                .action(action)
                .status(CompensationStatus.FAILED)
                .beforeState(state)
                .errorMessage(errorMessage)
                .timestamp(Instant.now())
                .build());
    }

    @Override
    public void publishCompensated(UUID transactionId, CompensationAction action, Map<String, Object> state) {
        publish(CompensationEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion(EVENT_VERSION)
                .transactionId(transactionId)
                .serviceName(serviceName)
                .action(action)
                .status(CompensationStatus.COMPENSATED)
                .beforeState(state)
                .timestamp(Instant.now())
                .build());
    }
}
