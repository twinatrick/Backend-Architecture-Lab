package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Entity.CompensationOutboxEvent;
import com.example.BackendArchitectureLab.Repository.CompensationOutboxEventRepository;
import com.example.BackendArchitectureLab.Vo.CompensationOutboxDeliveryStatus;
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

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompensationOutboxServiceImplTest {

    @Mock
    private CompensationOutboxEventRepository outboxRepository;

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
        assertEquals(CompensationOutboxDeliveryStatus.PENDING, saved.getDeliveryStatus());
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
        assertEquals(CompensationOutboxDeliveryStatus.PENDING, captor.getValue().getDeliveryStatus());
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
    void enqueueCompensationRequired_ShouldSaveOutboxEventWithCompensationRequiredStatus() throws Exception {
        UUID transactionId = UUID.randomUUID();
        Map<String, Object> state = Map.of("projectId", "p1", "memberCount", 1);

        compensationOutboxService.enqueueCompensationRequired(
                transactionId, CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, state, "boom");

        ArgumentCaptor<CompensationOutboxEvent> captor = ArgumentCaptor.forClass(CompensationOutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        CompensationOutboxEvent saved = captor.getValue();
        assertEquals(CompensationStatus.COMPENSATION_REQUIRED, saved.getStatus());
        assertEquals(CompensationOutboxDeliveryStatus.PENDING, saved.getDeliveryStatus());

        CompensationEvent payload = objectMapper.readValue(saved.getPayload(), CompensationEvent.class);
        assertEquals(CompensationStatus.COMPENSATION_REQUIRED, payload.getStatus());
        assertEquals("boom", payload.getErrorMessage());
        assertEquals(state, payload.getBeforeState());
    }
}
