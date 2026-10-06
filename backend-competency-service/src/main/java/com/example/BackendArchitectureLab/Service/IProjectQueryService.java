package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import com.example.BackendArchitectureLab.Vo.ProjectVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.Search.ProjectSearchQuery;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * IProjectQueryService - 專案查詢操作
 */
public interface IProjectQueryService {

    /**
     * 以響應式分塊 SSE 串流方式取得專案列表
     * @param chunkSize 每次推送筆數
     * @return Server-Sent Events 串流
     */
    Flux<ServerSentEvent<List<ProjectVo>>> streamProjectsChunked(int chunkSize);

    /**
     * 分頁搜尋專案
     *
     * @param query 搜尋查詢參數
     * @return 分頁結果
     */
    PageResult<ProjectVo> searchProjects(ProjectSearchQuery query);

    /**
     * 取得當前使用者的專案列表
     *
     * @return 當前使用者的專案列表
     */
    List<ProjectVo> getCurrentUserProjects();

    CacheListWrapper<ProjectVo> getCurrentUserProjectsCache(String currentUserId);

    /**
     * 分頁搜尋當前使用者的專案
     *
     * @param query 搜尋查詢參數
     * @return 分頁結果
     */
    PageResult<ProjectVo> searchCurrentUserProjects(ProjectSearchQuery query);

    PageResult<ProjectVo> searchCurrentUserProjectsCache(String currentUserId, ProjectSearchQuery query);

    ProjectVo getProjectById(UUID id);
}
