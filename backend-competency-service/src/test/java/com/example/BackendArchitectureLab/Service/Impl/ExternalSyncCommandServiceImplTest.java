package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IExternalSyncCommandDataAccess;
import com.example.BackendArchitectureLab.Entity.ExternalSyncCommand;
import com.example.BackendArchitectureLab.Service.ICompensationOutboxService;
import com.example.BackendArchitectureLab.Vo.CompensationOutboxDeliveryStatus;
import com.example.BackendArchitectureLab.Vo.ExternalSyncCommandPayload;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalSyncCommandServiceImplTest {

    @Mock
    private IExternalSyncCommandDataAccess commandRepository;

    @Mock
    private ICompensationOutboxService compensationOutboxService;

    private ExternalSyncCommandServiceImpl externalSyncCommandService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        externalSyncCommandService = new ExternalSyncCommandServiceImpl(
                commandRepository,
                objectMapper,
                compensationOutboxService
        );
    }

    @Test
    void enqueue_shouldSkip_whenSyncDisabled() {
        ReflectionTestUtils.setField(externalSyncCommandService, "syncEnabled", false);

        externalSyncCommandService.enqueue(
                UUID.randomUUID(), UUID.randomUUID(), Map.of(), Map.of());

        verifyNoInteractions(commandRepository);
    }

    @Test
    void enqueue_shouldSavePendingCommandWithJsonPayload_whenSyncEnabled() throws Exception {
        ReflectionTestUtils.setField(externalSyncCommandService, "syncEnabled", true);
        UUID transactionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Map<UUID, Map<UUID, UUID>> memberSkillsMap = Map.of(
                UUID.randomUUID(), Map.of(UUID.randomUUID(), UUID.randomUUID()));
        Map<String, Object> beforeState = Map.of("projectId", projectId.toString(), "memberCount", 1);

        externalSyncCommandService.enqueue(transactionId, projectId, memberSkillsMap, beforeState);

        ArgumentCaptor<ExternalSyncCommand> captor = ArgumentCaptor.forClass(ExternalSyncCommand.class);
        verify(commandRepository).save(captor.capture());
        ExternalSyncCommand saved = captor.getValue();
        assertEquals(transactionId, saved.getTransactionId());
        assertEquals(projectId, saved.getProjectId());
        assertEquals(CompensationOutboxDeliveryStatus.PENDING, saved.getDeliveryStatus());

        ExternalSyncCommandPayload payload =
                objectMapper.readValue(saved.getPayload(), ExternalSyncCommandPayload.class);
        assertEquals(memberSkillsMap, payload.getMemberSkillsMap());
        assertEquals(beforeState, payload.getBeforeState());
    }

    @Test
    void enqueue_shouldPersistEmptyCollections_whenNullProvided() throws Exception {
        ReflectionTestUtils.setField(externalSyncCommandService, "syncEnabled", true);
        UUID transactionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        externalSyncCommandService.enqueue(transactionId, projectId, null, null);

        ArgumentCaptor<ExternalSyncCommand> captor = ArgumentCaptor.forClass(ExternalSyncCommand.class);
        verify(commandRepository).save(captor.capture());
        ExternalSyncCommandPayload payload =
                objectMapper.readValue(captor.getValue().getPayload(), ExternalSyncCommandPayload.class);
        assertTrue(payload.getMemberSkillsMap().isEmpty());
        assertTrue(payload.getBeforeState().isEmpty());
    }

    @Test
    void enqueue_shouldThrow_whenTransactionIdNull() {
        ReflectionTestUtils.setField(externalSyncCommandService, "syncEnabled", true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> externalSyncCommandService.enqueue(null, UUID.randomUUID(), Map.of(), Map.of()));
        assertEquals("transactionId and projectId must not be null", exception.getMessage());
        verify(commandRepository, never()).save(any(ExternalSyncCommand.class));
    }

    @Test
    void markDeadAndEnqueueCompensation_shouldEnqueue_whenMarkDeadAffectedRowIsOne() {
        UUID commandId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        String ownerId = UUID.randomUUID().toString();
        Long fencingVersion = 1L;
        Map<String, Object> beforeState = Map.of("projectId", "p1");
        String errorMessage = "sync timeout";

        when(commandRepository.markDead(commandId, ownerId, fencingVersion,
                CompensationOutboxDeliveryStatus.DEAD,
                CompensationOutboxDeliveryStatus.PROCESSING, errorMessage)).thenReturn(1);

        boolean result = externalSyncCommandService.markDeadAndEnqueueCompensation(
                commandId, ownerId, fencingVersion, transactionId, beforeState, errorMessage);

        assertTrue(result);
        verify(compensationOutboxService).enqueueFailureAndCompensationRequired(
                eq(transactionId),
                eq(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND),
                eq(beforeState),
                eq(errorMessage));
    }

    @Test
    void markDeadAndEnqueueCompensation_shouldSkipCompensation_whenMarkDeadAffectedRowIsZero() {
        UUID commandId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        String ownerId = UUID.randomUUID().toString();
        Long fencingVersion = 1L;
        Map<String, Object> beforeState = Map.of("projectId", "p1");
        String errorMessage = "sync timeout";

        when(commandRepository.markDead(commandId, ownerId, fencingVersion,
                CompensationOutboxDeliveryStatus.DEAD,
                CompensationOutboxDeliveryStatus.PROCESSING, errorMessage)).thenReturn(0);

        boolean result = externalSyncCommandService.markDeadAndEnqueueCompensation(
                commandId, ownerId, fencingVersion, transactionId, beforeState, errorMessage);

        assertFalse(result);
        verify(compensationOutboxService, never()).enqueueFailureAndCompensationRequired(
                any(UUID.class), any(CompensationAction.class), anyMap(), anyString());
    }
}
