package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.ICompensationRestoreLogDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillLevelDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectSkillDataAccess;
import com.example.BackendArchitectureLab.Entity.CompensationRestoreLog;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.Entity.Skill;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
import com.example.BackendArchitectureLab.Entity.UserProjectSkill;
import com.example.BackendArchitectureLab.Exception.CompensationConflictException;
import com.example.BackendArchitectureLab.Vo.BindingSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompensationRestoreServiceTest {

    @Mock
    private IProjectDataAccess projectDataAccess;
    @Mock
    private IUserProjectSkillDataAccess userProjectSkillDataAccess;
    @Mock
    private ISkillDataAccess skillDataAccess;
    @Mock
    private ISkillLevelDataAccess skillLevelDataAccess;
    @Mock
    private ICompensationRestoreLogDataAccess restoreLogRepository;

    @Mock
    private IUserProjectDataAccess userProjectDataAccess;

    @InjectMocks
    private CompensationRestoreService compensationRestoreService;

    @BeforeEach
    void setUp() {
        // Inject self reference（自?�代?��?REQUIRES_NEW/REQUIRED 交�??��??��?步�??��?經代?��?
        try {
            Field selfField = CompensationRestoreService.class.getDeclaredField("self");
            selfField.setAccessible(true);
            selfField.set(compensationRestoreService, compensationRestoreService);
            // Inject ?�實 ObjectMapper，�? before-state JSON 序�????��??��?比�?使用
            Field mapperField = CompensationRestoreService.class.getDeclaredField("objectMapper");
            mapperField.setAccessible(true);
            mapperField.set(compensationRestoreService, new ObjectMapper());
        } catch (Exception e) {
            throw new RuntimeException("Could not inject self/objectMapper into CompensationRestoreService", e);
        }
    }

    @Test
    void testRestoreMemberSkills() {
        UUID projectId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        String ownerId = "owner-" + UUID.randomUUID();
        long fencingVersion = 3L;

        Project project = new Project();
        project.setId(projectId);
        project.setVersion(1L);
        Skill skill = new Skill();
        skill.setId(skillId);
        SkillLevel level = new SkillLevel();
        level.setId(levelId);
        level.setSkill(skill);

        CompensationRestoreLog claimLog = new CompensationRestoreLog();
        claimLog.setEventId(eventId);
        claimLog.setStatus("PROCESSING");
        claimLog.setOwnerId(ownerId);
        claimLog.setFencingVersion(fencingVersion);

        when(restoreLogRepository.findById(eventId)).thenReturn(Optional.empty());
        when(restoreLogRepository.saveAndFlush(any(CompensationRestoreLog.class))).thenReturn(claimLog);
        when(restoreLogRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(claimLog));
        when(restoreLogRepository.markRestoreState(eq(eventId), eq(ownerId), eq(fencingVersion),
                eq("SUCCESS"), any(Date.class), isNull())).thenReturn(1);
        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(skill));
        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.of(level));
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(true);

        BindingSnapshot binding = new BindingSnapshot(userId, skillId, levelId);

        compensationRestoreService.restoreMemberSkills(projectId, eventId, 1L, ownerId, fencingVersion, List.of(binding));

        verify(userProjectSkillDataAccess).deleteByProjectId(projectId);
        verify(userProjectSkillDataAccess).save(any(UserProjectSkill.class));
        verify(restoreLogRepository).markRestoreState(eq(eventId), eq(ownerId), eq(fencingVersion),
                eq("SUCCESS"), any(Date.class), isNull());
        verify(projectDataAccess).save(project);
    }

    @Test
    void testRestoreMemberSkills_shouldMarkSuccessInSameTransaction() {
        UUID projectId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        String ownerId = "owner-" + UUID.randomUUID();
        long fencingVersion = 3L;

        Project project = new Project();
        project.setId(projectId);
        project.setVersion(1L);
        Skill skill = new Skill();
        skill.setId(skillId);
        SkillLevel level = new SkillLevel();
        level.setId(levelId);
        level.setSkill(skill);

        CompensationRestoreLog claimLog = new CompensationRestoreLog();
        claimLog.setEventId(eventId);
        claimLog.setStatus("PROCESSING");
        claimLog.setOwnerId(ownerId);
        claimLog.setFencingVersion(fencingVersion);

        when(restoreLogRepository.findById(eventId)).thenReturn(Optional.empty());
        when(restoreLogRepository.saveAndFlush(any(CompensationRestoreLog.class))).thenReturn(claimLog);
        when(restoreLogRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(claimLog));
        when(restoreLogRepository.markRestoreState(eq(eventId), eq(ownerId), eq(fencingVersion),
                eq("SUCCESS"), any(Date.class), isNull())).thenReturn(1);
        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(skill));
        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.of(level));
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(true);

        BindingSnapshot binding = new BindingSnapshot(userId, skillId, levelId);

        TransactionSynchronizationManager.initSynchronization();
        try {
            compensationRestoreService.restoreMemberSkills(projectId, eventId, 1L, ownerId, fencingVersion, List.of(binding));
            // Option A：SUCCESS 於同一交易內立即標記，不再延後到 afterCommit。
            // 「SUCCESS 絕不早於還原 commit」的保證改由「同交易原子 commit/rollback」達成。
            verify(restoreLogRepository).markRestoreState(eq(eventId), eq(ownerId), eq(fencingVersion),
                    eq("SUCCESS"), any(Date.class), isNull());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void testRestoreMemberSkills_shouldMarkFailedAfterRollback_whenRestoreFails() {
        UUID projectId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        String ownerId = "owner-" + UUID.randomUUID();
        long fencingVersion = 3L;

        Project project = new Project();
        project.setId(projectId);
        project.setVersion(1L);
        Skill skill = new Skill();
        skill.setId(skillId);
        SkillLevel level = new SkillLevel();
        level.setId(levelId);
        level.setSkill(skill);

        CompensationRestoreLog claimLog = new CompensationRestoreLog();
        claimLog.setEventId(eventId);
        claimLog.setStatus("PROCESSING");
        claimLog.setOwnerId(ownerId);
        claimLog.setFencingVersion(fencingVersion);

        when(restoreLogRepository.findById(eventId)).thenReturn(Optional.empty());
        when(restoreLogRepository.saveAndFlush(any(CompensationRestoreLog.class))).thenReturn(claimLog);
        when(restoreLogRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(claimLog));
        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(skill));
        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.of(level));
        when(restoreLogRepository.markRestoreState(eq(eventId), eq(ownerId), eq(fencingVersion),
                eq("FAILED"), any(Date.class), anyString())).thenReturn(1);
        when(userProjectDataAccess.existsByUserIdAndProjectId(any(), eq(projectId))).thenReturn(true);
        doThrow(new RuntimeException("skill rebind failed"))
                .when(userProjectSkillDataAccess).deleteByProjectId(projectId);

        BindingSnapshot binding = new BindingSnapshot(UUID.randomUUID(), skillId, levelId);

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThrows(RuntimeException.class,
                    () -> compensationRestoreService.restoreMemberSkills(projectId, eventId, 1L, ownerId,
                            fencingVersion, List.of(binding)));
            // commit 尚未失�??��?標�?；�?�?rollback 後�?�?FAILED 標�?
            verify(restoreLogRepository, never()).markRestoreState(eq(eventId), eq(ownerId), eq(fencingVersion),
                    eq("FAILED"), any(Date.class), anyString());

            TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(restoreLogRepository).markRestoreState(eq(eventId), eq(ownerId), eq(fencingVersion),
                    eq("FAILED"), any(Date.class), anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void testRestoreMemberSkills_shouldSkip_whenAlreadyProcessed() {
        UUID projectId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        CompensationRestoreLog successLog = new CompensationRestoreLog();
        successLog.setEventId(eventId);
        successLog.setStatus("SUCCESS");
        when(restoreLogRepository.findById(eventId)).thenReturn(Optional.of(successLog));

        compensationRestoreService.restoreMemberSkills(projectId, eventId, null, "owner-x", 1L, List.of());

        verifyNoInteractions(projectDataAccess);
        verifyNoInteractions(userProjectSkillDataAccess);
    }

    @Test
    void testRestoreMemberSkills_shouldThrow_whenVersionConflict() {
        UUID projectId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        long expectedVersion = 1L;
        String ownerId = "owner-" + UUID.randomUUID();
        long fencingVersion = 3L;

        Project project = new Project();
        project.setId(projectId);
        // DB project ?��??��?觀??version ??2，�?快照??expectedVersion=1 不�???        project.setVersion(2L);

        // ?��? DB 中�???1 筆�?定�??�空 snapshot ?��?不符）�?reconciliation 不可?�接�?SUCCESS�?        // 必�??�入?�本衝�? ??FAILED 路�?
        UUID driftedSkillId = UUID.randomUUID();
        UUID driftedLevelId = UUID.randomUUID();
        Skill driftedSkill = new Skill();
        driftedSkill.setId(driftedSkillId);
        SkillLevel driftedLevel = new SkillLevel();
        driftedLevel.setId(driftedLevelId);
        driftedLevel.setSkill(driftedSkill);
        UserProjectSkill driftedRow = new UserProjectSkill();
        driftedRow.setUserId(UUID.randomUUID());
        driftedRow.setProject(project);
        driftedRow.setSkill(driftedSkill);
        driftedRow.setSkillLevel(driftedLevel);

        CompensationRestoreLog claimLog = new CompensationRestoreLog();
        claimLog.setEventId(eventId);
        claimLog.setStatus("PROCESSING");
        claimLog.setOwnerId(ownerId);
        claimLog.setFencingVersion(fencingVersion);

        when(restoreLogRepository.findById(eventId)).thenReturn(Optional.empty());
        when(restoreLogRepository.saveAndFlush(any(CompensationRestoreLog.class))).thenReturn(claimLog);
        when(restoreLogRepository.markRestoreState(eq(eventId), eq(ownerId), eq(fencingVersion),
                eq("FAILED"), any(Date.class), anyString())).thenReturn(1);
        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(userProjectSkillDataAccess.findByProjectId(projectId)).thenReturn(List.of(driftedRow));

        assertThrows(CompensationConflictException.class,
                () -> compensationRestoreService.restoreMemberSkills(projectId, eventId, expectedVersion,
                        ownerId, fencingVersion, List.of()));

        verify(userProjectSkillDataAccess, never()).deleteByProjectId(projectId);
        verify(restoreLogRepository).markRestoreState(eq(eventId), eq(ownerId), eq(fencingVersion),
                eq("FAILED"), any(Date.class), anyString());
    }

    @Test
    void testClaimRestoreEvent_shouldReturnFalse_whenOnlySuccessIsClaimable() {
        UUID eventId = UUID.randomUUID();
        CompensationRestoreLog successLog = new CompensationRestoreLog();
        successLog.setEventId(eventId);
        successLog.setStatus("SUCCESS");
        when(restoreLogRepository.findById(eventId)).thenReturn(Optional.of(successLog));

        boolean claimed = compensationRestoreService.claimRestoreEvent(eventId, UUID.randomUUID(), "owner-x", 1L, List.of());

        assertFalse(claimed);
        verify(restoreLogRepository, never()).saveAndFlush(any(CompensationRestoreLog.class));
    }

    @Test
    void testClaimRestoreEvent_shouldReturnFalse_whenLeaseNotExpired() {
        UUID eventId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        CompensationRestoreLog processingLog = new CompensationRestoreLog();
        processingLog.setEventId(eventId);
        processingLog.setProjectId(projectId);
        processingLog.setStatus("PROCESSING");
        processingLog.setLeaseUntil(new Date(System.currentTimeMillis() + 60_000L));
        processingLog.setFencingVersion(3L);
        when(restoreLogRepository.findById(eventId)).thenReturn(Optional.of(processingLog));

        boolean claimed = compensationRestoreService.claimRestoreEvent(eventId, projectId, "owner-x", 1L, List.of());

        assertFalse(claimed);
        verify(restoreLogRepository, never()).saveAndFlush(any(CompensationRestoreLog.class));
        verify(restoreLogRepository, never()).takeOverClaim(any(), anyString(), anyString(), any(Date.class),
                any(Date.class), anyString(), anyLong());
    }

    @Test
    void testClaimRestoreEvent_shouldReturnFalse_whenStaleFencingToken() {
        UUID eventId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        CompensationRestoreLog processingLog = new CompensationRestoreLog();
        processingLog.setEventId(eventId);
        processingLog.setProjectId(projectId);
        processingLog.setStatus("PROCESSING");
        processingLog.setLeaseUntil(new Date(System.currentTimeMillis() - 60_000L));
        processingLog.setFencingVersion(5L);
        when(restoreLogRepository.findById(eventId)).thenReturn(Optional.of(processingLog));

        boolean claimed = compensationRestoreService.claimRestoreEvent(eventId, projectId, "owner-x", 1L, List.of());

        assertFalse(claimed);
        verify(restoreLogRepository, never()).takeOverClaim(any(), anyString(), anyString(), any(Date.class),
                any(Date.class), anyString(), anyLong());
    }

    @Test
    void testClaimRestoreEvent_shouldReturnFalse_whenTakeOverRejected() {
        UUID eventId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        CompensationRestoreLog failedLog = new CompensationRestoreLog();
        failedLog.setEventId(eventId);
        failedLog.setProjectId(projectId);
        failedLog.setStatus("FAILED");
        when(restoreLogRepository.findById(eventId)).thenReturn(Optional.of(failedLog));
        when(restoreLogRepository.takeOverClaim(any(), anyString(), anyString(), any(Date.class),
                any(Date.class), anyString(), anyLong())).thenReturn(0);

        boolean claimed = compensationRestoreService.claimRestoreEvent(eventId, projectId, "owner-x", 1L, List.of());

        assertFalse(claimed);
    }

    @Test
    void testClaimRestoreEvent_shouldReturnFalse_whenFailedWithStaleFencingToken() {
        UUID eventId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        CompensationRestoreLog failedLog = new CompensationRestoreLog();
        failedLog.setEventId(eventId);
        failedLog.setProjectId(projectId);
        failedLog.setStatus("FAILED");
        failedLog.setFencingVersion(10L);
        when(restoreLogRepository.findById(eventId)).thenReturn(Optional.of(failedLog));

        boolean claimed = compensationRestoreService.claimRestoreEvent(eventId, projectId, "owner-x", 1L, List.of());

        assertFalse(claimed);
        verify(restoreLogRepository, never()).takeOverClaim(any(), anyString(), anyString(), any(Date.class),
                any(Date.class), anyString(), anyLong());
    }

    @Test
    void testClaimRestoreEvent_shouldReturnFalse_whenDuplicateInsertKey() {
        UUID eventId = UUID.randomUUID();
        when(restoreLogRepository.findById(eventId)).thenReturn(Optional.empty());
        when(restoreLogRepository.saveAndFlush(any(CompensationRestoreLog.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        boolean claimed = compensationRestoreService.claimRestoreEvent(eventId, UUID.randomUUID(), "owner-x", 1L, List.of());

        assertFalse(claimed);
    }

    @Test
    void testClaimRestoreEvent_shouldRethrow_unexpectedDbError() {
        UUID eventId = UUID.randomUUID();
        when(restoreLogRepository.findById(eventId)).thenReturn(Optional.empty());
        when(restoreLogRepository.saveAndFlush(any(CompensationRestoreLog.class)))
                .thenThrow(new RuntimeException("db connection lost"));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> compensationRestoreService.claimRestoreEvent(eventId, UUID.randomUUID(), "owner-x", 1L, List.of()));

        assertEquals("db connection lost", exception.getMessage());
    }

    @Test
    void testRestoreMemberSkills_shouldThrow_whenBindingHasMissingField() {
        UUID projectId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        String ownerId = "owner-" + UUID.randomUUID();
        long fencingVersion = 3L;

        Project project = new Project();
        project.setId(projectId);
        project.setVersion(1L);

        CompensationRestoreLog claimLog = new CompensationRestoreLog();
        claimLog.setEventId(eventId);
        claimLog.setStatus("PROCESSING");
        claimLog.setOwnerId(ownerId);
        claimLog.setFencingVersion(fencingVersion);

        when(restoreLogRepository.findById(eventId)).thenReturn(Optional.empty());
        when(restoreLogRepository.saveAndFlush(any(CompensationRestoreLog.class))).thenReturn(claimLog);
        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));

        BindingSnapshot malformed = new BindingSnapshot(userId, skillId, null);

        assertThrows(IllegalArgumentException.class,
                () -> compensationRestoreService.restoreMemberSkills(projectId, eventId, 1L, ownerId,
                        fencingVersion, List.of(malformed)));

        verify(userProjectSkillDataAccess, never()).deleteByProjectId(projectId);
        verify(restoreLogRepository).markRestoreState(eq(eventId), eq(ownerId), eq(fencingVersion),
                eq("FAILED"), any(Date.class), anyString());
    }

    @Test
    void testRestoreMemberSkills_shouldThrow_whenUserNotProjectMember() {
        UUID projectId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        String ownerId = "owner-" + UUID.randomUUID();
        long fencingVersion = 3L;

        Project project = new Project();
        project.setId(projectId);
        project.setVersion(1L);

        CompensationRestoreLog claimLog = new CompensationRestoreLog();
        claimLog.setEventId(eventId);
        claimLog.setProjectId(projectId);
        claimLog.setStatus("PROCESSING");
        claimLog.setOwnerId(ownerId);
        claimLog.setFencingVersion(fencingVersion);

        when(restoreLogRepository.findById(eventId)).thenReturn(Optional.empty());
        when(restoreLogRepository.saveAndFlush(any(CompensationRestoreLog.class))).thenReturn(claimLog);
        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(false);

        BindingSnapshot binding = new BindingSnapshot(userId, skillId, levelId);

        assertThrows(IllegalArgumentException.class,
                () -> compensationRestoreService.restoreMemberSkills(projectId, eventId, 1L, ownerId,
                        fencingVersion, List.of(binding)));

        verify(userProjectSkillDataAccess, never()).deleteByProjectId(projectId);
        verify(restoreLogRepository).markRestoreState(eq(eventId), eq(ownerId), eq(fencingVersion),
                eq("FAILED"), any(Date.class), anyString());
    }

    @Test
    void testClaimRestoreEvent_shouldThrow_whenSameEventIdDifferentProjectId() {
        UUID eventId = UUID.randomUUID();
        UUID boundProjectId = UUID.randomUUID();
        UUID otherProjectId = UUID.randomUUID();

        CompensationRestoreLog existingLog = new CompensationRestoreLog();
        existingLog.setEventId(eventId);
        existingLog.setProjectId(boundProjectId);
        existingLog.setStatus("FAILED");
        existingLog.setBeforeStateJson("[]");
        when(restoreLogRepository.findById(eventId)).thenReturn(Optional.of(existingLog));

        assertThrows(IllegalArgumentException.class,
                () -> compensationRestoreService.claimRestoreEvent(eventId, otherProjectId, "owner-x", 1L, List.of()));
    }

    @Test
    void testClaimRestoreEvent_shouldThrow_whenSameEventIdDifferentBindings() {
        UUID eventId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        CompensationRestoreLog existingLog = new CompensationRestoreLog();
        existingLog.setEventId(eventId);
        existingLog.setProjectId(projectId);
        existingLog.setStatus("FAILED");
        existingLog.setBeforeStateJson("[]");
        when(restoreLogRepository.findById(eventId)).thenReturn(Optional.of(existingLog));

        BindingSnapshot binding = new BindingSnapshot(userId, skillId, levelId);

        assertThrows(IllegalArgumentException.class,
                () -> compensationRestoreService.claimRestoreEvent(eventId, projectId, "owner-x", 1L, List.of(binding)));
    }

    @Test
    void testRestoreMemberSkills_shouldNotThrowNpe_whenMalformedBindingAndVersionMismatch() {
        UUID projectId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        String ownerId = "owner-" + UUID.randomUUID();
        long fencingVersion = 3L;

        Project project = new Project();
        project.setId(projectId);
        project.setVersion(2L);

        CompensationRestoreLog claimLog = new CompensationRestoreLog();
        claimLog.setEventId(eventId);
        claimLog.setProjectId(projectId);
        claimLog.setStatus("PROCESSING");
        claimLog.setOwnerId(ownerId);
        claimLog.setFencingVersion(fencingVersion);

        when(restoreLogRepository.findById(eventId)).thenReturn(Optional.empty());
        when(restoreLogRepository.saveAndFlush(any(CompensationRestoreLog.class))).thenReturn(claimLog);
        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));

        BindingSnapshot malformed = new BindingSnapshot(userId, skillId, null);

        assertThrows(IllegalArgumentException.class,
                () -> compensationRestoreService.restoreMemberSkills(projectId, eventId, 1L, ownerId,
                        fencingVersion, List.of(malformed)));

        verify(userProjectSkillDataAccess, never()).deleteByProjectId(projectId);
        verify(restoreLogRepository).markRestoreState(eq(eventId), eq(ownerId), eq(fencingVersion),
                eq("FAILED"), any(Date.class), anyString());
    }
}