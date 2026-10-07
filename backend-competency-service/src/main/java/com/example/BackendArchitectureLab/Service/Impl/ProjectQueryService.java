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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
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
    private final IProjectDataAccess projectDataAccess;
    private final IUserProjectDataAccess userProjectDataAccess;
    private final SecurityUtil securityUtil;
    private final ProjectMapper projectMapper;

    @Override
    public Flux<ServerSentEvent<List<ProjectVo>>> streamProjectsChunked(int chunkSize) {
        int effectiveSize = Math.max(10, Math.min(chunkSize, 1000));

        Flux<ServerSentEvent<List<ProjectVo>>> dataFlux = Flux.<ServerSentEvent<List<ProjectVo>>, Integer>generate(
                () -> 0,
                (page, sink) -> {
                    try {
                        Page<Project> projectPage = transactionExecutor.executeReadOnly(() ->
                                projectDataAccess.findAllPaged(
                                        PageRequest.of(page, effectiveSize, Sort.by("id").ascending())
                                )
                        );

                        if (projectPage.isEmpty()) {
                            sink.complete();
                            return page;
                        }

                        List<ProjectVo> voList = projectPage.getContent().stream()
                                .map(projectMapper::toVo)
                                .toList();

                        sink.next(ServerSentEvent.<List<ProjectVo>>builder()
                                .event("chunk")
                                .id(String.valueOf(page))
                                .data(voList)
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

        return Flux.merge(streamWithComplete, heartbeatFlux.takeUntilOther(streamWithComplete.ignoreElements()))
                .doOnCancel(() -> log.info("客戶端中斷專案 SSE 串流連線"))
                .onErrorResume(ex -> {
                    log.error("專案 SSE 串流處理發生異常: {}", ex.getMessage(), ex);
                    return Flux.just(
                            ServerSentEvent.<List<ProjectVo>>builder()
                                    .event("error")
                                    .comment("串流處理發生異常，請聯繫管理員")
                                    .data(List.of())
                                    .build()
                    );
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
