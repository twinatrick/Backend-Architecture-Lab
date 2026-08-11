package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.Repository.UserProjectRepository;
import com.example.BackendArchitectureLab.Repository.ProjectRepository;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.Entity.UserProject;
import com.example.BackendArchitectureLab.Entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for UserProjectDataAccessImpl.
 * Uses in-memory H2 database for testing.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(UserProjectDataAccessImpl.class)
class UserProjectDataAccessImplTest {

    @Autowired
    private UserProjectRepository userProjectRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private IUserProjectDataAccess userProjectDataAccess;

    @BeforeEach
    void setUp() {
        userProjectRepository.deleteAll();
        projectRepository.deleteAll();
    }

    @Test
    @DisplayName("應該檢查 UserProject 是否存在（依 userId 和 projectId）")
    void testExistsByUserIdAndProjectId() {
        // Arrange
        UUID userId = UUID.randomUUID();

        Project project = new Project();
        project.setName("測試專案");
        projectRepository.save(project);

        UserProject userProject = new UserProject();
        userProject.setUserId(userId);
        userProject.setProject(project);
        userProjectRepository.save(userProject);

        // Act & Assert
        assertTrue(userProjectDataAccess.existsByUserIdAndProjectId(userId, project.getId()));
        assertFalse(userProjectDataAccess.existsByUserIdAndProjectId(UUID.randomUUID(), project.getId()));
    }

    @Test
    @DisplayName("應該保存 UserProject")
    void testSave() {
        // Arrange
        UUID userId = UUID.randomUUID();

        Project project = new Project();
        project.setName("測試專案");
        projectRepository.save(project);

        UserProject userProject = new UserProject();
        userProject.setUserId(userId);
        userProject.setProject(project);

        // Act
        UserProject saved = userProjectDataAccess.save(userProject);

        // Assert
        assertNotNull(saved.getId());
        assertEquals(userId, saved.getUserId());
        assertEquals(project.getId(), saved.getProject().getId());
    }

    @Test
    @DisplayName("應該根據 projectId 刪除所有 UserProject")
    void testDeleteByProjectId() {
        // Arrange
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        Project project = new Project();
        project.setName("測試專案");
        projectRepository.save(project);

        UserProject userProject1 = new UserProject();
        userProject1.setUserId(userId1);
        userProject1.setProject(project);
        userProjectRepository.save(userProject1);

        UserProject userProject2 = new UserProject();
        userProject2.setUserId(userId2);
        userProject2.setProject(project);
        userProjectRepository.save(userProject2);

        assertEquals(2, userProjectRepository.count());

        // Act
        userProjectDataAccess.deleteByProjectId(project.getId());

        // Assert
        assertEquals(0, userProjectRepository.count());
    }

    @Test
    @DisplayName("應該根據 userId 查詢所有 UserProject")
    void testFindByUserId() {
        // Arrange
        UUID userId = UUID.randomUUID();

        Project project1 = new Project();
        project1.setName("專案1");
        projectRepository.save(project1);

        Project project2 = new Project();
        project2.setName("專案2");
        projectRepository.save(project2);

        UserProject userProject1 = new UserProject();
        userProject1.setUserId(userId);
        userProject1.setProject(project1);
        userProjectRepository.save(userProject1);

        UserProject userProject2 = new UserProject();
        userProject2.setUserId(userId);
        userProject2.setProject(project2);
        userProjectRepository.save(userProject2);

        // Act
        List<UserProject> result = userProjectDataAccess.findByUserId(userId);

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(up -> up.getProject().getName().equals("專案1")));
        assertTrue(result.stream().anyMatch(up -> up.getProject().getName().equals("專案2")));
    }

    @Test
    @DisplayName("當 userId 不存在時應該返回空列表")
    void testFindByUserId_NotFound() {
        // Act
        List<UserProject> result = userProjectDataAccess.findByUserId(UUID.randomUUID());

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("應該檢查是否存在使用指定 Project 的 UserProject")
    void testExistsByProjectId() {
        UUID userId = UUID.randomUUID();

        Project project = new Project();
        project.setName("測試專案");
        projectRepository.save(project);

        UserProject userProject = new UserProject();
        userProject.setUserId(userId);
        userProject.setProject(project);
        userProjectRepository.save(userProject);

        assertTrue(userProjectDataAccess.existsByProjectId(project.getId()));
        assertFalse(userProjectDataAccess.existsByProjectId(UUID.randomUUID()));
    }

    @Test
    @DisplayName("應該根據 userId 和 projectId 刪除 UserProject")
    void testDeleteByUserIdAndProjectId() {
        UUID userId = UUID.randomUUID();

        Project project = new Project();
        project.setName("測試專案");
        projectRepository.save(project);

        UserProject userProject = new UserProject();
        userProject.setUserId(userId);
        userProject.setProject(project);
        userProjectRepository.save(userProject);

        assertEquals(1, userProjectRepository.count());

        userProjectDataAccess.deleteByUserIdAndProjectId(userId, project.getId());

        assertEquals(0, userProjectRepository.count());
    }

    @Test
    @DisplayName("應該根據 projectId 查詢所有 UserProject")
    void testFindByProjectId() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        Project project = new Project();
        project.setName("測試專案");
        projectRepository.save(project);

        UserProject up1 = new UserProject();
        up1.setUserId(userId1);
        up1.setProject(project);
        userProjectRepository.save(up1);

        UserProject up2 = new UserProject();
        up2.setUserId(userId2);
        up2.setProject(project);
        userProjectRepository.save(up2);

        List<UserProject> result = userProjectDataAccess.findByProjectId(project.getId());

        assertEquals(2, result.size());
    }
}
