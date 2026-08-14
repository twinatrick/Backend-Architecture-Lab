package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Entity.CompensationEventLog;
import com.example.BackendArchitectureLab.Exception.CompensationConflictException;
import com.example.BackendArchitectureLab.Exception.UnsupportedEventVersionException;
import com.example.BackendArchitectureLab.Exception.UnsupportedCompensationActionException;
import com.example.BackendArchitectureLab.Repository.CompensationEventLogRepository;
import com.example.BackendArchitectureLab.Service.Strategy.CompensationStrategy;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEventLogStatus;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * CompensationEventProcessor - 補償事件處理核心（由 {@code CompensationConsumer} 委派）。
 * <p>
 * 以 event_id 唯一鍵原子領取事件（at-least-once 下同一 eventId 至多被處理一次補償），
 * 處理狀態 PROCESSING → PROCESSED / FAILED，FAILED 於下次送達時以 retryClaim CAS 重試，
 * PROCESSING 且租約過期（預設 300 秒）由其他實例以 reclaimLease 接手。
 * <p>
 * Fencing Token：每次認領會生成新的 ownerId 並使 fencingVersion 單調遞增，快照存於
 * {@code compensation_event_log}，並於呼叫策略時原樣傳遞；下游還原端以此驗證
 * 只有「最新一代」的持有者能真正執行還原並標記結果，避免舊租約持有者覆寫新結果。
 * <p>
 * 不可重試（permanent）例外（{@link UnsupportedEventVersionException}、
 * {@link UnsupportedCompensationActionException}、{@link CompensationConflictException}、
 * {@link IllegalArgumentException}）會直接標記 DEAD 後向外 rethrow，由 Kafka 錯誤處理器
 * 轉發至 DLT 供人工介入，不進行無意義的重試。
 */
@Slf4j
@Service
public class CompensationEventProcessor {

    /** 目前唯一支援的事件 schema 版本 */
    private static final int SUPPORTED_EVENT_VERSION = 1;

    @Autowired
    private CompensationEventLogRepository eventLogRepository;

    @Autowired
    private List<CompensationStrategy> compensationStrategies;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${compensation.consumer.lease-seconds:300}")
    private long leaseSeconds;

    @Value("${compensation.consumer.max-attempts:5}")
    private int maxAttempts;

    @Value("${compensation.consumer.retry-backoff-ms:60000}")
    private long retryBackoffMs;

    public void process(CompensationEvent event) {
        if (event.getEventId() == null) {
            throw new IllegalArgumentException("Compensation eventId must not be null");
        }
        if (event.getEventVersion() != SUPPORTED_EVENT_VERSION) {
            throw new UnsupportedEventVersionException(
                    "Unsupported event version: " + event.getEventVersion());
        }

        // 驗證是否有支援的 strategy (若是 COMPENSATION_REQUIRED 且是未知的 Action)
        if (CompensationStatus.COMPENSATION_REQUIRED.equals(event.getStatus())) {
            boolean hasStrategy = false;
            for (CompensationStrategy strategy : compensationStrategies) {
                if (strategy.supports(event.getAction())) {
                    hasStrategy = true;
                    break;
                }
            }
            if (!hasStrategy) {
                throw new UnsupportedCompensationActionException(
                        "Unsupported compensation action: " + event.getAction());
            }
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
        entry.setOwnerId(UUID.randomUUID().toString());
        entry.setFencingVersion(1L);
        entry.setReceivedAt(now);
        entry.setProcessingAt(now);
        entry.setLeaseUntil(new Date(now.getTime() + leaseSeconds * 1000L));
        entry.setPayload(serialize(event));
        try {
            return eventLogRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException e) {
            return null;
        }
    }

    /**
     * 處理過期租約回收的滯留事件：由 {@code CompensationLeaseReclaimer} 在 CAS 重新認領並刷新
     * ownerId/fencingVersion 後呼叫本方法。事件內容自 stored payload 反序列化，且不再重新 claim。
     */
    public void processReclaimed(CompensationEventLog entry) {
        CompensationEvent event = replayFromPersistedPayload(entry);
        log.info("Processing reclaimed compensation event after lease expiry: eventId={}, attempt={}",
                event.getEventId(), entry.getAttemptCount());
        processEntry(entry, event);
    }

    private String serialize(CompensationEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize compensation event payload: " + event.getEventId(), e);
        }
    }

    private CompensationEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, CompensationEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize compensation event payload: " + payload, e);
        }
    }

    /**
     * 以 persisted payload 還原處理事件（所有回復路徑共同的 authoritative source）。
     * payload 損毀無法還原時，依 persistence error semantics 直接標記 DEAD 後向外 rethrow，
     * 不退回 Kafka redelivery 的 payload，避免重複取得不同內容的事件。
     */
    private CompensationEvent replayFromPersistedPayload(CompensationEventLog entry) {
        try {
            return deserialize(entry.getPayload());
        } catch (RuntimeException e) {
            log.error("Persisted compensation event payload is corrupt, quarantine in DEAD status: eventId={}",
                    entry.getEventId(), e);
            markDead(entry, e.getMessage());
            throw e;
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
                UUID.randomUUID().toString(),
                now,
                new Date(now.getTime() + leaseSeconds * 1000L),
                now);
        if (claimed == 1) {
            CompensationEventLog entry = eventLogRepository.findByEventId(event.getEventId()).orElse(null);
            if (entry == null) {
                log.warn("Failed event log disappeared during retry, skipped: eventId={}", event.getEventId());
                return;
            }
            log.info("Retrying previously failed compensation event: eventId={}, attempt={}",
                    event.getEventId(), entry.getAttemptCount());
            processEntry(entry, replayFromPersistedPayload(entry));
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
                UUID.randomUUID().toString(),
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
            processEntry(entry, replayFromPersistedPayload(entry));
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
            if (!CompensationStatus.COMPENSATION_REQUIRED.equals(event.getStatus())) {
                markProcessed(entry);
                return;
            }
            log.warn("Executing compensation for transaction {} action {} with owner {} fence {}",
                    event.getTransactionId(), event.getAction(), entry.getOwnerId(), entry.getFencingVersion());
            executeCompensation(event, entry.getOwnerId(), entry.getFencingVersion());
            markProcessed(entry);
        } catch (Exception e) {
            if (isNonRetryable(e)) {
                log.error("Compensation failed with non-retryable error. Quarantine in DEAD status: eventId={}",
                        event.getEventId(), e);
                markDead(entry, e.getMessage());
                throw e; // 仍 rethrow：讓 Kafka 錯誤處理器識別為 non-retryable，直接轉發 DLT
            } else if (entry.getAttemptCount() >= maxAttempts) {
                log.error("Compensation failed and reached max attempts ({}). Quarantine in DEAD status: eventId={}",
                        maxAttempts, event.getEventId(), e);
                markDead(entry, e.getMessage());
                // 不再 rethrow，使 Kafka offset 順利 commit
            } else {
                log.warn("Compensation failed, will retry: eventId={}, attempt={}",
                        event.getEventId(), entry.getAttemptCount(), e);
                markFailed(entry, e.getMessage());
                throw e; // 拋出異常，觸發 Kafka 重試/redelivery
            }
        }
    }

    private void executeCompensation(CompensationEvent event, String ownerId, Long fencingVersion) {
        for (CompensationStrategy strategy : compensationStrategies) {
            if (strategy.supports(event.getAction())) {
                strategy.compensate(event, ownerId, fencingVersion);
                return;
            }
        }
        throw new UnsupportedCompensationActionException("Unsupported compensation action: " + event.getAction());
    }

    /**
     * 判斷是否為不可重試的永久錯誤：契約不相容（版本 / 未知 action / 缺失必要狀態）與永久性業務衝突。
     * 這類錯誤重試亦不會成功，Kafka 端已設定為 non-retryable 並轉發 DLT。
     */
    private boolean isNonRetryable(Throwable e) {
        return e instanceof UnsupportedEventVersionException
                || e instanceof UnsupportedCompensationActionException
                || e instanceof CompensationConflictException
                || e instanceof IllegalArgumentException;
    }

    private void markProcessed(CompensationEventLog entry) {
        int updated = eventLogRepository.markState(
                entry.getEventId(), entry.getOwnerId(), entry.getFencingVersion(),
                CompensationEventLogStatus.PROCESSING,
                CompensationEventLogStatus.PROCESSED,
                new Date(), null, null, null);
        if (updated != 1) {
            log.warn("markState skipped (stale token or status changed), processed result not committed: eventId={}",
                    entry.getEventId());
        }
    }

    private void markFailed(CompensationEventLog entry, String errorMessage) {
        Date now = new Date();
        int updated = eventLogRepository.markState(
                entry.getEventId(), entry.getOwnerId(), entry.getFencingVersion(),
                CompensationEventLogStatus.PROCESSING,
                CompensationEventLogStatus.FAILED,
                null, now, truncate(errorMessage),
                new Date(now.getTime() + retryBackoffMs * entry.getAttemptCount()));
        if (updated != 1) {
            log.warn("markState skipped (stale token or status changed), failed result not committed: eventId={}",
                    entry.getEventId());
        }
    }

    private void markDead(CompensationEventLog entry, String errorMessage) {
        Date now = new Date();
        int updated = eventLogRepository.markState(
                entry.getEventId(), entry.getOwnerId(), entry.getFencingVersion(),
                CompensationEventLogStatus.PROCESSING,
                CompensationEventLogStatus.DEAD,
                null, now, truncate(errorMessage), null);
        if (updated != 1) {
            log.warn("markState skipped (stale token or status changed), dead result not committed: eventId={}",
                    entry.getEventId());
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1024 ? message.substring(0, 1024) : message;
    }
}
