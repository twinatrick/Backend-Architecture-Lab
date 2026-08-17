package com.example.BackendArchitectureLab.Service;

import java.util.Map;
import java.util.UUID;

/**
 * IExternalSyncService - 專案成員技能綁定的外部系統同步抽象介面（DIP）。
 * <p>
 * 於本地事務 commit 之後執行（外部系統非資料庫，無法參與本機 rollback）。
 * 失敗時拋出例外，由呼叫端（ProjectUserBindingService）依補償流程寫入
 * FAILED 與 COMPENSATION_REQUIRED 事件，觸發後續還原。
 */
public interface IExternalSyncService {

    /**
     * 同步專案成員技能綁定至外部系統。
     *
     * @param projectId       專案 ID
     * @param memberSkillsMap 成員技能等級對應（userId -> (skillId -> levelId)）
     * @throws RuntimeException 外部同步失敗時拋出，語意由呼叫端承擔
     */
    void syncProjectMemberSkills(UUID projectId, Map<UUID, Map<UUID, UUID>> memberSkillsMap);
}