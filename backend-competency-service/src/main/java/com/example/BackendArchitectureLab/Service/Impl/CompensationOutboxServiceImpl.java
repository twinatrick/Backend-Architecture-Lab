package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Entity.CompensationOutboxEvent;
import com.example.BackendArchitectureLab.Repository.CompensationOutboxEventRepository;
import com.example.BackendArchitectureLab.Service.ICompensationOutboxService;
import com.example.BackendArchitectureLab.Service.ICompensationPublisher;
import com.example.BackendArchitectureLab.Vo.CompensationOutboxDeliveryStatus;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * CompensationOutboxServiceImpl - 交易補償事件的 Outbox 實作，
 * 事件先寫入自家資料庫（與業務交易同 commit），排程批次經 CompensationPublisher 發送至 Kafka，
 * 僅在收到 Kafka ACK 後才標記為 SENT；失敗時指數退避重試，超過最大次數轉為 DEAD。
 */
@Slf4j
@Service
public class CompensationOutboxServiceImpl implements ICompensationOutboxService {

    private static final int EVENT_VERSION = 1;
    private static final long ACK_TIMEOUT_SECONDS = 10L;
    private static final long[] BACKOFF_SECONDS = {5L, 15L, 30L, 60L, 300L};
    private static final int BATCH_SIZE = 20;

    @Value("${compensation.outbox.max-attempts:5}")
    private int maxAttempts;

    @Value("${compensation.outbox.lease-seconds:300}")
    private long leaseSeconds;

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
    public void enqueueCommitted(UUID transactionId, CompensationAction action, Map<String, Object> state) {
        saveOutbox(transactionId, action, CompensationStatus.COMMITTED, state, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueFailed(UUID transactionId, CompensationAction action, Map<String, Object> state, String errorMessage) {
        saveOutbox(transactionId, action, CompensationStatus.FAILED, state, errorMessage);
    }

    /**
     * 批次發佈尚未送達的事件（預設每 5 秒執行一次）。
     * 先原子領取（PENDING/FAILED 到期或 PROCESSING 租約過期 → PROCESSING），再等待 Kafka ACK，
     * ACK 成功才標記 SENT；失敗依指數退避安排下次重試，超過最大次數轉為 DEAD。
     */
    @Scheduled(fixedDelayString = "${compensation.outbox.flush-delay-ms:5000}")
    @Override
    public void flushPendingEvents() {
        List<CompensationOutboxEvent> pending = outboxRepository.findPendingDue(
                List.of(CompensationOutboxDeliveryStatus.PENDING,
                        CompensationOutboxDeliveryStatus.FAILED,
                        CompensationOutboxDeliveryStatus.PROCESSING),
                CompensationOutboxDeliveryStatus.PROCESSING,
                PageRequest.of(0, BATCH_SIZE));
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
            outbox.setDeliveryStatus(CompensationOutboxDeliveryStatus.PROCESSING);
            outbox.setProcessingAt(now);
            outbox.setAttemptCount(outbox.getAttemptCount() + 1);
            try {
                CompensationEvent event = objectMapper.readValue(outbox.getPayload(), CompensationEvent.class);
                compensationPublisher.publish(event).get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                outbox.setDeliveryStatus(CompensationOutboxDeliveryStatus.SENT);
                outbox.setSentAt(new Date());
                outboxRepository.save(outbox);
            } catch (Exception e) {
                handleDeliveryFailure(outbox, e);
            }
        }
    }

    /**
     * 投遞失敗處理：記錄錯誤；未達上限 → 回到 FAILED 並排下次重試；已達上限 → DEAD。
     * attemptCount 於 claim 時遞增，此處直接以現值判斷。
     */
    private void handleDeliveryFailure(CompensationOutboxEvent outbox, Exception e) {
        int attempt = outbox.getAttemptCount();
        outbox.setErrorMessage(truncate(e.getMessage()));
        if (attempt >= maxAttempts) {
            outbox.setDeliveryStatus(CompensationOutboxDeliveryStatus.DEAD);
            log.error("Outbox 事件已達最大重試次數，轉為 DEAD: eventId={}, attempt={}, cause={}",
                    outbox.getEventId(), attempt, e.toString());
        } else {
            outbox.setDeliveryStatus(CompensationOutboxDeliveryStatus.FAILED);
            long backoffSeconds = BACKOFF_SECONDS[Math.min(attempt - 1, BACKOFF_SECONDS.length - 1)];
            outbox.setNextAttemptAt(new Date(System.currentTimeMillis() + backoffSeconds * 1000L));
            log.warn("Outbox 事件發佈失敗，排定重試: eventId={}, attempt={}, nextAttemptIn={}s, cause={}",
                    outbox.getEventId(), attempt, backoffSeconds, e.toString());
        }
        outboxRepository.save(outbox);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1024 ? message.substring(0, 1024) : message;
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