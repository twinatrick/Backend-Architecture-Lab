package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.ProjectMemberSkillVo;
import com.example.BackendArchitectureLab.Entity.Skill;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
import com.example.BackendArchitectureLab.Entity.UserProject;
import com.example.BackendArchitectureLab.Entity.UserProjectSkill;
import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectSkillDataAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectUserBindingServiceTest {

    @Mock
    private IProjectDataAccess projectDataAccess;
    @Mock
    private IUserProjectDataAccess userProjectDataAccess;
    @Mock
    private IUserProjectSkillDataAccess userProjectSkillDataAccess;

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
}
