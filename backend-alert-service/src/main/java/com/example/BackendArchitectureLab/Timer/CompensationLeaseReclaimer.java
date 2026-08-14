package com.example.BackendArchitectureLab.Timer;

import com.example.BackendArchitectureLab.Entity.CompensationEventLog;
import com.example.BackendArchitectureLab.Repository.CompensationEventLogRepository;
import com.example.BackendArchitectureLab.Service.CompensationEventProcessor;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEventLogStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * CompensationLeaseReclaimer - 過期補償租約回收排程。
 * <p>
 * 消費端若在認領後、標記完成前 crash，且該事件不再被 Kafka 重新投遞，其
 * {@code compensation_event_log} 紀錄將永久停留在 PROCESSING。本排程定期掃描
 * 租約已過期的 PROCESSING 紀錄，以 CAS（reclaimLease，同時產生新 ownerId 並使
 * fencingVersion 遞增）重新認領後，從 stored payload 反序列化事件並重新執行補償。
 * <p>
 * 此外，若 reclaimer 重新執行時遭遇 transient failure，事件會被標記為 FAILED 並寫入
 * nextAttemptAt（線性退避）。本排程同時掃描已達 nextAttemptAt 的 FAILED 紀錄，
 * 以 CAS（retryClaim）重新領取後重跑補償，避免 Kafka 已無 redelivery 的事件永久滯留。
 * 兩階段皆至多處理 50 筆、單一事件失敗不中斷整批。
 */
@Slf4j
@Component
public class CompensationLeaseReclaimer {

    @Autowired
    private CompensationEventLogRepository eventLogRepository;

    @Autowired
    private CompensationEventProcessor compensationEventProcessor;

    @Value("${compensation.consumer.lease-seconds:300}")
    private long leaseSeconds;

    @Scheduled(fixedDelayString = "${compensation.consumer.reclaim-delay-ms:60000}")
    public void reclaimExpiredLeases() {
        List<CompensationEventLog> expiredEvents =
                eventLogRepository.findTop50ByStatusAndLeaseUntilBeforeOrderByLeaseUntilAsc(
                        CompensationEventLogStatus.PROCESSING, new Date());
        if (!expiredEvents.isEmpty()) {
            log.info("Reclaiming {} expired PROCESSING compensation event(s)", expiredEvents.size());
            for (CompensationEventLog expired : expiredEvents) {
                reclaimOne(expired);
            }
        }
        reclaimEligibleFailedEvents();
    }

    private void reclaimEligibleFailedEvents() {
        List<CompensationEventLog> retryableEvents =
                eventLogRepository.findTop50ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
                        CompensationEventLogStatus.FAILED, new Date());
        if (retryableEvents.isEmpty()) {
            return;
        }
        log.info("Reclaiming {} retry-eligible FAILED compensation event(s)", retryableEvents.size());
        for (CompensationEventLog failed : retryableEvents) {
            retryOne(failed);
        }
    }

    private void retryOne(CompensationEventLog failed) {
        Date now = new Date();
        int claimed = eventLogRepository.retryClaim(
                failed.getEventId(),
                CompensationEventLogStatus.PROCESSING,
                CompensationEventLogStatus.FAILED,
                UUID.randomUUID().toString(),
                now,
                new Date(now.getTime() + leaseSeconds * 1000L));
        if (claimed != 1) {
            log.debug("Retry-eligible event claimed by another instance, skipped: eventId={}", failed.getEventId());
            return;
        }
        CompensationEventLog entry = eventLogRepository.findByEventId(failed.getEventId()).orElse(null);
        if (entry == null) {
            log.warn("Failed event log disappeared during retry, skipped: eventId={}", failed.getEventId());
            return;
        }
        try {
            compensationEventProcessor.processReclaimed(entry);
        } catch (Exception e) {
            // 單一事件失敗不影響整批回收；重試失敗仍會以 FAILED + nextAttemptAt 排入下一輪
            log.warn("Retried compensation event processing failed, will be retried next cycle: eventId={}",
                    entry.getEventId(), e);
        }
    }

    private void reclaimOne(CompensationEventLog expired) {
        Date now = new Date();
        String newOwnerId = UUID.randomUUID().toString();
        int reclaimed = eventLogRepository.reclaimLease(
                expired.getEventId(),
                CompensationEventLogStatus.PROCESSING,
                now,
                newOwnerId,
                now,
                new Date(now.getTime() + leaseSeconds * 1000L));
        if (reclaimed != 1) {
            log.debug("Expired-lease event reclaimed by another instance, skipped: eventId={}", expired.getEventId());
            return;
        }
        CompensationEventLog entry = eventLogRepository.findByEventId(expired.getEventId()).orElse(null);
        if (entry == null) {
            log.warn("Expired-lease event log disappeared during reclaim, skipped: eventId={}", expired.getEventId());
            return;
        }
        try {
            compensationEventProcessor.processReclaimed(entry);
        } catch (Exception e) {
            // 單一事件失敗不影響整批回收；失敗後事件會被標記 FAILED + nextAttemptAt，
            // 由本排程的 FAILED 階段於下一輪重新領取重試
            log.warn("Reclaimed compensation event processing failed, will be retried next cycle: eventId={}",
                    entry.getEventId(), e);
        }
    }
}
