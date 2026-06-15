package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.CurrentUserSkillVo;
import com.example.BackendArchitectureLab.Dto.Vo.PersonalSkillRequest;
import com.example.BackendArchitectureLab.Dto.Vo.Search.SkillLevelSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.Search.SkillSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.SkillLevelVo;
import com.example.BackendArchitectureLab.Dto.Vo.SkillVo;
import com.example.BackendArchitectureLab.Dto.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Dto.Vo.UserVo;
import com.example.BackendArchitectureLab.Entity.*;
import com.example.BackendArchitectureLab.Service.impl.SkillService;
import com.example.BackendArchitectureLab.DataAccess.*;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import com.example.BackendArchitectureLab.Mapper.SkillMapper;
import org.springframework.cache.CacheManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkillServiceTest {

    @Mock
    private ISkillDataAccess skillDataAccess;
    @Mock
    private IProjectDataAccess projectDataAccess;
    @Mock
    private ISkillLevelDataAccess skillLevelDataAccess;
    @Mock
    private IUserSkillDataAccess userSkillDataAccess;
    @Mock
    private IProjectSkillDataAccess projectSkillDataAccess;
    @Mock
    private IUserProjectDataAccess userProjectDataAccess;
    @Mock
    private SkillMapper skillMapper;
    @Mock
    private UserServiceFeignClient userServiceFeignClient;
    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private SkillService skillService;

    private Skill testSkill;
    private SkillVo testSkillVo;
    private SkillLevel testSkillLevel;
    private SkillLevelVo testSkillLevelVo;
    private UUID testSkillId;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testSkillId = UUID.randomUUID();
        testUserId = UUID.randomUUID();

        testSkill = new Skill();
        testSkill.setId(testSkillId);
        testSkill.setName("Java");
        testSkill.setDescription("Java Programming");

        testSkillVo = new SkillVo();
        testSkillVo.setId(testSkillId);
        testSkillVo.setName("Java");
        testSkillVo.setDescription("Java Programming");

        testSkillLevel = new SkillLevel();
        testSkillLevel.setId(UUID.randomUUID());
        testSkillLevel.setSkill(testSkill);
        testSkillLevel.setLevelValue(1);
        testSkillLevel.setTitle("Beginner");

        testSkillLevelVo = new SkillLevelVo();
        testSkillLevelVo.setId(testSkillLevel.getId().toString());
        testSkillLevelVo.setSkillId(testSkillId.toString());
        testSkillLevelVo.setLevelValue(1);
        testSkillLevelVo.setTitle("Beginner");

        when(skillMapper.toVo(any(Skill.class))).thenReturn(testSkillVo);
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

    private User createUserRef(UUID id) {
        User ref = new User();
        ref.setId(id);
        return ref;
    }

    @Test
    void bindUserSkill_shouldSave_whenValid() {
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);
        Skill skill = new Skill();
        skill.setId(skillId);
        SkillLevel level = new SkillLevel();
        level.setId(levelId);
        level.setSkill(skill);

        when(userServiceFeignClient.existsUserById(userId)).thenReturn(true);
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(skill));
        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.of(level));
        when(userSkillDataAccess.existsByUserIdAndSkillId(userId, skillId)).thenReturn(false);
        when(userSkillDataAccess.findByUserId(userId)).thenReturn(List.of());

        skillService.bindUserSkill(userId.toString(), skillId.toString(), levelId.toString());

        verify(userSkillDataAccess).save(any());
    }

    @Test
    void bindProjectSkill_shouldThrow_whenLevelNotBelongToSkill() {
        UUID projectId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID otherSkillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        Project project = new Project();
        project.setId(projectId);
        Skill skill = new Skill();
        skill.setId(skillId);
        Skill otherSkill = new Skill();
        otherSkill.setId(otherSkillId);
        SkillLevel level = new SkillLevel();
        level.setId(levelId);
        level.setSkill(otherSkill);

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(skill));
        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.of(level));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.bindProjectSkill(projectId.toString(), skillId.toString(), levelId.toString())
        );

        assertEquals("Skill level does not belong to skill", exception.getMessage());
        verify(projectSkillDataAccess, never()).save(any());
    }

    @Test
    void addSkillLevel_shouldSave_whenValid() {
        UUID skillId = UUID.randomUUID();

        Skill skill = new Skill();
        skill.setId(skillId);

        SkillLevelVo request = new SkillLevelVo();
        request.setSkillId(skillId.toString());
        request.setLevelValue(1);
        request.setTitle("Beginner");
        request.setDescription("Basic understanding");

        SkillLevel saved = new SkillLevel();
        saved.setId(UUID.randomUUID());
        saved.setSkill(skill);
        saved.setLevelValue(1);
        saved.setTitle("Beginner");
        saved.setDescription("Basic understanding");

        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(skill));
        when(skillLevelDataAccess.existsBySkillIdAndLevelValue(skillId, 1)).thenReturn(false);
        when(skillLevelDataAccess.save(any(SkillLevel.class))).thenReturn(saved);

        SkillLevelVo result = skillService.addSkillLevel(request);

        assertEquals(skillId.toString(), result.getSkillId());
        assertEquals(1, result.getLevelValue());
        verify(skillLevelDataAccess).save(any(SkillLevel.class));
    }

    @Test
    void testSearchSkills_Success() {
        // Arrange
        SkillSearchQuery query = new SkillSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("createdTime");
        query.setSortDir("desc");
        query.setName("Java");

        List<Skill> skills = List.of(testSkill);
        Page<Skill> skillPage = new PageImpl<>(skills, PageRequest.of(0, 20), 1);

        when(skillDataAccess.searchSkills(any(SkillSearchQuery.class))).thenReturn(skillPage);

        // Act
        PageResult<SkillVo> result = skillService.searchSkills(query);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getTotalElements());
        assertEquals("Java", result.getContent().get(0).getName());
        verify(skillDataAccess).searchSkills(any(SkillSearchQuery.class));
    }

    @Test
    void testSearchSkills_InvalidSortField() {
        // Arrange
        SkillSearchQuery query = new SkillSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("invalidField");
        query.setSortDir("desc");

        // Act & Assert
        assertThrows(AppException.class, () -> skillService.searchSkills(query));
    }

    @Test
    void testGetCurrentUserSkills_Success() {
        // Arrange
        setupSecurityContext(testUserId, "test@example.com");

        UserSkill userSkill = new UserSkill();
        userSkill.setUser(createUserRef(testUserId));
        userSkill.setSkill(testSkill);
        userSkill.setSkillLevel(testSkillLevel);

        when(userSkillDataAccess.findByUserId(testUserId)).thenReturn(List.of(userSkill));
        when(userProjectDataAccess.findByUserId(testUserId)).thenReturn(Collections.emptyList());

        // Act
        List<CurrentUserSkillVo> result = skillService.getCurrentUserSkills();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Java", result.get(0).getName());
        assertEquals("USER", result.get(0).getSourceType());
        assertNull(result.get(0).getProjectId());
        verify(userSkillDataAccess).findByUserId(testUserId);
    }

    @Test
    void testGetCurrentUserSkills_WithProjectSkills() {
        // Arrange
        setupSecurityContext(testUserId, "test@example.com");

        UUID projectId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);
        project.setName("Test Project");

        Skill projectSkill = new Skill();
        projectSkill.setId(UUID.randomUUID());
        projectSkill.setName("Python");

        SkillLevel projectSkillLevel = new SkillLevel();
        projectSkillLevel.setSkill(projectSkill);
        projectSkillLevel.setLevelValue(2);

        ProjectSkill projSkill = new ProjectSkill();
        projSkill.setProject(project);
        projSkill.setSkill(projectSkill);
        projSkill.setSkillLevel(projectSkillLevel);

        // 設置 project 的 projectSkills 列表
        project.setProjectSkills(List.of(projSkill));

        UserProject userProject = new UserProject();
        userProject.setUser(createUserRef(testUserId));
        userProject.setProject(project);

        SkillVo pythonVo = new SkillVo();
        pythonVo.setId(projectSkill.getId());
        pythonVo.setName("Python");

        when(userSkillDataAccess.findByUserId(testUserId)).thenReturn(Collections.emptyList());
        when(userProjectDataAccess.findByUserId(testUserId)).thenReturn(List.of(userProject));
        when(skillMapper.toVo(projectSkill)).thenReturn(pythonVo);

        // Act
        List<CurrentUserSkillVo> result = skillService.getCurrentUserSkills();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Python", result.get(0).getName());
        assertEquals("PROJECT", result.get(0).getSourceType());
        assertEquals(projectId, result.get(0).getProjectId());
        assertEquals("Test Project", result.get(0).getProjectName());
    }

    @Test
    void testSearchCurrentUserSkills_Success() {
        // Arrange
        setupSecurityContext(testUserId, "test@example.com");

        SkillSearchQuery query = new SkillSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("name");
        query.setSortDir("asc");
        query.setName("Java");

        UserSkill userSkill = new UserSkill();
        userSkill.setUser(createUserRef(testUserId));
        userSkill.setSkill(testSkill);
        userSkill.setSkillLevel(testSkillLevel);

        when(userSkillDataAccess.findByUserId(testUserId)).thenReturn(List.of(userSkill));
        when(userProjectDataAccess.findByUserId(testUserId)).thenReturn(Collections.emptyList());

        // Act
        PageResult<CurrentUserSkillVo> result = skillService.searchCurrentUserSkills(query);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Java", result.getContent().get(0).getName());
        assertEquals(0, result.getCurrentPage());
    }

    @Test
    void testSearchCurrentUserSkills_Pagination() {
        // Arrange
        setupSecurityContext(testUserId, "test@example.com");

        SkillSearchQuery query = new SkillSearchQuery();
        query.setPage(0);
        query.setSize(1);
        query.setSortBy("name");
        query.setSortDir("asc");

        Skill skill2 = new Skill();
        skill2.setId(UUID.randomUUID());
        skill2.setName("Python");

        SkillLevel level2 = new SkillLevel();
        level2.setSkill(skill2);
        level2.setLevelValue(2);

        UserSkill userSkill1 = new UserSkill();
        userSkill1.setSkill(testSkill);
        userSkill1.setSkillLevel(testSkillLevel);

        UserSkill userSkill2 = new UserSkill();
        userSkill2.setSkill(skill2);
        userSkill2.setSkillLevel(level2);

        SkillVo skillVo2 = new SkillVo();
        skillVo2.setId(skill2.getId());
        skillVo2.setName("Python");

        when(userSkillDataAccess.findByUserId(testUserId)).thenReturn(List.of(userSkill1, userSkill2));
        when(userProjectDataAccess.findByUserId(testUserId)).thenReturn(Collections.emptyList());
        when(skillMapper.toVo(skill2)).thenReturn(skillVo2);

        // Act
        PageResult<CurrentUserSkillVo> result = skillService.searchCurrentUserSkills(query);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size()); // Only 1 per page
        assertEquals(2L, result.getTotalElements()); // Total 2 skills
        assertEquals(2, result.getTotalPages()); // 2 pages
    }

    @Test
    void testSearchSkillLevels_Success() {
        // Arrange
        SkillLevelSearchQuery query = new SkillLevelSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("levelValue");
        query.setSortDir("asc");
        query.setSkillId(testSkillId);

        List<SkillLevel> levels = List.of(testSkillLevel);
        Page<SkillLevel> levelPage = new PageImpl<>(levels, PageRequest.of(0, 20), 1);

        when(skillLevelDataAccess.searchSkillLevels(any(SkillLevelSearchQuery.class))).thenReturn(levelPage);

        // Act
        PageResult<SkillLevelVo> result = skillService.searchSkillLevels(query);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getTotalElements());
        verify(skillLevelDataAccess).searchSkillLevels(any(SkillLevelSearchQuery.class));
    }

    @Test
    void testSearchSkillLevels_InvalidSortField() {
        // Arrange
        SkillLevelSearchQuery query = new SkillLevelSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("invalidField");
        query.setSortDir("asc");

        // Act & Assert
        assertThrows(AppException.class, () -> skillService.searchSkillLevels(query));
    }

    @Test
    void addSkill_shouldSave_whenValid() {
        SkillVo request = new SkillVo();
        request.setName("Java");
        request.setDescription("Java Programming");

        Skill skill = new Skill();
        skill.setName("Java");

        when(skillMapper.toEntity(request)).thenReturn(skill);
        when(skillDataAccess.exists(any())).thenReturn(false);
        when(skillDataAccess.save(any(Skill.class))).thenReturn(testSkill);

        SkillVo result = skillService.addSkill(request);

        assertNotNull(result);
        verify(skillDataAccess).save(any(Skill.class));
    }

    @Test
    void addSkill_shouldThrow_whenIdNotNull() {
        SkillVo request = new SkillVo();
        request.setId(UUID.randomUUID());
        request.setName("Java");

        Skill skill = new Skill();
        skill.setId(UUID.randomUUID());
        skill.setName("Java");

        when(skillMapper.toEntity(request)).thenReturn(skill);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.addSkill(request)
        );
        assertEquals("Key must be null", exception.getMessage());
    }

    @Test
    void addSkill_shouldThrow_whenNameNull() {
        SkillVo request = new SkillVo();

        Skill skill = new Skill();

        when(skillMapper.toEntity(request)).thenReturn(skill);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.addSkill(request)
        );
        assertEquals("Name must not be null", exception.getMessage());
    }

    @Test
    void addSkill_shouldThrow_whenNameAlreadyExists() {
        SkillVo request = new SkillVo();
        request.setName("Java");

        Skill skill = new Skill();
        skill.setName("Java");

        when(skillMapper.toEntity(request)).thenReturn(skill);
        when(skillDataAccess.exists(any())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.addSkill(request)
        );
        assertEquals("Name already exists", exception.getMessage());
    }

    @Test
    void updateSkill_shouldSave_whenValid() {
        SkillVo request = new SkillVo();
        request.setId(testSkillId);
        request.setName("Java Updated");

        Skill skill = new Skill();
        skill.setId(testSkillId);
        skill.setName("Java Updated");

        when(skillMapper.toEntity(request)).thenReturn(skill);

        skillService.updateSkill(request);

        verify(skillDataAccess).save(skill);
    }

    @Test
    void updateSkill_shouldThrow_whenIdNull() {
        SkillVo request = new SkillVo();
        request.setName("Java");

        Skill skill = new Skill();
        skill.setName("Java");

        when(skillMapper.toEntity(request)).thenReturn(skill);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.updateSkill(request)
        );
        assertEquals("Key must not be null", exception.getMessage());
    }

    @Test
    void updateSkill_shouldThrow_whenNameNull() {
        SkillVo request = new SkillVo();
        request.setId(testSkillId);

        Skill skill = new Skill();
        skill.setId(testSkillId);

        when(skillMapper.toEntity(request)).thenReturn(skill);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.updateSkill(request)
        );
        assertEquals("Name must not be null", exception.getMessage());
    }

    @Test
    void getSkill_shouldReturnAll() {
        List<Skill> skills = List.of(testSkill);
        when(skillDataAccess.findAll()).thenReturn(skills);

        List<SkillVo> result = skillService.getSkill();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(skillDataAccess).findAll();
    }

    @Test
    void addSkillLevel_shouldThrow_whenIdNotNull() {
        SkillLevelVo request = new SkillLevelVo();
        request.setId("some-id");
        request.setSkillId(testSkillId.toString());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.addSkillLevel(request)
        );
        assertEquals("Key must be null", exception.getMessage());
    }

    @Test
    void addSkillLevel_shouldThrow_whenSkillIdNull() {
        SkillLevelVo request = new SkillLevelVo();
        request.setLevelValue(1);
        request.setTitle("Beginner");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.addSkillLevel(request)
        );
        assertEquals("Skill key must not be null", exception.getMessage());
    }

    @Test
    void addSkillLevel_shouldThrow_whenSkillNotFound() {
        UUID skillId = UUID.randomUUID();
        SkillLevelVo request = new SkillLevelVo();
        request.setSkillId(skillId.toString());
        request.setLevelValue(1);
        request.setTitle("Beginner");

        when(skillDataAccess.findById(skillId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.addSkillLevel(request)
        );
        assertEquals("Skill not found", exception.getMessage());
    }

    @Test
    void addSkillLevel_shouldThrow_whenLevelValueAlreadyExists() {
        UUID skillId = UUID.randomUUID();
        Skill skill = new Skill();
        skill.setId(skillId);

        SkillLevelVo request = new SkillLevelVo();
        request.setSkillId(skillId.toString());
        request.setLevelValue(1);
        request.setTitle("Beginner");

        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(skill));
        when(skillLevelDataAccess.existsBySkillIdAndLevelValue(skillId, 1)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.addSkillLevel(request)
        );
        assertEquals("Skill level value already exists", exception.getMessage());
    }

    @Test
    void addSkillLevel_shouldThrow_whenLevelValueInvalid() {
        UUID skillId = UUID.randomUUID();
        SkillLevelVo request = new SkillLevelVo();
        request.setSkillId(skillId.toString());
        request.setLevelValue(0);
        request.setTitle("Beginner");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.addSkillLevel(request)
        );
        assertEquals("Level value must be greater than 0", exception.getMessage());
    }

    @Test
    void addSkillLevel_shouldThrow_whenTitleNull() {
        UUID skillId = UUID.randomUUID();
        SkillLevelVo request = new SkillLevelVo();
        request.setSkillId(skillId.toString());
        request.setLevelValue(1);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.addSkillLevel(request)
        );
        assertEquals("Title must not be null", exception.getMessage());
    }

    @Test
    void updateSkillLevel_shouldSave_whenValid() {
        UUID levelId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        
        Skill skill = new Skill();
        skill.setId(skillId);
        
        SkillLevel existingLevel = new SkillLevel();
        existingLevel.setId(levelId);
        existingLevel.setSkill(skill);
        existingLevel.setLevelValue(1);

        SkillLevelVo request = new SkillLevelVo();
        request.setId(levelId.toString());
        request.setLevelValue(2);
        request.setTitle("Intermediate");

        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.of(existingLevel));
        when(skillLevelDataAccess.findBySkillIdOrderByLevelValueAsc(skillId)).thenReturn(List.of(existingLevel));

        skillService.updateSkillLevel(request);

        verify(skillLevelDataAccess).save(existingLevel);
        assertEquals(2, existingLevel.getLevelValue());
        assertEquals("Intermediate", existingLevel.getTitle());
    }

    @Test
    void updateSkillLevel_shouldThrow_whenIdNull() {
        SkillLevelVo request = new SkillLevelVo();
        request.setLevelValue(1);
        request.setTitle("Beginner");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.updateSkillLevel(request)
        );
        assertEquals("Key must not be null", exception.getMessage());
    }

    @Test
    void updateSkillLevel_shouldThrow_whenNotFound() {
        UUID levelId = UUID.randomUUID();
        SkillLevelVo request = new SkillLevelVo();
        request.setId(levelId.toString());
        request.setLevelValue(1);
        request.setTitle("Beginner");

        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.updateSkillLevel(request)
        );
        assertEquals("Skill level not found", exception.getMessage());
    }

    @Test
    void updateSkillLevel_shouldThrow_whenLevelValueUsedByOther() {
        UUID levelId1 = UUID.randomUUID();
        UUID levelId2 = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        
        Skill skill = new Skill();
        skill.setId(skillId);
        
        SkillLevel level1 = new SkillLevel();
        level1.setId(levelId1);
        level1.setSkill(skill);
        level1.setLevelValue(1);

        SkillLevel level2 = new SkillLevel();
        level2.setId(levelId2);
        level2.setSkill(skill);
        level2.setLevelValue(2);

        SkillLevelVo request = new SkillLevelVo();
        request.setId(levelId1.toString());
        request.setLevelValue(2); // Trying to use level2's value
        request.setTitle("Updated");

        when(skillLevelDataAccess.findById(levelId1)).thenReturn(Optional.of(level1));
        when(skillLevelDataAccess.findBySkillIdOrderByLevelValueAsc(skillId)).thenReturn(List.of(level1, level2));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.updateSkillLevel(request)
        );
        assertEquals("Skill level value already exists", exception.getMessage());
    }

    @Test
    void getSkillLevels_shouldReturnLevels() {
        UUID skillId = UUID.randomUUID();
        Skill skill = new Skill();
        skill.setId(skillId);

        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(skill));
        when(skillLevelDataAccess.findBySkillIdOrderByLevelValueAsc(skillId)).thenReturn(List.of(testSkillLevel));

        List<SkillLevelVo> result = skillService.getSkillLevels(skillId.toString());

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(skillLevelDataAccess).findBySkillIdOrderByLevelValueAsc(skillId);
    }

    @Test
    void getSkillLevels_shouldThrow_whenSkillIdNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.getSkillLevels(null)
        );
        assertEquals("Skill key must not be null", exception.getMessage());
    }

    @Test
    void getSkillLevels_shouldThrow_whenSkillNotFound() {
        UUID skillId = UUID.randomUUID();
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.getSkillLevels(skillId.toString())
        );
        assertEquals("Skill not found", exception.getMessage());
    }

    @Test
    void deleteSkillLevel_shouldDelete_whenValid() {
        UUID levelId = UUID.randomUUID();
        SkillLevel level = new SkillLevel();
        level.setId(levelId);
        level.setSkill(testSkill);

        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.of(level));
        when(userSkillDataAccess.existsBySkillLevelId(levelId)).thenReturn(false);
        when(projectSkillDataAccess.existsBySkillLevelId(levelId)).thenReturn(false);

        skillService.deleteSkillLevel(levelId.toString());

        verify(skillLevelDataAccess).delete(level);
    }

    @Test
    void deleteSkillLevel_shouldThrow_whenIdNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.deleteSkillLevel(null)
        );
        assertEquals("Skill level key must not be null", exception.getMessage());
    }

    @Test
    void deleteSkillLevel_shouldThrow_whenInUseByUser() {
        UUID levelId = UUID.randomUUID();
        when(userSkillDataAccess.existsBySkillLevelId(levelId)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.deleteSkillLevel(levelId.toString())
        );
        assertEquals("Skill level is already in use", exception.getMessage());
    }

    @Test
    void deleteSkillLevel_shouldThrow_whenInUseByProject() {
        UUID levelId = UUID.randomUUID();
        when(userSkillDataAccess.existsBySkillLevelId(levelId)).thenReturn(false);
        when(projectSkillDataAccess.existsBySkillLevelId(levelId)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.deleteSkillLevel(levelId.toString())
        );
        assertEquals("Skill level is already in use", exception.getMessage());
    }

    @Test
    void deleteSkillLevel_shouldThrow_whenNotFound() {
        UUID levelId = UUID.randomUUID();
        when(userSkillDataAccess.existsBySkillLevelId(levelId)).thenReturn(false);
        when(projectSkillDataAccess.existsBySkillLevelId(levelId)).thenReturn(false);
        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.deleteSkillLevel(levelId.toString())
        );
        assertEquals("Skill level not found", exception.getMessage());
    }

    @Test
    void bindUserSkill_shouldThrow_whenUserIdNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.bindUserSkill(null, UUID.randomUUID().toString(), UUID.randomUUID().toString())
        );
        assertEquals("Key must not be null", exception.getMessage());
    }

    @Test
    void bindUserSkill_shouldThrow_whenUserNotFound() {
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        when(userServiceFeignClient.existsUserById(userId)).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.bindUserSkill(userId.toString(), skillId.toString(), levelId.toString())
        );
        assertEquals("User not found", exception.getMessage());
    }

    @Test
    void bindUserSkill_shouldThrow_whenSkillNotFound() {
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);

        when(userServiceFeignClient.existsUserById(userId)).thenReturn(true);
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.bindUserSkill(userId.toString(), skillId.toString(), levelId.toString())
        );
        assertEquals("Skill not found", exception.getMessage());
    }

    @Test
    void bindUserSkill_shouldThrow_whenSkillLevelNotFound() {
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);
        Skill skill = new Skill();
        skill.setId(skillId);

        when(userServiceFeignClient.existsUserById(userId)).thenReturn(true);
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(skill));
        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.bindUserSkill(userId.toString(), skillId.toString(), levelId.toString())
        );
        assertEquals("Skill level not found", exception.getMessage());
    }

    @Test
    void bindUserSkill_shouldThrow_whenAlreadyBound() {
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);
        Skill skill = new Skill();
        skill.setId(skillId);
        SkillLevel level = new SkillLevel();
        level.setId(levelId);
        level.setSkill(skill);

        when(userServiceFeignClient.existsUserById(userId)).thenReturn(true);
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(skill));
        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.of(level));
        when(userSkillDataAccess.existsByUserIdAndSkillId(userId, skillId)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.bindUserSkill(userId.toString(), skillId.toString(), levelId.toString())
        );
        assertEquals("Skill already bind to user", exception.getMessage());
    }

    @Test
    void bindProjectSkill_shouldSave_whenValid() {
        UUID projectId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        Project project = new Project();
        project.setId(projectId);
        Skill skill = new Skill();
        skill.setId(skillId);
        SkillLevel level = new SkillLevel();
        level.setId(levelId);
        level.setSkill(skill);

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(skill));
        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.of(level));
        when(projectSkillDataAccess.existsByProjectIdAndSkillId(projectId, skillId)).thenReturn(false);

        skillService.bindProjectSkill(projectId.toString(), skillId.toString(), levelId.toString());

        verify(projectSkillDataAccess).save(any(ProjectSkill.class));
    }

    @Test
    void bindProjectSkill_shouldThrow_whenProjectIdNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.bindProjectSkill(null, UUID.randomUUID().toString(), UUID.randomUUID().toString())
        );
        assertEquals("Key must not be null", exception.getMessage());
    }

    @Test
    void bindProjectSkill_shouldThrow_whenProjectNotFound() {
        UUID projectId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.bindProjectSkill(projectId.toString(), skillId.toString(), levelId.toString())
        );
        assertEquals("Project not found", exception.getMessage());
    }

    @Test
    void bindProjectSkill_shouldThrow_whenSkillNotFound() {
        UUID projectId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        Project project = new Project();
        project.setId(projectId);

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.bindProjectSkill(projectId.toString(), skillId.toString(), levelId.toString())
        );
        assertEquals("Skill not found", exception.getMessage());
    }

    @Test
    void bindProjectSkill_shouldThrow_whenSkillLevelNotFound() {
        UUID projectId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        Project project = new Project();
        project.setId(projectId);
        Skill skill = new Skill();
        skill.setId(skillId);

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(skill));
        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.bindProjectSkill(projectId.toString(), skillId.toString(), levelId.toString())
        );
        assertEquals("Skill level not found", exception.getMessage());
    }

    @Test
    void bindProjectSkill_shouldThrow_whenAlreadyBound() {
        UUID projectId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        Project project = new Project();
        project.setId(projectId);
        Skill skill = new Skill();
        skill.setId(skillId);
        SkillLevel level = new SkillLevel();
        level.setId(levelId);
        level.setSkill(skill);

        when(projectDataAccess.findById(projectId)).thenReturn(Optional.of(project));
        when(skillDataAccess.findById(skillId)).thenReturn(Optional.of(skill));
        when(skillLevelDataAccess.findById(levelId)).thenReturn(Optional.of(level));
        when(projectSkillDataAccess.existsByProjectIdAndSkillId(projectId, skillId)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.bindProjectSkill(projectId.toString(), skillId.toString(), levelId.toString())
        );
        assertEquals("Skill already bind to project", exception.getMessage());
    }

    @Test
    void deleteSkill_shouldDelete_whenValid() {
        SkillVo request = new SkillVo();
        request.setId(testSkillId);

        Skill skill = new Skill();
        skill.setId(testSkillId);

        when(skillMapper.toEntity(request)).thenReturn(skill);
        when(skillDataAccess.findById(testSkillId)).thenReturn(Optional.of(skill));

        skillService.deleteSkill(request);

        verify(userSkillDataAccess).deleteBySkillId(testSkillId);
        verify(projectSkillDataAccess).deleteBySkillId(testSkillId);
        verify(skillLevelDataAccess).deleteBySkillId(testSkillId);
        verify(skillDataAccess).deleteById(testSkillId);
    }

    @Test
    void deleteSkill_shouldThrow_whenIdNull() {
        SkillVo request = new SkillVo();
        Skill skill = new Skill();

        when(skillMapper.toEntity(request)).thenReturn(skill);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.deleteSkill(request)
        );
        assertEquals("Key must not be null", exception.getMessage());
    }

    @Test
    void deleteSkill_shouldThrow_whenNotFound() {
        SkillVo request = new SkillVo();
        request.setId(testSkillId);

        Skill skill = new Skill();
        skill.setId(testSkillId);

        when(skillMapper.toEntity(request)).thenReturn(skill);
        when(skillDataAccess.findById(testSkillId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.deleteSkill(request)
        );
        assertEquals("Skill not found", exception.getMessage());
    }

    @Test
    void testSearchCurrentUserSkills_WithDescriptionFilter() {
        setupSecurityContext(testUserId, "test@example.com");

        SkillSearchQuery query = new SkillSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("name");
        query.setSortDir("asc");
        query.setDescription("Programming");

        testSkillVo.setDescription("Java Programming");

        UserSkill userSkill = new UserSkill();
        userSkill.setUser(createUserRef(testUserId));
        userSkill.setSkill(testSkill);
        userSkill.setSkillLevel(testSkillLevel);

        when(userSkillDataAccess.findByUserId(testUserId)).thenReturn(List.of(userSkill));
        when(userProjectDataAccess.findByUserId(testUserId)).thenReturn(Collections.emptyList());

        PageResult<CurrentUserSkillVo> result = skillService.searchCurrentUserSkills(query);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    void testSearchCurrentUserSkills_WithCreatedByFilter() {
        setupSecurityContext(testUserId, "test@example.com");

        SkillSearchQuery query = new SkillSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("name");
        query.setSortDir("asc");
        query.setCreatedBy("admin");

        testSkillVo.setCreatedBy("admin");

        UserSkill userSkill = new UserSkill();
        userSkill.setUser(createUserRef(testUserId));
        userSkill.setSkill(testSkill);
        userSkill.setSkillLevel(testSkillLevel);

        when(userSkillDataAccess.findByUserId(testUserId)).thenReturn(List.of(userSkill));
        when(userProjectDataAccess.findByUserId(testUserId)).thenReturn(Collections.emptyList());

        PageResult<CurrentUserSkillVo> result = skillService.searchCurrentUserSkills(query);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }
    
    // ========== 個人技能測試 ==========
    
    @Test
    void addPersonalSkill_shouldSave_whenValid() {
        // Arrange
        PersonalSkillRequest request = new PersonalSkillRequest();
        request.setName("Java");
        request.setDescription("Java Programming");
        request.setSkillLevelId(testSkillLevel.getId().toString());
        
        setupSecurityContext(testUserId, "test@example.com");
        when(skillDataAccess.exists(any())).thenReturn(false);
        when(skillDataAccess.save(any(Skill.class))).thenReturn(testSkill);
        when(skillLevelDataAccess.findById(testSkillLevel.getId())).thenReturn(Optional.of(testSkillLevel));
        when(skillMapper.toVo(testSkill)).thenReturn(testSkillVo);
        
        // Act
        SkillVo result = skillService.addPersonalSkill(request);
        
        // Assert
        assertNotNull(result);
        assertEquals("Java", result.getName());
        verify(skillDataAccess).save(any(Skill.class));
        verify(userSkillDataAccess).save(any(UserSkill.class));
    }

    @Test
    void addPersonalSkill_shouldCreateLevelFromManualInput_whenNoSkillLevelId() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        PersonalSkillRequest request = new PersonalSkillRequest();
        request.setName("Java");
        request.setDescription("Java Programming");
        request.setSkillLevelValue(1);
        request.setSkillLevelTitle("Beginner");
        request.setSkillLevelDescription("Basic");

        SkillLevel createdLevel = new SkillLevel();
        createdLevel.setId(UUID.randomUUID());
        createdLevel.setSkill(testSkill);
        createdLevel.setLevelValue(1);
        createdLevel.setTitle("Beginner");

        setupSecurityContext(userId, "test@example.com");
        when(skillDataAccess.exists(any())).thenReturn(false);
        when(skillDataAccess.save(any(Skill.class))).thenReturn(testSkill);
        when(skillLevelDataAccess.save(any(SkillLevel.class))).thenReturn(createdLevel);
        when(skillMapper.toVo(testSkill)).thenReturn(testSkillVo);

        SkillVo result = skillService.addPersonalSkill(request);

        assertNotNull(result);
        verify(skillLevelDataAccess).save(any(SkillLevel.class));
        verify(userSkillDataAccess).save(argThat(userSkill -> userSkill.getSkillLevel().equals(createdLevel)));
    }

    @Test
    void addPersonalSkill_shouldThrow_whenNoSkillLevelDataProvided() {
        UUID userId = UUID.randomUUID();

        PersonalSkillRequest request = new PersonalSkillRequest();
        request.setName("Java");
        request.setDescription("Java Programming");

        setupSecurityContext(userId, "test@example.com");
        when(skillDataAccess.exists(any())).thenReturn(false);
        when(skillDataAccess.save(any(Skill.class))).thenReturn(testSkill);
        when(skillLevelDataAccess.findBySkillIdOrderByLevelValueAsc(testSkill.getId())).thenReturn(List.of());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.addPersonalSkill(request)
        );
        assertEquals("Skill level data is required", exception.getMessage());
    }
    
    @Test
    void addPersonalSkill_shouldThrow_whenNameNull() {
        // Arrange
        PersonalSkillRequest request = new PersonalSkillRequest();
        request.setDescription("Description");
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.addPersonalSkill(request)
        );
        assertEquals("Name must not be null", exception.getMessage());
    }
    
    @Test
    void addPersonalSkill_shouldThrow_whenNameExists() {
        // Arrange
        PersonalSkillRequest request = new PersonalSkillRequest();
        request.setName("Java");
        
        when(skillDataAccess.exists(any())).thenReturn(true);
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.addPersonalSkill(request)
        );
        assertEquals("Name already exists", exception.getMessage());
    }
    
    @Test
    void addPersonalSkill_shouldBindCurrentUser() {
        // Arrange
        PersonalSkillRequest request = new PersonalSkillRequest();
        request.setName("Java");
        request.setSkillLevelId(testSkillLevel.getId().toString());
        
        setupSecurityContext(testUserId, "test@example.com");
        when(skillDataAccess.exists(any())).thenReturn(false);
        when(skillDataAccess.save(any(Skill.class))).thenReturn(testSkill);
        when(skillLevelDataAccess.findById(testSkillLevel.getId())).thenReturn(Optional.of(testSkillLevel));
        when(skillMapper.toVo(testSkill)).thenReturn(testSkillVo);
        
        // Act
        skillService.addPersonalSkill(request);
        
        // Assert
        verify(userSkillDataAccess).save(argThat(userSkill ->
                userSkill.getUser().getId().equals(testUserId) &&
                userSkill.getSkill().equals(testSkill)
        ));
    }
    
    @Test
    void addPersonalSkill_withSkillLevel_shouldBindWithLevel() {
        // Arrange
        PersonalSkillRequest request = new PersonalSkillRequest();
        request.setName("Java");
        request.setSkillLevelId(testSkillLevel.getId().toString());
        
        setupSecurityContext(testUserId, "test@example.com");
        when(skillDataAccess.exists(any())).thenReturn(false);
        when(skillDataAccess.save(any(Skill.class))).thenReturn(testSkill);
        when(skillLevelDataAccess.findById(testSkillLevel.getId())).thenReturn(Optional.of(testSkillLevel));
        when(skillMapper.toVo(testSkill)).thenReturn(testSkillVo);
        
        // Act
        skillService.addPersonalSkill(request);
        
        // Assert
        verify(userSkillDataAccess).save(argThat(userSkill ->
                userSkill.getSkillLevel().equals(testSkillLevel)
        ));
    }
    
    @Test
    void updatePersonalSkill_shouldUpdate_whenOwner() {
        // Arrange
        PersonalSkillRequest request = new PersonalSkillRequest();
        request.setName("Java Updated");
        request.setDescription("Updated Description");
        
        setupSecurityContext(testUserId, "test@example.com");
        when(skillDataAccess.findById(testSkillId)).thenReturn(Optional.of(testSkill));
        when(userSkillDataAccess.existsByUserIdAndSkillId(testUserId, testSkillId)).thenReturn(true);
        
        // Act
        skillService.updatePersonalSkill(testSkillId, request);
        
        // Assert
        verify(skillDataAccess).save(argThat(skill ->
                skill.getName().equals("Java Updated") &&
                skill.getDescription().equals("Updated Description")
        ));
    }
    
    @Test
    void updatePersonalSkill_shouldThrow_whenNotOwner() {
        // Arrange
        PersonalSkillRequest request = new PersonalSkillRequest();
        request.setName("Java");
        
        setupSecurityContext(testUserId, "test@example.com");
        when(skillDataAccess.findById(testSkillId)).thenReturn(Optional.of(testSkill));
        when(userSkillDataAccess.existsByUserIdAndSkillId(testUserId, testSkillId)).thenReturn(false);
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.updatePersonalSkill(testSkillId, request)
        );
        assertEquals("You are not the owner of this skill", exception.getMessage());
    }

    @Test
    void updatePersonalSkill_shouldThrow_whenAssignedByAdminReadOnly() {
        PersonalSkillRequest request = new PersonalSkillRequest();
        request.setName("Java Updated");
        request.setDescription("Updated Description");

        testSkill.setCreatedBy(UUID.randomUUID().toString());

        setupSecurityContext(testUserId, "test@example.com");
        when(skillDataAccess.findById(testSkillId)).thenReturn(Optional.of(testSkill));
        when(userSkillDataAccess.existsByUserIdAndSkillId(testUserId, testSkillId)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.updatePersonalSkill(testSkillId, request)
        );
        assertEquals("Skill assigned by admin is read-only", exception.getMessage());
    }

    @Test
    void updatePersonalSkillLevel_shouldUpdateBinding_whenValid() {
        UserSkill userSkill = new UserSkill();
        userSkill.setUser(createUserRef(testUserId));
        userSkill.setSkill(testSkill);
        userSkill.setSkillLevel(testSkillLevel);

        SkillLevel newLevel = new SkillLevel();
        newLevel.setId(UUID.randomUUID());
        newLevel.setSkill(testSkill);
        newLevel.setLevelValue(2);
        newLevel.setTitle("Intermediate");

        setupSecurityContext(testUserId, "test@example.com");
        when(skillDataAccess.findById(testSkillId)).thenReturn(Optional.of(testSkill));
        when(userSkillDataAccess.existsByUserIdAndSkillId(testUserId, testSkillId)).thenReturn(true);
        when(skillLevelDataAccess.findById(newLevel.getId())).thenReturn(Optional.of(newLevel));
        when(userSkillDataAccess.findByUserIdAndSkillId(testUserId, testSkillId)).thenReturn(List.of(userSkill));

        skillService.updatePersonalSkillLevel(testSkillId, newLevel.getId());

        verify(userSkillDataAccess).save(userSkill);
        assertEquals(newLevel, userSkill.getSkillLevel());
    }
    
    @Test
    void updatePersonalSkill_shouldThrow_whenSkillNotFound() {
        // Arrange
        PersonalSkillRequest request = new PersonalSkillRequest();
        request.setName("Java");
        
        when(skillDataAccess.findById(testSkillId)).thenReturn(Optional.empty());
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.updatePersonalSkill(testSkillId, request)
        );
        assertEquals("Skill not found", exception.getMessage());
    }
    
    @Test
    void deletePersonalSkill_shouldOnlyUnbind_whenOwner() {
        // Arrange
        setupSecurityContext(testUserId, "test@example.com");
        when(skillDataAccess.findById(testSkillId)).thenReturn(Optional.of(testSkill));
        when(userSkillDataAccess.existsByUserIdAndSkillId(testUserId, testSkillId)).thenReturn(true);
        
        // Act
        skillService.deletePersonalSkill(testSkillId);
        
        // Assert
        verify(userSkillDataAccess).deleteByUserIdAndSkillId(testUserId, testSkillId);
        verify(skillLevelDataAccess, never()).deleteBySkillId(any());
        verify(skillDataAccess, never()).delete(any());
    }
    
    @Test
    void deletePersonalSkill_shouldThrow_whenNotOwner() {
        // Arrange
        setupSecurityContext(testUserId, "test@example.com");
        when(skillDataAccess.findById(testSkillId)).thenReturn(Optional.of(testSkill));
        when(userSkillDataAccess.existsByUserIdAndSkillId(testUserId, testSkillId)).thenReturn(false);
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.deletePersonalSkill(testSkillId)
        );
        assertEquals("You are not the owner of this skill", exception.getMessage());
    }
    
    @Test
    void deletePersonalSkill_shouldThrow_whenSkillNotFound() {
        // Arrange
        when(skillDataAccess.findById(testSkillId)).thenReturn(Optional.empty());
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.deletePersonalSkill(testSkillId)
        );
        assertEquals("Skill not found", exception.getMessage());
    }
    
    // ========== 管理者介面測試 ==========
    
    @Test
    void addSkill_shouldBindUsers_whenUserIdsProvided() {
        // Arrange
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        User user1 = new User();
        user1.setId(userId1);
        User user2 = new User();
        user2.setId(userId2);
        
        Skill newSkill = new Skill();
        newSkill.setName("Java");
        newSkill.setDescription("Java Programming");
        
        SkillVo skillVo = new SkillVo();
        skillVo.setName("Java");
        skillVo.setDescription("Java Programming");
        skillVo.setUserIds(List.of(userId1.toString(), userId2.toString()));
        skillVo.setSkillLevelId(testSkillLevel.getId().toString());
        
        when(skillMapper.toEntity(skillVo)).thenReturn(newSkill);
        when(skillDataAccess.exists(any())).thenReturn(false);
        when(skillDataAccess.save(newSkill)).thenReturn(testSkill);
        when(skillDataAccess.findById(testSkill.getId())).thenReturn(Optional.of(testSkill));
        when(userServiceFeignClient.existsUserById(userId1)).thenReturn(true);
        when(userServiceFeignClient.existsUserById(userId2)).thenReturn(true);
        when(skillLevelDataAccess.findById(testSkillLevel.getId())).thenReturn(Optional.of(testSkillLevel));
        when(userSkillDataAccess.existsByUserIdAndSkillId(userId1, testSkill.getId())).thenReturn(false);
        when(userSkillDataAccess.existsByUserIdAndSkillId(userId2, testSkill.getId())).thenReturn(false);
        when(skillMapper.toVo(testSkill)).thenReturn(skillVo);
        
        // Act
        SkillVo result = skillService.addSkill(skillVo);
        
        // Assert
        assertNotNull(result);
        verify(userSkillDataAccess, times(2)).save(any(UserSkill.class));
    }
    
    @Test
    void addSkill_shouldUseFirstLevel_whenNoSkillLevelIdProvided() {
        // Arrange
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        
        Skill newSkill = new Skill();
        newSkill.setName("Java");
        newSkill.setDescription("Java Programming");
        
        SkillVo skillVo = new SkillVo();
        skillVo.setName("Java");
        skillVo.setDescription("Java Programming");
        skillVo.setUserIds(List.of(userId.toString()));
        skillVo.setSkillLevelId(null);
        
        when(skillMapper.toEntity(skillVo)).thenReturn(newSkill);
        when(skillDataAccess.exists(any())).thenReturn(false);
        when(skillDataAccess.save(newSkill)).thenReturn(testSkill);
        when(skillDataAccess.findById(testSkill.getId())).thenReturn(Optional.of(testSkill));
        when(userServiceFeignClient.existsUserById(userId)).thenReturn(true);
        when(skillLevelDataAccess.findBySkillIdOrderByLevelValueAsc(testSkill.getId())).thenReturn(List.of(testSkillLevel));
        when(userSkillDataAccess.existsByUserIdAndSkillId(userId, testSkill.getId())).thenReturn(false);
        when(skillMapper.toVo(testSkill)).thenReturn(skillVo);
        
        // Act
        SkillVo result = skillService.addSkill(skillVo);
        
        // Assert
        assertNotNull(result);
        verify(userSkillDataAccess).save(any(UserSkill.class));
    }
    
    @Test
    void addSkill_shouldThrow_whenInvalidUserId() {
        // Arrange
        UUID invalidUserId = UUID.randomUUID();
        
        Skill newSkill = new Skill();
        newSkill.setName("Java");
        newSkill.setDescription("Java Programming");
        
        SkillVo skillVo = new SkillVo();
        skillVo.setName("Java");
        skillVo.setDescription("Java Programming");
        skillVo.setUserIds(List.of(invalidUserId.toString()));
        
        when(skillMapper.toEntity(skillVo)).thenReturn(newSkill);
        when(skillDataAccess.exists(any())).thenReturn(false);
        when(skillDataAccess.save(newSkill)).thenReturn(testSkill);
        when(skillDataAccess.findById(testSkill.getId())).thenReturn(Optional.of(testSkill));
        when(userServiceFeignClient.existsUserById(invalidUserId)).thenReturn(false);
        when(skillLevelDataAccess.findBySkillIdOrderByLevelValueAsc(testSkill.getId())).thenReturn(List.of(testSkillLevel));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.addSkill(skillVo)
        );
        assertTrue(exception.getMessage().contains("User not found"));
    }

    @Test
    void addSkill_shouldThrow_whenInvalidSkillLevelId() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID invalidLevelId = UUID.randomUUID();
        
        Skill newSkill = new Skill();
        newSkill.setName("Java");
        newSkill.setDescription("Java Programming");
        
        SkillVo skillVo = new SkillVo();
        skillVo.setName("Java");
        skillVo.setDescription("Java Programming");
        skillVo.setUserIds(List.of(userId.toString()));
        skillVo.setSkillLevelId(invalidLevelId.toString());
        
        when(skillMapper.toEntity(skillVo)).thenReturn(newSkill);
        when(skillDataAccess.exists(any())).thenReturn(false);
        when(skillDataAccess.save(newSkill)).thenReturn(testSkill);
        when(skillDataAccess.findById(testSkill.getId())).thenReturn(Optional.of(testSkill));
        when(skillLevelDataAccess.findById(invalidLevelId)).thenReturn(Optional.empty());
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                skillService.addSkill(skillVo)
        );
        assertEquals("Skill level not found", exception.getMessage());
    }
    
    @Test
    void updateSkill_shouldRebindUsers_whenUserIdsProvided() {
        // Arrange
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        User user1 = new User();
        user1.setId(userId1);
        User user2 = new User();
        user2.setId(userId2);
        
        UserSkill existingBinding = new UserSkill();
        existingBinding.setUser(user1);
        existingBinding.setSkill(testSkill);
        
        SkillVo skillVo = new SkillVo();
        skillVo.setId(testSkillId);
        skillVo.setName("Java");
        skillVo.setDescription("Java Programming");
        skillVo.setUserIds(List.of(userId2.toString()));
        skillVo.setSkillLevelId(testSkillLevel.getId().toString());
        
        when(skillMapper.toEntity(skillVo)).thenReturn(testSkill);
        when(userSkillDataAccess.findBySkillId(testSkillId)).thenReturn(List.of(existingBinding));
        when(skillDataAccess.findById(testSkillId)).thenReturn(Optional.of(testSkill));
        when(userServiceFeignClient.existsUserById(userId2)).thenReturn(true);
        when(skillLevelDataAccess.findById(testSkillLevel.getId())).thenReturn(Optional.of(testSkillLevel));
        when(userSkillDataAccess.existsByUserIdAndSkillId(userId2, testSkillId)).thenReturn(false);
        
        // Act
        skillService.updateSkill(skillVo);
        
        // Assert
        verify(userSkillDataAccess).deleteByUserIdAndSkillId(userId1, testSkillId);
        verify(userSkillDataAccess).save(any(UserSkill.class));
    }
    
    @Test
    void updateSkill_shouldRemoveAllBindings_whenEmptyUserIds() {
        // Arrange
        UUID userId1 = UUID.randomUUID();
        User user1 = new User();
        user1.setId(userId1);
        
        UserSkill existingBinding = new UserSkill();
        existingBinding.setUser(user1);
        existingBinding.setSkill(testSkill);
        
        SkillVo skillVo = new SkillVo();
        skillVo.setId(testSkillId);
        skillVo.setName("Java");
        skillVo.setDescription("Java Programming");
        skillVo.setUserIds(List.of());
        
        when(skillMapper.toEntity(skillVo)).thenReturn(testSkill);
        when(userSkillDataAccess.findBySkillId(testSkillId)).thenReturn(List.of(existingBinding));
        
        // Act
        skillService.updateSkill(skillVo);
        
        // Assert
        verify(userSkillDataAccess).deleteByUserIdAndSkillId(userId1, testSkillId);
        verify(userSkillDataAccess, never()).save(any(UserSkill.class));
    }
    
    @Test
    void updateSkill_shouldNotRebind_whenUserIdsNull() {
        // Arrange
        SkillVo skillVo = new SkillVo();
        skillVo.setId(testSkillId);
        skillVo.setName("Java");
        skillVo.setDescription("Java Programming");
        skillVo.setUserIds(null);
        
        when(skillMapper.toEntity(skillVo)).thenReturn(testSkill);
        
        // Act
        skillService.updateSkill(skillVo);
        
        // Assert
        verify(userSkillDataAccess, never()).findBySkillId(any());
        verify(userSkillDataAccess, never()).deleteByUserIdAndSkillId(any(), any());
        verify(userSkillDataAccess, never()).save(any(UserSkill.class));
    }
}
