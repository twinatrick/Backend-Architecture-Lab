package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Entity.CompensationEventLog;
import com.example.BackendArchitectureLab.Exception.UnsupportedEventVersionException;
import com.example.BackendArchitectureLab.Repository.CompensationEventLogRepository;
import com.example.BackendArchitectureLab.Service.Strategy.CompensationStrategy;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEventLogStatus;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 補償事件消費端（transaction-compensation）。
 * <p>
 * 以 event_id 唯一鍵原子領取事件（at-least-once 下同一 eventId 至多被本 Consumer 執行一次補償），
 * 處理狀態 PROCESSING → PROCESSED / FAILED，FAILED 於下次送達時以 retryClaim CAS 重試，
 * PROCESSING 且租約過期（預設 300 秒）由其他實例以 reclaimLease 接手。
 * <p>
 * 注意：事件層級去重無法保證業務 side effect 的 exactly-once——租約過期時前一個實例可能仍在執行，
 * 補償可能被重複執行；`CompensationStrategy` 實作必須以 eventId 為冪等鍵自行設計冪等
 * （目前策略為 log-only 無副作用）。
 */
@Slf4j
@Service
public class CompensationConsumer {

    /** 目前唯一支援的事件 schema 版本 */
    private static final int SUPPORTED_EVENT_VERSION = 1;

    @Autowired
    private CompensationEventLogRepository eventLogRepository;

    @Autowired
    private List<CompensationStrategy> compensationStrategies;

    @Value("${compensation.consumer.lease-seconds:300}")
    private long leaseSeconds;

    @KafkaListener(topics = "transaction-compensation", containerFactory = "compensationKafkaListenerContainerFactory")
    public void handleCompensation(CompensationEvent event) {
        log.info("Received compensation event: action={}, status={}, transactionId={}, eventId={}, eventVersion={}",
                event.getAction(), event.getStatus(), event.getTransactionId(),
                event.getEventId(), event.getEventVersion());

        if (event.getEventId() == null) {
            log.warn("Compensation event without eventId ignored (unsupported legacy event): transactionId={}",
                    event.getTransactionId());
            return;
        }

        // 原子領取：event_id 唯一鍵保證同一事件只會被一個實例處理
        CompensationEventLog entry = claimEventLog(event);
        if (entry == null) {
            handleDuplicateEvent(event);
            return;
        }

        processEntry(entry, event);
    }

    /**
     * 領取事件處理權：以 event_id 唯一鍵執行 INSERT（saveAndFlush），
     * 成功 → PROCESSING（帶處理租約）進入處理流程；唯一鍵衝突 → 回傳 null（由呼叫端依狀態決定去重或重試）。
     */
    private CompensationEventLog claimEventLog(CompensationEvent event) {
        CompensationEventLog entry = new CompensationEventLog();
        Date now = new Date();
        entry.setEventId(event.getEventId());
        entry.setTransactionId(event.getTransactionId());
        entry.setStatus(CompensationEventLogStatus.PROCESSING);
        entry.setAttemptCount(1);
        entry.setReceivedAt(now);
        entry.setProcessingAt(now);
        entry.setLeaseUntil(new Date(now.getTime() + leaseSeconds * 1000L));
        try {
            return eventLogRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException e) {
            return null;
        }
    }

    private void handleDuplicateEvent(CompensationEvent event) {
        CompensationEventLog existing = eventLogRepository.findByEventId(event.getEventId()).orElse(null);
        if (existing == null) {
            log.warn("Duplicate compensation event without existing log, ignored: eventId={}", event.getEventId());
            return;
        }
        switch (existing.getStatus()) {
            case CompensationEventLogStatus.PROCESSED -> {
                log.debug("Compensation event already processed, skipped: eventId={}", event.getEventId());
            }
            case CompensationEventLogStatus.FAILED -> retryFailedEvent(event, existing);
            case CompensationEventLogStatus.PROCESSING -> recoverExpiredLease(event, existing);
            default -> log.debug("Compensation event already in-flight, skipped: eventId={}, status={}",
                    event.getEventId(), existing.getStatus());
        }
    }

    private void retryFailedEvent(CompensationEvent event, CompensationEventLog existing) {
        Date now = new Date();
        int claimed = eventLogRepository.retryClaim(
                event.getEventId(),
                CompensationEventLogStatus.PROCESSING,
                CompensationEventLogStatus.FAILED,
                now,
                new Date(now.getTime() + leaseSeconds * 1000L));
        if (claimed == 1) {
            CompensationEventLog entry = eventLogRepository.findByEventId(event.getEventId()).orElse(null);
            if (entry == null) {
                log.warn("Failed event log disappeared during retry, skipped: eventId={}", event.getEventId());
                return;
            }
            log.info("Retrying previously failed compensation event: eventId={}, attempt={}",
                    event.getEventId(), entry.getAttemptCount());
            processEntry(entry, event);
        } else {
            log.debug("Failed event is being retried by another instance, skipped: eventId={}", event.getEventId());
        }
    }

    /**
     * PROCESSING 但租約到期（處理者 crash / 逾時）：以 CAS 重新認領；租約未到期 → 跳過（仍在處理中）。
     */
    private void recoverExpiredLease(CompensationEvent event, CompensationEventLog existing) {
        Date now = new Date();
        if (existing.getLeaseUntil() == null || existing.getLeaseUntil().after(now)) {
            log.debug("Compensation event lease not expired, skipped: eventId={}", event.getEventId());
            return;
        }
        int reclaimed = eventLogRepository.reclaimLease(
                event.getEventId(),
                CompensationEventLogStatus.PROCESSING,
                now,
                now,
                new Date(now.getTime() + leaseSeconds * 1000L));
        if (reclaimed == 1) {
            CompensationEventLog entry = eventLogRepository.findByEventId(event.getEventId()).orElse(null);
            if (entry == null) {
                log.warn("Expired-lease event log disappeared during reclaim, skipped: eventId={}",
                        event.getEventId());
                return;
            }
            log.info("Reclaimed compensation event after lease expiry: eventId={}, attempt={}",
                    event.getEventId(), entry.getAttemptCount());
            processEntry(entry, event);
        } else {
            log.debug("Expired-lease event reclaimed by another instance, skipped: eventId={}", event.getEventId());
        }
    }

    /**
     * 執行事件處理：僅 COMPENSATION_REQUIRED 才執行補償（成功 = COMPENSATED 語意），
     * 其餘狀態（TRANSACTION_STARTED/COMMITTED/FAILED/COMPENSATED）僅記錄後標記完成。
     */
    private void processEntry(CompensationEventLog entry, CompensationEvent event) {
        try {
            if (event.getEventVersion() != SUPPORTED_EVENT_VERSION) {
                throw new UnsupportedEventVersionException(
                        "Unsupported event version: " + event.getEventVersion());
            }
            if (!CompensationStatus.COMPENSATION_REQUIRED.equals(event.getStatus())) {
                markProcessed(entry);
                return;
            }
            log.warn("Executing compensation for transaction {} action {}",
                    event.getTransactionId(), event.getAction());
            executeCompensation(event);
            markProcessed(entry);
        } catch (Exception e) {
            markFailed(entry, e.getMessage());
            throw e;
        }
    }

    private void executeCompensation(CompensationEvent event) {
        for (CompensationStrategy strategy : compensationStrategies) {
            if (strategy.supports(event.getAction())) {
                strategy.compensate(event);
                return;
            }
        }
        // 未知動作：拋出例外 → 消費者標記 FAILED 並 rethrow 交由 Kafka 重試（最終 DEAD/DLQ）
        throw new IllegalStateException("Unsupported compensation action: " + event.getAction());
    }

    private void markProcessed(CompensationEventLog entry) {
        entry.setStatus(CompensationEventLogStatus.PROCESSED);
        entry.setProcessedAt(new Date());
        entry.setLastError(null);
        eventLogRepository.save(entry);
    }

    private void markFailed(CompensationEventLog entry, String errorMessage) {
        entry.setStatus(CompensationEventLogStatus.FAILED);
        entry.setLastError(truncate(errorMessage));
        eventLogRepository.save(entry);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1024 ? message.substring(0, 1024) : message;
    }
}