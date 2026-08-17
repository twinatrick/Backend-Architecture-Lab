package com.example.BackendArchitectureLab.Timer;

import com.example.BackendArchitectureLab.DataAccess.IExternalSyncCommandDataAccess;
import com.example.BackendArchitectureLab.Entity.ExternalSyncCommand;
import com.example.BackendArchitectureLab.Service.IExternalSyncCommandService;
import com.example.BackendArchitectureLab.Service.IExternalSyncService;
import com.example.BackendArchitectureLab.Vo.CompensationOutboxDeliveryStatus;
import com.example.BackendArchitectureLab.Vo.ExternalSyncCommandPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class ExternalSyncWorkerTest {

    @Mock
    private IExternalSyncCommandDataAccess commandRepository;

    @Mock
    private IExternalSyncService externalSyncService;

    @Mock
    private IExternalSyncCommandService externalSyncCommandService;

    @InjectMocks
    private ExternalSyncWorker externalSyncWorker;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(externalSyncWorker, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(externalSyncWorker, "maxAttempts", 5);
        ReflectionTestUtils.setField(externalSyncWorker, "leaseSeconds", 300L);
        ReflectionTestUtils.setField(externalSyncWorker, "batchSize", 20);
        ReflectionTestUtils.setField(externalSyncWorker, "backoffSeconds", List.of(5L, 15L, 30L, 60L, 300L));
    }

    @Test
    void flushPendingCommands_shouldSkip_whenSyncDisabled() {
        when(externalSyncCommandService.isEnabled()).thenReturn(false);

        externalSyncWorker.flushPendingCommands();

        verifyNoInteractions(commandRepository);
        verifyNoInteractions(externalSyncService);
    }

    @Test
    void flushPendingCommands_shouldSyncAndMarkSent() throws Exception {
        ExternalSyncCommand command = newCommand(1);
        when(externalSyncCommandService.isEnabled()).thenReturn(true);
        when(commandRepository.findPendingDue(anyList(), anyString(), any(Pageable.class)))
                .thenReturn(List.of(command));
        when(commandRepository.claimCommand(any(UUID.class), anyList(), anyString(), any(Date.class), any(Date.class)))
                .thenReturn(1);
        when(commandRepository.findById(command.getId())).thenReturn(Optional.of(command));

        externalSyncWorker.flushPendingCommands();

        verify(externalSyncService).syncProjectMemberSkills(eq(command.getProjectId()), anyMap());
        verify(commandRepository).markSent(eq(command.getId()),
                eq(CompensationOutboxDeliveryStatus.SENT),
                eq(CompensationOutboxDeliveryStatus.PROCESSING),
                any(Date.class));
        verify(commandRepository, never()).save(any(ExternalSyncCommand.class));
    }

    @Test
    void flushPendingCommands_shouldSkip_whenClaimFails() {
        ExternalSyncCommand command = newCommand(1);
        when(externalSyncCommandService.isEnabled()).thenReturn(true);
        when(commandRepository.findPendingDue(anyList(), anyString(), any(Pageable.class)))
                .thenReturn(List.of(command));
        when(commandRepository.claimCommand(any(UUID.class), anyList(), anyString(), any(Date.class), any(Date.class)))
                .thenReturn(0);

        externalSyncWorker.flushPendingCommands();

        verifyNoInteractions(externalSyncService);
        verify(commandRepository, never()).markSent(any(UUID.class), anyString(), anyString(), any(Date.class));
    }

    @Test
    void flushPendingCommands_shouldRetryWithBackoff_whenSyncFails() {
        ExternalSyncCommand command = newCommand(1);
        when(externalSyncCommandService.isEnabled()).thenReturn(true);
        when(commandRepository.findPendingDue(anyList(), anyString(), any(Pageable.class)))
                .thenReturn(List.of(command));
        when(commandRepository.claimCommand(any(UUID.class), anyList(), anyString(), any(Date.class), any(Date.class)))
                .thenReturn(1);
        when(commandRepository.findById(command.getId())).thenReturn(Optional.of(command));
        doThrow(new RuntimeException("external sync unreachable"))
                .when(externalSyncService).syncProjectMemberSkills(any(UUID.class), anyMap());

        externalSyncWorker.flushPendingCommands();

        ArgumentCaptor<Date> nextAttemptCaptor = ArgumentCaptor.forClass(Date.class);
        verify(commandRepository).markFailed(eq(command.getId()),
                eq(CompensationOutboxDeliveryStatus.FAILED),
                eq(CompensationOutboxDeliveryStatus.PROCESSING),
                contains("external sync unreachable"),
                nextAttemptCaptor.capture());
        assertNotNull(nextAttemptCaptor.getValue());
        verify(commandRepository, never()).markDead(any(UUID.class), anyString(), anyString(), anyString());
        verify(externalSyncCommandService, never()).markDeadAndEnqueueCompensation(
                any(UUID.class), any(UUID.class), anyMap(), anyString());
    }

    @Test
    void flushPendingCommands_shouldMarkDeadAndEnqueueCompensation_whenMaxAttemptsReached() {
        ExternalSyncCommand command = newCommand(5);
        when(externalSyncCommandService.isEnabled()).thenReturn(true);
        when(commandRepository.findPendingDue(anyList(), anyString(), any(Pageable.class)))
                .thenReturn(List.of(command));
        when(commandRepository.claimCommand(any(UUID.class), anyList(), anyString(), any(Date.class), any(Date.class)))
                .thenReturn(1);
        when(commandRepository.findById(command.getId())).thenReturn(Optional.of(command));
        doThrow(new RuntimeException("external sync unreachable"))
                .when(externalSyncService).syncProjectMemberSkills(any(UUID.class), anyMap());

        externalSyncWorker.flushPendingCommands();

        verify(externalSyncCommandService).markDeadAndEnqueueCompensation(
                eq(command.getId()),
                eq(command.getTransactionId()),
                anyMap(),
                contains("external sync unreachable"));
        verify(commandRepository, never()).markFailed(any(UUID.class), anyString(), anyString(), anyString(), any(Date.class));
    }

    @Test
    void flushPendingCommands_shouldRetryBackoff_whenPayloadCorrupted() {
        ExternalSyncCommand command = newCommand(1);
        command.setPayload("not-valid-json{");
        when(externalSyncCommandService.isEnabled()).thenReturn(true);
        when(commandRepository.findPendingDue(anyList(), anyString(), any(Pageable.class)))
                .thenReturn(List.of(command));
        when(commandRepository.claimCommand(any(UUID.class), anyList(), anyString(), any(Date.class), any(Date.class)))
                .thenReturn(1);
        when(commandRepository.findById(command.getId())).thenReturn(Optional.of(command));

        externalSyncWorker.flushPendingCommands();

        verifyNoInteractions(externalSyncService);
        verify(commandRepository).markFailed(eq(command.getId()),
                eq(CompensationOutboxDeliveryStatus.FAILED),
                eq(CompensationOutboxDeliveryStatus.PROCESSING),
                contains("JSON"),
                any(Date.class));
    }

    private ExternalSyncCommand newCommand(int attemptCount) {
        ExternalSyncCommand command = new ExternalSyncCommand();
        command.setId(UUID.randomUUID());
        command.setTransactionId(UUID.randomUUID());
        command.setProjectId(UUID.randomUUID());
        command.setDeliveryStatus(CompensationOutboxDeliveryStatus.PENDING);
        command.setAttemptCount(attemptCount);
        ExternalSyncCommandPayload payload = new ExternalSyncCommandPayload();
        payload.setMemberSkillsMap(Map.of());
        payload.setBeforeState(Map.of("projectId", "p1"));
        try {
            command.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return command;
    }
}
