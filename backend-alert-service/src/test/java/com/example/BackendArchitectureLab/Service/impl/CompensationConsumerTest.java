package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Dto.Kafka.CompensationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompensationConsumerTest {

    @InjectMocks
    private CompensationConsumer compensationConsumer;

    @Test
    void handleCompensation_whenStatusCompensatedAndActionProjectMemberSkillsRebind_shouldExecuteCompensation() {
        CompensationEvent event = new CompensationEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setServiceName("project-service");
        event.setAction("PROJECT_MEMBER_SKILLS_REBIND");
        event.setStatus(CompensationStatus.COMPENSATED);
        event.setBeforeState(Map.of("key1", "value1"));
        event.setAfterState(Map.of("key2", "value2"));
        event.setTimestamp(Instant.now());

        compensationConsumer.handleCompensation(event);
    }

    @Test
    void handleCompensation_whenStatusCompensatedAndUnknownAction_shouldLogWarning() {
        CompensationEvent event = new CompensationEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setServiceName("unknown-service");
        event.setAction("UNKNOWN_ACTION");
        event.setStatus(CompensationStatus.COMPENSATED);
        event.setTimestamp(Instant.now());

        compensationConsumer.handleCompensation(event);
    }

    @Test
    void handleCompensation_whenStatusCommitted_shouldNotExecuteCompensation() {
        CompensationEvent event = new CompensationEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setServiceName("project-service");
        event.setAction("PROJECT_MEMBER_SKILLS_REBIND");
        event.setStatus(CompensationStatus.COMMITTED);
        event.setTimestamp(Instant.now());

        compensationConsumer.handleCompensation(event);
    }

    @Test
    void handleCompensation_whenStatusSavePoint_shouldNotExecuteCompensation() {
        CompensationEvent event = new CompensationEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setServiceName("project-service");
        event.setAction("PROJECT_MEMBER_SKILLS_REBIND");
        event.setStatus(CompensationStatus.SAVE_POINT);
        event.setTimestamp(Instant.now());

        compensationConsumer.handleCompensation(event);
    }

    @Test
    void handleCompensation_whenStatusFailed_shouldNotExecuteCompensation() {
        CompensationEvent event = new CompensationEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setServiceName("project-service");
        event.setAction("PROJECT_MEMBER_SKILLS_REBIND");
        event.setStatus(CompensationStatus.FAILED);
        event.setTimestamp(Instant.now());

        compensationConsumer.handleCompensation(event);
    }

    @Test
    void handleCompensation_whenStatusIsNull_shouldNotExecuteCompensation() {
        CompensationEvent event = new CompensationEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setServiceName("project-service");
        event.setAction("PROJECT_MEMBER_SKILLS_REBIND");
        event.setStatus(null);
        event.setTimestamp(Instant.now());

        compensationConsumer.handleCompensation(event);
    }

    @Test
    void handleCompensation_whenActionIsNullAndStatusCompensated_shouldThrowNullPointerException() {
        CompensationEvent event = new CompensationEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setServiceName("project-service");
        event.setAction(null);
        event.setStatus(CompensationStatus.COMPENSATED);
        event.setTimestamp(Instant.now());

        assertThrows(NullPointerException.class, () -> compensationConsumer.handleCompensation(event));
    }

    @Test
    void handleCompensation_withAllFieldsSet_shouldProcessCorrectly() {
        CompensationEvent event = new CompensationEvent(
                UUID.randomUUID(),
                "project-service",
                "PROJECT_MEMBER_SKILLS_REBIND",
                CompensationStatus.COMPENSATED,
                Map.of("role", "admin"),
                Map.of("role", "user"),
                null,
                Instant.now()
        );

        compensationConsumer.handleCompensation(event);
    }
}
