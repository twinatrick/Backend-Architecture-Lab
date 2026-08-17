package com.example.BackendArchitectureLab.Service;

import java.util.Map;
import java.util.UUID;

/**
 * IExternalSyncService - 專案成員技能綁定的外部系統同步抽象介面（DIP）。
 * <p>
 * 由 {@code Timer/ExternalSyncWorker} 依 durable command（external_sync_command）可靠呼叫：
 * 命令與業務交易同 commit，因此本地 commit 與外部同步之間即使 JVM crash，
 * 同步仍會在 worker 重試時執行，不再存在 crash window。
 * 失敗時拋出例外，由 worker 依退避重試；重試耗盡（DEAD）時才觸發補償閉環。
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