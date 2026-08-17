package com.example.BackendArchitectureLab.Service;

import java.util.Map;
import java.util.UUID;

/**
 * IExternalSyncCommandService - 外部同步 durable command 寫入抽象介面。
 * <p>
 * 命令與業務交易同 commit 寫入（external_sync_command），由 {@code Timer/ExternalSyncWorker}
 * 可靠執行，確保「本地 DB commit → 外部同步」之間即使 JVM crash 也不會永久遺失同步動作。
 */
public interface IExternalSyncCommandService {

    /**
     * 是否已啟用外部同步（external-sync.enabled）：
     * 未啟用時 {@link #enqueue} 為 no-op，也不需建立任何 command。
     */
    boolean isEnabled();

    /**
     * 在業務交易內寫入一筆外部同步命令（與業務同 commit，rollback 時一併消失）。
     *
     * @param transactionId   補償交易 ID
     * @param projectId       專案 ID
     * @param memberSkillsMap 要同步的目標綁定（userId -> (skillId -> levelId)）
     * @param beforeState     同步前的一致性快照（補償還原所需），null 時存空 map
     */
    void enqueue(UUID transactionId, UUID projectId, Map<UUID, Map<UUID, UUID>> memberSkillsMap,
                 Map<String, Object> beforeState);

    /**
     * 將外部同步命令標記為 DEAD 並在同一交易內寫入補償請求（FAILED + COMPENSATION_REQUIRED）。
     * <p>
     * 三個操作收斂於同一本地交易：DEAD 標記、FAILED 事件、COMPENSATION_REQUIRED 事件
     * 要嘛一起 commit、要嘛一起 rollback，消除「命令已 DEAD 但補償事件遺失」的 window。
     *
     * @param commandId     外部同步命令 ID（將被標記 DEAD）
     * @param transactionId 補償交易 ID
     * @param beforeState   同步前的一致性快照（補償還原所需），null 時存空 map
     * @param errorMessage  失敗訊息（記錄於命令與補償事件）
     */
    void markDeadAndEnqueueCompensation(UUID commandId, UUID transactionId, Map<String, Object> beforeState,
                                        String errorMessage);
}