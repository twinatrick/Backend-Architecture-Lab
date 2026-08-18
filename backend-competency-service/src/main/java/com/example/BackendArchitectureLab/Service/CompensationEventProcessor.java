package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Entity.CompensationEventLog;
import com.example.BackendArchitectureLab.Exception.UnsupportedEventVersionException;
import com.example.BackendArchitectureLab.Exception.UnsupportedCompensationActionException;
import com.example.BackendArchitectureLab.Exception.CompensationConflictException;
import com.example.BackendArchitectureLab.Exception.CompensationDeadEventException;
import com.example.BackendArchitectureLab.DataAccess.ICompensationEventLogDataAccess;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEventLogStatus;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.Date;
import java.util.UUID;

/**
 * CompensationEventProcessor - 補償事件處理協調者（由 {@code CompensationConsumer} 委派）。
 * <p>
 * 以 event_id 唯一鍵原子領取事件（at-least-once 下同一 eventId 至多被處理一次補償），
 * 處理狀態 PROCESSING → PROCESSED / FAILED，FAILED 於下次送達時以 retryClaim CAS 重試，
 * PROCESSING 且租約過期（預設 300 秒）由其他實例以 reclaimLease 接手。
 * <p>
 * Fencing Token：每次認領會生成新的 ownerId 並使 fencingVersion 單調遞增，快照存於
 * {@code compensation_event_log}，並於呼叫策略時原樣傳遞；下游還原端以此驗證
 * 只有「最新一代」的持有者能真正執行還原並標記結果，避免舊租約持有者覆寫新結果。
 * <p>
 * 本類僅負責：事件驗證、原子領取（claim）、去重路由、失敗重試、租約恢復與狀態編排；
 * 實際職責委派如下：
 * <ul>
 *   <li>payload 序列化／還原：{@link ICompensationPayloadService}</li>
 *   <li>終態（PROCESSED/FAILED/DEAD）CAS 標記：{@link ICompensationStateService}</li>
 *   <li>Strategy 分派與重試分類：{@link ICompensationExecutionService}</li>
 * </ul>
 * <p>
 * 不可重試（permanent）例外（{@link UnsupportedEventVersionException}、
 * {@link UnsupportedCompensationActionException}、
 * {@link CompensationConflictException}、
 * {@link IllegalArgumentException}）會直接標記 DEAD 後向外 rethrow，由 Kafka 錯誤處理器
 * 轉發至 DLT 供人工介入，不進行無意義的重試。
 * <p>
 * 已達最大重試次數（{@code compensation.consumer.max-attempts}）或重送時發現事件已是 DEAD
 * （先前已隔離）亦以 {@link CompensationDeadEventException} 向外 rethrow，確保隔離事件一律
 * 交由 DLT 記錄，而非被 Kafka offset commit 靜默吞掉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationEventProcessor {

    /** 目前唯一支援的事件 schema 版本 */
    private static final int SUPPORTED_EVENT_VERSION = 1;

    private final ICompensationEventLogDataAccess eventLogRepository;
    private final ICompensationPayloadService payloadService;
    private final ICompensationStateService stateService;
    private final ICompensationExecutionService executionService;

    @Value("${compensation.consumer.lease-seconds:300}")
    private long leaseSeconds;

    @Value("${compensation.consumer.max-attempts:5}")
    private int maxAttempts;

    public void process(CompensationEvent event) {
        if (event.getEventId() == null) {
            throw new IllegalArgumentException("Compensation eventId must not be null");
        }
        if (event.getEventVersion() != SUPPORTED_EVENT_VERSION) {
            throw new UnsupportedEventVersionException(
                    "Unsupported event version: " + event.getEventVersion());
        }

        // 驗證是否有支援的 strategy (若是 COMPENSATION_REQUIRED 且是未知的 Action)
        if (CompensationStatus.COMPENSATION_REQUIRED.equals(event.getStatus())
                && !executionService.supports(event.getAction())) {
            throw new UnsupportedCompensationActionException(
                    "Unsupported compensation action: " + event.getAction());
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
        entry.setPayload(payloadService.serialize(event));
        try {
            return eventLogRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateKeyViolation(e)) {
                return null;
            }
            // 其他完整性錯誤（欄位長度/NULL constraint/trigger 等）不可當成 duplicate 吞掉：
            // rethrow 交由 Kafka 錯誤處理器重試 / 轉發 DLT，避免事件 silent drop。
            throw e;
        }
    }

    /**
     * 判斷 DataIntegrityViolationException 是否為明確的 unique key conflict（SQLState 23505）。
     * 僅此類錯誤可視為「eventId 重複」；其他 integrity 失敗必須向外傳播。
     */
    private boolean isDuplicateKeyViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        if (cause instanceof ConstraintViolationException cve) {
            return "23505".equals(cve.getSQLState());
        }
        if (cause instanceof SQLException sqlEx) {
            return "23505".equals(sqlEx.getSQLState());
        }
        return false;
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

    /**
     * 以 persisted payload 還原處理事件（所有回復路徑共同的 authoritative source）。
     * payload 損毀無法還原時，依 persistence error semantics 直接標記 DEAD 後向外 rethrow，
     * 不退回 Kafka redelivery 的 payload，避免重複取得不同內容的事件。
     */
    private CompensationEvent replayFromPersistedPayload(CompensationEventLog entry) {
        try {
            return payloadService.deserialize(entry.getPayload());
        } catch (RuntimeException e) {
            log.error("Persisted compensation event payload is corrupt, quarantine in DEAD status: eventId={}",
                    entry.getEventId(), e);
            stateService.markDead(entry.getEventId(), entry.getOwnerId(), entry.getFencingVersion(), e.getMessage());
            throw e;
        }
    }

    private void handleDuplicateEvent(CompensationEvent event) {
        CompensationEventLog existing = eventLogRepository.findByEventId(event.getEventId()).orElse(null);
        if (existing == null) {
            log.error("Duplicate-key collision occurred but existing event log could not be found: eventId={}",
                    event.getEventId());
            throw new IllegalStateException(
                    "Compensation event log disappeared after duplicate-key collision: " + event.getEventId());
        }
        switch (existing.getStatus()) {
            case CompensationEventLogStatus.PROCESSED -> {
                log.debug("Compensation event already processed, skipped: eventId={}", event.getEventId());
            }
            case CompensationEventLogStatus.FAILED -> retryFailedEvent(event, existing);
            case CompensationEventLogStatus.PROCESSING -> recoverExpiredLease(event, existing);
            case CompensationEventLogStatus.DEAD -> {
                log.error("Compensation event already quarantined in DEAD, forward to DLT: eventId={}",
                        event.getEventId());
                throw new CompensationDeadEventException(
                        "Compensation event is already in DEAD status, not retryable: eventId=" + event.getEventId());
            }
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
                stateService.markProcessed(entry.getEventId(), entry.getOwnerId(), entry.getFencingVersion());
                return;
            }
            log.warn("Executing compensation for transaction {} action {} with owner {} fence {}",
                    event.getTransactionId(), event.getAction(), entry.getOwnerId(), entry.getFencingVersion());
            executionService.execute(event, entry.getOwnerId(), entry.getFencingVersion());
            stateService.markProcessed(entry.getEventId(), entry.getOwnerId(), entry.getFencingVersion());
        } catch (Exception e) {
            if (executionService.isNonRetryable(e)) {
                log.error("Compensation failed with non-retryable error. Quarantine in DEAD status: eventId={}",
                        event.getEventId(), e);
                stateService.markDead(entry.getEventId(), entry.getOwnerId(), entry.getFencingVersion(), e.getMessage());
                throw e; // 仍 rethrow：讓 Kafka 錯誤處理器識別為 non-retryable，直接轉發 DLT
            } else if (entry.getAttemptCount() >= maxAttempts) {
                log.error("Compensation failed and reached max attempts ({}). Quarantine in DEAD status: eventId={}",
                        maxAttempts, event.getEventId(), e);
                stateService.markDead(entry.getEventId(), entry.getOwnerId(), entry.getFencingVersion(), e.getMessage());
                throw new CompensationDeadEventException(
                        "Compensation event reached max attempts and was quarantined in DEAD: eventId="
                                + event.getEventId(), e); // 向外 rethrow：Kafka 錯誤處理器識別為不可重試，直接轉發 DLT
            } else {
                log.warn("Compensation failed, will retry: eventId={}, attempt={}",
                        event.getEventId(), entry.getAttemptCount(), e);
                stateService.markFailed(entry.getEventId(), entry.getOwnerId(), entry.getFencingVersion(), entry.getAttemptCount(), e.getMessage());
                throw e; // 拋出異常，觸發 Kafka 重試/redelivery
            }
        }
    }
}
