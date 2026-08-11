package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.Entity.ProjectSkill;
import com.example.BackendArchitectureLab.Entity.Skill;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
import com.example.BackendArchitectureLab.Entity.UserSkill;
import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IProjectSkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserSkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillLevelDataAccess;
import com.example.BackendArchitectureLab.Util.SecurityUtil;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectSkillServiceTest {

    @Mock
    private TransactionExecutor transactionExecutor;
    @Mock
    private IProjectDataAccess projectDataAccess;
    @Mock
    private IProjectSkillDataAccess projectSkillDataAccess;
    @Mock
    private IUserProjectDataAccess userProjectDataAccess;
    @Mock
    private IUserSkillDataAccess userSkillDataAccess;
    @Mock
    private ISkillLevelDataAccess skillLevelDataAccess;
    @Mock
    private ISkillDataAccess skillDataAccess;
    @Mock
    private SecurityUtil securityUtil;

    @InjectMocks
    private ProjectSkillService projectSkillService;

    private Project testProject;

    @BeforeEach
    void setUp() {
        lenient().when(transactionExecutor.executeReadOnly(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        testProject = new Project();
        testProject.setId(UUID.randomUUID());
    }

    private void setupSecurityContext(UUID userId) {
        lenient().when(securityUtil.requireCurrentUserId()).thenReturn(userId);
    }

    @Test
    void bindPersonalProjectSkill_shouldBind_whenVisibleAndAllowed() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        setupSecurityContext(userId);
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(true);

        UserSkill userSkill = new UserSkill();
        Skill visibleSkill = new Skill();
        visibleSkill.setId(skillId);
        userSkill.setSkill(visibleSkill);
        when(userSkillDataAccess.findByUserId(userId)).thenReturn(List.of(userSkill));
        when(userProjectDataAccess.findByUserId(userId)).thenReturn(List.of());

        when(projectSkillDataAccess.existsByProjectIdAndSkillId(projectId, skillId)).thenReturn(false);
        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(testProject));

        SkillLevel level = new SkillLevel();
        level.setId(levelId);
        level.setSkill(visibleSkill);
        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.of(level));
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(visibleSkill));

        projectSkillService.bindPersonalProjectSkill(projectId, skillId, levelId);

        verify(projectSkillDataAccess).save(any(ProjectSkill.class));
    }

    @Test
    void bindPersonalProjectSkill_shouldThrow_whenSkillNotVisible() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        setupSecurityContext(userId);
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(true);
        when(userSkillDataAccess.findByUserId(userId)).thenReturn(List.of());
        when(userProjectDataAccess.findByUserId(userId)).thenReturn(List.of());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectSkillService.bindPersonalProjectSkill(projectId, skillId, levelId));
        assertEquals("Skill is not visible to current user", exception.getMessage());
    }

    @Test
    void updatePersonalProjectSkillLevel_shouldUpdate_whenBindingExists() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        setupSecurityContext(userId);
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(true);

        UserSkill userSkill = new UserSkill();
        Skill skill = new Skill();
        skill.setId(skillId);
        userSkill.setSkill(skill);
        when(userSkillDataAccess.findByUserId(userId)).thenReturn(List.of(userSkill));
        when(userProjectDataAccess.findByUserId(userId)).thenReturn(List.of());

        ProjectSkill projectSkill = new ProjectSkill();
        projectSkill.setSkill(skill);
        when(projectSkillDataAccess.findByProjectIdAndSkillId(projectId, skillId)).thenReturn(Optional.of(projectSkill));

        SkillLevel level = new SkillLevel();
        level.setId(levelId);
        level.setSkill(skill);
        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.of(level));

        projectSkillService.updatePersonalProjectSkillLevel(projectId, skillId, levelId);

        verify(projectSkillDataAccess).save(projectSkill);
        assertEquals(level, projectSkill.getSkillLevel());
    }

    @Test
    void unbindPersonalProjectSkill_shouldDelete_whenBindingExists() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();

        setupSecurityContext(userId);
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(true);
        when(projectSkillDataAccess.existsByProjectIdAndSkillId(projectId, skillId)).thenReturn(true);

        projectSkillService.unbindPersonalProjectSkill(projectId, skillId);

        verify(projectSkillDataAccess).deleteByProjectIdAndSkillId(projectId, skillId);
    }
}
