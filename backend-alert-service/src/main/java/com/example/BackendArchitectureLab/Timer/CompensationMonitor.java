package com.example.BackendArchitectureLab.Timer;

import com.example.BackendArchitectureLab.Entity.CompensationEventLog;
import com.example.BackendArchitectureLab.Repository.CompensationEventLogRepository;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEventLogStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 補償消費端監控：定期掃描近期處理失敗（FAILED）的事件並告警，
 * 供人工介入（Kafka 重試耗盡後事件不會自動回收）。
 * 僅掃描 {@code failedAt} 落在監控窗口內的事件（預設 60 分鐘），避免每輪全量掃描歷史 FAILED。
 */
@Slf4j
@Component
public class CompensationMonitor {

    @Autowired
    private CompensationEventLogRepository eventLogRepository;

    @Value("${compensation.monitor.failed-window-minutes:60}")
    private long failedWindowMinutes;

    @Scheduled(fixedDelayString = "${compensation.monitor.interval-ms:60000}")
    public void monitorFailedEvents() {
        // 1. 全量掃描已歸於死信（DEAD）的補償事件，進行 error 告警（無時間窗口）
        List<CompensationEventLog> deadEvents =
                eventLogRepository.findByStatus(CompensationEventLogStatus.DEAD);
        for (CompensationEventLog eventLog : deadEvents) {
            log.error("補償事件處理失敗且已隔離於死信（DEAD）: eventId={}, transactionId={}, attemptCount={}, lastError={}",
                    eventLog.getEventId(), eventLog.getTransactionId(),
                    eventLog.getAttemptCount(), eventLog.getLastError());
        }

        // 2. 僅掃描近期暫時失敗重試中（FAILED）的事件，僅作 info 記錄
        Date threshold = new Date(System.currentTimeMillis() - failedWindowMinutes * 60_000L);
        List<CompensationEventLog> failedEvents =
                eventLogRepository.findTop20ByStatusAndFailedAtAfter(CompensationEventLogStatus.FAILED, threshold);
        for (CompensationEventLog eventLog : failedEvents) {
            log.info("補償事件重試中（FAILED）: eventId={}, transactionId={}, attemptCount={}, lastError={}",
                    eventLog.getEventId(), eventLog.getTransactionId(),
                    eventLog.getAttemptCount(), eventLog.getLastError());
        }
    }
}
