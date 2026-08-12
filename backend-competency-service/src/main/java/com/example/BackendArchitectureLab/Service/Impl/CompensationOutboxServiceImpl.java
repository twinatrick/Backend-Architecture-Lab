package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Entity.CompensationOutboxEvent;
import com.example.BackendArchitectureLab.Repository.CompensationOutboxEventRepository;
import com.example.BackendArchitectureLab.Service.ICompensationOutboxService;
import com.example.BackendArchitectureLab.Service.ICompensationPublisher;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CompensationOutboxServiceImpl - 交易補償事件的 Outbox 實作，
 * 事件先寫入自家資料庫（與業務交易同 commit），排程批次經 CompensationPublisher 發送至 Kafka。
 */
@Slf4j
@Service
public class CompensationOutboxServiceImpl implements ICompensationOutboxService {

    private static final int EVENT_VERSION = 1;

    @Autowired
    private CompensationOutboxEventRepository outboxRepository;

    @Autowired
    private ICompensationPublisher compensationPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void enqueueTransactionStarted(UUID transactionId, CompensationAction action, Map<String, Object> state) {
        saveOutbox(transactionId, action, CompensationStatus.TRANSACTION_STARTED, state, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueCommitted(UUID transactionId, CompensationAction action, Map<String, Object> state) {
        saveOutbox(transactionId, action, CompensationStatus.COMMITTED, state, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueFailed(UUID transactionId, CompensationAction action, Map<String, Object> state, String errorMessage) {
        saveOutbox(transactionId, action, CompensationStatus.FAILED, state, errorMessage);
    }

    /**
     * 批次發佈尚未發送的事件（預設每 5 秒執行一次）
     */
    @Scheduled(fixedDelayString = "${compensation.outbox.flush-delay-ms:5000}")
    @Override
    public void flushPendingEvents() {
        List<CompensationOutboxEvent> pending = outboxRepository.findTop20BySentFalseOrderByCreatedTimeAsc();
        for (CompensationOutboxEvent outbox : pending) {
            try {
                CompensationEvent event = objectMapper.readValue(outbox.getPayload(), CompensationEvent.class);
                compensationPublisher.publish(event);
                outbox.setSent(true);
                outbox.setSentAt(new Date());
                outboxRepository.save(outbox);
            } catch (Exception e) {
                outbox.setAttemptCount(outbox.getAttemptCount() + 1);
                outbox.setErrorMessage(e.getMessage());
                outboxRepository.save(outbox);
                log.warn("Outbox 事件發佈失敗: eventId={}, attempt={}, cause={}",
                        outbox.getEventId(), outbox.getAttemptCount(), e.toString());
            }
        }
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