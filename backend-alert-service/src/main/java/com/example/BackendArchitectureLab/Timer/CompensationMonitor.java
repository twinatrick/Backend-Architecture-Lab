package com.example.BackendArchitectureLab.Timer;

import com.example.BackendArchitectureLab.Entity.CompensationEventLog;
import com.example.BackendArchitectureLab.Repository.CompensationEventLogRepository;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEventLogStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 補償消費端監控：定期掃描處理失敗後滯留（FAILED）的事件並告警，
 * 供人工介入（Kafka 重試耗盡後事件不會自動回收）。
 */
@Slf4j
@Component
public class CompensationMonitor {

    @Autowired
    private CompensationEventLogRepository eventLogRepository;

    @Scheduled(fixedDelayString = "${compensation.monitor.interval-ms:60000}")
    public void monitorFailedEvents() {
        List<CompensationEventLog> failedEvents =
                eventLogRepository.findByStatus(CompensationEventLogStatus.FAILED);
        if (failedEvents.isEmpty()) {
            return;
        }
        for (CompensationEventLog eventLog : failedEvents) {
            log.error("補償事件處理失敗且滯留（FAILED）: eventId={}, transactionId={}, attemptCount={}, lastError={}",
                    eventLog.getEventId(), eventLog.getTransactionId(),
                    eventLog.getAttemptCount(), eventLog.getLastError());
        }
    }
}
