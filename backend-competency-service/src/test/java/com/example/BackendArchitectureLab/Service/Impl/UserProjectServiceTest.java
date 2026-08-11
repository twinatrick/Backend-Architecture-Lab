package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.Entity.UserProject;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserProjectService.
 * Covers the full-coverage rebind strategy for user-project relations.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProjectServiceTest {

    @Mock
    private IUserProjectDataAccess userProjectDataAccess;
    @Mock
    private IProjectDataAccess projectDataAccess;
    @Mock
    private UserServiceFeignClient userServiceFeignClient;

    @InjectMocks
    private UserProjectService userProjectService;

    private UUID userId;
    private UUID projectId1;
    private UUID projectId2;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        projectId1 = UUID.randomUUID();
        projectId2 = UUID.randomUUID();

        // Inject self reference
        try {
            Field selfField = UserProjectService.class.getDeclaredField("self");
            selfField.setAccessible(true);
            selfField.set(userProjectService, userProjectService);
        } catch (Exception e) {
            throw new RuntimeException("Could not inject self into UserProjectService", e);
        }
    }

    private UserProject userProject(UUID projectId) {
        Project project = new Project();
        project.setId(projectId);
        UserProject userProject = new UserProject();
        userProject.setUserId(userId);
        userProject.setProject(project);
        return userProject;
    }

    @Test
    @DisplayName("Should rebind user projects with diff strategy")
    void testRebindUserProjects_Success() {
        // Arrange
        when(userServiceFeignClient.existsUserById(userId)).thenReturn(true);
        when(userProjectDataAccess.findByUserId(userId)).thenReturn(List.of(userProject(projectId1)));
        Project project2 = new Project();
        project2.setId(projectId2);
        when(projectDataAccess.findById(projectId2)).thenReturn(Optional.of(project2));

        // Act
        userProjectService.rebindUserProjects(userId, List.of(projectId1, projectId2));

        // Assert
        verify(userServiceFeignClient).existsUserById(userId);
        verify(userProjectDataAccess, never()).deleteByUserIdAndProjectId(any(), any());
        verify(userProjectDataAccess).save(any(UserProject.class));
    }

    @Test
    @DisplayName("Should throw Exception when userId is null")
    void testRebindUserProjects_NullUserId() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userProjectService.rebindUserProjects(null, List.of(projectId1)));
        assertEquals("Key must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw Exception when user not found")
    void testRebindUserProjects_UserNotFound() {
        // Arrange
        when(userServiceFeignClient.existsUserById(userId)).thenReturn(false);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userProjectService.rebindUserProjects(userId, List.of(projectId1)));
        assertEquals("User not found", exception.getMessage());
        verify(userProjectDataAccess, never()).save(any());
    }

    @Test
    @DisplayName("Should delete all existing relations when target is empty")
    void testRebindUserProjects_EmptyProjectList() {
        // Arrange
        when(userServiceFeignClient.existsUserById(userId)).thenReturn(true);
        when(userProjectDataAccess.findByUserId(userId)).thenReturn(List.of(userProject(projectId1)));

        // Act
        userProjectService.rebindUserProjects(userId, List.of());

        // Assert
        verify(userProjectDataAccess).deleteByUserIdAndProjectId(userId, projectId1);
        verify(userProjectDataAccess, never()).save(any());
    }

    @Test
    @DisplayName("Should throw Exception when target project not found")
    void testRebindUserProjects_ProjectNotFound() {
        // Arrange
        when(userServiceFeignClient.existsUserById(userId)).thenReturn(true);
        when(userProjectDataAccess.findByUserId(userId)).thenReturn(List.of());
        when(projectDataAccess.findById(projectId1)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userProjectService.rebindUserProjects(userId, List.of(projectId1)));
        assertEquals("Project not found", exception.getMessage());
    }

    @Test
    @DisplayName("Should skip null project ids in target list")
    void testRebindUserProjects_SkipNullProjectIds() {
        // Arrange
        when(userServiceFeignClient.existsUserById(userId)).thenReturn(true);
        when(userProjectDataAccess.findByUserId(userId)).thenReturn(List.of());
        Project project1Entity = new Project();
        project1Entity.setId(projectId1);
        when(projectDataAccess.findById(projectId1)).thenReturn(Optional.of(project1Entity));

        // Act
        userProjectService.rebindUserProjects(userId, Arrays.asList(null, projectId1, null));

        // Assert
        verify(userProjectDataAccess).save(any(UserProject.class));
    }
}