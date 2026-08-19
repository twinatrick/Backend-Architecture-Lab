package com.example.BackendArchitectureLab.Timer;

import com.example.BackendArchitectureLab.Entity.CompensationOutboxEvent;
import com.example.BackendArchitectureLab.DataAccess.ICompensationOutboxEventDataAccess;
import com.example.BackendArchitectureLab.Service.ICompensationPublisher;
import com.example.BackendArchitectureLab.Vo.CompensationOutboxDeliveryStatus;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CompensationOutboxWorker - Outbox 發佈工作者（與寫入 Service 分離）。
 * 定期將尚未送達的事件批次發佈至 Kafka：先原子領取（PENDING/FAILED 到期或 PROCESSING 租約過期 → PROCESSING），
 * 等待 Kafka ACK 成功後才標記 SENT；失敗依指數退避安排下次重試，超過最大次數轉為 DEAD。
 * claim 後以 id 重新讀取最新狀態（attemptCount 已由 claim 遞增），狀態轉換一律透過
 * repository 的原子 UPDATE（markSent/markFailed/markDead，WHERE deliveryStatus = PROCESSING），
 * 不直接 save 陳舊 entity，避免覆蓋其他實例寫入的最新狀態。
 * 所有重試/退避政策皆可由組態調整（compensation.outbox.*）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompensationOutboxWorker {

    private static final long[] DEFAULT_BACKOFF_SECONDS = {5L, 15L, 30L, 60L, 300L};

    @Value("${compensation.outbox.max-attempts:5}")
    private int maxAttempts;

    @Value("${compensation.outbox.lease-seconds:300}")
    private long leaseSeconds;

    @Value("${compensation.outbox.batch-size:20}")
    private int batchSize;

    @Value("${compensation.outbox.ack-timeout-seconds:10}")
    private long ackTimeoutSeconds;

    @Value("${compensation.outbox.backoff-seconds:5,15,30,60,300}")
    private List<Long> backoffSeconds;

    @Value("${compensation.outbox.publish-parallelism:4}")
    private int publishParallelism;

    private final ICompensationOutboxEventDataAccess outboxRepository;
    private final ICompensationPublisher compensationPublisher;
    private final ObjectMapper objectMapper;
    private final ExecutorService compensationOutboxPublisherPool;
    private final AtomicBoolean isFlushing = new AtomicBoolean(false);
    private Semaphore publishSemaphore = new Semaphore(8);

    /**
     * 啟動時驗證租約組態不變式：
     * 批次最壞完成時間為 (expectedWaves + 1) * ackTimeoutSeconds + 1 秒（expectedWaves 由
     * batch-size 與 publish-parallelism 決定）；lease-seconds 若不足（少於該時間 + 60 秒安全餘裕），
     * 可能在同一批尚未完成時就被其他實例接管認領，造成重複發佈。此處 fail-fast 於啟動期攔截，
     * 避免上線後才出現 lease 打穿。
     */
    @PostConstruct
    void validateConfiguration() {
        int poolSize = Math.max(1, publishParallelism);
        this.publishSemaphore = new Semaphore(poolSize);
        int expectedWaves = Math.max(1, (batchSize + poolSize - 1) / poolSize);
        long worstCaseWaitSeconds = (expectedWaves + 1) * ackTimeoutSeconds + 1L;
        if (leaseSeconds < worstCaseWaitSeconds + 60L) {
            throw new IllegalStateException(
                    "compensation.outbox.lease-seconds (" + leaseSeconds
                            + ") must be at least worst-case batch wait (" + worstCaseWaitSeconds
                            + "s) + 60s margin to avoid lease expiry during a publish batch");
        }
    }

    private Semaphore getPublishSemaphore() {
        return publishSemaphore;
    }

    /**
     * 批次發佈尚未送達的事件（預設每 5 秒執行一次）。
     * 並行任務於 Java 21 虛擬執行緒執行器中執行，並由內部 {@link Semaphore} 嚴格約束最大 Kafka 發布併發度
     * （上限為 {@code compensation.outbox.publish-parallelism}），確保高併發下不衝擊下游 Broker 與資料庫；
     * 依批次大小與許可數估算最壞完成時間作為批次整體等待的保守逾時。
     */
    @Scheduled(fixedDelayString = "${compensation.outbox.flush-delay-ms:5000}")
    public void flushPendingEvents() {
        if (!isFlushing.compareAndSet(false, true)) {
            log.debug("上一次 Outbox 批次發布仍在執行中，略過本次排程");
            return;
        }
        try {
            List<CompensationOutboxEvent> pending = outboxRepository.findPendingDue(
                    List.of(CompensationOutboxDeliveryStatus.PENDING,
                            CompensationOutboxDeliveryStatus.FAILED,
                            CompensationOutboxDeliveryStatus.PROCESSING),
                    CompensationOutboxDeliveryStatus.PROCESSING,
                    PageRequest.of(0, batchSize));
            if (pending.isEmpty()) {
                return;
            }
            List<Future<?>> futures = new ArrayList<>(pending.size());
            for (CompensationOutboxEvent outbox : pending) {
                futures.add(compensationOutboxPublisherPool.submit(() -> publishOne(outbox)));
            }
            int poolSize = Math.max(1, publishParallelism);
            int expectedWaves = Math.max(1, (batchSize + poolSize - 1) / poolSize);
            long timeoutMs = TimeUnit.SECONDS.toMillis((expectedWaves + 1) * ackTimeoutSeconds + 1L);
            long deadline = System.currentTimeMillis() + timeoutMs;
            try {
                for (Future<?> future : futures) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        throw new TimeoutException("Batch timeout reached while waiting for outbox tasks");
                    }
                    future.get(remaining, TimeUnit.MILLISECONDS);
                }
                log.debug("Published {} outbox event(s) via shared publisher pool", pending.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Outbox publish batch wait interrupted", e);
            } catch (ExecutionException | TimeoutException e) {
                log.warn("Outbox publish batch wait elapsed or timed out, background tasks will continue independently: {}", e.getMessage());
            }
        } finally {
            isFlushing.set(false);
        }
    }

    /**
     * 發佈單一事件：原子領取 → 重新讀取最新狀態 → 呼叫 Kafka publisher 等待 ACK → 原子標記 SENT。
     * 任何例外皆由 handleDeliveryFailure 以帶 ownerId + fencingVersion 的原子 UPDATE 標記 FAILED/DEAD，不向上拋出。
     */
    private void publishOne(CompensationOutboxEvent outbox) {
        if (Thread.currentThread().isInterrupted()) {
            log.warn("Outbox publish task interrupted before permit acquisition: eventId={}", outbox.getEventId());
            return;
        }
        Semaphore semaphore = getPublishSemaphore();
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Outbox publish interrupted while acquiring concurrency permit", e);
            return;
        }
        try {
            if (Thread.currentThread().isInterrupted()) {
                log.warn("Outbox publish task interrupted after permit acquisition: eventId={}", outbox.getEventId());
                return;
            }
            Date now = new Date();
            String ownerId = UUID.randomUUID().toString();
            int claimed = outboxRepository.claimEvent(
                    outbox.getId(),
                    List.of(CompensationOutboxDeliveryStatus.PENDING,
                            CompensationOutboxDeliveryStatus.FAILED,
                            CompensationOutboxDeliveryStatus.PROCESSING),
                    CompensationOutboxDeliveryStatus.PROCESSING,
                    ownerId,
                    now,
                    new Date(now.getTime() + leaseSeconds * 1000L));
            if (claimed == 0) {
                return;
            }
            // claim 後重新讀取最新狀態（attemptCount 與 fencingVersion 已由 claim 原子遞增），避免使用陳舊資料
            CompensationOutboxEvent fresh = outboxRepository.findById(outbox.getId()).orElse(null);
            if (fresh == null) {
                log.error("Outbox 事件 claim 成功但 findById 查無資料，觸發失敗補償: id={}", outbox.getId());
                handleDeliveryFailure(outbox.getId(), outbox.getEventId(),
                        outbox.getAttemptCount() + 1,
                        ownerId, null, new IllegalStateException("Event claimed but fresh entity not found"));
                return;
            }
            Long fencingVersion = fresh.getFencingVersion();
            try {
                CompensationEvent event = objectMapper.readValue(fresh.getPayload(), CompensationEvent.class);
                compensationPublisher.publish(event).get(ackTimeoutSeconds, TimeUnit.SECONDS);
                outboxRepository.markSent(fresh.getId(),
                        ownerId,
                        fencingVersion,
                        CompensationOutboxDeliveryStatus.SENT,
                        CompensationOutboxDeliveryStatus.PROCESSING,
                        new Date());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Outbox publish task interrupted during event delivery: id={}", fresh.getId());
                handleDeliveryFailure(fresh.getId(), fresh.getEventId(), fresh.getAttemptCount(), ownerId, fencingVersion, e);
            } catch (Exception e) {
                handleDeliveryFailure(fresh.getId(), fresh.getEventId(), fresh.getAttemptCount(), ownerId, fencingVersion, e);
            }
        } finally {
            semaphore.release();
        }
    }

    /**
     * 投遞失敗處理：以原子 UPDATE 標記狀態；未達上限 → FAILED 並排下次重試；已達上限 → DEAD。
     * attemptCount 於 claim 時遞增（fresh 為遞增後的值），此處直接以現值判斷。
     */
    private void handleDeliveryFailure(UUID id, UUID eventId, int attempt, String ownerId, Long fencingVersion, Exception e) {
        String rawMessage = (e != null && e.getMessage() != null && !e.getMessage().isBlank())
                ? e.getMessage()
                : (e != null ? e.getClass().getSimpleName() : "Unknown error");
        String errorMessage = truncate(rawMessage);
        if (attempt >= maxAttempts) {
            int affected = outboxRepository.markDead(id,
                    ownerId,
                    fencingVersion,
                    CompensationOutboxDeliveryStatus.DEAD,
                    CompensationOutboxDeliveryStatus.PROCESSING,
                    errorMessage);
            if (affected > 0) {
                log.error("Outbox 事件已達最大重試次數，轉為 DEAD: eventId={}, attempt={}, ownerId={}, fencingVersion={}, cause={}",
                        eventId, attempt, ownerId, fencingVersion, e.toString());
            } else {
                log.warn("Outbox 事件達最大重試次數但租約已被接管，略過 markDead: eventId={}, ownerId={}, fencingVersion={}",
                        eventId, ownerId, fencingVersion);
            }
        } else {
            long backoff = resolveBackoffSeconds(attempt);
            int affected = outboxRepository.markFailed(id,
                    ownerId,
                    fencingVersion,
                    CompensationOutboxDeliveryStatus.FAILED,
                    CompensationOutboxDeliveryStatus.PROCESSING,
                    errorMessage,
                    new Date(System.currentTimeMillis() + backoff * 1000L));
            if (affected > 0) {
                log.warn("Outbox 事件發佈失敗，排定重試: eventId={}, attempt={}, ownerId={}, fencingVersion={}, nextAttemptIn={}s, cause={}",
                        eventId, attempt, ownerId, fencingVersion, backoff, e.toString());
            } else {
                log.warn("Outbox 事件發佈失敗但租約已被接管，略過 markFailed: eventId={}, ownerId={}, fencingVersion={}",
                        eventId, ownerId, fencingVersion);
            }
        }
    }

    private long resolveBackoffSeconds(int attempt) {
        List<Long> backoffs = (backoffSeconds == null || backoffSeconds.isEmpty())
                ? List.of(DEFAULT_BACKOFF_SECONDS[0], DEFAULT_BACKOFF_SECONDS[1], DEFAULT_BACKOFF_SECONDS[2],
                DEFAULT_BACKOFF_SECONDS[3], DEFAULT_BACKOFF_SECONDS[4])
                : backoffSeconds;
        return backoffs.get(Math.clamp(attempt - 1, 0, backoffs.size() - 1));
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1024 ? message.substring(0, 1024) : message;
    }
}
