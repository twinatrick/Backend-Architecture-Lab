package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.DataAccess.IUserProjectSkillDataAccess;
import com.example.BackendArchitectureLab.Entity.*;
import com.example.BackendArchitectureLab.Repository.UserProjectSkillRepository;
import com.example.BackendArchitectureLab.Repository.SkillLevelRepository;
import com.example.BackendArchitectureLab.Repository.SkillRepository;
import com.example.BackendArchitectureLab.Repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(UserProjectSkillDataAccessImpl.class)
class UserProjectSkillDataAccessImplTest {

    @Autowired
    private UserProjectSkillRepository repository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private SkillLevelRepository skillLevelRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private IUserProjectSkillDataAccess dataAccess;
    private User testUser;
    private Project testProject;
    private Skill testSkill;
    private SkillLevel testSkillLevel;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        skillLevelRepository.deleteAll();
        entityManager.createQuery("DELETE FROM User").executeUpdate();
        projectRepository.deleteAll();
        skillRepository.deleteAll();

        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setPassword("password");
        entityManager.persist(testUser);
        entityManager.flush();

        testProject = new Project();
        testProject.setName("Test Project");
        testProject = projectRepository.save(testProject);

        testSkill = new Skill();
        testSkill.setName("Java");
        testSkill = skillRepository.save(testSkill);

        testSkillLevel = new SkillLevel();
        testSkillLevel.setSkill(testSkill);
        testSkillLevel.setLevelValue(1);
        testSkillLevel.setTitle("Beginner");
        testSkillLevel = skillLevelRepository.save(testSkillLevel);
    }

    private UserProjectSkill createUserProjectSkill() {
        UserProjectSkill ups = new UserProjectSkill();
        ups.setUser(testUser);
        ups.setProject(testProject);
        ups.setSkill(testSkill);
        ups.setSkillLevel(testSkillLevel);
        return ups;
    }

    @Test
    @DisplayName("Should save user project skill successfully")
    void testSave() {
        UserProjectSkill ups = createUserProjectSkill();

        UserProjectSkill saved = dataAccess.save(ups);

        assertNotNull(saved.getId());
        assertEquals(testUser.getId(), saved.getUser().getId());
        assertEquals(testProject.getId(), saved.getProject().getId());
        assertEquals(testSkill.getId(), saved.getSkill().getId());
    }

    @Test
    @DisplayName("Should find by user id and project id")
    void testFindByUserIdAndProjectId() {
        repository.save(createUserProjectSkill());

        List<UserProjectSkill> result = dataAccess.findByUserIdAndProjectId(
                testUser.getId(), testProject.getId());

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should return empty list when no skills for user and project")
    void testFindByUserIdAndProjectId_NotFound() {
        List<UserProjectSkill> result = dataAccess.findByUserIdAndProjectId(
                UUID.randomUUID(), testProject.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should find by project id")
    void testFindByProjectId() {
        repository.save(createUserProjectSkill());

        List<UserProjectSkill> result = dataAccess.findByProjectId(testProject.getId());

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should find by user id")
    void testFindByUserId() {
        repository.save(createUserProjectSkill());

        List<UserProjectSkill> result = dataAccess.findByUserId(testUser.getId());

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should find by user id, project id and skill id")
    void testFindByUserIdAndProjectIdAndSkillId() {
        repository.save(createUserProjectSkill());

        Optional<UserProjectSkill> result = dataAccess.findByUserIdAndProjectIdAndSkillId(
                testUser.getId(), testProject.getId(), testSkill.getId());

        assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("Should check existence by user id and project id")
    void testExistsByUserIdAndProjectId() {
        repository.save(createUserProjectSkill());

        assertTrue(dataAccess.existsByUserIdAndProjectId(testUser.getId(), testProject.getId()));
        assertFalse(dataAccess.existsByUserIdAndProjectId(UUID.randomUUID(), testProject.getId()));
    }

    @Test
    @DisplayName("Should check existence by user id, project id and skill id")
    void testExistsByUserIdAndProjectIdAndSkillId() {
        repository.save(createUserProjectSkill());

        assertTrue(dataAccess.existsByUserIdAndProjectIdAndSkillId(
                testUser.getId(), testProject.getId(), testSkill.getId()));
        assertFalse(dataAccess.existsByUserIdAndProjectIdAndSkillId(
                UUID.randomUUID(), testProject.getId(), testSkill.getId()));
    }

    @Test
    @DisplayName("Should delete by project id")
    void testDeleteByProjectId() {
        repository.save(createUserProjectSkill());

        dataAccess.deleteByProjectId(testProject.getId());

        assertEquals(0, repository.findAll().size());
    }

    @Test
    @DisplayName("Should delete by user id and project id")
    void testDeleteByUserIdAndProjectId() {
        repository.save(createUserProjectSkill());

        dataAccess.deleteByUserIdAndProjectId(testUser.getId(), testProject.getId());

        assertEquals(0, repository.findAll().size());
    }

    @Test
    @DisplayName("Should delete by user id, project id and skill id")
    void testDeleteByUserIdAndProjectIdAndSkillId() {
        repository.save(createUserProjectSkill());

        dataAccess.deleteByUserIdAndProjectIdAndSkillId(
                testUser.getId(), testProject.getId(), testSkill.getId());

        assertEquals(0, repository.findAll().size());
    }

    @Test
    @DisplayName("Should delete by skill id")
    void testDeleteBySkillId() {
        repository.save(createUserProjectSkill());

        dataAccess.deleteBySkillId(testSkill.getId());

        assertEquals(0, repository.findAll().size());
    }

    @Test
    @DisplayName("Should check existence by skill id")
    void testExistsBySkillId() {
        repository.save(createUserProjectSkill());

        assertTrue(dataAccess.existsBySkillId(testSkill.getId()));
        assertFalse(dataAccess.existsBySkillId(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Should check existence by skill level id")
    void testExistsBySkillLevelId() {
        repository.save(createUserProjectSkill());

        assertTrue(dataAccess.existsBySkillLevelId(testSkillLevel.getId()));
        assertFalse(dataAccess.existsBySkillLevelId(UUID.randomUUID()));
    }
}
