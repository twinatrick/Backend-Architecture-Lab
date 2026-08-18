package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.IProjectSkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.Impl.ProjectSkillDataAccessImpl;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.Entity.ProjectSkill;
import com.example.BackendArchitectureLab.Entity.Skill;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
import com.example.BackendArchitectureLab.Repository.ProjectRepository;
import com.example.BackendArchitectureLab.Repository.ProjectSkillRepository;
import com.example.BackendArchitectureLab.Repository.SkillLevelRepository;
import com.example.BackendArchitectureLab.Repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ProjectSkillDataAccessImpl.
 * Uses in-memory H2 database for testing.
 */
@DataJpaTest
@ActiveProfiles("test")
class ProjectSkillDataAccessImplTest {

    private final IProjectSkillDataAccess projectSkillDataAccess;
    private final ProjectSkillRepository projectSkillRepository;
    private final ProjectRepository projectRepository;
    private final SkillRepository skillRepository;
    private final SkillLevelRepository skillLevelRepository;

    @Autowired
    public ProjectSkillDataAccessImplTest(ProjectSkillRepository projectSkillRepository,
                                          ProjectRepository projectRepository,
                                          SkillRepository skillRepository,
                                          SkillLevelRepository skillLevelRepository) {
        this.projectSkillRepository = projectSkillRepository;
        this.projectRepository = projectRepository;
        this.skillRepository = skillRepository;
        this.skillLevelRepository = skillLevelRepository;
        this.projectSkillDataAccess = new ProjectSkillDataAccessImpl(projectSkillRepository);
    }

    @BeforeEach
    void setUp() {
        projectSkillRepository.deleteAll();
        projectRepository.deleteAll();
        skillRepository.deleteAll();
        skillLevelRepository.deleteAll();
    }

    @Test
    @DisplayName("應該檢查 ProjectSkill 是否存在（依 projectId 和 skillId）")
    void testExistsByProjectIdAndSkillId() {
        // Arrange
        Project project = new Project();
        project.setName("測試專案");
        projectRepository.save(project);

        Skill skill = new Skill();
        skill.setName("Java");
        skillRepository.save(skill);

        SkillLevel skillLevel = new SkillLevel();
        skillLevel.setSkill(skill);
        skillLevel.setTitle("Default");
        skillLevel.setLevelValue(1);
        skillLevelRepository.save(skillLevel);

        ProjectSkill projectSkill = new ProjectSkill();
        projectSkill.setProject(project);
        projectSkill.setSkill(skill);
        projectSkill.setSkillLevel(skillLevel);
        projectSkillRepository.save(projectSkill);

        // Act & Assert
        assertTrue(projectSkillDataAccess.existsByProjectIdAndSkillId(project.getId(), skill.getId()));
        assertFalse(projectSkillDataAccess.existsByProjectIdAndSkillId(UUID.randomUUID(), skill.getId()));
    }

    @Test
    @DisplayName("應該檢查是否存在使用指定 SkillLevel 的 ProjectSkill")
    void testExistsBySkillLevelId() {
        // Arrange
        Project project = new Project();
        project.setName("測試專案");
        projectRepository.save(project);

        Skill skill = new Skill();
        skill.setName("Java");
        skillRepository.save(skill);

        SkillLevel skillLevel = new SkillLevel();
        skillLevel.setSkill(skill);
        skillLevel.setTitle("Junior");
        skillLevel.setLevelValue(1);
        skillLevelRepository.save(skillLevel);

        ProjectSkill projectSkill = new ProjectSkill();
        projectSkill.setProject(project);
        projectSkill.setSkill(skill);
        projectSkill.setSkillLevel(skillLevel);
        projectSkillRepository.save(projectSkill);

        // Act & Assert
        assertTrue(projectSkillDataAccess.existsBySkillLevelId(skillLevel.getId()));
        assertFalse(projectSkillDataAccess.existsBySkillLevelId(UUID.randomUUID()));
    }

    @Test
    @DisplayName("應該保存 ProjectSkill")
    void testSave() {
        // Arrange
        Project project = new Project();
        project.setName("測試專案");
        projectRepository.save(project);

        Skill skill = new Skill();
        skill.setName("Java");
        skillRepository.save(skill);

        SkillLevel skillLevel = new SkillLevel();
        skillLevel.setSkill(skill);
        skillLevel.setTitle("Default");
        skillLevel.setLevelValue(1);
        skillLevelRepository.save(skillLevel);

        ProjectSkill projectSkill = new ProjectSkill();
        projectSkill.setProject(project);
        projectSkill.setSkill(skill);
        projectSkill.setSkillLevel(skillLevel);

        // Act
        ProjectSkill saved = projectSkillDataAccess.save(projectSkill);

        // Assert
        assertNotNull(saved.getId());
        assertEquals(project.getId(), saved.getProject().getId());
        assertEquals(skill.getId(), saved.getSkill().getId());
    }

    @Test
    @DisplayName("應該根據 projectId 刪除所有 ProjectSkill")
    void testDeleteByProjectId() {
        // Arrange
        Project project = new Project();
        project.setName("測試專案");
        projectRepository.save(project);

        Skill skill1 = new Skill();
        skill1.setName("Java");
        skillRepository.save(skill1);

        Skill skill2 = new Skill();
        skill2.setName("Python");
        skillRepository.save(skill2);

        SkillLevel skillLevel1 = new SkillLevel();
        skillLevel1.setSkill(skill1);
        skillLevel1.setTitle("Default");
        skillLevel1.setLevelValue(1);
        skillLevelRepository.save(skillLevel1);

        SkillLevel skillLevel2 = new SkillLevel();
        skillLevel2.setSkill(skill2);
        skillLevel2.setTitle("Default");
        skillLevel2.setLevelValue(1);
        skillLevelRepository.save(skillLevel2);

        ProjectSkill projectSkill1 = new ProjectSkill();
        projectSkill1.setProject(project);
        projectSkill1.setSkill(skill1);
        projectSkill1.setSkillLevel(skillLevel1);
        projectSkillRepository.save(projectSkill1);

        ProjectSkill projectSkill2 = new ProjectSkill();
        projectSkill2.setProject(project);
        projectSkill2.setSkill(skill2);
        projectSkill2.setSkillLevel(skillLevel2);
        projectSkillRepository.save(projectSkill2);

        assertEquals(2, projectSkillRepository.count());

        // Act
        projectSkillDataAccess.deleteByProjectId(project.getId());

        // Assert
        assertEquals(0, projectSkillRepository.count());
    }

    @Test
    @DisplayName("應該根據 skillId 刪除所有 ProjectSkill")
    void testDeleteBySkillId() {
        // Arrange
        Project project1 = new Project();
        project1.setName("專案1");
        projectRepository.save(project1);

        Project project2 = new Project();
        project2.setName("專案2");
        projectRepository.save(project2);

        Skill skill = new Skill();
        skill.setName("Java");
        skillRepository.save(skill);

        SkillLevel skillLevel = new SkillLevel();
        skillLevel.setSkill(skill);
        skillLevel.setTitle("Default");
        skillLevel.setLevelValue(1);
        skillLevelRepository.save(skillLevel);

        ProjectSkill projectSkill1 = new ProjectSkill();
        projectSkill1.setProject(project1);
        projectSkill1.setSkill(skill);
        projectSkill1.setSkillLevel(skillLevel);
        projectSkillRepository.save(projectSkill1);

        ProjectSkill projectSkill2 = new ProjectSkill();
        projectSkill2.setProject(project2);
        projectSkill2.setSkill(skill);
        projectSkill2.setSkillLevel(skillLevel);
        projectSkillRepository.save(projectSkill2);

        assertEquals(2, projectSkillRepository.count());

        // Act
        projectSkillDataAccess.deleteBySkillId(skill.getId());

        // Assert
        assertEquals(0, projectSkillRepository.count());
    }

    @Test
    @DisplayName("應該檢查是否存在使用指定 Skill 的 ProjectSkill")
    void testExistsBySkillId() {
        Project project = new Project();
        project.setName("測試專案");
        projectRepository.save(project);

        Skill skill = new Skill();
        skill.setName("Java");
        skillRepository.save(skill);

        SkillLevel skillLevel = new SkillLevel();
        skillLevel.setSkill(skill);
        skillLevel.setTitle("Default");
        skillLevel.setLevelValue(1);
        skillLevelRepository.save(skillLevel);

        ProjectSkill projectSkill = new ProjectSkill();
        projectSkill.setProject(project);
        projectSkill.setSkill(skill);
        projectSkill.setSkillLevel(skillLevel);
        projectSkillRepository.save(projectSkill);

        assertTrue(projectSkillDataAccess.existsBySkillId(skill.getId()));
        assertFalse(projectSkillDataAccess.existsBySkillId(UUID.randomUUID()));
    }

    @Test
    @DisplayName("應該根據 projectId 和 skillId 查詢 ProjectSkill")
    void testFindByProjectIdAndSkillId() {
        Project project = new Project();
        project.setName("測試專案");
        projectRepository.save(project);

        Skill skill = new Skill();
        skill.setName("Java");
        skillRepository.save(skill);

        SkillLevel skillLevel = new SkillLevel();
        skillLevel.setSkill(skill);
        skillLevel.setTitle("Default");
        skillLevel.setLevelValue(1);
        skillLevelRepository.save(skillLevel);

        ProjectSkill projectSkill = new ProjectSkill();
        projectSkill.setProject(project);
        projectSkill.setSkill(skill);
        projectSkill.setSkillLevel(skillLevel);
        projectSkillRepository.save(projectSkill);

        var result = projectSkillDataAccess.findByProjectIdAndSkillId(project.getId(), skill.getId());

        assertTrue(result.isPresent());
        assertEquals(project.getId(), result.get().getProject().getId());
    }

    @Test
    @DisplayName("應該根據 projectId 查詢所有 ProjectSkill")
    void testFindByProjectId() {
        Project project = new Project();
        project.setName("測試專案");
        projectRepository.save(project);

        Skill skill1 = new Skill();
        skill1.setName("Java");
        skillRepository.save(skill1);

        Skill skill2 = new Skill();
        skill2.setName("Python");
        skillRepository.save(skill2);

        SkillLevel skillLevel1 = new SkillLevel();
        skillLevel1.setSkill(skill1);
        skillLevel1.setTitle("Default");
        skillLevel1.setLevelValue(1);
        skillLevelRepository.save(skillLevel1);

        SkillLevel skillLevel2 = new SkillLevel();
        skillLevel2.setSkill(skill2);
        skillLevel2.setTitle("Default");
        skillLevel2.setLevelValue(1);
        skillLevelRepository.save(skillLevel2);

        ProjectSkill ps1 = new ProjectSkill();
        ps1.setProject(project);
        ps1.setSkill(skill1);
        ps1.setSkillLevel(skillLevel1);
        projectSkillRepository.save(ps1);

        ProjectSkill ps2 = new ProjectSkill();
        ps2.setProject(project);
        ps2.setSkill(skill2);
        ps2.setSkillLevel(skillLevel2);
        projectSkillRepository.save(ps2);

        var result = projectSkillDataAccess.findByProjectId(project.getId());

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("應該根據 projectId 和 skillId 刪除 ProjectSkill")
    void testDeleteByProjectIdAndSkillId() {
        Project project = new Project();
        project.setName("測試專案");
        projectRepository.save(project);

        Skill skill = new Skill();
        skill.setName("Java");
        skillRepository.save(skill);

        SkillLevel skillLevel = new SkillLevel();
        skillLevel.setSkill(skill);
        skillLevel.setTitle("Default");
        skillLevel.setLevelValue(1);
        skillLevelRepository.save(skillLevel);

        ProjectSkill projectSkill = new ProjectSkill();
        projectSkill.setProject(project);
        projectSkill.setSkill(skill);
        projectSkill.setSkillLevel(skillLevel);
        projectSkillRepository.save(projectSkill);

        assertEquals(1, projectSkillRepository.count());

        projectSkillDataAccess.deleteByProjectIdAndSkillId(project.getId(), skill.getId());

        assertEquals(0, projectSkillRepository.count());
    }
}
