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
     * 僅當命令目前仍由相同 ownerId + fencingVersion 持有且仍為 PROCESSING 時才標記 DEAD 並寫入補償請求，
     * 若已被其他實例接管（stale token）則略過，避免過期 worker 誤發補償回滾。
     *
     * @param commandId      外部同步命令 ID（將被標記 DEAD）
     * @param ownerId        認領者唯一識別碼
     * @param fencingVersion 認領代數
     * @param transactionId  補償交易 ID
     * @param beforeState    同步前的一致性快照（補償還原所需），null 時存空 map
     * @param errorMessage   失敗訊息（記錄於命令與補償事件）
     * @return true 表示成功標記並寫入補償請求；false 表示為 stale token 略過
     */
    boolean markDeadAndEnqueueCompensation(UUID commandId, String ownerId, Long fencingVersion,
                                           UUID transactionId, Map<String, Object> beforeState,
                                           String errorMessage);
}