package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.ICompensationEventLogDataAccess;
import com.example.BackendArchitectureLab.Entity.CompensationEventLog;
import com.example.BackendArchitectureLab.Service.ICompensationStateService;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEventLogStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * CompensationStateService - {@link ICompensationStateService} 實作。
 */
@Slf4j
@Service
public class CompensationStateService implements ICompensationStateService {

    /** 錯誤訊息保留上限，避免單一事件塞爆 DB 欄位 */
    private static final int MAX_ERROR_LENGTH = 1024;

    @Autowired
    private ICompensationEventLogDataAccess eventLogRepository;

    @Value("${compensation.consumer.retry-backoff-ms:60000}")
    private long retryBackoffMs;

    @Override
    public void markProcessed(CompensationEventLog entry) {
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

    @Override
    public void markFailed(CompensationEventLog entry, String errorMessage) {
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

    @Override
    public void markDead(CompensationEventLog entry, String errorMessage) {
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
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
