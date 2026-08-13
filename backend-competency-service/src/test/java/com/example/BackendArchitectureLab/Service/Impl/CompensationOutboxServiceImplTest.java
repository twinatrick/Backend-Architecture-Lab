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
    void enqueueFailureAndCompensationRequired_ShouldSaveBothEventsInSameTransaction() throws Exception {
        UUID transactionId = UUID.randomUUID();
        Map<String, Object> state = Map.of("projectId", "p1", "memberCount", 1);

        compensationOutboxService.enqueueFailureAndCompensationRequired(
                transactionId, CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, state, "boom");

        ArgumentCaptor<CompensationOutboxEvent> captor = ArgumentCaptor.forClass(CompensationOutboxEvent.class);
        verify(outboxRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        var saved = captor.getAllValues();
        assertEquals(2, saved.size());
        assertEquals(CompensationStatus.FAILED, saved.get(0).getStatus());
        assertEquals(CompensationStatus.COMPENSATION_REQUIRED, saved.get(1).getStatus());
        assertEquals(CompensationOutboxDeliveryStatus.PENDING, saved.get(0).getDeliveryStatus());
        assertEquals(CompensationOutboxDeliveryStatus.PENDING, saved.get(1).getDeliveryStatus());

        CompensationEvent failedPayload = objectMapper.readValue(saved.get(0).getPayload(), CompensationEvent.class);
        assertEquals(CompensationStatus.FAILED, failedPayload.getStatus());
        assertEquals("boom", failedPayload.getErrorMessage());
        assertEquals(state, failedPayload.getBeforeState());

        CompensationEvent compensationPayload =
                objectMapper.readValue(saved.get(1).getPayload(), CompensationEvent.class);
        assertEquals(CompensationStatus.COMPENSATION_REQUIRED, compensationPayload.getStatus());
        assertEquals("boom", compensationPayload.getErrorMessage());
        assertEquals(state, compensationPayload.getBeforeState());
    }
}
