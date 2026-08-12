package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.PersonalProjectRequest;
import com.example.BackendArchitectureLab.Vo.ProjectVo;

import java.util.UUID;

/**
 * IProjectCommandService - 專案寫入操作（新增/更新/刪除/個人專案）
 */
public interface IProjectCommandService {

    /**
     * 新增專案
     * @param projectVo 要新增的專案 VO
     * @return 保存後的專案 VO
     * @throws IllegalArgumentException 當參數驗證失敗時拋出
     */
    ProjectVo addProject(ProjectVo projectVo);

    /**
     * 更新專案
     * @param projectVo 要更新的專案 VO
     * @throws IllegalArgumentException 當參數驗證失敗時拋出
     */
    void updateProject(ProjectVo projectVo);

    /**
     * 刪除專案及其關聯的技能映射
     * @param projectVo 要刪除的專案 VO
     * @throws IllegalArgumentException 當參數驗證失敗或專案不存在時拋出
     */
    void deleteProject(ProjectVo projectVo);

    /**
     * 新增個人專案（自動綁定當前使用者）
     *
     * @param request 個人專案請求
     * @return 新增的專案 VO
     */
    ProjectVo addPersonalProject(PersonalProjectRequest request);

    /**
     * 修改個人專案（僅限擁有者）
     *
     * @param projectId 專案 ID
     * @param request 個人專案請求
     */
    void updatePersonalProject(UUID projectId, PersonalProjectRequest request);

    /**
     * 刪除個人專案（僅限擁有者）
     *
     * @param projectId 專案 ID
     */
    void deletePersonalProject(UUID projectId);
}
