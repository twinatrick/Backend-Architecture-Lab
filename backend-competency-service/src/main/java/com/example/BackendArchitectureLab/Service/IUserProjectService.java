package com.example.BackendArchitectureLab.Service;

import java.util.List;
import java.util.UUID;

/**
 * 使用者專案綁定服務：負責 UserProject 關聯的完整覆蓋式綁定。
 */
public interface IUserProjectService {

    /**
     * 完整覆蓋式綁定使用者專案關聯（diff 策略：刪除多餘、新增缺少）。
     *
     * @param userId 使用者 ID
     * @param projectIds 目標專案 ID 清單
     */
    void rebindUserProjects(UUID userId, List<UUID> projectIds);
}
