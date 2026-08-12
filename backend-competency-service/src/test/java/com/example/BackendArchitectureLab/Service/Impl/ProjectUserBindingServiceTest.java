package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IProjectSkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectSkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillLevelDataAccess;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.Entity.Skill;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
import com.example.BackendArchitectureLab.Entity.UserProject;
import com.example.BackendArchitectureLab.Entity.UserProjectSkill;
import com.example.BackendArchitectureLab.Service.ICompensationOutboxService;
import com.example.BackendArchitectureLab.Service.IUserGateway;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.ProjectMemberSkillVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectUserBindingServiceTest {

    @Mock
    private IProjectDataAccess projectDataAccess;
    @Mock
    private IUserProjectDataAccess userProjectDataAccess;
    @Mock
    private IUserProjectSkillDataAccess userProjectSkillDataAccess;
    @Mock
    private ISkillDataAccess skillDataAccess;
    @Mock
    private ISkillLevelDataAccess skillLevelDataAccess;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private IUserGateway userGateway;
    @Mock
    private ICompensationOutboxService compensationOutboxService;
    @Mock
    private com.example.BackendArchitectureLab.Repository.CompensationRestoreLogRepository restoreLogRepository;

    @InjectMocks
    private ProjectUserBindingService projectUserBindingService;

    @BeforeEach
    void setUp() {
        // Inject self reference（自我代理：交易外驗證後呼叫自身交易方法）
        try {
            Field selfField = ProjectUserBindingService.class.getDeclaredField("self");
            selfField.setAccessible(true);
            selfField.set(projectUserBindingService, projectUserBindingService);
        } catch (Exception e) {
            throw new RuntimeException("Could not inject self into ProjectUserBindingService", e);
        }
    }

    @Test
    void getProjectMemberSkills_shouldThrow_whenProjectIdNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectUserBindingService.getProjectMemberSkills(null));
        assertEquals("Project ID must not be null", exception.getMessage());
    }

    @Test
    void getProjectMemberSkills_shouldThrow_whenProjectNotFound() {
        UUID projectId = UUID.randomUUID();
        when(projectDataAccess.existsById(projectId)).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectUserBindingService.getProjectMemberSkills(projectId));
        assertEquals("Project not found", exception.getMessage());
    }

    @Test
    void getProjectMemberSkills_shouldReturnMembersWithSkills() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        UserProject userProject = new UserProject();
        userProject.setUserId(userId);

        Skill skill = new Skill();
        skill.setId(skillId);
        skill.setName("Java");

        SkillLevel level = new SkillLevel();
        level.setId(levelId);
        level.setTitle("Senior");
        level.setLevelValue(3);

        UserProjectSkill binding = new UserProjectSkill();
        binding.setUserId(userId);
        binding.setSkill(skill);
        binding.setSkillLevel(level);

        when(projectDataAccess.existsById(projectId)).thenReturn(true);
        when(userProjectDataAccess.findByProjectId(projectId)).thenReturn(List.of(userProject));
        when(userProjectSkillDataAccess.findByProjectId(projectId)).thenReturn(List.of(binding));

        List<ProjectMemberSkillVo> result = projectUserBindingService.getProjectMemberSkills(projectId);

        assertEquals(1, result.size());
        assertEquals(userId.toString(), result.get(0).getUserId());
        assertEquals("", result.get(0).getUserEmail());
        assertEquals(1, result.get(0).getSkills().size());
        assertEquals(skillId.toString(), result.get(0).getSkills().get(0).getSkillId());
        assertEquals("Java", result.get(0).getSkills().get(0).getSkillName());
        assertEquals(levelId.toString(), result.get(0).getSkills().get(0).getSkillLevelId());
        assertEquals("Senior", result.get(0).getSkills().get(0).getLevelTitle());
        assertEquals(3, result.get(0).getSkills().get(0).getLevelValue());
    }

    @Test
    void getProjectMemberSkills_shouldReturnEmptySkills_whenMemberHasNoBindings() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        UserProject userProject = new UserProject();
        userProject.setUserId(userId);

        when(projectDataAccess.existsById(projectId)).thenReturn(true);
        when(userProjectDataAccess.findByProjectId(projectId)).thenReturn(List.of(userProject));
        when(userProjectSkillDataAccess.findByProjectId(projectId)).thenReturn(List.of());

        List<ProjectMemberSkillVo> result = projectUserBindingService.getProjectMemberSkills(projectId);

        assertEquals(1, result.size());
        assertEquals(userId.toString(), result.get(0).getUserId());
        assertEquals("", result.get(0).getUserEmail());
        assertTrue(result.get(0).getSkills().isEmpty());
    }

    @Test
    void getProjectMemberSkills_shouldReturnVoWithoutLevel_whenLevelNull() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();

        UserProject userProject = new UserProject();
        userProject.setUserId(userId);

        Skill skill = new Skill();
        skill.setId(skillId);
        skill.setName("Java");

        UserProjectSkill binding = new UserProjectSkill();
        binding.setUserId(userId);
        binding.setSkill(skill);
        binding.setSkillLevel(null);

        when(projectDataAccess.existsById(projectId)).thenReturn(true);
        when(userProjectDataAccess.findByProjectId(projectId)).thenReturn(List.of(userProject));
        when(userProjectSkillDataAccess.findByProjectId(projectId)).thenReturn(List.of(binding));

        List<ProjectMemberSkillVo> result = projectUserBindingService.getProjectMemberSkills(projectId);

        assertNull(result.get(0).getSkills().get(0).getSkillLevelId());
        assertNull(result.get(0).getSkills().get(0).getLevelTitle());
    }

    @Test
    void bindUsersToProject_shouldThrow_whenProjectNotFound() {
        UUID projectId = UUID.randomUUID();
        when(projectDataAccess.findById(projectId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectUserBindingService.bindUsersToProject(projectId, List.of(UUID.randomUUID().toString())));
        assertEquals("Project not found", exception.getMessage());
    }

    @Test
    void bindUsersToProject_shouldSaveOnlyMissingBindings() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);
        UUID existingUserId = UUID.randomUUID();
        UUID newUserId = UUID.randomUUID();

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(userProjectDataAccess.existsByUserIdAndProjectId(existingUserId, projectId)).thenReturn(true);
        when(userProjectDataAccess.existsByUserIdAndProjectId(newUserId, projectId)).thenReturn(false);

        projectUserBindingService.bindUsersToProject(
                projectId, List.of(existingUserId.toString(), newUserId.toString()));

        verify(userProjectDataAccess).save(any(UserProject.class));
        verify(userProjectDataAccess, times(2)).existsByUserIdAndProjectId(any(UUID.class), eq(projectId));
    }

    @Test
    void validateUsersExist_shouldReturn_whenEmptyOrNull() {
        projectUserBindingService.validateUsersExist(null);
        projectUserBindingService.validateUsersExist(Collections.emptyList());
        verifyNoInteractions(userGateway);
    }

    @Test
    void validateUsersExist_shouldThrow_whenUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userGateway.existsUserById(userId)).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectUserBindingService.validateUsersExist(List.of(userId)));
        assertEquals("User not found: " + userId, exception.getMessage());
    }

    @Test
    void validateUsersExist_shouldPass_whenAllExist() {
        UUID userId = UUID.randomUUID();
        when(userGateway.existsUserById(userId)).thenReturn(true);

        projectUserBindingService.validateUsersExist(List.of(userId));

        verify(userGateway).existsUserById(userId);
    }

    @Test
    void evictUserProjectsCache_shouldReturn_whenNullUser() {
        projectUserBindingService.evictUserProjectsCache(null);
        verifyNoInteractions(cacheManager);
    }

    @Test
    void evictUserProjectsCache_shouldReturn_whenCacheMissing() {
        when(cacheManager.getCache("projects")).thenReturn(null);

        projectUserBindingService.evictUserProjectsCache(UUID.randomUUID());
    }

    @Test
    void evictUserProjectsCache_shouldEvictEntry() {
        Cache cache = mock(Cache.class);
        UUID userId = UUID.randomUUID();
        when(cacheManager.getCache("projects")).thenReturn(cache);

        projectUserBindingService.evictUserProjectsCache(userId);

        verify(cache).evict("byuser:" + userId);
    }

    @Test
    void rebindProjectMemberSkills_shouldThrow_whenProjectIdNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectUserBindingService.rebindProjectMemberSkills(null, Map.of()));
        assertEquals("Project ID must not be null", exception.getMessage());
    }

    @Test
    void rebindProjectMemberSkills_shouldHandleNullMap() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(userProjectSkillDataAccess.findByProjectId(projectId)).thenReturn(List.of());

        projectUserBindingService.rebindProjectMemberSkills(projectId, null);

        verify(projectDataAccess).findById(projectId);
    }

    @Test
    void rebindProjectMemberSkills_shouldPublishSavePointAndCommitted_whenSuccess() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);
        Skill skill = new Skill();
        skill.setId(skillId);
        SkillLevel level = new SkillLevel();
        level.setId(levelId);
        level.setSkill(skill);
        Map<UUID, Map<UUID, UUID>> memberSkillsMap = Map.of(userId, Map.of(skillId, levelId));

        when(userGateway.existsUserById(userId)).thenReturn(true);
        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(true);
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(skill));
        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.of(level));
        when(userProjectSkillDataAccess.findByProjectId(projectId)).thenReturn(List.of());

        projectUserBindingService.rebindProjectMemberSkills(projectId, memberSkillsMap);

        ArgumentCaptor<Map<String, Object>> stateCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<UUID> txCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(compensationOutboxService).enqueueTransactionStarted(txCaptor.capture(),
                eq(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND), stateCaptor.capture());
        verify(compensationOutboxService).enqueueCommitted(any(UUID.class),
                eq(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND), anyMap());
        verify(compensationOutboxService, never()).enqueueFailureAndCompensationRequired(any(UUID.class),
                eq(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND), anyMap(), any());
        assertEquals(projectId.toString(), stateCaptor.getValue().get("projectId"));
        assertEquals(1, stateCaptor.getValue().get("memberCount"));
        assertEquals(1, stateCaptor.getValue().get("skillsCount"));
    }

    @Test
    void outboxEnqueue_shouldCommitCommittedWithinBusinessTransaction_butFailedRequiresNew() throws Exception {
        Method enqueueCommitted = CompensationOutboxServiceImpl.class.getMethod("enqueueCommitted",
                UUID.class, CompensationAction.class, Map.class);
        assertNull(enqueueCommitted.getAnnotation(Transactional.class),
                "enqueueCommitted 必須與業務交易同 commit（不得 REQUIRES_NEW）");

        Method enqueueFailure = CompensationOutboxServiceImpl.class
                .getMethod("enqueueFailureAndCompensationRequired",
                        UUID.class, CompensationAction.class, Map.class, String.class);
        Transactional failedTx = enqueueFailure.getAnnotation(Transactional.class);
        assertNotNull(failedTx, "失敗閉環需在 rollback 後以新交易寫入 FAILED 與 COMPENSATION_REQUIRED");
        assertEquals(Propagation.REQUIRES_NEW, failedTx.propagation());
    }

    @Test
    void outboxEnqueue_shouldCommitTransactionStartedWithinBusinessTransaction() throws Exception {
        Method enqueueStarted = CompensationOutboxServiceImpl.class.getMethod("enqueueTransactionStarted",
                UUID.class, CompensationAction.class, Map.class);
        assertNull(enqueueStarted.getAnnotation(Transactional.class),
                "enqueueTransactionStarted 必須與業務交易同 commit（不得 REQUIRES_NEW）");
    }

    @Test
    void rebindProjectMemberSkills_shouldNotEnqueueCommitted_whenBusinessFails() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(userGateway.existsUserById(userId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> projectUserBindingService.rebindProjectMemberSkills(projectId, Map.of(userId, Map.of())));

        verify(compensationOutboxService, never()).enqueueCommitted(
                any(UUID.class), any(CompensationAction.class), anyMap());
    }

    @Test
    void rebindProjectMemberSkills_shouldPublishFailed_whenUserValidationFails() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Map<UUID, Map<UUID, UUID>> memberSkillsMap = Map.of(userId, Map.of());

        when(userGateway.existsUserById(userId)).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectUserBindingService.rebindProjectMemberSkills(projectId, memberSkillsMap));
        assertEquals("User not found: " + userId, exception.getMessage());

        verify(compensationOutboxService, never()).enqueueFailureAndCompensationRequired(any(UUID.class),
                any(CompensationAction.class), anyMap(), anyString());
    }

    @Test
    void rebindProjectMemberSkills_shouldPublishFailed_whenRebindFails() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);
        Skill skill = new Skill();
        skill.setId(skillId);
        Map<UUID, Map<UUID, UUID>> memberSkillsMap = Map.of(userId, Map.of(skillId, levelId));

        when(userGateway.existsUserById(userId)).thenReturn(true);
        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectUserBindingService.rebindProjectMemberSkills(projectId, memberSkillsMap));
        assertTrue(exception.getMessage().contains("is not a member"));

        verify(compensationOutboxService).enqueueTransactionStarted(any(UUID.class),
                eq(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND), anyMap());
        verify(compensationOutboxService, never()).enqueueFailureAndCompensationRequired(any(UUID.class),
                any(CompensationAction.class), anyMap(), anyString());
    }

    @Test
    void doRebindProjectMemberSkills_shouldAddDeleteAndUpdate() {
        UUID projectId = UUID.randomUUID();
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        UUID skillId1 = UUID.randomUUID();
        UUID skillId2 = UUID.randomUUID();
        UUID levelId1 = UUID.randomUUID();
        UUID levelId2 = UUID.randomUUID();

        Project project = new Project();
        project.setId(projectId);

        Skill skill1 = new Skill();
        skill1.setId(skillId1);
        skill1.setName("Java");
        Skill skill2 = new Skill();
        skill2.setId(skillId2);
        skill2.setName("Python");

        SkillLevel level1 = new SkillLevel();
        level1.setId(levelId1);
        level1.setSkill(skill1);
        SkillLevel level2 = new SkillLevel();
        level2.setId(levelId2);
        level2.setSkill(skill2);

        // 現有綁定：user1 綁 skill2（目標清單只有 skill1 → skill2 應刪除）
        UserProjectSkill existingBinding = new UserProjectSkill();
        existingBinding.setUserId(userId1);
        existingBinding.setSkill(skill2);
        existingBinding.setSkillLevel(level2);

        // 現有綁定：user2 綁 skill1（user2 不在目標清單 → 全部刪除）
        UserProjectSkill existingUser2 = new UserProjectSkill();
        existingUser2.setUserId(userId2);
        existingUser2.setSkill(skill1);
        existingUser2.setSkillLevel(level1);

        Map<UUID, Map<UUID, UUID>> memberSkillsMap = Map.of(userId1, Map.of(skillId1, levelId1));

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId1, projectId)).thenReturn(true);
        when(skillDataAccess.findById(skillId1)).thenReturn(Optional.of(skill1));
        when(skillLevelDataAccess.findById(levelId1)).thenReturn(Optional.of(level1));
        when(userProjectSkillDataAccess.findByProjectId(projectId))
                .thenReturn(List.of(existingBinding, existingUser2));

        projectUserBindingService.doRebindProjectMemberSkills(projectId, memberSkillsMap, UUID.randomUUID());

        verify(userProjectSkillDataAccess).deleteByUserIdAndProjectIdAndSkillId(userId1, projectId, skillId2);
        verify(userProjectSkillDataAccess).deleteByUserIdAndProjectIdAndSkillId(userId2, projectId, skillId1);
        verify(userProjectSkillDataAccess).save(any(UserProjectSkill.class));
    }

    @Test
    void doRebindProjectMemberSkills_shouldUpdateLevel_whenChanged() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId1 = UUID.randomUUID();
        UUID levelId2 = UUID.randomUUID();

        Project project = new Project();
        project.setId(projectId);

        Skill skill = new Skill();
        skill.setId(skillId);
        skill.setName("Java");

        SkillLevel level1 = new SkillLevel();
        level1.setId(levelId1);
        level1.setSkill(skill);
        SkillLevel level2 = new SkillLevel();
        level2.setId(levelId2);
        level2.setSkill(skill);

        // 現有綁定：user 已綁 skill 於 level1，目標改為 level2 → 更新等級
        UserProjectSkill existingBinding = new UserProjectSkill();
        existingBinding.setUserId(userId);
        existingBinding.setSkill(skill);
        existingBinding.setSkillLevel(level1);

        Map<UUID, Map<UUID, UUID>> memberSkillsMap = Map.of(userId, Map.of(skillId, levelId2));

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(true);
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(skill));
        when(skillLevelDataAccess.findById(levelId2)).thenReturn(Optional.of(level2));
        when(userProjectSkillDataAccess.findByProjectId(projectId)).thenReturn(List.of(existingBinding));

        projectUserBindingService.doRebindProjectMemberSkills(projectId, memberSkillsMap, UUID.randomUUID());

        verify(userProjectSkillDataAccess).save(existingBinding);
        assertEquals(level2, existingBinding.getSkillLevel());
    }

    @Test
    void doRebindProjectMemberSkills_shouldThrow_whenUserNotMember() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);

        Map<UUID, Map<UUID, UUID>> memberSkillsMap = Map.of(userId, Map.of());

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectUserBindingService.doRebindProjectMemberSkills(projectId, memberSkillsMap, UUID.randomUUID()));
        assertTrue(exception.getMessage().contains("is not a member"));
    }

    @Test
    void doRebindProjectMemberSkills_shouldThrow_whenSkillNotFound() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);

        Map<UUID, Map<UUID, UUID>> memberSkillsMap = Map.of(userId, Map.of(skillId, levelId));

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(true);
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectUserBindingService.doRebindProjectMemberSkills(projectId, memberSkillsMap, UUID.randomUUID()));
        assertEquals("Skill not found: " + skillId, exception.getMessage());
    }

    @Test
    void doRebindProjectMemberSkills_shouldThrow_whenLevelNotFound() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);
        Skill skill = new Skill();
        skill.setId(skillId);

        Map<UUID, Map<UUID, UUID>> memberSkillsMap = Map.of(userId, Map.of(skillId, levelId));

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(true);
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(skill));
        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectUserBindingService.doRebindProjectMemberSkills(projectId, memberSkillsMap, UUID.randomUUID()));
        assertEquals("Skill level not found: " + levelId, exception.getMessage());
    }

    @Test
    void doRebindProjectMemberSkills_shouldThrow_whenLevelMismatch() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID otherSkillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);
        Skill skill = new Skill();
        skill.setId(skillId);
        Skill otherSkill = new Skill();
        otherSkill.setId(otherSkillId);
        SkillLevel level = new SkillLevel();
        level.setId(levelId);
        level.setSkill(otherSkill);

        Map<UUID, Map<UUID, UUID>> memberSkillsMap = Map.of(userId, Map.of(skillId, levelId));

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(true);
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(skill));
        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.of(level));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectUserBindingService.doRebindProjectMemberSkills(projectId, memberSkillsMap, UUID.randomUUID()));
        assertEquals("Skill level does not belong to skill", exception.getMessage());
    }

    @Test
    void testRestoreMemberSkills() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        Project project = new Project();
        project.setId(projectId);
        Skill skill = new Skill();
        skill.setId(skillId);
        SkillLevel level = new SkillLevel();
        level.setId(levelId);

        when(restoreLogRepository.existsById(any(UUID.class))).thenReturn(false);
        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(skill));
        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.of(level));

        Map<String, String> bindingMap = Map.of(
                "userId", userId.toString(),
                "skillId", skillId.toString(),
                "levelId", levelId.toString()
        );

        projectUserBindingService.restoreMemberSkills(projectId, UUID.randomUUID(), null, List.of(bindingMap));

        verify(userProjectSkillDataAccess).deleteByProjectId(projectId);
        verify(userProjectSkillDataAccess).save(any(UserProjectSkill.class));
        verify(restoreLogRepository).save(any(com.example.BackendArchitectureLab.Entity.CompensationRestoreLog.class));
    }

    @Test
    void testRestoreMemberSkills_shouldSkip_whenAlreadyProcessed() {
        UUID projectId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        when(restoreLogRepository.existsById(eventId)).thenReturn(true);

        projectUserBindingService.restoreMemberSkills(projectId, eventId, null, List.of());

        verifyNoInteractions(projectDataAccess);
        verifyNoInteractions(userProjectSkillDataAccess);
    }

    @Test
    void testRestoreMemberSkills_shouldThrow_whenTimestampConflict() {
        UUID projectId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        long expectedTime = 1000L;

        Project project = new Project();
        project.setId(projectId);
        // DB project updatedTime is 2000L
        project.setUpdatedTime(new java.util.Date(2000L));

        when(restoreLogRepository.existsById(eventId)).thenReturn(false);
        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));

        assertThrows(com.example.BackendArchitectureLab.Exception.CompensationConflictException.class,
                () -> projectUserBindingService.restoreMemberSkills(projectId, eventId, expectedTime, List.of()));

        verify(userProjectSkillDataAccess, never()).deleteByProjectId(projectId);
    }

    @Test
    void testRebindSkillsWithSimulatedExternalFailure() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);
        Skill skill = new Skill();
        skill.setId(skillId);
        SkillLevel level = new SkillLevel();
        level.setId(levelId);
        level.setSkill(skill);

        Map<UUID, Map<UUID, UUID>> memberSkillsMap = Map.of(userId, Map.of(skillId, levelId));

        when(userGateway.existsUserById(userId)).thenReturn(true);
        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(true);
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(skill));
        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.of(level));
        when(userProjectSkillDataAccess.findByProjectId(projectId)).thenReturn(List.of());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> projectUserBindingService.rebindProjectMemberSkills(projectId, memberSkillsMap));
        assertEquals("Simulated external partner sync failed after DB commit", exception.getMessage());

        verify(compensationOutboxService).enqueueTransactionStarted(any(UUID.class),
                eq(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND), anyMap());
        verify(compensationOutboxService).enqueueFailureAndCompensationRequired(any(UUID.class),
                eq(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND), anyMap(),
                eq("Simulated external partner sync failed after DB commit"));
    }
}
