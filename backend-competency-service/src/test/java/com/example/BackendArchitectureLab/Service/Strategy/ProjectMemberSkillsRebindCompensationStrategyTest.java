package com.example.BackendArchitectureLab.Service.Strategy;

import com.example.BackendArchitectureLab.Service.ICompensationRestoreService;
import com.example.BackendArchitectureLab.Vo.BindingSnapshot;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectMemberSkillsRebindCompensationStrategyTest {

    @Mock
    private ICompensationRestoreService compensationRestoreService;

    @InjectMocks
    private ProjectMemberSkillsRebindCompensationStrategy strategy;

    @Test
    void supports_shouldReturnTrue_forMatchingAction() {
        assertTrue(strategy.supports(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND));
    }

    @Test
    void supports_shouldReturnFalse_forOtherAction() {
        assertFalse(strategy.supports(null));
    }

    @Test
    void compensate_shouldNotThrow_andShouldCallRestoreService() {
        UUID projectId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String ownerId = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        Map<String, String> rawBinding = Map.of(
                "userId", userId.toString(), "skillId", skillId.toString(), "levelId", levelId.toString());
        List<Map<String, String>> rawBindings = List.of(rawBinding);
        List<BindingSnapshot> expectedBindings = List.of(new BindingSnapshot(userId, skillId, levelId));
        CompensationEvent event = CompensationEvent.builder()
                .eventId(eventId)
                .eventVersion(1)
                .transactionId(UUID.randomUUID())
                .serviceName("competency-service")
                .action(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND)
                .status(CompensationStatus.COMPENSATED)
                .beforeState(Map.of("projectId", projectId.toString(), "expectedVersion", 123456L, "bindings", rawBindings))
                .timestamp(Instant.now())
                .build();

        assertDoesNotThrow(() -> strategy.compensate(event, ownerId, 1L));
        verify(compensationRestoreService).restoreMemberSkills(
                eq(projectId), eq(eventId), eq(123456L), eq(ownerId), eq(1L), eq(expectedBindings));
    }

    @Test
    void compensate_shouldThrowIllegalArgument_whenBeforeStateNull() {
        UUID eventId = UUID.randomUUID();
        CompensationEvent event = CompensationEvent.builder()
                .eventId(eventId)
                .eventVersion(1)
                .transactionId(UUID.randomUUID())
                .serviceName("competency-service")
                .action(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND)
                .status(CompensationStatus.COMPENSATED)
                .beforeState(null)
                .timestamp(Instant.now())
                .build();

        assertThrows(IllegalArgumentException.class, () -> strategy.compensate(event, "ownerId", 1L));
        verify(compensationRestoreService, never()).restoreMemberSkills(any(), any(), any(), any(), any(), any());
    }

    @Test
    void compensate_shouldThrowIllegalArgument_whenProjectIdMissing() {
        UUID eventId = UUID.randomUUID();
        CompensationEvent event = CompensationEvent.builder()
                .eventId(eventId)
                .eventVersion(1)
                .transactionId(UUID.randomUUID())
                .serviceName("competency-service")
                .action(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND)
                .status(CompensationStatus.COMPENSATED)
                .beforeState(Map.of("expectedVersion", 123456L))
                .timestamp(Instant.now())
                .build();

        assertThrows(IllegalArgumentException.class, () -> strategy.compensate(event, "ownerId", 1L));
        verify(compensationRestoreService, never()).restoreMemberSkills(any(), any(), any(), any(), any(), any());
    }

    @Test
    void compensate_shouldThrowIllegalArgument_whenExpectedVersionMissing() {
        UUID projectId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        CompensationEvent event = CompensationEvent.builder()
                .eventId(eventId)
                .eventVersion(1)
                .transactionId(UUID.randomUUID())
                .serviceName("competency-service")
                .action(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND)
                .status(CompensationStatus.COMPENSATED)
                .beforeState(Map.of("projectId", projectId.toString()))
                .timestamp(Instant.now())
                .build();

        assertThrows(IllegalArgumentException.class, () -> strategy.compensate(event, "ownerId", 1L));
        verify(compensationRestoreService, never()).restoreMemberSkills(any(), any(), any(), any(), any(), any());
    }
}
