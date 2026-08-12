package com.example.BackendArchitectureLab.Timer;

import com.example.BackendArchitectureLab.Entity.CompensationOutboxEvent;
import com.example.BackendArchitectureLab.Repository.CompensationOutboxEventRepository;
import com.example.BackendArchitectureLab.Vo.CompensationOutboxDeliveryStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outbox 監控：定期掃描重試耗盡後滯留（DEAD）的事件並告警，供人工查核。
 */
@Slf4j
@Component
public class CompensationOutboxMonitor {

    @Autowired
    private CompensationOutboxEventRepository outboxEventRepository;

    @Scheduled(fixedDelayString = "${compensation.monitor.interval-ms:60000}")
    public void monitorDeadEvents() {
        List<CompensationOutboxEvent> deadEvents =
                outboxEventRepository.findByDeliveryStatus(CompensationOutboxDeliveryStatus.DEAD);
        if (deadEvents.isEmpty()) {
            return;
        }
        for (CompensationOutboxEvent outboxEvent : deadEvents) {
            log.error("補償 Outbox 事件重試耗盡且滯留（DEAD）: eventId={}, transactionId={}, action={}, "
                            + "attemptCount={}, lastError={}",
                    outboxEvent.getEventId(), outboxEvent.getTransactionId(), outboxEvent.getAction(),
                    outboxEvent.getAttemptCount(), outboxEvent.getErrorMessage());
        }
    }
}
