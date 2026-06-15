package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.PersonalProjectRequest;
import com.example.BackendArchitectureLab.Dto.Vo.ProjectMemberSkillVo;
import com.example.BackendArchitectureLab.Dto.Vo.ProjectVo;
import com.example.BackendArchitectureLab.Dto.Vo.Search.ProjectSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.Entity.ProjectSkill;
import com.example.BackendArchitectureLab.Entity.Skill;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Entity.UserProject;
import com.example.BackendArchitectureLab.Entity.UserProjectSkill;
import com.example.BackendArchitectureLab.Entity.UserSkill;
import com.example.BackendArchitectureLab.Service.impl.ProjectService;
import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IProjectSkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillLevelDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectSkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserSkillDataAccess;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import com.example.BackendArchitectureLab.Mapper.ProjectMapper;
import com.example.BackendArchitectureLab.Dto.Vo.UserVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

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
    private IUserProjectSkillDataAccess userProjectSkillDataAccess;
    @Mock
    private ProjectMapper projectMapper;

    @InjectMocks
    private ProjectService projectService;

    @Mock
    private UserServiceFeignClient userServiceFeignClient;

    private Project testProject;
    private ProjectVo testProjectVo;
    private UUID testId;

    @BeforeEach
    void setUp() {
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
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(email);
        lenient().when(auth.getPrincipal()).thenReturn(auth);
        SecurityContextHolder.getContext().setAuthentication(auth);
        UserVo userVo = new UserVo();
        userVo.setId(userId.toString());
        lenient().when(userServiceFeignClient.getUserByEmail(email)).thenReturn(userVo);
    }

    @Test
    void addProject_shouldThrow_whenNameExists() {
        ProjectVo vo = new ProjectVo();
        vo.setName("Demo");

        Project entity = new Project();
        entity.setName("Demo");

        when(projectMapper.toEntity(vo)).thenReturn(entity);
        when(projectDataAccess.findByName("Demo")).thenReturn(List.of(new Project()));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> projectService.addProject(vo));
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

        projectService.deleteProject(vo);

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

        projectService.updateProject(vo);

        verify(projectDataAccess).save(any(Project.class));
    }

    @Test
    void testSearchProjects_Success() {
        // Arrange
        ProjectSearchQuery query = new ProjectSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("createdTime");
        query.setSortDir("desc");
        query.setName("Test");

        List<Project> projects = List.of(testProject);
        Page<Project> projectPage = new PageImpl<>(projects, PageRequest.of(0, 20), 1);

        when(projectDataAccess.searchProjects(any(ProjectSearchQuery.class))).thenReturn(projectPage);
        when(projectMapper.toVo(testProject)).thenReturn(testProjectVo);

        // Act
        PageResult<ProjectVo> result = projectService.searchProjects(query);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getTotalElements());
        assertEquals("Test Project", result.getContent().get(0).getName());
        verify(projectDataAccess).searchProjects(any(ProjectSearchQuery.class));
    }

    @Test
    void testSearchProjects_InvalidSortField() {
        // Arrange
        ProjectSearchQuery query = new ProjectSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("invalidField");
        query.setSortDir("desc");

        // Act & Assert
        assertThrows(AppException.class, () -> projectService.searchProjects(query));
    }

    @Test
    void testSearchProjects_InvalidSortDirection() {
        // Arrange
        ProjectSearchQuery query = new ProjectSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("createdTime");
        query.setSortDir("invalid");

        // Act & Assert
        assertThrows(AppException.class, () -> projectService.searchProjects(query));
    }

    @Test
    void testGetCurrentUserProjects_Success() {
        // Arrange
        UUID userId = UUID.randomUUID();
        setupSecurityContext(userId, "test@example.com");

        UserProject userProject = new UserProject();
        userProject.setProject(testProject);

        when(userProjectDataAccess.findByUserId(userId)).thenReturn(List.of(userProject));
        when(projectMapper.toVo(testProject)).thenReturn(testProjectVo);

        // Act
        List<ProjectVo> result = projectService.getCurrentUserProjects();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test Project", result.get(0).getName());
        verify(userProjectDataAccess).findByUserId(userId);
    }

    @Test
    void testSearchCurrentUserProjects_Success() {
        // Arrange
        UUID userId = UUID.randomUUID();
        setupSecurityContext(userId, "test@example.com");

        ProjectSearchQuery query = new ProjectSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("createdTime");
        query.setSortDir("desc");
        query.setName("Test");

        List<Project> projects = List.of(testProject);
        Page<Project> projectPage = new PageImpl<>(projects, PageRequest.of(0, 20), 1);

        when(projectDataAccess.searchCurrentUserProjects(any(String.class), any(ProjectSearchQuery.class)))
                .thenReturn(projectPage);
        when(projectMapper.toVo(testProject)).thenReturn(testProjectVo);

        // Act
        PageResult<ProjectVo> result = projectService.searchCurrentUserProjects(query);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getTotalElements());
        assertEquals("Test Project", result.getContent().get(0).getName());
        verify(projectDataAccess).searchCurrentUserProjects(any(String.class), any(ProjectSearchQuery.class));
    }

    @Test
    void testSearchCurrentUserProjects_EmptyResult() {
        // Arrange
        UUID userId = UUID.randomUUID();
        setupSecurityContext(userId, "test@example.com");

        ProjectSearchQuery query = new ProjectSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("createdTime");
        query.setSortDir("desc");

        Page<Project> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);

        when(projectDataAccess.searchCurrentUserProjects(any(String.class), any(ProjectSearchQuery.class)))
                .thenReturn(emptyPage);

        // Act
        PageResult<ProjectVo> result = projectService.searchCurrentUserProjects(query);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getContent().size());
        assertEquals(0L, result.getTotalElements());
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
        ProjectVo result = projectService.addPersonalProject(request);
        
        // Assert
        assertNotNull(result);
        verify(projectDataAccess).save(any(Project.class));
        verify(userProjectDataAccess).save(any(UserProject.class));
    }
    
    @Test
    void addPersonalProject_shouldThrow_whenNameIsNull() {
        // Arrange
        PersonalProjectRequest request = new PersonalProjectRequest();
        request.setName(null);
        request.setDescription("Description");
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectService.addPersonalProject(request));
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
                () -> projectService.addPersonalProject(request));
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
                () -> projectService.addPersonalProject(request));
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
        projectService.updatePersonalProject(projectId, request);
        
        // Assert
        verify(projectDataAccess).save(testProject);
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
                () -> projectService.updatePersonalProject(null, request));
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
                () -> projectService.updatePersonalProject(projectId, request));
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
                () -> projectService.updatePersonalProject(projectId, request));
        assertEquals("You are not the owner of this project", exception.getMessage());
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
        projectService.deletePersonalProject(projectId);
        
        // Assert
        verify(userProjectDataAccess).deleteByUserIdAndProjectId(userId, projectId);
        verify(projectDataAccess, never()).delete(any(Project.class));
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
        projectService.deletePersonalProject(projectId);
        
        // Assert
        verify(userProjectDataAccess).deleteByUserIdAndProjectId(userId, projectId);
        verify(projectSkillDataAccess).deleteByProjectId(projectId);
        verify(projectDataAccess).deleteById(projectId);
    }
    
    // ========== 管理者介面測試 ==========
    
    @Test
    void addProject_shouldBindUsers_whenUserIdsProvided() {
        // Arrange
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        User user1 = new User();
        user1.setId(userId1);
        User user2 = new User();
        user2.setId(userId2);
        
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
        when(projectDataAccess.findById(testProject.getId())).thenReturn(Optional.of(testProject));
        when(userServiceFeignClient.existsUserById(userId1)).thenReturn(true);
        when(userServiceFeignClient.existsUserById(userId2)).thenReturn(true);
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId1, testProject.getId())).thenReturn(false);
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId2, testProject.getId())).thenReturn(false);
        when(projectMapper.toVo(testProject)).thenReturn(projectVo);
        
        // Act
        ProjectVo result = projectService.addProject(projectVo);
        
        // Assert
        assertNotNull(result);
        verify(userProjectDataAccess, times(2)).save(any(UserProject.class));
    }
    
    @Test
    void addProject_shouldThrow_whenInvalidUserId() {
        // Arrange
        UUID invalidUserId = UUID.randomUUID();
        
        Project newProject = new Project();
        newProject.setName("Java Project");
        newProject.setDescription("Java Project Description");
        
        ProjectVo projectVo = new ProjectVo();
        projectVo.setName("Java Project");
        projectVo.setDescription("Java Project Description");
        projectVo.setUserIds(List.of(invalidUserId.toString()));
        
        when(projectMapper.toEntity(projectVo)).thenReturn(newProject);
        when(projectDataAccess.findByName("Java Project")).thenReturn(Collections.emptyList());
        when(projectDataAccess.save(newProject)).thenReturn(testProject);
        when(projectDataAccess.findById(testProject.getId())).thenReturn(Optional.of(testProject));
        when(userServiceFeignClient.existsUserById(invalidUserId)).thenReturn(false);
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                projectService.addProject(projectVo)
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
        ProjectVo result = projectService.addProject(projectVo);
        
        // Assert
        assertNotNull(result);
        verify(userProjectDataAccess, never()).save(any(UserProject.class));
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
        ProjectVo result = projectService.addProject(projectVo);
        
        // Assert
        assertNotNull(result);
        verify(userProjectDataAccess, never()).save(any(UserProject.class));
    }
    
    @Test
    void updateProject_shouldRebindUsers_whenUserIdsProvided() {
        // Arrange
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        User user1 = new User();
        user1.setId(userId1);
        User user2 = new User();
        user2.setId(userId2);
        
        ProjectVo projectVo = new ProjectVo();
        projectVo.setId(testId);
        projectVo.setName("Updated Project");
        projectVo.setDescription("Updated Description");
        projectVo.setUserIds(List.of(userId2.toString()));
        
        when(projectMapper.toEntity(projectVo)).thenReturn(testProject);
        when(projectDataAccess.findById(testId)).thenReturn(Optional.of(testProject));
        when(userServiceFeignClient.existsUserById(userId2)).thenReturn(true);
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId2, testId)).thenReturn(false);
        
        // Act
        projectService.updateProject(projectVo);
        
        // Assert
        verify(userProjectDataAccess).deleteByProjectId(testId);
        verify(userProjectDataAccess).save(any(UserProject.class));
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
        projectService.updateProject(projectVo);
        
        // Assert
        verify(userProjectDataAccess).deleteByProjectId(testId);
        verify(userProjectDataAccess, never()).save(any(UserProject.class));
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
        projectService.updateProject(projectVo);
        
        // Assert
        verify(userProjectDataAccess, never()).deleteByProjectId(any());
        verify(userProjectDataAccess, never()).save(any(UserProject.class));
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
                () -> projectService.updatePersonalProject(projectId, request));
        assertEquals("Project assigned by admin is read-only", exception.getMessage());
    }

    @Test
    void bindPersonalProjectSkill_shouldBind_whenVisibleAndAllowed() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        setupSecurityContext(userId, "test@example.com");
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

        projectService.bindPersonalProjectSkill(projectId, skillId, levelId);

        verify(projectSkillDataAccess).save(any(ProjectSkill.class));
    }

    @Test
    void bindPersonalProjectSkill_shouldThrow_whenSkillNotVisible() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        setupSecurityContext(userId, "test@example.com");
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(true);
        when(userSkillDataAccess.findByUserId(userId)).thenReturn(List.of());
        when(userProjectDataAccess.findByUserId(userId)).thenReturn(List.of());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectService.bindPersonalProjectSkill(projectId, skillId, levelId));
        assertEquals("Skill is not visible to current user", exception.getMessage());
    }

    @Test
    void updatePersonalProjectSkillLevel_shouldUpdate_whenBindingExists() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        setupSecurityContext(userId, "test@example.com");
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

        projectService.updatePersonalProjectSkillLevel(projectId, skillId, levelId);

        verify(projectSkillDataAccess).save(projectSkill);
        assertEquals(level, projectSkill.getSkillLevel());
    }

    @Test
    void unbindPersonalProjectSkill_shouldDelete_whenBindingExists() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();

        setupSecurityContext(userId, "test@example.com");
        when(userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)).thenReturn(true);
        when(projectSkillDataAccess.existsByProjectIdAndSkillId(projectId, skillId)).thenReturn(true);

        projectService.unbindPersonalProjectSkill(projectId, skillId);

        verify(projectSkillDataAccess).deleteByProjectIdAndSkillId(projectId, skillId);
    }

    @Test
    void getProjectMemberSkills_shouldThrow_whenProjectNotFound() {
        UUID projectId = UUID.randomUUID();
        when(projectDataAccess.existsById(projectId)).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> projectService.getProjectMemberSkills(projectId));
        assertEquals("Project not found", exception.getMessage());
    }

    @Test
    void getProjectMemberSkills_shouldReturnMembersWithSkills() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);
        user.setEmail("member@example.com");

        UserProject userProject = new UserProject();
        userProject.setUser(user);

        Skill skill = new Skill();
        skill.setId(skillId);
        skill.setName("Java");

        SkillLevel level = new SkillLevel();
        level.setId(levelId);
        level.setTitle("Senior");
        level.setLevelValue(3);

        UserProjectSkill binding = new UserProjectSkill();
        binding.setUser(user);
        binding.setSkill(skill);
        binding.setSkillLevel(level);

        when(projectDataAccess.existsById(projectId)).thenReturn(true);
        when(userProjectDataAccess.findByProjectId(projectId)).thenReturn(List.of(userProject));
        when(userProjectSkillDataAccess.findByProjectId(projectId)).thenReturn(List.of(binding));

        List<ProjectMemberSkillVo> result = projectService.getProjectMemberSkills(projectId);

        assertEquals(1, result.size());
        assertEquals(userId.toString(), result.get(0).getUserId());
        assertEquals("member@example.com", result.get(0).getUserEmail());
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

        User user = new User();
        user.setId(userId);
        user.setEmail("member@example.com");

        UserProject userProject = new UserProject();
        userProject.setUser(user);

        when(projectDataAccess.existsById(projectId)).thenReturn(true);
        when(userProjectDataAccess.findByProjectId(projectId)).thenReturn(List.of(userProject));
        when(userProjectSkillDataAccess.findByProjectId(projectId)).thenReturn(List.of());

        List<ProjectMemberSkillVo> result = projectService.getProjectMemberSkills(projectId);

        assertEquals(1, result.size());
        assertEquals(userId.toString(), result.get(0).getUserId());
        assertEquals("member@example.com", result.get(0).getUserEmail());
        assertTrue(result.get(0).getSkills().isEmpty());
    }
}
