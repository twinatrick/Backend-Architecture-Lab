package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.ISkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillLevelDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectSkillDataAccess;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.Entity.Skill;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
import com.example.BackendArchitectureLab.Entity.UserProject;
import com.example.BackendArchitectureLab.Entity.UserProjectSkill;
import com.example.BackendArchitectureLab.Vo.BindingSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompensationRestoreValidatorServiceTest {

    @Mock
    private IUserProjectSkillDataAccess userProjectSkillDataAccess;
    @Mock
    private IUserProjectDataAccess userProjectDataAccess;
    @Mock
    private ISkillDataAccess skillDataAccess;
    @Mock
    private ISkillLevelDataAccess skillLevelDataAccess;

    private CompensationRestoreValidatorService validatorService;

    @BeforeEach
    void setUp() {
        validatorService = new CompensationRestoreValidatorService(
                userProjectSkillDataAccess,
                userProjectDataAccess,
                skillDataAccess,
                skillLevelDataAccess
        );
    }

    @Test
    @DisplayName("isBindingsAlreadyRestored: 相同綁定回傳 true")
    void testIsBindingsAlreadyRestored_true() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        Skill skill = new Skill();
        skill.setId(skillId);
        SkillLevel level = new SkillLevel();
        level.setId(levelId);

        UserProjectSkill ups = new UserProjectSkill();
        ups.setUserId(userId);
        ups.setSkill(skill);
        ups.setSkillLevel(level);

        when(userProjectSkillDataAccess.findByProjectId(projectId)).thenReturn(List.of(ups));

        BindingSnapshot binding = new BindingSnapshot(userId, skillId, levelId);
        boolean result = validatorService.isBindingsAlreadyRestored(projectId, List.of(binding));

        assertTrue(result);
    }

    @Test
    @DisplayName("isBindingsAlreadyRestored: 不相同綁定回傳 false")
    void testIsBindingsAlreadyRestored_false() {
        UUID projectId = UUID.randomUUID();
        when(userProjectSkillDataAccess.findByProjectId(projectId)).thenReturn(List.of());

        BindingSnapshot binding = new BindingSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        boolean result = validatorService.isBindingsAlreadyRestored(projectId, List.of(binding));

        assertFalse(result);
    }

    @Test
    @DisplayName("validateBindingsForRestore: null 或空清單直接返回")
    void testValidateBindingsForRestore_nullOrEmpty() {
        UUID projectId = UUID.randomUUID();
        assertDoesNotThrow(() -> validatorService.validateBindingsForRestore(projectId, null));
        assertDoesNotThrow(() -> validatorService.validateBindingsForRestore(projectId, List.of()));
        verifyNoInteractions(skillDataAccess, skillLevelDataAccess, userProjectDataAccess);
    }

    @Test
    @DisplayName("validateBindingsForRestore: 超過 1000 筆限制拋出異常")
    void testValidateBindingsForRestore_exceedsLimit() {
        UUID projectId = UUID.randomUUID();
        List<BindingSnapshot> tooMany = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            tooMany.add(new BindingSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        }

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validatorService.validateBindingsForRestore(projectId, tooMany)
        );
        assertTrue(ex.getMessage().contains("exceeds maximum allowed limit of 1000"));
    }

    @Test
    @DisplayName("validateBindingsForRestore: 元素為 null 拋出異常")
    void testValidateBindingsForRestore_nullElement() {
        UUID projectId = UUID.randomUUID();
        List<BindingSnapshot> listWithNull = new ArrayList<>();
        listWithNull.add(null);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validatorService.validateBindingsForRestore(projectId, listWithNull)
        );
        assertTrue(ex.getMessage().contains("element must not be null"));
    }

    @Test
    @DisplayName("validateBindingsForRestore: 欄位有 null 拋出異常")
    void testValidateBindingsForRestore_nullField() {
        UUID projectId = UUID.randomUUID();
        BindingSnapshot binding = new BindingSnapshot(UUID.randomUUID(), null, UUID.randomUUID());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validatorService.validateBindingsForRestore(projectId, List.of(binding))
        );
        assertTrue(ex.getMessage().contains("must not be null"));
    }

    @Test
    @DisplayName("validateBindingsForRestore: 同一成員重複技能拋出異常")
    void testValidateBindingsForRestore_duplicateUserSkill() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();

        BindingSnapshot b1 = new BindingSnapshot(userId, skillId, UUID.randomUUID());
        BindingSnapshot b2 = new BindingSnapshot(userId, skillId, UUID.randomUUID());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validatorService.validateBindingsForRestore(projectId, List.of(b1, b2))
        );
        assertTrue(ex.getMessage().contains("Duplicate binding snapshot detected"));
    }

    @Test
    @DisplayName("validateBindingsForRestore: 成員不屬於專案拋出異常")
    void testValidateBindingsForRestore_userNotMember() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        when(userProjectDataAccess.findByProjectId(projectId)).thenReturn(List.of());

        BindingSnapshot b = new BindingSnapshot(userId, skillId, levelId);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validatorService.validateBindingsForRestore(projectId, List.of(b))
        );
        assertTrue(ex.getMessage().contains("is not a member of project"));
    }

    @Test
    @DisplayName("validateBindingsForRestore: 技能不存在拋出異常")
    void testValidateBindingsForRestore_skillNotFound() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        Project project = new Project();
        project.setId(projectId);
        UserProject up = new UserProject();
        up.setUserId(userId);
        up.setProject(project);

        when(userProjectDataAccess.findByProjectId(projectId)).thenReturn(List.of(up));
        when(skillDataAccess.findAllById(anyList())).thenReturn(List.of());

        BindingSnapshot b = new BindingSnapshot(userId, skillId, levelId);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validatorService.validateBindingsForRestore(projectId, List.of(b))
        );
        assertTrue(ex.getMessage().contains("Skill not found"));
    }

    @Test
    @DisplayName("validateBindingsForRestore: 技能等級不存在拋出異常")
    void testValidateBindingsForRestore_skillLevelNotFound() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        Project project = new Project();
        project.setId(projectId);
        UserProject up = new UserProject();
        up.setUserId(userId);
        up.setProject(project);

        Skill skill = new Skill();
        skill.setId(skillId);

        when(userProjectDataAccess.findByProjectId(projectId)).thenReturn(List.of(up));
        when(skillDataAccess.findAllById(anyList())).thenReturn(List.of(skill));
        when(skillLevelDataAccess.findAllById(anyList())).thenReturn(List.of());

        BindingSnapshot b = new BindingSnapshot(userId, skillId, levelId);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validatorService.validateBindingsForRestore(projectId, List.of(b))
        );
        assertTrue(ex.getMessage().contains("Skill level not found"));
    }

    @Test
    @DisplayName("validateBindingsForRestore: 技能等級不屬於該技能拋出異常")
    void testValidateBindingsForRestore_levelMismatchSkill() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        UUID otherSkillId = UUID.randomUUID();

        Project project = new Project();
        project.setId(projectId);
        UserProject up = new UserProject();
        up.setUserId(userId);
        up.setProject(project);

        Skill skill = new Skill();
        skill.setId(skillId);

        Skill otherSkill = new Skill();
        otherSkill.setId(otherSkillId);

        SkillLevel level = new SkillLevel();
        level.setId(levelId);
        level.setSkill(otherSkill);

        when(userProjectDataAccess.findByProjectId(projectId)).thenReturn(List.of(up));
        when(skillDataAccess.findAllById(anyList())).thenReturn(List.of(skill));
        when(skillLevelDataAccess.findAllById(anyList())).thenReturn(List.of(level));

        BindingSnapshot b = new BindingSnapshot(userId, skillId, levelId);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validatorService.validateBindingsForRestore(projectId, List.of(b))
        );
        assertTrue(ex.getMessage().contains("does not belong to skill"));
    }

    @Test
    @DisplayName("validateBindingsForRestore: 正確綁定校驗成功")
    void testValidateBindingsForRestore_success() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        Project project = new Project();
        project.setId(projectId);
        UserProject up = new UserProject();
        up.setUserId(userId);
        up.setProject(project);

        Skill skill = new Skill();
        skill.setId(skillId);

        SkillLevel level = new SkillLevel();
        level.setId(levelId);
        level.setSkill(skill);

        when(userProjectDataAccess.findByProjectId(projectId)).thenReturn(List.of(up));
        when(skillDataAccess.findAllById(anyList())).thenReturn(List.of(skill));
        when(skillLevelDataAccess.findAllById(anyList())).thenReturn(List.of(level));

        BindingSnapshot b = new BindingSnapshot(userId, skillId, levelId);

        assertDoesNotThrow(() -> validatorService.validateBindingsForRestore(projectId, List.of(b)));
        verify(skillDataAccess).findAllById(List.of(skillId));
        verify(skillLevelDataAccess).findAllById(List.of(levelId));
    }
}
