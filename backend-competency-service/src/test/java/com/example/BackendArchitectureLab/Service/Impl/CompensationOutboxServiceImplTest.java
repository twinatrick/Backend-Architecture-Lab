package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Entity.CompensationOutboxEvent;
import com.example.BackendArchitectureLab.Repository.CompensationOutboxEventRepository;
import com.example.BackendArchitectureLab.Service.ICompensationPublisher;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompensationOutboxServiceImplTest {

    @Mock
    private CompensationOutboxEventRepository outboxRepository;

    @Mock
    private ICompensationPublisher compensationPublisher;

    @InjectMocks
    private CompensationOutboxServiceImpl compensationOutboxService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(compensationOutboxService, "objectMapper", objectMapper);
    }

    @Test
    void enqueueTransactionStarted_ShouldSaveOutboxEventWithJsonPayload() throws Exception {
        UUID transactionId = UUID.randomUUID();
        Map<String, Object> state = Map.of("projectId", "p1", "memberCount", 1);

        compensationOutboxService.enqueueTransactionStarted(
                transactionId, CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, state);

        ArgumentCaptor<CompensationOutboxEvent> captor = ArgumentCaptor.forClass(CompensationOutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        CompensationOutboxEvent saved = captor.getValue();
        assertEquals(transactionId, saved.getTransactionId());
        assertEquals(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND.name(), saved.getAction());
        assertEquals(CompensationStatus.TRANSACTION_STARTED, saved.getStatus());
        assertFalse(saved.isSent());
        assertEquals(0, saved.getAttemptCount());

        CompensationEvent payload = objectMapper.readValue(saved.getPayload(), CompensationEvent.class);
        assertEquals(saved.getEventId(), payload.getEventId());
        assertEquals(transactionId, payload.getTransactionId());
        assertEquals(CompensationStatus.TRANSACTION_STARTED, payload.getStatus());
        assertEquals(1, payload.getEventVersion());
        assertEquals(state, payload.getBeforeState());
        assertNull(payload.getErrorMessage());
    }

    @Test
    void enqueueCommitted_ShouldSaveOutboxEventWithCommittedStatus() throws Exception {
        UUID transactionId = UUID.randomUUID();

        compensationOutboxService.enqueueCommitted(
                transactionId, CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, Map.of());

        ArgumentCaptor<CompensationOutboxEvent> captor = ArgumentCaptor.forClass(CompensationOutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertEquals(CompensationStatus.COMMITTED, captor.getValue().getStatus());
    }

    @Test
    void enqueueFailed_ShouldSaveOutboxEventWithErrorMessage() throws Exception {
        UUID transactionId = UUID.randomUUID();

        compensationOutboxService.enqueueFailed(
                transactionId, CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, Map.of(), "boom");

        ArgumentCaptor<CompensationOutboxEvent> captor = ArgumentCaptor.forClass(CompensationOutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertEquals(CompensationStatus.FAILED, captor.getValue().getStatus());

        CompensationEvent payload = objectMapper.readValue(captor.getValue().getPayload(), CompensationEvent.class);
        assertEquals("boom", payload.getErrorMessage());
    }

    @Test
    void flushPendingEvents_ShouldPublishAndMarkSent() throws Exception {
        CompensationOutboxEvent outbox = newOutboxEvent(CompensationStatus.COMMITTED);
        when(outboxRepository.findTop20BySentFalseOrderByCreatedTimeAsc()).thenReturn(List.of(outbox));

        compensationOutboxService.flushPendingEvents();

        ArgumentCaptor<CompensationEvent> eventCaptor = ArgumentCaptor.forClass(CompensationEvent.class);
        verify(compensationPublisher).publish(eventCaptor.capture());
        assertEquals(outbox.getEventId(), eventCaptor.getValue().getEventId());
        assertEquals(CompensationStatus.COMMITTED, eventCaptor.getValue().getStatus());

        ArgumentCaptor<CompensationOutboxEvent> savedCaptor = ArgumentCaptor.forClass(CompensationOutboxEvent.class);
        verify(outboxRepository).save(savedCaptor.capture());
        assertTrue(savedCaptor.getValue().isSent());
        assertNotNull(savedCaptor.getValue().getSentAt());
    }

    @Test
    void flushPendingEvents_ShouldNotPublish_whenNoPendingEvents() {
        when(outboxRepository.findTop20BySentFalseOrderByCreatedTimeAsc()).thenReturn(List.of());

        compensationOutboxService.flushPendingEvents();

        verifyNoInteractions(compensationPublisher);
        verify(outboxRepository, never()).save(any(CompensationOutboxEvent.class));
    }

    @Test
    void flushPendingEvents_ShouldCountAttemptAndRetain_whenPayloadCorrupted() {
        CompensationOutboxEvent outbox = new CompensationOutboxEvent();
        outbox.setEventId(UUID.randomUUID());
        outbox.setTransactionId(UUID.randomUUID());
        outbox.setAction(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND.name());
        outbox.setStatus(CompensationStatus.COMMITTED);
        outbox.setPayload("not-valid-json{");
        when(outboxRepository.findTop20BySentFalseOrderByCreatedTimeAsc()).thenReturn(List.of(outbox));

        compensationOutboxService.flushPendingEvents();

        verifyNoInteractions(compensationPublisher);
        ArgumentCaptor<CompensationOutboxEvent> savedCaptor = ArgumentCaptor.forClass(CompensationOutboxEvent.class);
        verify(outboxRepository).save(savedCaptor.capture());
        CompensationOutboxEvent saved = savedCaptor.getValue();
        assertFalse(saved.isSent());
        assertEquals(1, saved.getAttemptCount());
        assertNotNull(saved.getErrorMessage());
    }

    private CompensationOutboxEvent newOutboxEvent(String status) throws Exception {
        CompensationOutboxEvent outbox = new CompensationOutboxEvent();
        UUID eventId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        outbox.setEventId(eventId);
        outbox.setTransactionId(transactionId);
        outbox.setAction(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND.name());
        outbox.setStatus(status);
        CompensationEvent event = CompensationEvent.builder()
                .eventId(eventId)
                .eventVersion(1)
                .transactionId(transactionId)
                .serviceName("competency-service")
                .action(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND)
                .status(status)
                .beforeState(Map.of("projectId", "p1"))
                .timestamp(java.time.Instant.now())
                .build();
        outbox.setPayload(objectMapper.writeValueAsString(event));
        return outbox;
    }
}