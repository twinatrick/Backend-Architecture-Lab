package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Entity.CompensationEventLog;
import com.example.BackendArchitectureLab.Exception.CompensationConflictException;
import com.example.BackendArchitectureLab.Exception.UnsupportedEventVersionException;
import com.example.BackendArchitectureLab.Exception.UnsupportedCompensationActionException;
import com.example.BackendArchitectureLab.Repository.CompensationEventLogRepository;
import com.example.BackendArchitectureLab.Service.Strategy.CompensationStrategy;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEventLogStatus;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
        ReflectionTestUtils.setField(compensationEventProcessor, "retryBackoffMs", 60_000L);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        ReflectionTestUtils.setField(compensationEventProcessor, "objectMapper", mapper);
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
        log.setOwnerId("owner-" + UUID.randomUUID());
        log.setFencingVersion(1L);
        return log;
    }

    private String serializeEvent(CompensationEvent event) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper.writeValueAsString(event);
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
        copy.setOwnerId(source.getOwnerId());
        copy.setFencingVersion(source.getFencingVersion());
        copy.setPayload(source.getPayload());
        return copy;
    }

    private void stubDuplicate() {
        when(eventLogRepository.saveAndFlush(any(CompensationEventLog.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
    }

    @Test
    void process_whenCompensationRequiredAndStrategySupports_shouldCompensateAndMarkProcessed() {
        compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATION_REQUIRED));

        verify(strategy).compensate(any(CompensationEvent.class), anyString(), anyLong());
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

        verify(strategy, never()).compensate(any(CompensationEvent.class), anyString(), anyLong());
        verify(eventLogRepository, never()).saveAndFlush(any(CompensationEventLog.class));
        verify(eventLogRepository, never()).save(any(CompensationEventLog.class));
    }

    @Test
    void process_whenCompensated_shouldMarkProcessedWithoutCompensating() {
        compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATED));

        verify(strategy, never()).compensate(any(CompensationEvent.class), anyString(), anyLong());
        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).save(captor.capture());
        assertEquals(CompensationEventLogStatus.PROCESSED, captor.getValue().getStatus());
    }

    @Test
    void process_whenStatusCommitted_shouldMarkProcessedWithoutCompensating() {
        compensationEventProcessor.process(newEvent(CompensationStatus.COMMITTED));

        verify(strategy, never()).compensate(any(CompensationEvent.class), anyString(), anyLong());
        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).save(captor.capture());
        assertEquals(CompensationEventLogStatus.PROCESSED, captor.getValue().getStatus());
    }

    @Test
    void process_whenCompensationFails_shouldMarkFailedAndRethrow() {
        doThrow(new RuntimeException("rollback failed")).when(strategy).compensate(any(CompensationEvent.class), anyString(), anyLong());

        assertThrows(RuntimeException.class,
                () -> compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATION_REQUIRED)));

        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).save(captor.capture());
        CompensationEventLog saved = captor.getValue();
        assertEquals(CompensationEventLogStatus.FAILED, saved.getStatus());
        assertNotNull(saved.getLastError());
    }

    @Test
    void process_whenCompensationFails_shouldSetNextAttemptAtForScheduledRetry() {
        doThrow(new RuntimeException("rollback failed")).when(strategy).compensate(any(CompensationEvent.class), anyString(), anyLong());

        assertThrows(RuntimeException.class,
                () -> compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATION_REQUIRED)));

        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).save(captor.capture());
        CompensationEventLog saved = captor.getValue();
        assertEquals(CompensationEventLogStatus.FAILED, saved.getStatus());
        // attemptCount=1 → nextAttemptAt = now + 1 * 60000ms，應晚於現在且距今不超過退避值
        assertNotNull(saved.getNextAttemptAt());
        assertTrue(saved.getNextAttemptAt().after(new Date()),
                "nextAttemptAt 應設定在未來以供排程重新領取");
        assertTrue(System.currentTimeMillis() + 90_000L >= saved.getNextAttemptAt().getTime(),
                "nextAttemptAt 不應超出退避範圍");
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

        verify(strategy, never()).compensate(any(CompensationEvent.class), anyString(), anyLong());
        verify(eventLogRepository, never()).save(any(CompensationEventLog.class));
        verify(eventLogRepository, never()).retryClaim(any(), anyString(), anyString(), anyString(), any(Date.class), any(Date.class));
        verify(eventLogRepository, never()).reclaimLease(any(), anyString(), any(Date.class), anyString(), any(Date.class), any(Date.class));
    }

    @Test
    void process_whenDuplicateFailedAndRetryClaimSucceeds_shouldProcess() throws Exception {
        stubDuplicate();
        CompensationEventLog failedLog = newLog(CompensationEventLogStatus.FAILED);
        CompensationEventLog retriedLog = newLog(CompensationEventLogStatus.PROCESSING);
        retriedLog.setPayload(serializeEvent(newEvent(CompensationStatus.COMPENSATION_REQUIRED)));
        when(eventLogRepository.findByEventId(eventId))
                .thenReturn(Optional.of(failedLog))
                .thenReturn(Optional.of(retriedLog));
        when(eventLogRepository.retryClaim(eq(eventId), eq(CompensationEventLogStatus.PROCESSING),
                eq(CompensationEventLogStatus.FAILED), anyString(), any(Date.class), any(Date.class))).thenReturn(1);

        compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATION_REQUIRED));

        verify(strategy).compensate(any(CompensationEvent.class), anyString(), anyLong());
        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).save(captor.capture());
        assertEquals(CompensationEventLogStatus.PROCESSED, captor.getValue().getStatus());
    }

    @Test
    void process_whenRetrySucceedsButPayloadCorrupt_shouldMarkDeadAndRethrow() {
        stubDuplicate();
        CompensationEventLog failedLog = newLog(CompensationEventLogStatus.FAILED);
        CompensationEventLog retriedLog = newLog(CompensationEventLogStatus.PROCESSING);
        retriedLog.setPayload("not-a-json");
        when(eventLogRepository.findByEventId(eventId))
                .thenReturn(Optional.of(failedLog))
                .thenReturn(Optional.of(retriedLog));
        when(eventLogRepository.retryClaim(eq(eventId), eq(CompensationEventLogStatus.PROCESSING),
                eq(CompensationEventLogStatus.FAILED), anyString(), any(Date.class), any(Date.class))).thenReturn(1);

        assertThrows(IllegalStateException.class,
                () -> compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATION_REQUIRED)));

        verify(strategy, never()).compensate(any(CompensationEvent.class), anyString(), anyLong());
        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).save(captor.capture());
        assertEquals(CompensationEventLogStatus.DEAD, captor.getValue().getStatus());
        assertTrue(captor.getValue().getLastError().contains("Failed to deserialize"));
    }

    @Test
    void process_whenDuplicateFailedAndRetryClaimFails_shouldSkip() {
        stubDuplicate();
        when(eventLogRepository.findByEventId(eventId)).thenReturn(Optional.of(newLog(CompensationEventLogStatus.FAILED)));
        when(eventLogRepository.retryClaim(eq(eventId), eq(CompensationEventLogStatus.PROCESSING),
                eq(CompensationEventLogStatus.FAILED), anyString(), any(Date.class), any(Date.class))).thenReturn(0);

        compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATION_REQUIRED));

        verify(strategy, never()).compensate(any(CompensationEvent.class), anyString(), anyLong());
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

        verify(strategy, never()).compensate(any(CompensationEvent.class), anyString(), anyLong());
        verify(eventLogRepository, never()).reclaimLease(any(), anyString(), any(Date.class), anyString(), any(Date.class), any(Date.class));
        verify(eventLogRepository, never()).save(any(CompensationEventLog.class));
    }

    @Test
    void process_whenDuplicateProcessingAndLeaseExpired_shouldReclaimAndProcess() throws Exception {
        stubDuplicate();
        CompensationEventLog expired = newLog(CompensationEventLogStatus.PROCESSING);
        expired.setProcessingAt(new Date(System.currentTimeMillis() - 600000L));
        expired.setLeaseUntil(new Date(System.currentTimeMillis() - 60000L));
        CompensationEventLog reclaimed = newLog(CompensationEventLogStatus.PROCESSING);
        reclaimed.setAttemptCount(2);
        reclaimed.setPayload(serializeEvent(newEvent(CompensationStatus.COMPENSATION_REQUIRED)));
        when(eventLogRepository.findByEventId(eventId))
                .thenReturn(Optional.of(expired))
                .thenReturn(Optional.of(reclaimed));
        when(eventLogRepository.reclaimLease(eq(eventId), eq(CompensationEventLogStatus.PROCESSING),
                any(Date.class), anyString(), any(Date.class), any(Date.class))).thenReturn(1);

        compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATION_REQUIRED));

        verify(eventLogRepository).reclaimLease(eq(eventId), eq(CompensationEventLogStatus.PROCESSING),
                any(Date.class), anyString(), any(Date.class), any(Date.class));
        verify(strategy).compensate(any(CompensationEvent.class), anyString(), anyLong());
        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).save(captor.capture());
        assertEquals(CompensationEventLogStatus.PROCESSED, captor.getValue().getStatus());
    }

    @Test
    void process_whenLeaseReclaimedButPayloadCorrupt_shouldMarkDeadAndRethrow() {
        stubDuplicate();
        CompensationEventLog expired = newLog(CompensationEventLogStatus.PROCESSING);
        expired.setProcessingAt(new Date(System.currentTimeMillis() - 600000L));
        expired.setLeaseUntil(new Date(System.currentTimeMillis() - 60000L));
        CompensationEventLog reclaimed = newLog(CompensationEventLogStatus.PROCESSING);
        reclaimed.setAttemptCount(2);
        reclaimed.setPayload("not-a-json");
        when(eventLogRepository.findByEventId(eventId))
                .thenReturn(Optional.of(expired))
                .thenReturn(Optional.of(reclaimed));
        when(eventLogRepository.reclaimLease(eq(eventId), eq(CompensationEventLogStatus.PROCESSING),
                any(Date.class), anyString(), any(Date.class), any(Date.class))).thenReturn(1);

        assertThrows(IllegalStateException.class,
                () -> compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATION_REQUIRED)));

        verify(strategy, never()).compensate(any(CompensationEvent.class), anyString(), anyLong());
        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).save(captor.capture());
        assertEquals(CompensationEventLogStatus.DEAD, captor.getValue().getStatus());
        assertTrue(captor.getValue().getLastError().contains("Failed to deserialize"));
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
        verify(strategy, never()).compensate(any(CompensationEvent.class), anyString(), anyLong());
    }

    @Test
    void process_whenUnsupportedEventVersion_shouldThrowAndNotWriteDB() {
        CompensationEvent event = newEvent(CompensationStatus.COMPENSATION_REQUIRED);
        event.setEventVersion(2);

        UnsupportedEventVersionException exception = assertThrows(UnsupportedEventVersionException.class,
                () -> compensationEventProcessor.process(event));

        assertTrue(exception.getMessage().contains("Unsupported event version: 2"));
        verify(strategy, never()).compensate(any(CompensationEvent.class), anyString(), anyLong());
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

        verify(strategy, times(1)).compensate(any(CompensationEvent.class), anyString(), anyLong());
    }

    @Test
    void process_whenCompensationFailsRepeatedly_shouldMarkDeadAndNotRethrow() {
        doThrow(new RuntimeException("compensate error")).when(strategy).compensate(any(CompensationEvent.class), anyString(), anyLong());

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

    @Test
    void process_whenCompensationConflictNonRetryable_shouldMarkDeadAndRethrow() {
        doThrow(new CompensationConflictException("conflict")).when(strategy)
                .compensate(any(CompensationEvent.class), anyString(), anyLong());

        CompensationEventLog entry = newLog(CompensationEventLogStatus.PROCESSING);
        entry.setAttemptCount(1);
        when(eventLogRepository.saveAndFlush(any(CompensationEventLog.class))).thenReturn(entry);

        CompensationConflictException exception = assertThrows(CompensationConflictException.class,
                () -> compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATION_REQUIRED)));

        assertEquals("conflict", exception.getMessage());
        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).save(captor.capture());
        assertEquals(CompensationEventLogStatus.DEAD, captor.getValue().getStatus());
        assertEquals("conflict", captor.getValue().getLastError());
    }

    @Test
    void process_whenIllegalArgumentNonRetryable_shouldMarkDeadAndRethrow() {
        doThrow(new IllegalArgumentException("invalid beforeState")).when(strategy)
                .compensate(any(CompensationEvent.class), anyString(), anyLong());

        CompensationEventLog entry = newLog(CompensationEventLogStatus.PROCESSING);
        entry.setAttemptCount(1);
        when(eventLogRepository.saveAndFlush(any(CompensationEventLog.class))).thenReturn(entry);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> compensationEventProcessor.process(newEvent(CompensationStatus.COMPENSATION_REQUIRED)));

        assertEquals("invalid beforeState", exception.getMessage());
        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).save(captor.capture());
        assertEquals(CompensationEventLogStatus.DEAD, captor.getValue().getStatus());
    }

    @Test
    void claimEventLog_shouldSetFencingTokenAndPayload() {
        CompensationEvent event = newEvent(CompensationStatus.COMPENSATION_REQUIRED);

        compensationEventProcessor.process(event);

        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).saveAndFlush(captor.capture());
        CompensationEventLog claimed = captor.getValue();
        assertNotNull(claimed.getOwnerId());
        assertEquals(1L, claimed.getFencingVersion());
        assertNotNull(claimed.getPayload());
        assertTrue(claimed.getPayload().contains("\"eventId\""));
    }

    @Test
    void processReclaimed_shouldDeserializePayloadAndProcess() throws Exception {
        CompensationEvent event = newEvent(CompensationStatus.COMPENSATION_REQUIRED);
        CompensationEventLog entry = newLog(CompensationEventLogStatus.PROCESSING);
        entry.setAttemptCount(2);
        entry.setOwnerId("owner-1");
        entry.setFencingVersion(3L);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        entry.setPayload(mapper.writeValueAsString(event));

        compensationEventProcessor.processReclaimed(entry);

        verify(strategy).compensate(any(CompensationEvent.class), eq("owner-1"), eq(3L));
        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).save(captor.capture());
        assertEquals(CompensationEventLogStatus.PROCESSED, captor.getValue().getStatus());
    }
}