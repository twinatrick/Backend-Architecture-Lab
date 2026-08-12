package com.example.BackendArchitectureLab.Timer;

import com.example.BackendArchitectureLab.Entity.CompensationOutboxEvent;
import com.example.BackendArchitectureLab.Repository.CompensationOutboxEventRepository;
import com.example.BackendArchitectureLab.Service.ICompensationPublisher;
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
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompensationOutboxWorkerTest {

    @Mock
    private CompensationOutboxEventRepository outboxRepository;

    @Mock
    private ICompensationPublisher compensationPublisher;

    @InjectMocks
    private CompensationOutboxWorker compensationOutboxWorker;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(compensationOutboxWorker, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(compensationOutboxWorker, "maxAttempts", 5);
        ReflectionTestUtils.setField(compensationOutboxWorker, "leaseSeconds", 300L);
        ReflectionTestUtils.setField(compensationOutboxWorker, "batchSize", 20);
        ReflectionTestUtils.setField(compensationOutboxWorker, "ackTimeoutSeconds", 10L);
        ReflectionTestUtils.setField(compensationOutboxWorker, "backoffSeconds", List.of(5L, 15L, 30L, 60L, 300L));
    }

    @Test
    void flushPendingEvents_ShouldPublishAndMarkSentAfterAck() throws Exception {
        CompensationOutboxEvent outbox = newOutboxEvent(CompensationStatus.COMMITTED);
        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), any(Date.class), any(Date.class))).thenReturn(1);
        when(compensationPublisher.publish(any(CompensationEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        compensationOutboxWorker.flushPendingEvents();

        ArgumentCaptor<CompensationEvent> eventCaptor = ArgumentCaptor.forClass(CompensationEvent.class);
        verify(compensationPublisher).publish(eventCaptor.capture());
        assertEquals(outbox.getEventId(), eventCaptor.getValue().getEventId());
        assertEquals(CompensationStatus.COMMITTED, eventCaptor.getValue().getStatus());

        ArgumentCaptor<CompensationOutboxEvent> savedCaptor = ArgumentCaptor.forClass(CompensationOutboxEvent.class);
        verify(outboxRepository).save(savedCaptor.capture());
        CompensationOutboxEvent saved = savedCaptor.getValue();
        assertEquals(CompensationOutboxDeliveryStatus.SENT, saved.getDeliveryStatus());
        assertNotNull(saved.getSentAt());
        assertNotNull(saved.getProcessingAt());
        assertEquals(1, saved.getAttemptCount());
    }

    @Test
    void flushPendingEvents_ShouldReclaimExpiredLeaseAndPublish() throws Exception {
        CompensationOutboxEvent outbox = newOutboxEvent(CompensationStatus.COMMITTED);
        outbox.setDeliveryStatus(CompensationOutboxDeliveryStatus.PROCESSING);
        outbox.setProcessingAt(new Date(System.currentTimeMillis() - 600_000L));
        outbox.setLeaseUntil(new Date(System.currentTimeMillis() - 60_000L));
        outbox.setAttemptCount(1);
        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), any(Date.class), any(Date.class))).thenReturn(1);
        when(compensationPublisher.publish(any(CompensationEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        compensationOutboxWorker.flushPendingEvents();

        verify(compensationPublisher).publish(any(CompensationEvent.class));
        ArgumentCaptor<CompensationOutboxEvent> savedCaptor = ArgumentCaptor.forClass(CompensationOutboxEvent.class);
        verify(outboxRepository).save(savedCaptor.capture());
        CompensationOutboxEvent saved = savedCaptor.getValue();
        assertEquals(CompensationOutboxDeliveryStatus.SENT, saved.getDeliveryStatus());
        assertEquals(2, saved.getAttemptCount());
        assertNotNull(saved.getLeaseUntil());
    }

    @Test
    void flushPendingEvents_ShouldSkip_whenClaimReturnsZeroOrLeaseNotExpired() throws Exception {
        CompensationOutboxEvent outbox = newOutboxEvent(CompensationStatus.COMMITTED);
        outbox.setDeliveryStatus(CompensationOutboxDeliveryStatus.PROCESSING);
        outbox.setLeaseUntil(new Date(System.currentTimeMillis() + 60_000L));
        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), any(Date.class), any(Date.class))).thenReturn(0);

        compensationOutboxWorker.flushPendingEvents();

        verifyNoInteractions(compensationPublisher);
        verify(outboxRepository, never()).save(any(CompensationOutboxEvent.class));
    }

    @Test
    void flushPendingEvents_ShouldNotPublish_whenNoPendingEvents() {
        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of());

        compensationOutboxWorker.flushPendingEvents();

        verifyNoInteractions(compensationPublisher);
        verify(outboxRepository, never()).save(any(CompensationOutboxEvent.class));
    }

    @Test
    void flushPendingEvents_ShouldRetryWithBackoff_whenPublishFails() throws Exception {
        CompensationOutboxEvent outbox = newOutboxEvent(CompensationStatus.COMMITTED);
        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), any(Date.class), any(Date.class))).thenReturn(1);
        CompletableFuture<Void> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("broker unreachable"));
        when(compensationPublisher.publish(any(CompensationEvent.class))).thenReturn(failedFuture);

        compensationOutboxWorker.flushPendingEvents();

        ArgumentCaptor<CompensationOutboxEvent> savedCaptor = ArgumentCaptor.forClass(CompensationOutboxEvent.class);
        verify(outboxRepository).save(savedCaptor.capture());
        CompensationOutboxEvent saved = savedCaptor.getValue();
        assertEquals(CompensationOutboxDeliveryStatus.FAILED, saved.getDeliveryStatus());
        assertEquals(1, saved.getAttemptCount());
        assertNotNull(saved.getNextAttemptAt());
        assertNotNull(saved.getErrorMessage());
    }

    @Test
    void flushPendingEvents_ShouldMarkDead_whenMaxAttemptsReached() throws Exception {
        CompensationOutboxEvent outbox = newOutboxEvent(CompensationStatus.COMMITTED);
        outbox.setAttemptCount(4);
        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), any(Date.class), any(Date.class))).thenReturn(1);
        CompletableFuture<Void> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("broker unreachable"));
        when(compensationPublisher.publish(any(CompensationEvent.class))).thenReturn(failedFuture);

        compensationOutboxWorker.flushPendingEvents();

        ArgumentCaptor<CompensationOutboxEvent> savedCaptor = ArgumentCaptor.forClass(CompensationOutboxEvent.class);
        verify(outboxRepository).save(savedCaptor.capture());
        CompensationOutboxEvent saved = savedCaptor.getValue();
        assertEquals(CompensationOutboxDeliveryStatus.DEAD, saved.getDeliveryStatus());
        assertEquals(5, saved.getAttemptCount());
        assertNotNull(saved.getErrorMessage());
    }

    @Test
    void flushPendingEvents_ShouldCountAttemptAndRetain_whenPayloadCorrupted() {
        CompensationOutboxEvent outbox = new CompensationOutboxEvent();
        outbox.setId(UUID.randomUUID());
        outbox.setEventId(UUID.randomUUID());
        outbox.setTransactionId(UUID.randomUUID());
        outbox.setAction(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND.name());
        outbox.setStatus(CompensationStatus.COMMITTED);
        outbox.setPayload("not-valid-json{");
        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), any(Date.class), any(Date.class))).thenReturn(1);

        compensationOutboxWorker.flushPendingEvents();

        verifyNoInteractions(compensationPublisher);
        ArgumentCaptor<CompensationOutboxEvent> savedCaptor = ArgumentCaptor.forClass(CompensationOutboxEvent.class);
        verify(outboxRepository).save(savedCaptor.capture());
        CompensationOutboxEvent saved = savedCaptor.getValue();
        assertEquals(CompensationOutboxDeliveryStatus.FAILED, saved.getDeliveryStatus());
        assertEquals(1, saved.getAttemptCount());
        assertNotNull(saved.getNextAttemptAt());
        assertNotNull(saved.getErrorMessage());
    }

    private CompensationOutboxEvent newOutboxEvent(String status) throws Exception {
        CompensationOutboxEvent outbox = new CompensationOutboxEvent();
        outbox.setId(UUID.randomUUID());
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
