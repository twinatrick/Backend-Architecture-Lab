package com.example.BackendArchitectureLab.Service.Strategy;

import com.example.BackendArchitectureLab.Feign.CompetencyServiceFeignClient;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectMemberSkillsRebindCompensationStrategyTest {

    @Mock
    private CompetencyServiceFeignClient competencyServiceFeignClient;

    @InjectMocks
    private ProjectMemberSkillsRebindCompensationStrategy strategy;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(strategy, "competencyServiceFeignClient", competencyServiceFeignClient);
    }

    @Test
    void supports_shouldReturnTrue_forMatchingAction() {
        assertTrue(strategy.supports(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND));
    }

    @Test
    void supports_shouldReturnFalse_forOtherAction() {
        assertFalse(strategy.supports(null));
    }

    @Test
    void compensate_shouldNotThrow_andShouldCallFeign() {
        UUID projectId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        List<Map<String, String>> bindings = List.of(Map.of("userId", UUID.randomUUID().toString()));
        CompensationEvent event = CompensationEvent.builder()
                .eventId(eventId)
                .eventVersion(1)
                .transactionId(UUID.randomUUID())
                .serviceName("competency-service")
                .action(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND)
                .status(CompensationStatus.COMPENSATED)
                .beforeState(Map.of("projectId", projectId.toString(), "expectedLastUpdatedTime", 123456L, "bindings", bindings))
                .timestamp(Instant.now())
                .build();

        assertDoesNotThrow(() -> strategy.compensate(event));
        verify(competencyServiceFeignClient).restoreProjectMemberSkills(eq(projectId), eq(eventId.toString()), eq(123456L), eq(bindings));
    }
}