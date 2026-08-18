package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.ICompensationEventLogDataAccess;
import com.example.BackendArchitectureLab.Service.ICompensationStateService;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEventLogStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

/**
 * CompensationStateService - {@link ICompensationStateService} 實作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationStateService implements ICompensationStateService {

    /** 錯誤訊息保留上限，避免單一事件塞爆 DB 欄位 */
    private static final int MAX_ERROR_LENGTH = 1024;

    private final ICompensationEventLogDataAccess eventLogRepository;

    @Value("${compensation.consumer.retry-backoff-ms:60000}")
    private long retryBackoffMs;

    @Override
    public void markProcessed(UUID eventId, String ownerId, Long fencingVersion) {
        int updated = eventLogRepository.markState(
                eventId, ownerId, fencingVersion,
                CompensationEventLogStatus.PROCESSING,
                CompensationEventLogStatus.PROCESSED,
                new Date(), null, null, null);
        if (updated != 1) {
            log.warn("markState skipped (stale token or status changed), processed result not committed: eventId={}",
                    eventId);
        }
    }

    @Override
    public void markFailed(UUID eventId, String ownerId, Long fencingVersion, int attemptCount, String errorMessage) {
        Date now = new Date();
        int updated = eventLogRepository.markState(
                eventId, ownerId, fencingVersion,
                CompensationEventLogStatus.PROCESSING,
                CompensationEventLogStatus.FAILED,
                null, now, truncate(errorMessage),
                new Date(now.getTime() + retryBackoffMs * attemptCount));
        if (updated != 1) {
            log.warn("markState skipped (stale token or status changed), failed result not committed: eventId={}",
                    eventId);
        }
    }

    @Override
    public void markDead(UUID eventId, String ownerId, Long fencingVersion, String errorMessage) {
        Date now = new Date();
        int updated = eventLogRepository.markState(
                eventId, ownerId, fencingVersion,
                CompensationEventLogStatus.PROCESSING,
                CompensationEventLogStatus.DEAD,
                null, now, truncate(errorMessage), null);
        if (updated != 1) {
            log.warn("markState skipped (stale token or status changed), dead result not committed: eventId={}",
                    eventId);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
