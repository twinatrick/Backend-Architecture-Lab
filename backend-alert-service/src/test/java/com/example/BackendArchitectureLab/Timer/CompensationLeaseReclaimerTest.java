package com.example.BackendArchitectureLab.Timer;

import com.example.BackendArchitectureLab.Entity.CompensationEventLog;
import com.example.BackendArchitectureLab.Repository.CompensationEventLogRepository;
import com.example.BackendArchitectureLab.Service.CompensationEventProcessor;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEventLogStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompensationLeaseReclaimerTest {

    @Mock
    private CompensationEventLogRepository eventLogRepository;

    @Mock
    private CompensationEventProcessor compensationEventProcessor;

    @InjectMocks
    private CompensationLeaseReclaimer compensationLeaseReclaimer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(compensationLeaseReclaimer, "leaseSeconds", 300L);
    }

    private CompensationEventLog newLog(String status) {
        CompensationEventLog log = new CompensationEventLog();
        log.setEventId(UUID.randomUUID());
        log.setStatus(status);
        log.setAttemptCount(1);
        log.setLeaseUntil(new Date(System.currentTimeMillis() - 60_000L));
        return log;
    }

    @Test
    void reclaimExpiredLeases_shouldProcessEachExpiredEvent() {
        CompensationEventLog expired1 = newLog(CompensationEventLogStatus.PROCESSING);
        CompensationEventLog expired2 = newLog(CompensationEventLogStatus.PROCESSING);

        when(eventLogRepository.findTop50ByStatusAndLeaseUntilBeforeOrderByLeaseUntilAsc(
                eq(CompensationEventLogStatus.PROCESSING), any(Date.class)))
                .thenReturn(List.of(expired1, expired2));
        when(eventLogRepository.reclaimLease(
                eq(expired1.getEventId()), eq(CompensationEventLogStatus.PROCESSING),
                any(Date.class), anyString(), any(Date.class), any(Date.class)))
                .thenReturn(1);
        when(eventLogRepository.reclaimLease(
                eq(expired2.getEventId()), eq(CompensationEventLogStatus.PROCESSING),
                any(Date.class), anyString(), any(Date.class), any(Date.class)))
                .thenReturn(1);
        when(eventLogRepository.findByEventId(expired1.getEventId())).thenReturn(Optional.of(expired1));
        when(eventLogRepository.findByEventId(expired2.getEventId())).thenReturn(Optional.of(expired2));

        compensationLeaseReclaimer.reclaimExpiredLeases();

        verify(compensationEventProcessor).processReclaimed(expired1);
        verify(compensationEventProcessor).processReclaimed(expired2);
    }

    @Test
    void reclaimExpiredLeases_shouldSkip_whenNoExpiredEvent() {
        when(eventLogRepository.findTop50ByStatusAndLeaseUntilBeforeOrderByLeaseUntilAsc(
                eq(CompensationEventLogStatus.PROCESSING), any(Date.class)))
                .thenReturn(List.of());

        compensationLeaseReclaimer.reclaimExpiredLeases();

        verify(compensationEventProcessor, never()).processReclaimed(any(CompensationEventLog.class));
    }

    @Test
    void reclaimExpiredLeases_shouldSkip_whenReclaimLostRace() {
        CompensationEventLog expired = newLog(CompensationEventLogStatus.PROCESSING);

        when(eventLogRepository.findTop50ByStatusAndLeaseUntilBeforeOrderByLeaseUntilAsc(
                eq(CompensationEventLogStatus.PROCESSING), any(Date.class)))
                .thenReturn(List.of(expired));
        when(eventLogRepository.reclaimLease(
                eq(expired.getEventId()), eq(CompensationEventLogStatus.PROCESSING),
                any(Date.class), anyString(), any(Date.class), any(Date.class)))
                .thenReturn(0);

        compensationLeaseReclaimer.reclaimExpiredLeases();

        verify(compensationEventProcessor, never()).processReclaimed(any(CompensationEventLog.class));
    }

    @Test
    void reclaimExpiredLeases_shouldContinueWhenProcessingFails() {
        CompensationEventLog expired = newLog(CompensationEventLogStatus.PROCESSING);
        CompensationEventLog entry = newLog(CompensationEventLogStatus.PROCESSING);

        when(eventLogRepository.findTop50ByStatusAndLeaseUntilBeforeOrderByLeaseUntilAsc(
                eq(CompensationEventLogStatus.PROCESSING), any(Date.class)))
                .thenReturn(List.of(expired));
        when(eventLogRepository.reclaimLease(
                eq(expired.getEventId()), eq(CompensationEventLogStatus.PROCESSING),
                any(Date.class), anyString(), any(Date.class), any(Date.class)))
                .thenReturn(1);
        when(eventLogRepository.findByEventId(expired.getEventId())).thenReturn(Optional.of(entry));
        doThrow(new IllegalStateException("deserialize failed"))
                .when(compensationEventProcessor).processReclaimed(entry);

        compensationLeaseReclaimer.reclaimExpiredLeases();

        // 單一事件失敗不應向外拋出，整批回收得以繼續
    }
}
