package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.ProjectVo;
import com.example.BackendArchitectureLab.Vo.Search.ProjectSearchQuery;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.Entity.UserProject;
import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Mapper.ProjectMapper;
import com.example.BackendArchitectureLab.Util.SecurityUtil;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectQueryServiceTest {

    @Mock
    private TransactionExecutor transactionExecutor;
    @Mock
    private IProjectDataAccess projectDataAccess;
    @Mock
    private IUserProjectDataAccess userProjectDataAccess;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private SecurityUtil securityUtil;

    @InjectMocks
    private ProjectQueryService projectQueryService;

    private Project testProject;
    private ProjectVo testProjectVo;
    private UUID testId;

    @BeforeEach
    void setUp() {
        lenient().when(transactionExecutor.executeReadOnly(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        lenient().when(transactionExecutor.executeWritable(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });

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
        PageResult<ProjectVo> result = projectQueryService.searchProjects(query);

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
        assertThrows(AppException.class, () -> projectQueryService.searchProjects(query));
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
        assertThrows(AppException.class, () -> projectQueryService.searchProjects(query));
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
        List<ProjectVo> result = projectQueryService.getCurrentUserProjects();

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
        PageResult<ProjectVo> result = projectQueryService.searchCurrentUserProjects(query);

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
        PageResult<ProjectVo> result = projectQueryService.searchCurrentUserProjects(query);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getContent().size());
        assertEquals(0L, result.getTotalElements());
    }
}
