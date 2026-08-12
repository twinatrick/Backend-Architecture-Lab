package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Entity.CompensationEventLog;
import com.example.BackendArchitectureLab.Exception.UnsupportedEventVersionException;
import com.example.BackendArchitectureLab.Exception.UnsupportedCompensationActionException;
import com.example.BackendArchitectureLab.Repository.CompensationEventLogRepository;
import com.example.BackendArchitectureLab.Service.Strategy.CompensationStrategy;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEventLogStatus;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompensationEventProcessorTest {

    @Mock
    private CompensationEventLogRepository eventLogRepository;

    @Mock
    private CompensationStrategy strategy;

    @InjectMocks
    private CompensationEventProcessor compensationEventProcessor;

    private UUID eventId;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        ReflectionTestUtils.setField(compensationEventProcessor, "compensationStrategies", List.of(strategy));
        ReflectionTestUtils.setField(compensationEventProcessor, "leaseSeconds", 300L);
        ReflectionTestUtils.setField(compensationEventProcessor, "maxAttempts", 5);
        when(eventLogRepository.saveAndFlush(any(CompensationEventLog.class)))
                .thenAnswer(invocation -> copyLog(invocation.getArgument(0)));
        when(strategy.supports(any(CompensationAction.class))).thenReturn(true);
    }

    private CompensationEvent newEvent(String status) {
        CompensationEvent event = new CompensationEvent();
        event.setEventId(eventId);
        event.setEventVersion(1);
        event.setTransactionId(UUID.randomUUID());
        event.setServiceName("competency-service");
        event.setAction(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND);
        event.setStatus(status);
        event.setBeforeState(Map.of("key1", "value1"));
        event.setAfterState(Map.of("key2", "value2"));
        event.setTimestamp(Instant.now());
        return event;
    }

    private CompensationEventLog newLog(String status) {
        CompensationEventLog log = new CompensationEventLog();
        log.setEventId(eventId);
        log.setTransactionId(UUID.randomUUID());
        log.setStatus(status);
        log.setAttemptCount(1);
        return log;
    }

    private CompensationEventLog copyLog(CompensationEventLog source) {
        CompensationEventLog copy = new CompensationEventLog();
        copy.setEventId(source.getEventId());
        copy.setTransactionId(source.getTransactionId());
        copy.setStatus(source.getStatus());
        copy.setReceivedAt(source.getReceivedAt());
        copy.setProcessingAt(source.getProcessingAt());
        copy.setLeaseUntil(source.getLeaseUntil());
        copy.setAttemptCount(source.getAttemptCount());
        return copy;
    }

    private void stubDuplicate() {
        when(eventLogRepository.saveAndFlush(any(CompensationEventLog.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
    }

    @Test
    void process_whenCompensationRequiredAndStrategySupports_shouldCompensateAndMarkProcessed() {
        compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATION_REQUIRED));

        verify(strategy).compensate(any(CompensationEvent.class));
        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).save(captor.capture());
        CompensationEventLog saved = captor.getValue();
        assertEquals(CompensationEventLogStatus.PROCESSED, saved.getStatus());
        assertNotNull(saved.getProcessedAt());
        assertNull(saved.getLastError());
    }

    @Test
    void process_whenUnknownAction_shouldThrowAndNotWriteDB() {
        when(strategy.supports(any(CompensationAction.class))).thenReturn(false);

        UnsupportedCompensationActionException exception = assertThrows(UnsupportedCompensationActionException.class,
                () -> compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATION_REQUIRED)));
        assertTrue(exception.getMessage().contains("Unsupported compensation action"));

        verify(strategy, never()).compensate(any(CompensationEvent.class));
        verify(eventLogRepository, never()).saveAndFlush(any(CompensationEventLog.class));
        verify(eventLogRepository, never()).save(any(CompensationEventLog.class));
    }

    @Test
    void process_whenCompensated_shouldMarkProcessedWithoutCompensating() {
        compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATED));

        verify(strategy, never()).compensate(any(CompensationEvent.class));
        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).save(captor.capture());
        assertEquals(CompensationEventLogStatus.PROCESSED, captor.getValue().getStatus());
    }

    @Test
    void process_whenStatusCommitted_shouldMarkProcessedWithoutCompensating() {
        compensationEventProcessor.process(newEvent(CompensationStatus.COMMITTED));

        verify(strategy, never()).compensate(any(CompensationEvent.class));
        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).save(captor.capture());
        assertEquals(CompensationEventLogStatus.PROCESSED, captor.getValue().getStatus());
    }

    @Test
    void process_whenCompensationFails_shouldMarkFailedAndRethrow() {
        doThrow(new RuntimeException("rollback failed")).when(strategy).compensate(any(CompensationEvent.class));

        assertThrows(RuntimeException.class,
                () -> compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATION_REQUIRED)));

        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).save(captor.capture());
        CompensationEventLog saved = captor.getValue();
        assertEquals(CompensationEventLogStatus.FAILED, saved.getStatus());
        assertNotNull(saved.getLastError());
    }

    @Test
    void process_recordsEventLogWithProcessingStatusAndLease() {
        CompensationEvent event = newEvent(CompensationStatus.COMMITTED);

        compensationEventProcessor.process(event);

        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).saveAndFlush(captor.capture());
        CompensationEventLog claimed = captor.getValue();
        assertEquals(eventId, claimed.getEventId());
        assertEquals(event.getTransactionId(), claimed.getTransactionId());
        assertEquals(CompensationEventLogStatus.PROCESSING, claimed.getStatus());
        assertNotNull(claimed.getReceivedAt());
        assertNotNull(claimed.getProcessingAt());
        assertNotNull(claimed.getLeaseUntil());
        assertTrue(claimed.getLeaseUntil().after(claimed.getProcessingAt()));
        assertEquals(1, claimed.getAttemptCount());
    }

    @Test
    void process_whenDuplicateAlreadyProcessed_shouldSkip() {
        stubDuplicate();
        when(eventLogRepository.findByEventId(eventId)).thenReturn(Optional.of(newLog(CompensationEventLogStatus.PROCESSED)));

        compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATION_REQUIRED));

        verify(strategy, never()).compensate(any(CompensationEvent.class));
        verify(eventLogRepository, never()).save(any(CompensationEventLog.class));
        verify(eventLogRepository, never()).retryClaim(any(), anyString(), anyString(), any(Date.class), any(Date.class));
        verify(eventLogRepository, never()).reclaimLease(any(), anyString(), any(Date.class), any(Date.class), any(Date.class));
    }

    @Test
    void process_whenDuplicateFailedAndRetryClaimSucceeds_shouldProcess() {
        stubDuplicate();
        CompensationEventLog failedLog = newLog(CompensationEventLogStatus.FAILED);
        CompensationEventLog retriedLog = newLog(CompensationEventLogStatus.PROCESSING);
        when(eventLogRepository.findByEventId(eventId))
                .thenReturn(Optional.of(failedLog))
                .thenReturn(Optional.of(retriedLog));
        when(eventLogRepository.retryClaim(eq(eventId), eq(CompensationEventLogStatus.PROCESSING),
                eq(CompensationEventLogStatus.FAILED), any(Date.class), any(Date.class))).thenReturn(1);

        compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATION_REQUIRED));

        verify(strategy).compensate(any(CompensationEvent.class));
        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).save(captor.capture());
        assertEquals(CompensationEventLogStatus.PROCESSED, captor.getValue().getStatus());
    }

    @Test
    void process_whenDuplicateFailedAndRetryClaimFails_shouldSkip() {
        stubDuplicate();
        when(eventLogRepository.findByEventId(eventId)).thenReturn(Optional.of(newLog(CompensationEventLogStatus.FAILED)));
        when(eventLogRepository.retryClaim(eq(eventId), eq(CompensationEventLogStatus.PROCESSING),
                eq(CompensationEventLogStatus.FAILED), any(Date.class), any(Date.class))).thenReturn(0);

        compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATION_REQUIRED));

        verify(strategy, never()).compensate(any(CompensationEvent.class));
        verify(eventLogRepository, never()).save(any(CompensationEventLog.class));
    }

    @Test
    void process_whenDuplicateProcessingAndLeaseNotExpired_shouldSkip() {
        stubDuplicate();
        CompensationEventLog inFlight = newLog(CompensationEventLogStatus.PROCESSING);
        inFlight.setProcessingAt(new Date());
        inFlight.setLeaseUntil(new Date(System.currentTimeMillis() + 60000L));
        when(eventLogRepository.findByEventId(eventId)).thenReturn(Optional.of(inFlight));

        compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATION_REQUIRED));

        verify(strategy, never()).compensate(any(CompensationEvent.class));
        verify(eventLogRepository, never()).reclaimLease(any(), anyString(), any(Date.class), any(Date.class), any(Date.class));
        verify(eventLogRepository, never()).save(any(CompensationEventLog.class));
    }

    @Test
    void process_whenDuplicateProcessingAndLeaseExpired_shouldReclaimAndProcess() {
        stubDuplicate();
        CompensationEventLog expired = newLog(CompensationEventLogStatus.PROCESSING);
        expired.setProcessingAt(new Date(System.currentTimeMillis() - 600000L));
        expired.setLeaseUntil(new Date(System.currentTimeMillis() - 60000L));
        CompensationEventLog reclaimed = newLog(CompensationEventLogStatus.PROCESSING);
        reclaimed.setAttemptCount(2);
        when(eventLogRepository.findByEventId(eventId))
                .thenReturn(Optional.of(expired))
                .thenReturn(Optional.of(reclaimed));
        when(eventLogRepository.reclaimLease(eq(eventId), eq(CompensationEventLogStatus.PROCESSING),
                any(Date.class), any(Date.class), any(Date.class))).thenReturn(1);

        compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATION_REQUIRED));

        verify(eventLogRepository).reclaimLease(eq(eventId), eq(CompensationEventLogStatus.PROCESSING),
                any(Date.class), any(Date.class), any(Date.class));
        verify(strategy).compensate(any(CompensationEvent.class));
        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).save(captor.capture());
        assertEquals(CompensationEventLogStatus.PROCESSED, captor.getValue().getStatus());
    }

    @Test
    void process_whenEventIdIsNull_shouldThrow() {
        CompensationEvent event = newEvent(CompensationStatus.COMPENSATION_REQUIRED);
        event.setEventId(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> compensationEventProcessor.process(event));
        assertTrue(exception.getMessage().contains("eventId must not be null"));

        verify(eventLogRepository, never()).saveAndFlush(any(CompensationEventLog.class));
        verify(eventLogRepository, never()).findByEventId(any(UUID.class));
        verify(strategy, never()).compensate(any(CompensationEvent.class));
    }

    @Test
    void process_whenUnsupportedEventVersion_shouldThrowAndNotWriteDB() {
        CompensationEvent event = newEvent(CompensationStatus.COMPENSATION_REQUIRED);
        event.setEventVersion(2);

        UnsupportedEventVersionException exception = assertThrows(UnsupportedEventVersionException.class,
                () -> compensationEventProcessor.process(event));

        assertTrue(exception.getMessage().contains("Unsupported event version: 2"));
        verify(strategy, never()).compensate(any(CompensationEvent.class));
        verify(eventLogRepository, never()).saveAndFlush(any(CompensationEventLog.class));
        verify(eventLogRepository, never()).save(any(CompensationEventLog.class));
    }

    @Test
    void process_shouldExecuteCompensationOnlyOnce_forDuplicateDelivery() {
        CompensationEvent event = newEvent(CompensationStatus.COMPENSATION_REQUIRED);

        compensationEventProcessor.process(event);
        stubDuplicate();
        when(eventLogRepository.findByEventId(eventId))
                .thenReturn(Optional.of(newLog(CompensationEventLogStatus.PROCESSED)));
        compensationEventProcessor.process(event);

        verify(strategy, times(1)).compensate(any(CompensationEvent.class));
    }

    @Test
    void process_whenCompensationFailsRepeatedly_shouldMarkDeadAndNotRethrow() {
        doThrow(new RuntimeException("compensate error")).when(strategy).compensate(any(CompensationEvent.class));

        CompensationEventLog entry = newLog(CompensationEventLogStatus.PROCESSING);
        entry.setAttemptCount(5);
        when(eventLogRepository.saveAndFlush(any(CompensationEventLog.class))).thenReturn(entry);

        assertDoesNotThrow(() -> compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATION_REQUIRED)));

        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).save(captor.capture());
        CompensationEventLog saved = captor.getValue();
        assertEquals(CompensationEventLogStatus.DEAD, saved.getStatus());
        assertEquals("compensate error", saved.getLastError());
        assertNotNull(saved.getFailedAt());
    }
}