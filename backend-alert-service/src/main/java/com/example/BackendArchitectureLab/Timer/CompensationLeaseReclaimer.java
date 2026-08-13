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
        if (expiredEvents.isEmpty()) {
            return;
        }
        log.info("Reclaiming {} expired PROCESSING compensation event(s)", expiredEvents.size());
        for (CompensationEventLog expired : expiredEvents) {
            reclaimOne(expired);
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
            // 單一事件失敗不影響整批回收；下一輪會再次掃到並重試
            log.warn("Reclaimed compensation event processing failed, will be retried next cycle: eventId={}",
                    entry.getEventId(), e);
        }
    }
}
