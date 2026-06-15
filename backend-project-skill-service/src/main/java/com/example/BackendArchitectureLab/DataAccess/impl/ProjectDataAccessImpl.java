package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Search.ProjectSearchQuery;
import com.example.BackendArchitectureLab.Repository.ProjectRepository;
import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.DataAccess.specification.ProjectSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ProjectDataAccess 實現類
 * 封裝 Project 相關的數據庫操作,內部使用 JPA Repository
 */
@Component
public class ProjectDataAccessImpl implements IProjectDataAccess {
    
    private final ProjectRepository projectRepository;
    
    @Autowired
    public ProjectDataAccessImpl(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }
    
    @Override
    public Project save(Project project) {
        return projectRepository.save(project);
    }
    
    @Override
    public List<Project> findAll() {
        return projectRepository.findAll();
    }
    
    @Override
    public Optional<Project> findById(UUID id) {
        return projectRepository.findById(id);
    }
    
    @Override
    public List<Project> findByName(String name) {
        return projectRepository.findByName(name);
    }
    
    @Override
    public void delete(Project project) {
        projectRepository.delete(project);
    }

    @Override
    public void deleteById(UUID projectId) {
        projectRepository.deleteByIdHard(projectId);
    }
    
    @Override
    public boolean existsById(UUID id) {
        return projectRepository.existsById(id);
    }
    
    @Override
    public Page<Project> searchProjects(ProjectSearchQuery query) {
        // 建立排序
        Sort sort = Sort.by(
            "asc".equalsIgnoreCase(query.getNormalizedSortDir()) 
                ? Sort.Direction.ASC 
                : Sort.Direction.DESC,
            query.getSortBy()
        );
        
        // 建立分頁請求
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), sort);
        
        // 執行查詢
        return projectRepository.findAll(ProjectSpecification.buildSpecification(query), pageable);
    }
    
    @Override
    public Page<Project> searchCurrentUserProjects(String currentUserId, ProjectSearchQuery query) {
        // 將字串轉換為 UUID
        UUID userUuid = UUID.fromString(currentUserId);
        
        // 建立排序
        Sort sort = Sort.by(
            "asc".equalsIgnoreCase(query.getNormalizedSortDir()) 
                ? Sort.Direction.ASC 
                : Sort.Direction.DESC,
            query.getSortBy()
        );
        
        // 建立分頁請求
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), sort);
        
        // 執行查詢（包含使用者過濾條件）
        return projectRepository.findAll(
            ProjectSpecification.buildCurrentUserSpecification(userUuid, query), 
            pageable
        );
    }
}
