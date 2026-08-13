package com.example.BackendArchitectureLab.Timer;

import com.example.BackendArchitectureLab.Entity.CompensationOutboxEvent;
import com.example.BackendArchitectureLab.Repository.CompensationOutboxEventRepository;
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

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private CompensationOutboxEventRepository outboxRepository;

    @Autowired
    private ICompensationPublisher compensationPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 批次發佈尚未送達的事件（預設每 5 秒執行一次）。
     */
    @Scheduled(fixedDelayString = "${compensation.outbox.flush-delay-ms:5000}")
    public void flushPendingEvents() {
        List<CompensationOutboxEvent> pending = outboxRepository.findPendingDue(
                List.of(CompensationOutboxDeliveryStatus.PENDING,
                        CompensationOutboxDeliveryStatus.FAILED,
                        CompensationOutboxDeliveryStatus.PROCESSING),
                CompensationOutboxDeliveryStatus.PROCESSING,
                PageRequest.of(0, batchSize));
        for (CompensationOutboxEvent outbox : pending) {
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
                continue;
            }
            // claim 後重新讀取最新狀態（attemptCount 已由 claim 原子遞增），避免使用陳舊資料
            CompensationOutboxEvent fresh = outboxRepository.findById(outbox.getId()).orElse(null);
            if (fresh == null) {
                continue;
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
