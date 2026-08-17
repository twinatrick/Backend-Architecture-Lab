package com.example.BackendArchitectureLab.Timer;

import com.example.BackendArchitectureLab.Entity.CompensationOutboxEvent;
import com.example.BackendArchitectureLab.DataAccess.ICompensationOutboxEventDataAccess;
import com.example.BackendArchitectureLab.Service.ICompensationPublisher;
import com.example.BackendArchitectureLab.Vo.CompensationOutboxDeliveryStatus;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    @Autowired
    private ICompensationOutboxEventDataAccess outboxRepository;

    @Autowired
    private ICompensationPublisher compensationPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExecutorService compensationOutboxPublisherPool;

    /**
     * 批次發佈尚未送達的事件（預設每 5 秒執行一次）。
     * 批次內以共用執行緒池（並行度 = min(batch, parallelism)，可組態
     * {@code compensation.outbox.publish-parallelism}）平行發佈，每筆各自等待 ACK
     * 至 ackTimeoutSeconds 逾時，避免序列發佈時單一轉發壅塞把整個批次拖到打穿租約。
     * 執行緒池由 {@code CompensationOutboxThreadPoolConfig} 提供（application-scoped），
     * 批次結束不重建/不銷毀（M-03），逾時殘留工作由 daemon 執行緒背景收尾。
     */
    @Scheduled(fixedDelayString = "${compensation.outbox.flush-delay-ms:5000}")
    public void flushPendingEvents() {
        List<CompensationOutboxEvent> pending = outboxRepository.findPendingDue(
                List.of(CompensationOutboxDeliveryStatus.PENDING,
                        CompensationOutboxDeliveryStatus.FAILED,
                        CompensationOutboxDeliveryStatus.PROCESSING),
                CompensationOutboxDeliveryStatus.PROCESSING,
                PageRequest.of(0, batchSize));
        if (pending.isEmpty()) {
            return;
        }
        int parallelism = Math.max(1, Math.min(pending.size(), publishParallelism));
        List<CompletableFuture<Void>> futures = new ArrayList<>(pending.size());
        for (CompensationOutboxEvent outbox : pending) {
            futures.add(CompletableFuture.runAsync(() -> publishOne(outbox), compensationOutboxPublisherPool));
        }
        int waves = (pending.size() + parallelism - 1) / parallelism;
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get((waves + 1) * ackTimeoutSeconds + 1L, TimeUnit.SECONDS);
            log.debug("Published {} outbox event(s) in parallel (parallelism={})", pending.size(), parallelism);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Outbox publish batch interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            // 單一事件逾時/失敗已在 publishOne 內各自處理並標記狀態；
            // 此處僅反映批次整體未能於預期時間內完成（殘留工作由共用池 daemon 執行緒繼續收尾）
            log.warn("Outbox publish batch did not complete within expected window, remaining tasks run in background", e);
        }
    }

    /**
     * 發佈單一事件：原子領取 → 重新讀取最新狀態 → 呼叫 Kafka publisher 等待 ACK → 原子標記 SENT。
     * 任何例外皆由 handleDeliveryFailure 以原子 UPDATE 標記 FAILED/DEAD，不向上拋出。
     */
    private void publishOne(CompensationOutboxEvent outbox) {
        Date now = new Date();
        int claimed = outboxRepository.claimEvent(
                outbox.getId(),
                List.of(CompensationOutboxDeliveryStatus.PENDING,
                        CompensationOutboxDeliveryStatus.FAILED,
                        CompensationOutboxDeliveryStatus.PROCESSING),
                CompensationOutboxDeliveryStatus.PROCESSING,
                now,
                new Date(now.getTime() + leaseSeconds * 1000L));
        if (claimed == 0) {
            return;
        }
        // claim 後重新讀取最新狀態（attemptCount 已由 claim 原子遞增），避免使用陳舊資料
        CompensationOutboxEvent fresh = outboxRepository.findById(outbox.getId()).orElse(null);
        if (fresh == null) {
            return;
        }
        try {
            CompensationEvent event = objectMapper.readValue(fresh.getPayload(), CompensationEvent.class);
            compensationPublisher.publish(event).get(ackTimeoutSeconds, TimeUnit.SECONDS);
            outboxRepository.markSent(fresh.getId(),
                    CompensationOutboxDeliveryStatus.SENT,
                    CompensationOutboxDeliveryStatus.PROCESSING,
                    new Date());
        } catch (Exception e) {
            handleDeliveryFailure(fresh.getId(), fresh.getEventId(), fresh.getAttemptCount(), e);
        }
    }

    /**
     * 投遞失敗處理：以原子 UPDATE 標記狀態；未達上限 → FAILED 並排下次重試；已達上限 → DEAD。
     * attemptCount 於 claim 時遞增（fresh 為遞增後的值），此處直接以現值判斷。
     */
    private void handleDeliveryFailure(UUID id, UUID eventId, int attempt, Exception e) {
        String errorMessage = truncate(e.getMessage());
        if (attempt >= maxAttempts) {
            outboxRepository.markDead(id,
                    CompensationOutboxDeliveryStatus.DEAD,
                    CompensationOutboxDeliveryStatus.PROCESSING,
                    errorMessage);
            log.error("Outbox 事件已達最大重試次數，轉為 DEAD: eventId={}, attempt={}, cause={}",
                    eventId, attempt, e.toString());
        } else {
            long backoff = resolveBackoffSeconds(attempt);
            outboxRepository.markFailed(id,
                    CompensationOutboxDeliveryStatus.FAILED,
                    CompensationOutboxDeliveryStatus.PROCESSING,
                    errorMessage,
                    new Date(System.currentTimeMillis() + backoff * 1000L));
            log.warn("Outbox 事件發佈失敗，排定重試: eventId={}, attempt={}, nextAttemptIn={}s, cause={}",
                    eventId, attempt, backoff, e.toString());
        }
    }

    private long resolveBackoffSeconds(int attempt) {
        List<Long> backoffs = (backoffSeconds == null || backoffSeconds.isEmpty())
                ? List.of(DEFAULT_BACKOFF_SECONDS[0], DEFAULT_BACKOFF_SECONDS[1], DEFAULT_BACKOFF_SECONDS[2],
                DEFAULT_BACKOFF_SECONDS[3], DEFAULT_BACKOFF_SECONDS[4])
                : backoffSeconds;
        return backoffs.get(Math.min(attempt - 1, backoffs.size() - 1));
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1024 ? message.substring(0, 1024) : message;
    }
}
