package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.Entity.UserProject;
import com.example.BackendArchitectureLab.Mapper.ProjectMapper;
import com.example.BackendArchitectureLab.Service.IProjectQueryService;
import com.example.BackendArchitectureLab.Util.BatchCacheHelper;
import com.example.BackendArchitectureLab.Util.CacheExecutor;
import com.example.BackendArchitectureLab.Util.SecurityUtil;
import com.example.BackendArchitectureLab.Util.SearchSortPolicy;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Vo.BatchResultVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.ProjectVo;
import com.example.BackendArchitectureLab.Dto.Query.ProjectSearchQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ProjectQueryService - 專案查詢業務邏輯服務
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectQueryService implements IProjectQueryService {

    private static final SearchSortPolicy SEARCH_SORT_POLICY = new SearchSortPolicy(
            "id", "name", "description",
            "createdBy", "updatedBy", "createdTime", "updatedTime"
    );

    private final TransactionExecutor transactionExecutor;
    private final CacheExecutor cacheExecutor;
    private final IProjectDataAccess projectDataAccess;
    private final IUserProjectDataAccess userProjectDataAccess;
    private final SecurityUtil securityUtil;
    private final ProjectMapper projectMapper;
    private final CacheManager cacheManager;

    @Override
    public Flux<ServerSentEvent<List<ProjectVo>>> streamProjectsChunked(int chunkSize) {
        int effectiveSize = Math.max(10, Math.min(chunkSize, 1000));

        Flux<ServerSentEvent<List<ProjectVo>>> dataFlux = Flux.<ServerSentEvent<List<ProjectVo>>, Integer>generate(
                () -> 0,
                (page, sink) -> {
                    try {
                        Page<ProjectVo> projectPage = transactionExecutor.executeReadOnly(() -> {
                            Page<Project> entityPage = projectDataAccess.findAllPaged(
                                    PageRequest.of(page, effectiveSize, Sort.by("id").ascending())
                            );

                            if (entityPage.isEmpty()) {
                                return new PageImpl<>(List.<ProjectVo>of(), entityPage.getPageable(), entityPage.getTotalElements());
                            }

                            List<ProjectVo> voList = entityPage.getContent().stream()
                                    .map(projectMapper::toVo)
                                    .toList();

                            return new PageImpl<>(voList, entityPage.getPageable(), entityPage.getTotalElements());
                        });

                        if (projectPage.isEmpty()) {
                            sink.complete();
                            return page;
                        }

                        sink.next(ServerSentEvent.<List<ProjectVo>>builder()
                                .event("chunk")
                                .id(String.valueOf(page))
                                .data(projectPage.getContent())
                                .build());

                        if (!projectPage.hasNext()) {
                            sink.complete();
                            return page;
                        }

                        return page + 1;
                    } catch (Exception ex) {
                        log.error("專案 SSE 分塊串流分頁查詢失敗 [page={}]: {}", page, ex.getMessage(), ex);
                        sink.error(ex);
                        return page;
                    }
                }
        ).subscribeOn(Schedulers.boundedElastic());

        ServerSentEvent<List<ProjectVo>> completeEvent = ServerSentEvent.<List<ProjectVo>>builder()
                .event("complete")
                .data(List.of())
                .build();

        Flux<ServerSentEvent<List<ProjectVo>>> streamWithComplete = Flux.concat(dataFlux, Flux.just(completeEvent));

        Flux<ServerSentEvent<List<ProjectVo>>> heartbeatFlux = Flux.interval(Duration.ofSeconds(15))
                .map(tick -> ServerSentEvent.<List<ProjectVo>>builder()
                        .comment("keep-alive")
                        .build());

        return Flux.merge(streamWithComplete, heartbeatFlux)
                .takeUntil(event -> "complete".equals(event.event()))
                .doOnCancel(() -> log.info("客戶端中斷專案 SSE 串流連線"))
                .onErrorResume(ex -> {
                    log.error("專案 SSE 串流處理發生異常: {}", ex.getMessage(), ex);
                    return Flux.just(
                            ServerSentEvent.<List<ProjectVo>>builder()
                                    .event("error")
                                    .comment("串流處理發生異常，請聯繫管理員")
                                    .build()
                    );
                });
    }

    @Override
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
        String currentUserId = securityUtil.requireCurrentUserId().toString();
        return cacheExecutor.executeCacheableList("projects", "byuser:" + currentUserId, () ->
                transactionExecutor.executeReadOnly(() -> {
                    UUID currentUserIdUuid = UUID.fromString(currentUserId);
                    List<UserProject> userProjects = userProjectDataAccess.findByUserId(currentUserIdUuid);
                    return userProjects.stream()
                            .map(UserProject::getProject)
                            .map(projectMapper::toVo)
                            .toList();
                })
        );
    }

    @Override
    public PageResult<ProjectVo> searchCurrentUserProjects(ProjectSearchQuery query) {
        String currentUserId = securityUtil.requireCurrentUserId().toString();
        return transactionExecutor.executeReadOnly(() -> {
            UUID currentUserIdUuid = UUID.fromString(currentUserId);

            SEARCH_SORT_POLICY.validate(query.getSortBy(), query.getSortDir());

            Page<Project> projectPage = projectDataAccess.searchCurrentUserProjects(
                currentUserIdUuid.toString(),
                query
            );

            List<ProjectVo> projectVos = projectPage.getContent().stream()
                    .map(projectMapper::toVo)
                    .toList();

            return PageResult.of(projectPage, projectVos);
        });
    }

    @Override
    @Cacheable(value = "projects", key = "#id")
    public ProjectVo getProjectById(UUID id) {
        Project project = projectDataAccess.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        return projectMapper.toVo(project);
    }

    @Override
    public BatchResultVo<ProjectVo> getProjectsByIds(List<UUID> ids) {
        return BatchCacheHelper.fetchBatchWithCache(
                cacheManager,
                "projects",
                ids,
                missingIds -> projectDataAccess.findAllById(new ArrayList<>(missingIds)).stream()
                        .map(projectMapper::toVo)
                        .toList(),
                ProjectVo::getId
        );
    }
}
