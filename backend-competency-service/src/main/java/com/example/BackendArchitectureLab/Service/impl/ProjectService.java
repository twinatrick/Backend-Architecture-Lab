package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.DataAccess.*;
import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import com.example.BackendArchitectureLab.Vo.MemberSkillLevelVo;
import com.example.BackendArchitectureLab.Vo.PersonalProjectRequest;
import com.example.BackendArchitectureLab.Vo.ProjectMemberSkillVo;
import com.example.BackendArchitectureLab.Vo.ProjectSkillVo;
import com.example.BackendArchitectureLab.Vo.ProjectVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.Search.ProjectSearchQuery;
import com.example.BackendArchitectureLab.Entity.*;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import com.example.BackendArchitectureLab.Mapper.ProjectMapper;
import com.example.BackendArchitectureLab.Service.IProjectService;
import com.example.BackendArchitectureLab.Service.ISkillService;
import com.example.BackendArchitectureLab.Util.SecurityUtil;
import com.example.BackendArchitectureLab.Util.SortFieldValidator;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ProjectService - 專案業務邏輯服務
 * 重構後依賴 DataAccess 抽象層,而非直接依賴 Repository
 */
@Service
public class ProjectService implements IProjectService {
    
    @Autowired
    private TransactionExecutor transactionExecutor;

    @Autowired
    private IProjectDataAccess projectDataAccess;
    @Autowired
    private IProjectSkillDataAccess projectSkillDataAccess;
    @Autowired
    private IUserProjectDataAccess userProjectDataAccess;
    @Autowired
    private SecurityUtil securityUtil;
    @Autowired
    private UserServiceFeignClient userServiceFeignClient;
    @Autowired
    private IUserSkillDataAccess userSkillDataAccess;
    @Autowired
    private ISkillLevelDataAccess skillLevelDataAccess;
    @Autowired
    private ISkillDataAccess skillDataAccess;
    @Autowired
    private IUserProjectSkillDataAccess userProjectSkillDataAccess;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private CacheManager cacheManager;

    @Autowired
    @Lazy
    private ProjectService self;

    /**
     * 新增專案
     * @param project 要新增的專案實體
     * @return 保存後的專案實體
     * @throws IllegalArgumentException 當參數驗證失敗時拋出
     */
    @Override
    public ProjectVo addProject(ProjectVo projectVo) {
        if (projectVo.getUserIds() != null) {
            validateUsersExist(projectVo.getUserIds().stream().map(UUID::fromString).toList());
        }
        return self.doAddProject(projectVo);
    }

    /**
     * 交易內新增專案（由 addProject 在交易外的 Feign 驗證後呼叫）
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "projects", key = "'all'")
    })
    public ProjectVo doAddProject(ProjectVo projectVo) {
        Project project = projectMapper.toEntity(projectVo);
        if (project.getId() != null) {
            throw new IllegalArgumentException("Key must be null");
        } else if (project.getName() == null) {
            throw new IllegalArgumentException("Name must not be null");
        } else if (!projectDataAccess.findByName(project.getName()).isEmpty()) {
            throw new IllegalArgumentException("Name already exists");
        }

        Project savedProject = projectDataAccess.save(project);
        
        // 處理使用者綁定（如果提供了 userIds）
        if (projectVo.getUserIds() != null && !projectVo.getUserIds().isEmpty()) {
            bindUsersToProject(savedProject.getId(), projectVo.getUserIds());
            for (String uid : projectVo.getUserIds()) {
                evictUserProjectsCache(UUID.fromString(uid));
            }
        }
        
        return projectMapper.toVo(savedProject);
    }
    /**
     * 更新專案
     * @param project 要更新的專案實體
     * @throws IllegalArgumentException 當參數驗證失敗時拋出
     */
    @Override
    public void updateProject(ProjectVo projectVo) {
        if (projectVo.getUserIds() != null) {
            validateUsersExist(projectVo.getUserIds().stream().map(UUID::fromString).toList());
        }
        self.doUpdateProject(projectVo);
    }

    /**
     * 交易內更新專案（由 updateProject 在交易外的 Feign 驗證後呼叫）
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "projects", key = "'all'")
    })
    public void doUpdateProject(ProjectVo projectVo) {
        Project project = projectMapper.toEntity(projectVo);
        if (project.getId() == null) {
            throw new IllegalArgumentException("Key must not be null");
        } else if (project.getName() == null) {
            throw new IllegalArgumentException("Name must not be null");
        }
        projectDataAccess.save(project);
        
        // 處理使用者重新綁定（如果提供了 userIds）
        if (projectVo.getUserIds() != null) {
            // 先刪除現有綁定
            userProjectDataAccess.deleteByProjectId(project.getId());
            
            // 重新綁定（如果 userIds 不為空）
            if (!projectVo.getUserIds().isEmpty()) {
                bindUsersToProject(project.getId(), projectVo.getUserIds());
            }
            for (String uid : projectVo.getUserIds()) {
                evictUserProjectsCache(UUID.fromString(uid));
            }
        }
    }
    
    /**
     * 綁定多個使用者到專案
     * 
     * @param projectId 專案 ID
     * @param userIds 使用者 ID 列表
     */
    private void bindUsersToProject(UUID projectId, List<String> userIds) {
        Project project = projectDataAccess.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        Set<UUID> targetUserIds = userIds.stream()
                .map(UUID::fromString)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        
        // 綁定每個使用者
        for (UUID userId : targetUserIds) {
            // 檢查是否已存在綁定
            if (!userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)) {
                UserProject userProject = new UserProject();
                userProject.setUserId(userId);
                userProject.setProject(project);
                userProjectDataAccess.save(userProject);
            }
        }
    }

    /**
     * 在交易外驗證所有使用者存在（同步 Feign 呼叫不應占用資料庫交易）
     *
     * @param userIds 要驗證的使用者 ID 集合
     * @throws IllegalArgumentException 當任一使用者不存在時拋出
     */
    private void validateUsersExist(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (UUID userId : userIds) {
            if (!userServiceFeignClient.existsUserById(userId)) {
                throw new IllegalArgumentException("User not found: " + userId);
            }
        }
    }

    /**
     * 查詢所有專案
     * @return 所有專案列表
     */
    @Override
    public List<ProjectVo> getProject() {
        return getProjectListCache().getData();
    }

    @Override
    @Cacheable(value = "projects", key = "'all'", sync = true)
    public CacheListWrapper<ProjectVo> getProjectListCache() {
        return transactionExecutor.executeReadOnly(() -> {
            List<ProjectVo> list = projectDataAccess.findAll().stream().map(projectMapper::toVo).toList();
            return new CacheListWrapper<>(list);
        });
    }
    /**
     * 刪除專案及其關聯的技能映射
     * @param project 要刪除的專案實體
     * @throws IllegalArgumentException 當參數驗證失敗或專案不存在時拋出
     */
    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "projects", key = "'all'"),
        @CacheEvict(value = "projectSkills", key = "#projectVo.id")
    })
    public void deleteProject(ProjectVo projectVo) {
        Project project = projectMapper.toEntity(projectVo);
        if (project.getId() == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        
        Project existingProject = projectDataAccess.findById(project.getId())
            .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        
        projectSkillDataAccess.deleteByProjectId(existingProject.getId());
        userProjectDataAccess.deleteByProjectId(existingProject.getId());
        projectDataAccess.deleteById(existingProject.getId());
    }
    
    @Override
    @Cacheable(value = "projects", key = "'search:' + #query.toString()", sync = true)
    public PageResult<ProjectVo> searchProjects(ProjectSearchQuery query) {
        return transactionExecutor.executeReadOnly(() -> {
            // 定義允許的排序欄位
            String[] allowedSortFields = {
                "id", "name", "description",
                "createdBy", "updatedBy", "createdTime", "updatedTime"
            };
            
            // 驗證排序欄位
            SortFieldValidator.validateSortField(query.getSortBy(), allowedSortFields);
            
            // 驗證排序方向
            SortFieldValidator.validateSortDirection(query.getSortDir());
            
            // 執行分頁查詢
            Page<Project> projectPage = projectDataAccess.searchProjects(query);
            
            // 轉換為 VO
            List<ProjectVo> projectVos = projectPage.getContent().stream()
                    .map(projectMapper::toVo)
                    .toList();
            
            // 返回分頁結果
            return PageResult.of(projectPage, projectVos);
        });
    }
    
    @Override
    public List<ProjectVo> getCurrentUserProjects() {
        return getCurrentUserProjectsCache(securityUtil.requireCurrentUserId().toString()).getData();
    }

    @Override
    @Cacheable(value = "projects", key = "'byuser:' + #currentUserId", sync = true)
    public CacheListWrapper<ProjectVo> getCurrentUserProjectsCache(String currentUserId) {
        return transactionExecutor.executeReadOnly(() -> {
            UUID currentUserIdUuid = UUID.fromString(currentUserId);
            // 透過 UserProject 關聯取得當前使用者的專案
            List<UserProject> userProjects = userProjectDataAccess.findByUserId(currentUserIdUuid);
            List<ProjectVo> list = userProjects.stream()
                    .map(UserProject::getProject)
                    .map(projectMapper::toVo)
                    .toList();
            return new CacheListWrapper<>(list);
        });
    }
    
    @Override
    public PageResult<ProjectVo> searchCurrentUserProjects(ProjectSearchQuery query) {
        return searchCurrentUserProjectsCache(securityUtil.requireCurrentUserId().toString(), query);
    }

    @Override
    @Cacheable(value = "projects", key = "'currentsearch:' + #currentUserId + ':' + #query.toString()", sync = true)
    public PageResult<ProjectVo> searchCurrentUserProjectsCache(String currentUserId, ProjectSearchQuery query) {
        return transactionExecutor.executeReadOnly(() -> {
            UUID currentUserIdUuid = UUID.fromString(currentUserId);
            
            // 定義允許的排序欄位
            String[] allowedSortFields = {
                "id", "name", "description",
                "createdBy", "updatedBy", "createdTime", "updatedTime"
            };
            
            // 驗證排序欄位
            SortFieldValidator.validateSortField(query.getSortBy(), allowedSortFields);
            
            // 驗證排序方向
            SortFieldValidator.validateSortDirection(query.getSortDir());
            
            // 執行分頁查詢（只查詢當前使用者的專案）
            Page<Project> projectPage = projectDataAccess.searchCurrentUserProjects(
                currentUserIdUuid.toString(), 
                query
            );
            
            // 轉換為 VO
            List<ProjectVo> projectVos = projectPage.getContent().stream()
                    .map(projectMapper::toVo)
                    .toList();
            
            // 返回分頁結果
            return PageResult.of(projectPage, projectVos);
        });
    }
    
    @Override
    public List<ProjectSkillVo> getProjectSkills(UUID projectId) {
        return getProjectSkillsCache(projectId).getData();
    }

    @Override
    @Cacheable(value = "projectSkills", key = "#projectId", sync = true)
    public CacheListWrapper<ProjectSkillVo> getProjectSkillsCache(UUID projectId) {
        return transactionExecutor.executeReadOnly(() -> {
            if (projectId == null) {
                throw new IllegalArgumentException("Project ID must not be null");
            }

            if (!projectDataAccess.existsById(projectId)) {
                throw new IllegalArgumentException("Project not found");
            }

            List<ProjectSkill> projectSkills = projectSkillDataAccess.findByProjectId(projectId);
            
            List<ProjectSkillVo> list = projectSkills.stream().map(ps -> {
                ProjectSkillVo vo = new ProjectSkillVo();
                vo.setProjectId(ps.getProject().getId());
                vo.setSkillId(ps.getSkill().getId());
                vo.setSkillName(ps.getSkill().getName());
                vo.setSkillDescription(ps.getSkill().getDescription());
                
                SkillLevel level = ps.getSkillLevel();
                if (level != null) {
                    vo.setSkillLevelId(level.getId());
                    vo.setLevelValue(level.getLevelValue());
                    vo.setLevelTitle(level.getTitle());
                    vo.setLevelDescription(level.getDescription());
                }
                return vo;
            }).collect(Collectors.toList());
            return new CacheListWrapper<>(list);
        });
    }

    @Override
    public List<ProjectSkillVo> getPersonalProjectSkills(UUID projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID must not be null");
        }
        
        UUID currentUserId = securityUtil.requireCurrentUserId();
        
        // 驗證是否為可見專案
        if (!userProjectDataAccess.existsByUserIdAndProjectId(currentUserId, projectId)) {
            throw new IllegalArgumentException("You do not have access to this project");
        }

        return getProjectSkills(projectId);
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "projects", key = "'all'")
    })
    public ProjectVo addPersonalProject(PersonalProjectRequest request) {
        // 驗證輸入
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name must not be null");
        }
        
        // 檢查名稱是否已存在
        if (!projectDataAccess.findByName(request.getName()).isEmpty()) {
            throw new IllegalArgumentException("Name already exists");
        }
        
        // 建立專案
        Project project = new Project();
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        Project savedProject = projectDataAccess.save(project);
        
        // 自動綁定當前使用者
        UUID currentUserId = securityUtil.requireCurrentUserId();
        UserProject userProject = new UserProject();
        userProject.setUserId(currentUserId);
        userProject.setProject(savedProject);
        userProjectDataAccess.save(userProject);
        
        evictUserProjectsCache(currentUserId);
        
        return projectMapper.toVo(savedProject);
    }
    
    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "projects", key = "'all'"),
        @CacheEvict(value = "projectSkills", key = "#projectId")
    })
    public void updatePersonalProject(UUID projectId, PersonalProjectRequest request) {
        // 驗證輸入
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID must not be null");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name must not be null");
        }
        
        // 查找專案
        Project project = projectDataAccess.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        
        // 驗證是否為擁有者
        if (!userProjectDataAccess.existsByUserIdAndProjectId(securityUtil.requireCurrentUserId(), projectId)) {
            throw new IllegalArgumentException("You are not the owner of this project");
        }

        UUID currentUserId = securityUtil.requireCurrentUserId();
        if (!canEditContent(project.getCreatedBy(), currentUserId)) {
            throw new IllegalArgumentException("Project assigned by admin is read-only");
        }
        
        // 更新專案資訊
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        projectDataAccess.save(project);
        
        evictUserProjectsCache(currentUserId);
    }
    
    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "projects", key = "'all'"),
        @CacheEvict(value = "projectSkills", key = "#projectId")
    })
    public void deletePersonalProject(UUID projectId) {
        // 驗證輸入
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID must not be null");
        }
        
        // 查找專案
        Project project = projectDataAccess.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        
        // 驗證是否為擁有者
        if (!userProjectDataAccess.existsByUserIdAndProjectId(securityUtil.requireCurrentUserId(), projectId)) {
            throw new IllegalArgumentException("You are not the owner of this project");
        }

        if (!canEditContent(project.getCreatedBy(), securityUtil.requireCurrentUserId())) {
            throw new IllegalArgumentException("Project assigned by admin is read-only");
        }
        
        // 刪除當前使用者與專案的綁定
        UUID currentUserId = securityUtil.requireCurrentUserId();
        userProjectDataAccess.deleteByUserIdAndProjectId(currentUserId, projectId);
        
        evictUserProjectsCache(currentUserId);
        
        // 檢查是否還有其他使用者綁定此專案
        boolean hasOtherBindings = userProjectDataAccess.existsByProjectId(projectId);
        
        // 如果沒有其他綁定，刪除專案本身及其技能綁定
        if (!hasOtherBindings) {
            projectSkillDataAccess.deleteByProjectId(projectId);
            projectDataAccess.deleteById(projectId);
        }
    }

    @Transactional
    @Override
    @CacheEvict(value = "projectSkills", key = "#projectId")
    public void bindPersonalProjectSkill(UUID projectId, UUID skillId, UUID skillLevelId) {
        UUID currentUserId = securityUtil.requireCurrentUserId();
        validateBindingInput(projectId, skillId, skillLevelId);
        ensureCanManageProjectBinding(projectId, currentUserId);
        ensureSkillVisibleToCurrentUser(skillId, currentUserId);

        if (projectSkillDataAccess.existsByProjectIdAndSkillId(projectId, skillId)) {
            throw new IllegalArgumentException("Skill already bind to project");
        }

        Project project = projectDataAccess.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        SkillLevel skillLevel = resolveAndValidateSkillLevel(skillId, skillLevelId);

        ProjectSkill projectSkill = new ProjectSkill();
        projectSkill.setProject(project);
        projectSkill.setSkill(skillDataAccess.findById(skillId).orElseThrow());
        projectSkill.setSkillLevel(skillLevel);
        projectSkillDataAccess.save(projectSkill);
    }

    @Transactional
    @Override
    @CacheEvict(value = "projectSkills", key = "#projectId")
    public void updatePersonalProjectSkillLevel(UUID projectId, UUID skillId, UUID skillLevelId) {
        UUID currentUserId = securityUtil.requireCurrentUserId();
        validateBindingInput(projectId, skillId, skillLevelId);
        ensureCanManageProjectBinding(projectId, currentUserId);
        ensureSkillVisibleToCurrentUser(skillId, currentUserId);

        ProjectSkill projectSkill = projectSkillDataAccess.findByProjectIdAndSkillId(projectId, skillId)
                .orElseThrow(() -> new IllegalArgumentException("Skill binding not found for project"));
        SkillLevel skillLevel = resolveAndValidateSkillLevel(skillId, skillLevelId);

        projectSkill.setSkillLevel(skillLevel);
        projectSkillDataAccess.save(projectSkill);
    }

    @Transactional
    @Override
    @CacheEvict(value = "projectSkills", key = "#projectId")
    public void unbindPersonalProjectSkill(UUID projectId, UUID skillId) {
        UUID currentUserId = securityUtil.requireCurrentUserId();
        if (projectId == null || skillId == null) {
            throw new IllegalArgumentException("Key must not be null");
        }

        ensureCanManageProjectBinding(projectId, currentUserId);

        if (!projectSkillDataAccess.existsByProjectIdAndSkillId(projectId, skillId)) {
            throw new IllegalArgumentException("Skill binding not found for project");
        }

        projectSkillDataAccess.deleteByProjectIdAndSkillId(projectId, skillId);
    }

    @Transactional
    @Override
    @CacheEvict(value = "projectSkills", key = "#projectId")
    public void rebindProjectSkills(UUID projectId, Map<UUID, UUID> skillLevelMapping) {
        if (projectId == null) {
            throw new IllegalArgumentException("Key must not be null");
        }

        Project project = projectDataAccess.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        Map<UUID, UUID> targetMap = normalizeSkillLevelMapping(skillLevelMapping);
        validateProjectSkillLevelMapping(targetMap);

        Map<UUID, ProjectSkill> existingMap = projectSkillDataAccess.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(ps -> ps.getSkill().getId(), ps -> ps));

        for (UUID existingSkillId : existingMap.keySet()) {
            if (!targetMap.containsKey(existingSkillId)) {
                projectSkillDataAccess.deleteByProjectIdAndSkillId(projectId, existingSkillId);
            }
        }

        for (Map.Entry<UUID, UUID> entry : targetMap.entrySet()) {
            UUID skillId = entry.getKey();
            UUID levelId = entry.getValue();
            ProjectSkill existing = existingMap.get(skillId);

            if (existing == null) {
                ProjectSkill projectSkill = new ProjectSkill();
                projectSkill.setProject(project);
                projectSkill.setSkill(skillDataAccess.findById(skillId).orElseThrow());
                projectSkill.setSkillLevel(skillLevelDataAccess.findById(levelId)
                        .orElseThrow(() -> new IllegalArgumentException("Skill level not found")));
                projectSkillDataAccess.save(projectSkill);
                continue;
            }

            UUID currentLevelId = existing.getSkillLevel() == null ? null : existing.getSkillLevel().getId();
            if (!Objects.equals(currentLevelId, levelId)) {
                existing.setSkillLevel(skillLevelDataAccess.findById(levelId)
                        .orElseThrow(() -> new IllegalArgumentException("Skill level not found")));
                projectSkillDataAccess.save(existing);
            }
        }
    }

    @Transactional
    @Override
    @CacheEvict(value = "projectSkills", key = "#projectId")
    public void rebindPersonalProjectSkills(UUID projectId, Map<UUID, UUID> skillLevelMapping) {
        UUID currentUserId = securityUtil.requireCurrentUserId();
        if (projectId == null) {
            throw new IllegalArgumentException("Key must not be null");
        }

        ensureCanManageProjectBinding(projectId, currentUserId);

        Map<UUID, UUID> targetMap = normalizeSkillLevelMapping(skillLevelMapping);
        for (UUID skillId : targetMap.keySet()) {
            ensureSkillVisibleToCurrentUser(skillId, currentUserId);
        }

        rebindProjectSkills(projectId, targetMap);
    }

    private void evictUserProjectsCache(UUID userId) {
        if (userId == null || cacheManager == null) {
            return;
        }
        Cache cache = cacheManager.getCache("projects");
        if (cache != null) {
            cache.evict("byuser:" + userId);
        }
    }

    private void validateBindingInput(UUID projectId, UUID skillId, UUID skillLevelId) {
        if (projectId == null || skillId == null || skillLevelId == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
    }

    private void ensureCanManageProjectBinding(UUID projectId, UUID currentUserId) {
        if (!userProjectDataAccess.existsByUserIdAndProjectId(currentUserId, projectId)) {
            throw new IllegalArgumentException("You are not allowed to manage bindings for this project");
        }
    }

    private void ensureSkillVisibleToCurrentUser(UUID skillId, UUID currentUserId) {
        Set<UUID> visibleSkillIds = userSkillDataAccess.findByUserId(currentUserId).stream()
                .map(userSkill -> userSkill.getSkill().getId())
                .collect(Collectors.toSet());

        List<UserProject> userProjects = userProjectDataAccess.findByUserId(currentUserId);
        if (!userProjects.isEmpty()) {
            List<UUID> projectIds = userProjects.stream()
                .map(up -> up.getProject().getId())
                .toList();
            projectSkillDataAccess.findByProjectIdIn(projectIds).stream()
                .map(ps -> ps.getSkill().getId())
                .forEach(visibleSkillIds::add);
        }

        if (!visibleSkillIds.contains(skillId)) {
            throw new IllegalArgumentException("Skill is not visible to current user");
        }
    }

    private SkillLevel resolveAndValidateSkillLevel(UUID skillId, UUID skillLevelId) {
        SkillLevel skillLevel = skillLevelDataAccess.findById(skillLevelId)
                .orElseThrow(() -> new IllegalArgumentException("Skill level not found"));
        if (!skillLevel.getSkill().getId().equals(skillId)) {
            throw new IllegalArgumentException("Skill level does not belong to skill");
        }
        return skillLevel;
    }

    private boolean canEditContent(String createdBy, UUID currentUserId) {
        if (createdBy == null || createdBy.isBlank()) {
            return true;
        }
        return createdBy.equals(currentUserId.toString());
    }

    private Map<UUID, UUID> normalizeSkillLevelMapping(Map<UUID, UUID> skillLevelMapping) {
        if (skillLevelMapping == null || skillLevelMapping.isEmpty()) {
            return Map.of();
        }

        Map<UUID, UUID> normalized = new LinkedHashMap<>();
        for (Map.Entry<UUID, UUID> entry : skillLevelMapping.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("Key must not be null");
            }
            normalized.put(entry.getKey(), entry.getValue());
        }
        return normalized;
    }

    private void validateProjectSkillLevelMapping(Map<UUID, UUID> mapping) {
        for (Map.Entry<UUID, UUID> entry : mapping.entrySet()) {
            UUID skillId = entry.getKey();
            UUID levelId = entry.getValue();
            SkillLevel level = skillLevelDataAccess.findById(levelId)
                    .orElseThrow(() -> new IllegalArgumentException("Skill level not found"));
            if (!level.getSkill().getId().equals(skillId)) {
                throw new IllegalArgumentException("Skill level does not belong to skill");
            }
        }
    }

    @Override
    public List<ProjectMemberSkillVo> getProjectMemberSkills(UUID projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID must not be null");
        }

        if (!projectDataAccess.existsById(projectId)) {
            throw new IllegalArgumentException("Project not found");
        }

        Map<UUID, List<UserProjectSkill>> bindingsByUser = userProjectSkillDataAccess.findByProjectId(projectId)
                .stream()
                .collect(Collectors.groupingBy(UserProjectSkill::getUserId));

        return userProjectDataAccess.findByProjectId(projectId).stream()
                .map(userProject -> {
                    UUID userId = userProject.getUserId();
                    ProjectMemberSkillVo vo = new ProjectMemberSkillVo();
                    vo.setUserId(userId.toString());
                    vo.setUserEmail("");

                    List<MemberSkillLevelVo> skills = bindingsByUser
                            .getOrDefault(userId, List.of())
                            .stream()
                            .map(this::toMemberSkillLevelVo)
                            .collect(Collectors.toCollection(ArrayList::new));
                    vo.setSkills(skills);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private MemberSkillLevelVo toMemberSkillLevelVo(UserProjectSkill binding) {
        MemberSkillLevelVo vo = new MemberSkillLevelVo();
        vo.setSkillId(binding.getSkill().getId().toString());
        vo.setSkillName(binding.getSkill().getName());

        SkillLevel level = binding.getSkillLevel();
        if (level != null) {
            vo.setSkillLevelId(level.getId().toString());
            vo.setLevelTitle(level.getTitle());
            vo.setLevelValue(level.getLevelValue());
        }
        return vo;
    }

    @Override
    public void rebindProjectMemberSkills(UUID projectId, Map<UUID, Map<UUID, UUID>> memberSkillsMap) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID must not be null");
        }
        if (memberSkillsMap == null) {
            memberSkillsMap = Map.of();
        }

        // 交易外的 Feign 驗證（使用者存在性）
        validateUsersExist(memberSkillsMap.keySet());
        self.doRebindProjectMemberSkills(projectId, memberSkillsMap);
    }

    /**
     * 交易內重新綁定成員技能（由 rebindProjectMemberSkills 在交易外的 Feign 驗證後呼叫）
     */
    @Transactional
    @CacheEvict(value = "projectSkills", key = "#projectId")
    public void doRebindProjectMemberSkills(UUID projectId, Map<UUID, Map<UUID, UUID>> memberSkillsMap) {
        // 驗證專案存在
        Project project = projectDataAccess.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        // 驗證所有使用者已綁定到該專案
        for (UUID userId : memberSkillsMap.keySet()) {
            if (!userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)) {
                throw new IllegalArgumentException(
                        "User " + userId + " is not a member of project " + projectId
                );
            }
        }

        // 驗證所有技能與等級存在
        for (Map<UUID, UUID> skillLevelMap : memberSkillsMap.values()) {
            for (Map.Entry<UUID, UUID> entry : skillLevelMap.entrySet()) {
                UUID skillId = entry.getKey();
                UUID levelId = entry.getValue();

                Skill skill = skillDataAccess.findById(skillId)
                        .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));
                SkillLevel skillLevel = skillLevelDataAccess.findById(levelId)
                        .orElseThrow(() -> new IllegalArgumentException("Skill level not found: " + levelId));

                if (!skillLevel.getSkill().getId().equals(skillId)) {
                    throw new IllegalArgumentException("Skill level does not belong to skill");
                }
            }
        }

        // 取得現有的 user_project_skill 綁定
        List<UserProjectSkill> existingBindings = userProjectSkillDataAccess.findByProjectId(projectId);
        Map<UUID, Map<UUID, UserProjectSkill>> existingMap = new HashMap<>();
        for (UserProjectSkill binding : existingBindings) {
            UUID userId = binding.getUserId();
            UUID skillId = binding.getSkill().getId();
            existingMap.computeIfAbsent(userId, k -> new HashMap<>()).put(skillId, binding);
        }

        // 刪除不在目標清單的綁定
        for (Map.Entry<UUID, Map<UUID, UserProjectSkill>> userEntry : existingMap.entrySet()) {
            UUID userId = userEntry.getKey();
            Map<UUID, UserProjectSkill> userSkills = userEntry.getValue();

            if (!memberSkillsMap.containsKey(userId)) {
                // 使用者不在目標清單，刪除該使用者在此專案的所有技能
                for (UUID skillId : userSkills.keySet()) {
                    userProjectSkillDataAccess.deleteByUserIdAndProjectIdAndSkillId(userId, projectId, skillId);
                }
            } else {
                // 使用者在目標清單，刪除不在目標技能清單的技能
                Map<UUID, UUID> targetSkills = memberSkillsMap.get(userId);
                for (UUID skillId : userSkills.keySet()) {
                    if (!targetSkills.containsKey(skillId)) {
                        userProjectSkillDataAccess.deleteByUserIdAndProjectIdAndSkillId(userId, projectId, skillId);
                    }
                }
            }
        }

        // 新增或更新目標綁定
        for (Map.Entry<UUID, Map<UUID, UUID>> memberEntry : memberSkillsMap.entrySet()) {
            UUID userId = memberEntry.getKey();
            Map<UUID, UUID> targetSkills = memberEntry.getValue();

            for (Map.Entry<UUID, UUID> skillEntry : targetSkills.entrySet()) {
                UUID skillId = skillEntry.getKey();
                UUID levelId = skillEntry.getValue();

                UserProjectSkill existingBinding = existingMap
                        .getOrDefault(userId, Map.of())
                        .get(skillId);

                if (existingBinding == null) {
                    // 新增
                    UserProjectSkill newBinding = new UserProjectSkill();
                    newBinding.setUserId(userId);
                    newBinding.setProject(project);
                    newBinding.setSkill(skillDataAccess.findById(skillId).orElseThrow());
                    newBinding.setSkillLevel(skillLevelDataAccess.findById(levelId).orElseThrow());
                    userProjectSkillDataAccess.save(newBinding);
                } else {
                    // 更新等級
                    UUID existingLevelId = existingBinding.getSkillLevel().getId();
                    if (!existingLevelId.equals(levelId)) {
                        SkillLevel newLevel = skillLevelDataAccess.findById(levelId).orElseThrow();
                        existingBinding.setSkillLevel(newLevel);
                        userProjectSkillDataAccess.save(existingBinding);
                    }
                }
            }
        }
    }

    @Override
    public ProjectVo getProjectById(UUID id) {
        Project project = projectDataAccess.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        return projectMapper.toVo(project);
    }

    @Override
    public boolean existsProjectSkillByLevelId(UUID levelId) {
        return projectSkillDataAccess.existsBySkillLevelId(levelId);
    }

    @Override
    public void deleteProjectSkillsBySkillId(UUID skillId) {
        projectSkillDataAccess.deleteBySkillId(skillId);
    }
}
