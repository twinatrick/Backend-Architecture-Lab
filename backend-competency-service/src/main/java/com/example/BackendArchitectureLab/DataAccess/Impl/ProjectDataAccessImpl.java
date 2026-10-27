package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.Dto.Query.ProjectSearchQuery;
import com.example.BackendArchitectureLab.Repository.ProjectRepository;
import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.DataAccess.specification.ProjectSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ProjectDataAccess 實現類
 * 封裝 Project 相關的數據庫操作,內部使用 JPA Repository
 */
@Component
@RequiredArgsConstructor
public class ProjectDataAccessImpl implements IProjectDataAccess {

    private static final long UNFILTERED_COUNT_TTL_NANOS = 5_000_000_000L;
    private static final long COUNT_CACHE_MIN_ROWS = 1_000L;

    private final ProjectRepository projectRepository;

    private volatile long cachedUnfilteredCount = -1L;
    private volatile long cachedCountTimestampNanos = 0L;
    
    @Override
    public Project save(Project project) {
        return projectRepository.save(project);
    }

    @Override
    public Project saveAndFlush(Project project) {
        return projectRepository.saveAndFlush(project);
    }
    
    @Override
    public Page<Project> findAllPaged(Pageable pageable) {
        return projectRepository.findAll(pageable);
    }

    @Override
    public List<Project> findAll() {
        return projectRepository.findAll();
    }

    @Override
    public List<Project> findAllById(List<UUID> ids) {
        return projectRepository.findAllById(ids);
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
        Sort sort = Sort.by(
            "asc".equalsIgnoreCase(query.getNormalizedSortDir()) 
                ? Sort.Direction.ASC 
                : Sort.Direction.DESC,
            query.getSortBy()
        );

        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), sort);

        if (isUnfilteredQuery(query)) {
            List<UUID> pagedIds = projectRepository.findPagedIds(pageable);
            long total = getUnfilteredTotalCount(pagedIds.size(), pageable);
            if (pagedIds.isEmpty()) {
                return new PageImpl<>(Collections.emptyList(), pageable, total);
            }
            Map<UUID, Project> projectMap = projectRepository.findAllById(pagedIds)
                    .stream()
                    .collect(Collectors.toMap(Project::getId, p -> p, (existing, replacement) -> existing));
            List<Project> ordered = pagedIds.stream()
                    .map(projectMap::get)
                    .filter(p -> p != null)
                    .toList();
            return new PageImpl<>(ordered, pageable, total);
        }

        return projectRepository.findAll(ProjectSpecification.buildSpecification(query), pageable);
    }

    private boolean isUnfilteredQuery(ProjectSearchQuery query) {
        if (query == null) {
            return true;
        }
        return isBlank(query.getName())
                && isBlank(query.getDescription())
                && isBlank(query.getCreatedBy());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private long getUnfilteredTotalCount(int fetchedSize, Pageable pageable) {
        if (pageable.getOffset() == 0 && fetchedSize < pageable.getPageSize()) {
            return fetchedSize;
        }
        long now = System.nanoTime();
        long cached = cachedUnfilteredCount;
        if (cached >= COUNT_CACHE_MIN_ROWS && (now - cachedCountTimestampNanos) < UNFILTERED_COUNT_TTL_NANOS) {
            return cached;
        }
        long exactCount = projectRepository.count();
        if (exactCount >= COUNT_CACHE_MIN_ROWS) {
            cachedUnfilteredCount = exactCount;
            cachedCountTimestampNanos = now;
        }
        return exactCount;
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
