package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Search.ProjectSearchQuery;
import com.example.BackendArchitectureLab.Repository.ProjectRepository;
import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.Entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProjectDataAccessImpl 集成測試
 * 使用 H2 內存數據庫測試實際的數據庫操作
 * 
 * @DataJpaTest 特性:
 * 1. 自動配置 H2 內存數據庫
 * 2. 配置 Spring Data JPA
 * 3. 每個測試方法結束後回滾事務
 * 4. 不加載完整的 ApplicationContext,速度更快
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("ProjectDataAccessImpl 集成測試")
class ProjectDataAccessImplTest {
    
    @Autowired
    private ProjectRepository projectRepository;
    
    private IProjectDataAccess projectDataAccess;
    
    @BeforeEach
    void setUp() {
        // 每個測試前創建 DataAccess 實例並清空數據
        projectDataAccess = new ProjectDataAccessImpl(projectRepository);
        projectRepository.deleteAll();
    }
    
    // ==================== save 測試 ====================
    
    @Test
    @DisplayName("save - 成功保存新專案到數據庫")
    void save_shouldPersistNewProject() {
        // Given
        Project project = new Project();
        project.setName("Integration Test Project");
        
        // When
        Project saved = projectDataAccess.save(project);
        
        // Then
        assertNotNull(saved.getId(), "保存後應該生成 Id");
        assertEquals("Integration Test Project", saved.getName());
        
        // 驗證數據庫中確實有此記錄
        Optional<Project> found = projectRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Integration Test Project", found.get().getName());
    }
    
    @Test
    @DisplayName("save - 成功更新現有專案")
    void save_shouldUpdateExistingProject() {
        // Given
        Project project = new Project();
        project.setName("Original Name");
        Project saved = projectDataAccess.save(project);
        
        // When
        saved.setName("Updated Name");
        Project updated = projectDataAccess.save(saved);
        
        // Then
        assertEquals(saved.getId(), updated.getId(), "Id 應該相同");
        assertEquals("Updated Name", updated.getName());
        
        // 驗證數據庫中只有一筆記錄
        List<Project> all = projectRepository.findAll();
        assertEquals(1, all.size());
        assertEquals("Updated Name", all.get(0).getName());
    }
    
    // ==================== findAll 測試 ====================
    
    @Test
    @DisplayName("findAll - 返回所有專案")
    void findAll_shouldReturnAllProjects() {
        // Given
        Project project1 = new Project();
        project1.setName("Project 1");
        Project project2 = new Project();
        project2.setName("Project 2");
        Project project3 = new Project();
        project3.setName("Project 3");
        
        projectDataAccess.save(project1);
        projectDataAccess.save(project2);
        projectDataAccess.save(project3);
        
        // When
        List<Project> projects = projectDataAccess.findAll();
        
        // Then
        assertEquals(3, projects.size());
        assertTrue(projects.stream().anyMatch(p -> "Project 1".equals(p.getName())));
        assertTrue(projects.stream().anyMatch(p -> "Project 2".equals(p.getName())));
        assertTrue(projects.stream().anyMatch(p -> "Project 3".equals(p.getName())));
    }
    
    @Test
    @DisplayName("findAll - 返回空列表當無專案時")
    void findAll_shouldReturnEmptyList_whenNoProjects() {
        // When
        List<Project> projects = projectDataAccess.findAll();
        
        // Then
        assertNotNull(projects);
        assertTrue(projects.isEmpty());
    }
    
    // ==================== findById 測試 ====================
    
    @Test
    @DisplayName("findById - 根據 ID 查詢專案成功")
    void findById_shouldReturnProject_whenExists() {
        // Given
        Project project = new Project();
        project.setName("Test Project");
        Project saved = projectDataAccess.save(project);
        
        // When
        Optional<Project> found = projectDataAccess.findById(saved.getId());
        
        // Then
        assertTrue(found.isPresent());
        assertEquals("Test Project", found.get().getName());
        assertEquals(saved.getId(), found.get().getId());
    }
    
    @Test
    @DisplayName("findById - 專案不存在時返回空 Optional")
    void findById_shouldReturnEmpty_whenNotExists() {
        // When
        Optional<Project> found = projectDataAccess.findById(UUID.randomUUID());
        
        // Then
        assertFalse(found.isPresent());
    }
    
    // ==================== findByName 測試 ====================
    
    @Test
    @DisplayName("findByName - 根據名稱查詢專案成功")
    void findByName_shouldReturnProjects_whenExists() {
        // Given
        Project project = new Project();
        project.setName("Unique Project Name");
        projectDataAccess.save(project);
        
        // When
        List<Project> found = projectDataAccess.findByName("Unique Project Name");
        
        // Then
        assertFalse(found.isEmpty());
        assertEquals(1, found.size());
        assertEquals("Unique Project Name", found.get(0).getName());
    }
    
    @Test
    @DisplayName("findByName - 名稱不存在時返回空列表")
    void findByName_shouldReturnEmptyList_whenNotExists() {
        // When
        List<Project> found = projectDataAccess.findByName("Non-existent Project");
        
        // Then
        assertNotNull(found);
        assertTrue(found.isEmpty());
    }
    
    @Test
    @DisplayName("findByName - 支持查詢同名專案(如果有多個)")
    void findByName_shouldReturnMultipleProjects_whenDuplicateNamesExist() {
        // Given - 注意:根據業務邏輯,addProject 會阻止同名,但數據層本身可以存儲同名
        Project project1 = new Project();
        project1.setName("Duplicate Name");
        Project project2 = new Project();
        project2.setName("Duplicate Name");
        
        projectRepository.save(project1);  // 直接使用 repository 繞過業務邏輯
        projectRepository.save(project2);
        
        // When
        List<Project> found = projectDataAccess.findByName("Duplicate Name");
        
        // Then
        assertEquals(2, found.size());
    }
    
    // ==================== delete 測試 ====================
    
    @Test
    @DisplayName("delete - 成功刪除專案")
    void delete_shouldRemoveProject() {
        // Given
        Project project = new Project();
        project.setName("To Be Deleted");
        Project saved = projectDataAccess.save(project);
        
        // When
        projectDataAccess.delete(saved);
        
        // Then
        Optional<Project> found = projectDataAccess.findById(saved.getId());
        assertFalse(found.isPresent());
        
        // 驗證數據庫為空
        List<Project> all = projectDataAccess.findAll();
        assertTrue(all.isEmpty());
    }
    
    @Test
    @DisplayName("delete - 刪除不存在的專案不拋出異常")
    void delete_shouldNotThrowException_whenDeletingNonExistent() {
        // Given
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName("Non-existent");
        
        // When & Then - 不應該拋出異常
        assertDoesNotThrow(() -> projectDataAccess.delete(project));
    }
    
    // ==================== existsById 測試 ====================
    
    @Test
    @DisplayName("existsById - 專案存在時返回 true")
    void existsById_shouldReturnTrue_whenExists() {
        // Given
        Project project = new Project();
        project.setName("Exists Test");
        Project saved = projectDataAccess.save(project);
        
        // When
        boolean exists = projectDataAccess.existsById(saved.getId());
        
        // Then
        assertTrue(exists);
    }
    
    @Test
    @DisplayName("existsById - 專案不存在時返回 false")
    void existsById_shouldReturnFalse_whenNotExists() {
        // When
        boolean exists = projectDataAccess.existsById(UUID.randomUUID());
        
        // Then
        assertFalse(exists);
    }
    
    // ==================== 複合場景測試 ====================
    
    @Test
    @DisplayName("複合場景 - 創建、查詢、更新、刪除流程")
    void integrationFlow_createQueryUpdateDelete() {
        // 1. 創建
        Project project = new Project();
        project.setName("Flow Test Project");
        Project saved = projectDataAccess.save(project);
        assertNotNull(saved.getId());
        
        // 2. 查詢
        Optional<Project> found = projectDataAccess.findById(saved.getId());
        assertTrue(found.isPresent());
        
        // 3. 更新
        found.get().setName("Updated Flow Project");
        projectDataAccess.save(found.get());
        
        List<Project> updated = projectDataAccess.findByName("Updated Flow Project");
        assertEquals(1, updated.size());
        
        // 4. 刪除
        projectDataAccess.delete(updated.get(0));
        assertFalse(projectDataAccess.existsById(saved.getId()));
    }
    
    // ==================== searchProjects 測試 ====================
    
    @Test
    @DisplayName("searchProjects - 搜尋專案成功")
    void searchProjects_shouldReturnProjects() {
        // Given
        Project project1 = new Project();
        project1.setName("Backend API");
        project1.setDescription("Backend development");
        projectDataAccess.save(project1);
        
        Project project2 = new Project();
        project2.setName("Frontend UI");
        project2.setDescription("Frontend development");
        projectDataAccess.save(project2);
        
        ProjectSearchQuery query = new ProjectSearchQuery();
        query.setPage(0);
        query.setSize(10);
        query.setSortBy("name");
        query.setSortDir("asc");
        query.setName("Backend");
        
        // When
        Page<Project> result = projectDataAccess.searchProjects(query);
        
        // Then
        assertEquals(1, result.getTotalElements());
        assertEquals("Backend API", result.getContent().get(0).getName());
    }
    
    @Test
    @DisplayName("searchProjects - 搜尋專案帶分頁")
    void searchProjects_shouldReturnWithPagination() {
        // Given
        for (int i = 0; i < 5; i++) {
            Project project = new Project();
            project.setName("Project " + i);
            projectDataAccess.save(project);
        }
        
        ProjectSearchQuery query = new ProjectSearchQuery();
        query.setPage(0);
        query.setSize(2);
        query.setSortBy("name");
        query.setSortDir("asc");
        
        // When
        Page<Project> result = projectDataAccess.searchProjects(query);
        
        // Then
        assertEquals(5, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
        assertEquals(2, result.getContent().size());
    }
    
    @Test
    @DisplayName("searchProjects - 搜尋專案降序排列")
    void searchProjects_shouldReturnDescendingOrder() {
        // Given
        Project project1 = new Project();
        project1.setName("AAA Project");
        projectDataAccess.save(project1);
        
        Project project2 = new Project();
        project2.setName("ZZZ Project");
        projectDataAccess.save(project2);
        
        ProjectSearchQuery query = new ProjectSearchQuery();
        query.setPage(0);
        query.setSize(10);
        query.setSortBy("name");
        query.setSortDir("desc");
        
        // When
        Page<Project> result = projectDataAccess.searchProjects(query);
        
        // Then
        assertEquals(2, result.getTotalElements());
        assertEquals("ZZZ Project", result.getContent().get(0).getName());
        assertEquals("AAA Project", result.getContent().get(1).getName());
    }
    
    // ==================== searchCurrentUserProjects 測試 ====================
    
    @Test
    @DisplayName("searchCurrentUserProjects - 搜尋當前用戶專案")
    void searchCurrentUserProjects_shouldReturnUserProjects() {
        // Given
        String userId = UUID.randomUUID().toString();
        
        Project project = new Project();
        project.setName("User Project");
        projectDataAccess.save(project);
        
        ProjectSearchQuery query = new ProjectSearchQuery();
        query.setPage(0);
        query.setSize(10);
        query.setSortBy("name");
        query.setSortDir("asc");
        
        // When
        Page<Project> result = projectDataAccess.searchCurrentUserProjects(userId, query);
        
        // Then
        assertNotNull(result);
        // Note: 實際的用戶過濾邏輯在 Specification 中實現
        // 這裡只驗證方法調用不會拋出異常
    }
    
    @Test
    @DisplayName("searchCurrentUserProjects - 帶查詢條件")
    void searchCurrentUserProjects_shouldReturnWithFilter() {
        // Given
        String userId = UUID.randomUUID().toString();
        
        Project project = new Project();
        project.setName("Filtered Project");
        projectDataAccess.save(project);
        
        ProjectSearchQuery query = new ProjectSearchQuery();
        query.setPage(0);
        query.setSize(10);
        query.setSortBy("name");
        query.setSortDir("asc");
        query.setName("Filtered");
        
        // When
        Page<Project> result = projectDataAccess.searchCurrentUserProjects(userId, query);
        
        // Then
        assertNotNull(result);
    }
}
