package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Entity.CompensationEventLog;
import com.example.BackendArchitectureLab.Repository.CompensationEventLogRepository;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompensationConsumerTest {

    @Mock
    private CompensationEventLogRepository eventLogRepository;

    @InjectMocks
    private CompensationConsumer compensationConsumer;

    private UUID eventId;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
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

    @Test
    void handleCompensation_whenStatusCompensatedAndActionProjectMemberSkillsRebind_shouldExecuteCompensation() {
        when(eventLogRepository.existsByEventId(eventId)).thenReturn(false);

        compensationConsumer.handleCompensation(newEvent(CompensationStatus.COMPENSATED));

        verify(eventLogRepository).save(any(CompensationEventLog.class));
    }

    @Test
    void handleCompensation_whenStatusCompensatedAndUnknownAction_shouldLogWarning() {
        when(eventLogRepository.existsByEventId(eventId)).thenReturn(false);
        CompensationEvent event = newEvent(CompensationStatus.COMPENSATED);
        event.setAction(null);

        compensationConsumer.handleCompensation(event);
    }

    @Test
    void handleCompensation_whenStatusCommitted_shouldNotExecuteCompensation() {
        when(eventLogRepository.existsByEventId(eventId)).thenReturn(false);

        compensationConsumer.handleCompensation(newEvent(CompensationStatus.COMMITTED));

        verify(eventLogRepository).save(any(CompensationEventLog.class));
    }

    @Test
    void handleCompensation_whenStatusTransactionStarted_shouldNotExecuteCompensation() {
        when(eventLogRepository.existsByEventId(eventId)).thenReturn(false);

        compensationConsumer.handleCompensation(newEvent(CompensationStatus.TRANSACTION_STARTED));

        verify(eventLogRepository).save(any(CompensationEventLog.class));
    }

    @Test
    void handleCompensation_whenStatusFailed_shouldNotExecuteCompensation() {
        when(eventLogRepository.existsByEventId(eventId)).thenReturn(false);

        compensationConsumer.handleCompensation(newEvent(CompensationStatus.FAILED));
    }

    @Test
    void handleCompensation_whenStatusIsNull_shouldNotExecuteCompensation() {
        when(eventLogRepository.existsByEventId(eventId)).thenReturn(false);

        compensationConsumer.handleCompensation(newEvent(null));
    }

    @Test
    void handleCompensation_whenActionIsNullAndStatusCompensated_shouldLogWarningNotThrow() {
        when(eventLogRepository.existsByEventId(eventId)).thenReturn(false);
        CompensationEvent event = newEvent(CompensationStatus.COMPENSATED);
        event.setAction(null);

        assertDoesNotThrow(() -> compensationConsumer.handleCompensation(event));
    }

    @Test
    void handleCompensation_withAllFieldsSet_shouldProcessCorrectly() {
        when(eventLogRepository.existsByEventId(eventId)).thenReturn(false);
        CompensationEvent event = newEvent(CompensationStatus.COMPENSATED);

        compensationConsumer.handleCompensation(event);
    }

    @Test
    void handleCompensation_whenDuplicateEventId_shouldSkipProcessing() {
        when(eventLogRepository.existsByEventId(eventId)).thenReturn(true);

        compensationConsumer.handleCompensation(newEvent(CompensationStatus.COMPENSATED));

        verify(eventLogRepository, never()).save(any(CompensationEventLog.class));
    }

    @Test
    void handleCompensation_recordsEventLogWithEventMetadata() {
        when(eventLogRepository.existsByEventId(eventId)).thenReturn(false);
        CompensationEvent event = newEvent(CompensationStatus.COMMITTED);

        compensationConsumer.handleCompensation(event);

        ArgumentCaptor<CompensationEventLog> captor = ArgumentCaptor.forClass(CompensationEventLog.class);
        verify(eventLogRepository).save(captor.capture());
        CompensationEventLog saved = captor.getValue();
        assertEquals(eventId, saved.getEventId());
        assertEquals(event.getTransactionId(), saved.getTransactionId());
        assertEquals(CompensationStatus.COMMITTED, saved.getStatus());
        assertNotNull(saved.getReceivedAt());
    }

    @Test
    void handleCompensation_whenEventIdIsNull_shouldIgnoreLegacyEvent() {
        CompensationEvent event = newEvent(CompensationStatus.COMPENSATED);
        event.setEventId(null);

        compensationConsumer.handleCompensation(event);

        verify(eventLogRepository, never()).existsByEventId(any(UUID.class));
        verify(eventLogRepository, never()).save(any(CompensationEventLog.class));
    }
}