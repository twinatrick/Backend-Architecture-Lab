package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Entity.CompensationOutboxEvent;
import com.example.BackendArchitectureLab.DataAccess.ICompensationOutboxEventDataAccess;
import com.example.BackendArchitectureLab.Service.ICompensationOutboxService;
import com.example.BackendArchitectureLab.Vo.CompensationOutboxDeliveryStatus;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * CompensationOutboxServiceImpl - 交易補償事件的 Outbox 寫入實作，
 * 事件與業務交易同 commit 寫入自家資料庫；實際發佈至 Kafka 由 {@code Timer/CompensationOutboxWorker} 負責。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationOutboxServiceImpl implements ICompensationOutboxService {

    private static final int EVENT_VERSION = 1;

    private final ICompensationOutboxEventDataAccess outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void enqueueTransactionStarted(UUID transactionId, CompensationAction action, Map<String, Object> state) {
        saveOutbox(transactionId, action, CompensationStatus.TRANSACTION_STARTED, state, null);
    }

    @Override
    public void enqueueCommitted(UUID transactionId, CompensationAction action, Map<String, Object> state) {
        saveOutbox(transactionId, action, CompensationStatus.COMMITTED, state, null);
    }

    @Override
    @Transactional
    public void enqueueFailureAndCompensationRequired(UUID transactionId, CompensationAction action,
                                                      Map<String, Object> state, String errorMessage) {
        saveOutbox(transactionId, action, CompensationStatus.FAILED, state, errorMessage);
        saveOutbox(transactionId, action, CompensationStatus.COMPENSATION_REQUIRED, state, errorMessage);
    }

    private void saveOutbox(UUID transactionId, CompensationAction action, String status,
                            Map<String, Object> state, String errorMessage) {
        UUID eventId = UUID.randomUUID();
        CompensationEvent event = CompensationEvent.builder()
                .eventId(eventId)
                .eventVersion(EVENT_VERSION)
                .transactionId(transactionId)
                .serviceName("competency-service")
                .action(action)
                .status(status)
                .beforeState(state)
                .errorMessage(errorMessage)
                .timestamp(Instant.now())
                .build();

        CompensationOutboxEvent outbox = new CompensationOutboxEvent();
        outbox.setTransactionId(transactionId);
        outbox.setEventId(eventId);
        outbox.setAction(action.name());
        outbox.setStatus(status);
        outbox.setDeliveryStatus(CompensationOutboxDeliveryStatus.PENDING);
        outbox.setPayload(toJson(event));
        outboxRepository.save(outbox);
    }

    private String toJson(CompensationEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("無法序列化補償事件", e);
        }
    }
}
