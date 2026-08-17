package com.example.BackendArchitectureLab.Timer;

import com.example.BackendArchitectureLab.Entity.CompensationOutboxEvent;
import com.example.BackendArchitectureLab.DataAccess.ICompensationOutboxEventDataAccess;
import com.example.BackendArchitectureLab.Vo.CompensationOutboxDeliveryStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * Outbox 監控：定期掃描重試耗盡後滯留（DEAD）的事件並告警，供人工查核。
 * 僅掃描最近更新的前 20 筆 DEAD 事件，並以 lastAlertedAt 實施一小時頻率控制，
 * 避免大表全表掃描與同一事件每輪重複告警。
 */
@Slf4j
@Component
public class CompensationOutboxMonitor {

    @Autowired
    private ICompensationOutboxEventDataAccess outboxEventRepository;

    @Scheduled(fixedDelayString = "${compensation.monitor.interval-ms:60000}")
    @Transactional
    public void monitorDeadEvents() {
        List<CompensationOutboxEvent> deadEvents =
                outboxEventRepository.findTop20ByDeliveryStatusOrderByUpdatedTimeDesc(
                        CompensationOutboxDeliveryStatus.DEAD);
        Date oneHourAgo = new Date(System.currentTimeMillis() - 3600_000L);
        for (CompensationOutboxEvent outboxEvent : deadEvents) {
            if (outboxEvent.getLastAlertedAt() == null || outboxEvent.getLastAlertedAt().before(oneHourAgo)) {
                log.error("補償 Outbox 事件重試耗盡且滯留（DEAD）: eventId={}, transactionId={}, action={}, "
                                + "attemptCount={}, lastError={}",
                        outboxEvent.getEventId(), outboxEvent.getTransactionId(), outboxEvent.getAction(),
                        outboxEvent.getAttemptCount(), outboxEvent.getErrorMessage());
                outboxEvent.setLastAlertedAt(new Date());
                outboxEventRepository.save(outboxEvent);
            }
        }
    }
}
