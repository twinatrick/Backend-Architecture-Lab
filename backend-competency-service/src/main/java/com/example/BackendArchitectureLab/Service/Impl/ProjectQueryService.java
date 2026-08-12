package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.Entity.UserProject;
import com.example.BackendArchitectureLab.Mapper.ProjectMapper;
import com.example.BackendArchitectureLab.Service.IProjectQueryService;
import com.example.BackendArchitectureLab.Util.SecurityUtil;
import com.example.BackendArchitectureLab.Util.SearchSortPolicy;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.ProjectVo;
import com.example.BackendArchitectureLab.Vo.Search.ProjectSearchQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * ProjectQueryService - 專案查詢業務邏輯服務
 */
@Service
public class ProjectQueryService implements IProjectQueryService {

    private static final SearchSortPolicy SEARCH_SORT_POLICY = new SearchSortPolicy(
            "id", "name", "description",
            "createdBy", "updatedBy", "createdTime", "updatedTime"
    );

    @Autowired
    private TransactionExecutor transactionExecutor;
    @Autowired
    private IProjectDataAccess projectDataAccess;
    @Autowired
    private IUserProjectDataAccess userProjectDataAccess;
    @Autowired
    private SecurityUtil securityUtil;
    @Autowired
    private ProjectMapper projectMapper;

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

    @Override
    @Cacheable(value = "projects", key = "'search:' + #query.toString()", sync = true)
    public PageResult<ProjectVo> searchProjects(ProjectSearchQuery query) {
        return transactionExecutor.executeReadOnly(() -> {
            SEARCH_SORT_POLICY.validate(query.getSortBy(), query.getSortDir());

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

            SEARCH_SORT_POLICY.validate(query.getSortBy(), query.getSortDir());

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
    public ProjectVo getProjectById(UUID id) {
        Project project = projectDataAccess.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        return projectMapper.toVo(project);
    }
}
