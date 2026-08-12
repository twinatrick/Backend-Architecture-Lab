package com.example.BackendArchitectureLab.Service.Strategy;

import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProjectMemberSkillsRebindCompensationStrategyTest {

    private final ProjectMemberSkillsRebindCompensationStrategy strategy =
            new ProjectMemberSkillsRebindCompensationStrategy();

    @Test
    void supports_shouldReturnTrue_forMatchingAction() {
        assertTrue(strategy.supports(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND));
    }

    @Test
    void supports_shouldReturnFalse_forOtherAction() {
        assertFalse(strategy.supports(null));
    }

    @Test
    void compensate_shouldNotThrow() {
        CompensationEvent event = CompensationEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion(1)
                .transactionId(UUID.randomUUID())
                .serviceName("competency-service")
                .action(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND)
                .status(CompensationStatus.COMPENSATED)
                .beforeState(Map.of("projectId", "p1"))
                .timestamp(Instant.now())
                .build();

        assertDoesNotThrow(() -> strategy.compensate(event));
    }
}
