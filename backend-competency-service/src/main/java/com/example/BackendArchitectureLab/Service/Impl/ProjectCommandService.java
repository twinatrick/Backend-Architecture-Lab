package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IProjectSkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.Entity.UserProject;
import com.example.BackendArchitectureLab.Mapper.ProjectMapper;
import com.example.BackendArchitectureLab.Service.IProjectCommandService;
import com.example.BackendArchitectureLab.Service.IProjectUserBindingService;
import com.example.BackendArchitectureLab.Util.SecurityUtil;
import com.example.BackendArchitectureLab.Vo.PersonalProjectRequest;
import com.example.BackendArchitectureLab.Vo.ProjectVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * ProjectCommandService - 專案寫入業務邏輯服務
 */
@Service
public class ProjectCommandService implements IProjectCommandService {

    @Autowired
    private IProjectDataAccess projectDataAccess;
    @Autowired
    private IProjectSkillDataAccess projectSkillDataAccess;
    @Autowired
    private IUserProjectDataAccess userProjectDataAccess;
    @Autowired
    private SecurityUtil securityUtil;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private IProjectUserBindingService projectUserBindingService;

    @Autowired
    @Lazy
    private ProjectCommandService self;

    /**
     * 新增專案
     * @param projectVo 要新增的專案 VO
     * @return 保存後的專案 VO
     * @throws IllegalArgumentException 當參數驗證失敗時拋出
     */
    @Override
    public ProjectVo addProject(ProjectVo projectVo) {
        if (projectVo.getUserIds() != null) {
            projectUserBindingService.validateUsersExist(projectVo.getUserIds().stream().map(UUID::fromString).toList());
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
            projectUserBindingService.bindUsersToProject(savedProject.getId(), projectVo.getUserIds());
            for (String uid : projectVo.getUserIds()) {
                projectUserBindingService.evictUserProjectsCache(UUID.fromString(uid));
            }
        }

        return projectMapper.toVo(savedProject);
    }

    /**
     * 更新專案
     * @param projectVo 要更新的專案 VO
     * @throws IllegalArgumentException 當參數驗證失敗時拋出
     */
    @Override
    public void updateProject(ProjectVo projectVo) {
        if (projectVo.getUserIds() != null) {
            projectUserBindingService.validateUsersExist(projectVo.getUserIds().stream().map(UUID::fromString).toList());
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
                projectUserBindingService.bindUsersToProject(project.getId(), projectVo.getUserIds());
            }
            for (String uid : projectVo.getUserIds()) {
                projectUserBindingService.evictUserProjectsCache(UUID.fromString(uid));
            }
        }
    }

    /**
     * 刪除專案及其關聯的技能映射
     * @param projectVo 要刪除的專案 VO
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

        projectUserBindingService.evictUserProjectsCache(currentUserId);

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

        projectUserBindingService.evictUserProjectsCache(currentUserId);
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

        projectUserBindingService.evictUserProjectsCache(currentUserId);

        // 檢查是否還有其他使用者綁定此專案
        boolean hasOtherBindings = userProjectDataAccess.existsByProjectId(projectId);

        // 如果沒有其他綁定，刪除專案本身及其技能綁定
        if (!hasOtherBindings) {
            projectSkillDataAccess.deleteByProjectId(projectId);
            projectDataAccess.deleteById(projectId);
        }
    }

    private boolean canEditContent(String createdBy, UUID currentUserId) {
        if (createdBy == null || createdBy.isBlank()) {
            return true;
        }
        return createdBy.equals(currentUserId.toString());
    }
}
