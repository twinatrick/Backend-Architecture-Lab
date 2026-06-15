package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Dto.Vo.Search.ProjectSearchQuery;
import com.example.BackendArchitectureLab.Entity.Project;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Project 數據訪問介面
 * 定義 Project 相關的數據操作方法
 * 
 * 此介面遵循依賴反轉原則,使 Service 層依賴抽象而非具體實現
 */
public interface IProjectDataAccess {
    
    /**
     * 保存 Project (新增或更新)
     * @param project 要保存的專案實體
     * @return 保存後的專案實體
     */
    Project save(Project project);
    
    /**
     * 查詢所有 Project
     * @return 所有專案列表
     */
    List<Project> findAll();
    
    /**
     * 根據 ID 查詢 Project
     * @param id 專案 ID
     * @return Optional 包裝的專案實體
     */
    Optional<Project> findById(UUID id);
    
    /**
     * 根據名稱查詢 Project
     * @param name 專案名稱
     * @return 符合條件的專案列表
     */
    List<Project> findByName(String name);
    
    /**
     * 刪除 Project
     * @param project 要刪除的專案實體
     */
    void delete(Project project);

    void deleteById(UUID projectId);
    
    /**
     * 檢查 Project 是否存在
     * @param id 專案 ID
     * @return 存在返回 true,否則返回 false
     */
    boolean existsById(UUID id);
    
    /**
     * 分頁查詢專案
     *
     * @param query 查詢參數
     * @return 分頁結果
     */
    Page<Project> searchProjects(ProjectSearchQuery query);
    
    /**
     * 分頁查詢當前使用者的專案
     *
     * @param currentUserId 當前使用者ID
     * @param query 查詢參數
     * @return 分頁結果
     */
    Page<Project> searchCurrentUserProjects(String currentUserId, ProjectSearchQuery query);
}
