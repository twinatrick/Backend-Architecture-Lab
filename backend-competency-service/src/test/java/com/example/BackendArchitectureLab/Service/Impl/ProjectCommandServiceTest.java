package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.PersonalProjectRequest;
import com.example.BackendArchitectureLab.Vo.ProjectVo;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.Entity.UserProject;
import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IProjectSkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.Mapper.ProjectMapper;
import com.example.BackendArchitectureLab.Service.IProjectUserBindingService;
import com.example.BackendArchitectureLab.Util.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectCommandServiceTest {

    @Mock
    private IProjectDataAccess projectDataAccess;
    @Mock
    private IProjectSkillDataAccess projectSkillDataAccess;
    @Mock
    private IUserProjectDataAccess userProjectDataAccess;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private SecurityUtil securityUtil;
    @Mock
    private IProjectUserBindingService projectUserBindingService;

    @InjectMocks
    private ProjectCommandService projectCommandService;

    private Project testProject;
    private ProjectVo testProjectVo;
    private UUID testId;

    @BeforeEach
    void setUp() {
        // Inject self reference（自我代理：交易外驗證後呼叫自身交易方法）
        try {
            Field selfField = ProjectCommandService.class.getDeclaredField("self");
            selfField.setAccessible(true);
            selfField.set(projectCommandService, projectCommandService);
        } catch (Exception e) {
            throw new RuntimeException("Could not inject self into ProjectCommandService", e);
        }

        testId = UUID.randomUUID();
        testProject = new Project();
        testProject.setId(testId);
        testProject.setName("Test Project");
        testProject.setDescription("Test Description");

        testProjectVo = new ProjectVo();
        testProjectVo.setId(testId);
        testProjectVo.setName("Test Project");
        testProjectVo.setDescription("Test Description");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setupSecurityContext(UUID userId, String email) {
        lenient().when(securityUtil.requireCurrentUserId()).thenReturn(userId);
    }

    @Test
    void addProject_shouldThrow_whenNameExists() {
        ProjectVo vo = new ProjectVo();
        vo.setName("Demo");

        Project entity = new Project();
        entity.setName("Demo");

        when(projectMapper.toEntity(vo)).thenReturn(entity);
        when(projectDataAccess.findByName("Demo")).thenReturn(List.of(new Project()));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> projectCommandService.addProject(vo));
        assertEquals("Name already exists", exception.getMessage());
    }

    @Test
    void deleteProject_shouldDeleteMappingsAndProject() {
        UUID projectId = UUID.randomUUID();

        ProjectVo vo = new ProjectVo();
        vo.setId(projectId);
        Project mapped = new Project();
        mapped.setId(projectId);
        Project existing = new Project();
        existing.setId(projectId);

        when(projectMapper.toEntity(vo)).thenReturn(mapped);
        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(existing));

        projectCommandService.deleteProject(vo);

        verify(projectSkillDataAccess).deleteByProjectId(projectId);
        verify(userProjectDataAccess).deleteByProjectId(projectId);
        verify(projectDataAccess).deleteById(projectId);
    }

    @Test
    void updateProject_shouldSave_whenValid() {
        UUID projectId = UUID.randomUUID();

        ProjectVo vo = new ProjectVo();
        vo.setId(projectId);
        vo.setName("Updated");

        Project mapped = new Project();
        mapped.setId(projectId);
        mapped.setName("Updated");

        when(projectMapper.toEntity(vo)).thenReturn(mapped);

        projectCommandService.updateProject(vo);

        verify(projectDataAccess).save(any(Project.class));
    }

    // ========== 管理者介面測試 ==========

    @Test
    void addProject_shouldBindUsers_whenUserIdsProvided() {
        // Arrange
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        Project newProject = new Project();
        newProject.setName("Java Project");
        newProject.setDescription("Java Project Description");

        ProjectVo projectVo = new ProjectVo();
        projectVo.setName("Java Project");
        projectVo.setDescription("Java Project Description");
        projectVo.setUserIds(List.of(userId1.toString(), userId2.toString()));

        when(projectMapper.toEntity(projectVo)).thenReturn(newProject);
        when(projectDataAccess.findByName("Java Project")).thenReturn(Collections.emptyList());
        when(projectDataAccess.save(newProject)).thenReturn(testProject);
        when(projectMapper.toVo(testProject)).thenReturn(projectVo);

        // Act
        ProjectVo result = projectCommandService.addProject(projectVo);

        // Assert
        assertNotNull(result);
        verify(projectUserBindingService).validateUsersExist(any());
        verify(projectUserBindingService).bindUsersToProject(testProject.getId(), List.of(userId1.toString(), userId2.toString()));
        verify(projectUserBindingService, times(2)).evictUserProjectsCache(any());
    }

    @Test
    void addProject_shouldThrow_whenInvalidUserId() {
        // Arrange
        UUID invalidUserId = UUID.randomUUID();

        ProjectVo projectVo = new ProjectVo();
        projectVo.setName("Java Project");
        projectVo.setDescription("Java Project Description");
        projectVo.setUserIds(List.of(invalidUserId.toString()));

        doThrow(new IllegalArgumentException("User not found: " + invalidUserId))
                .when(projectUserBindingService).validateUsersExist(any());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                projectCommandService.addProject(projectVo)
        );
        assertTrue(exception.getMessage().contains("User not found"));
    }

    @Test
    void addProject_shouldNotBindUsers_whenUserIdsNull() {
        // Arrange
        Project newProject = new Project();
        newProject.setName("Java Project");
        newProject.setDescription("Java Project Description");

        ProjectVo projectVo = new ProjectVo();
        projectVo.setName("Java Project");
        projectVo.setDescription("Java Project Description");
        projectVo.setUserIds(null);

        when(projectMapper.toEntity(projectVo)).thenReturn(newProject);
        when(projectDataAccess.findByName("Java Project")).thenReturn(Collections.emptyList());
        when(projectDataAccess.save(newProject)).thenReturn(testProject);
        when(projectMapper.toVo(testProject)).thenReturn(projectVo);

        // Act
        ProjectVo result = projectCommandService.addProject(projectVo);

        // Assert
        assertNotNull(result);
        verify(projectUserBindingService, never()).bindUsersToProject(any(), any());
    }

    @Test
    void addProject_shouldNotBindUsers_whenUserIdsEmpty() {
        // Arrange
        Project newProject = new Project();
        newProject.setName("Java Project");
        newProject.setDescription("Java Project Description");

        ProjectVo projectVo = new ProjectVo();
        projectVo.setName("Java Project");
        projectVo.setDescription("Java Project Description");
        projectVo.setUserIds(List.of());

        when(projectMapper.toEntity(projectVo)).thenReturn(newProject);
        when(projectDataAccess.findByName("Java Project")).thenReturn(Collections.emptyList());
        when(projectDataAccess.save(newProject)).thenReturn(testProject);
        when(projectMapper.toVo(testProject)).thenReturn(projectVo);

        // Act
        ProjectVo result = projectCommandService.addProject(projectVo);

        // Assert
        assertNotNull(result);
        verify(projectUserBindingService, never()).bindUsersToProject(any(), any());
    }

    @Test
    void updateProject_shouldRebindUsers_whenUserIdsProvided() {
        // Arrange
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        ProjectVo projectVo = new ProjectVo();
        projectVo.setId(testId);
        projectVo.setName("Updated Project");
        projectVo.setDescription("Updated Description");
        projectVo.setUserIds(List.of(userId2.toString()));

        when(projectMapper.toEntity(projectVo)).thenReturn(testProject);

        // Act
        projectCommandService.updateProject(projectVo);

        // Assert
        verify(projectUserBindingService).validateUsersExist(any());
        verify(userProjectDataAccess).deleteByProjectId(testId);
        verify(projectUserBindingService).bindUsersToProject(testId, List.of(userId2.toString()));
    }

    @Test
    void updateProject_shouldRemoveAllBindings_whenEmptyUserIds() {
        // Arrange
        ProjectVo projectVo = new ProjectVo();
        projectVo.setId(testId);
        projectVo.setName("Updated Project");
        projectVo.setDescription("Updated Description");
        projectVo.setUserIds(List.of());

        when(projectMapper.toEntity(projectVo)).thenReturn(testProject);

        // Act
        projectCommandService.updateProject(projectVo);

        // Assert
        verify(userProjectDataAccess).deleteByProjectId(testId);
        verify(projectUserBindingService, never()).bindUsersToProject(any(), any());
    }

    @Test
    void updateProject_shouldNotRebind_whenUserIdsNull() {
        // Arrange
        ProjectVo projectVo = new ProjectVo();
        projectVo.setId(testId);
        projectVo.setName("Updated Project");
        projectVo.setDescription("Updated Description");
        projectVo.setUserIds(null);

        when(projectMapper.toEntity(projectVo)).thenReturn(testProject);

        // Act
        projectCommandService.updateProject(projectVo);

        // Assert
        verify(userProjectDataAccess, never()).deleteByProjectId(any());
        verify(projectUserBindingService, never()).bindUsersToProject(any(), any());
    }

    // Personal Project Tests

    @Test
    void addPersonalProject_should_whenValid() {
        // Arrange
        UUID userId = UUID.randomUUID();
        setupSecurityContext(userId, "test@example.com");

        PersonalProjectRequest request = new PersonalProjectRequest();
        request.setName("Personal Project");
        request.setDescription("Personal Description");

        when(projectDataAccess.findByName("Personal Project")).thenReturn(Collections.emptyList());
        when(projectDataAccess.save(any(Project.class))).thenReturn(testProject);
        when(projectMapper.toVo(testProject)).thenReturn(testProjectVo);

        // Act
        ProjectVo result = projectCommandService.addPersonalProject(request);

        // Assert
        assertNotNull(result);
        verify(projectDataAccess).save(any(Project.class));
        verify(userProjectDataAccess).save(any(UserProject.class));
        verify(projectUserBindingService).evictUserProjectsCache(userId);
    }

    @Test
    void addPersonalProject_shouldThrow_whenNameIsNull() {
        // Arrange
        PersonalProjectRequest request = new PersonalProjectRequest();
        request.setName(null);
        request.setDescription("Description");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectCommandService.addPersonalProject(request));
        assertEquals("Name must not be null", exception.getMessage());
    }

    @Test
    void addPersonalProject_shouldThrow_whenNameIsEmpty() {
        // Arrange
        PersonalProjectRequest request = new PersonalProjectRequest();
        request.setName("   ");
        request.setDescription("Description");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectCommandService.addPersonalProject(request));
        assertEquals("Name must not be null", exception.getMessage());
    }

    @Test
    void addPersonalProject_shouldThrow_whenNameExists() {
        // Arrange
        PersonalProjectRequest request = new PersonalProjectRequest();
        request.setName("Existing Project");
        request.setDescription("Description");

        when(projectDataAccess.findByName("Existing Project")).thenReturn(List.of(new Project()));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectCommandService.addPersonalProject(request));
        assertEquals("Name already exists", exception.getMessage());
    }

    @Test
    void updatePersonalProject_should_whenValid() {
        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        setupSecurityContext(userId, "test@example.com");

        PersonalProjectRequest request = new PersonalProjectRequest();
        request.setName("Updated Project");
        request.setDescription("Updated Description");

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(testProject));
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(true);

        // Act
        projectCommandService.updatePersonalProject(projectId, request);

        // Assert
        verify(projectDataAccess).save(testProject);
        verify(projectUserBindingService).evictUserProjectsCache(userId);
        assertEquals("Updated Project", testProject.getName());
        assertEquals("Updated Description", testProject.getDescription());
    }

    @Test
    void updatePersonalProject_shouldThrow_whenProjectIdIsNull() {
        // Arrange
        PersonalProjectRequest request = new PersonalProjectRequest();
        request.setName("Updated Project");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectCommandService.updatePersonalProject(null, request));
        assertEquals("Project ID must not be null", exception.getMessage());
    }

    @Test
    void updatePersonalProject_shouldThrow_whenProjectNotFound() {
        // Arrange
        UUID projectId = UUID.randomUUID();
        PersonalProjectRequest request = new PersonalProjectRequest();
        request.setName("Updated Project");

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectCommandService.updatePersonalProject(projectId, request));
        assertEquals("Project not found", exception.getMessage());
    }

    @Test
    void updatePersonalProject_shouldThrow_whenNotOwner() {
        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        setupSecurityContext(userId, "test@example.com");

        PersonalProjectRequest request = new PersonalProjectRequest();
        request.setName("Updated Project");

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(testProject));
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(false);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectCommandService.updatePersonalProject(projectId, request));
        assertEquals("You are not the owner of this project", exception.getMessage());
    }

    @Test
    void updatePersonalProject_shouldThrow_whenAssignedByAdminReadOnly() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        setupSecurityContext(userId, "test@example.com");

        testProject.setCreatedBy(UUID.randomUUID().toString());
        PersonalProjectRequest request = new PersonalProjectRequest();
        request.setName("Updated Project");

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(testProject));
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectCommandService.updatePersonalProject(projectId, request));
        assertEquals("Project assigned by admin is read-only", exception.getMessage());
    }

    @Test
    void deletePersonalProject_should_whenValidAndHasOtherBindings() {
        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        setupSecurityContext(userId, "test@example.com");

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(testProject));
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(true);
        when(userProjectDataAccess.existsByProjectId(projectId)).thenReturn(true);

        // Act
        projectCommandService.deletePersonalProject(projectId);

        // Assert
        verify(userProjectDataAccess).deleteByUserIdAndProjectId(userId, projectId);
        verify(projectUserBindingService).evictUserProjectsCache(userId);
        verify(projectDataAccess, never()).deleteById(projectId);
        verify(projectSkillDataAccess, never()).deleteByProjectId(projectId);
    }

    @Test
    void deletePersonalProject_should_whenValidAndNoOtherBindings() {
        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        setupSecurityContext(userId, "test@example.com");

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(testProject));
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(true);
        when(userProjectDataAccess.existsByProjectId(projectId)).thenReturn(false);

        // Act
        projectCommandService.deletePersonalProject(projectId);

        // Assert
        verify(userProjectDataAccess).deleteByUserIdAndProjectId(userId, projectId);
        verify(projectSkillDataAccess).deleteByProjectId(projectId);
        verify(projectDataAccess).deleteById(projectId);
    }
}
