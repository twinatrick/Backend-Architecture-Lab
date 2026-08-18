package com.example.BackendArchitectureLab.Service;

import java.util.UUID;

/**
 * ICompensationStateService - 補償事件終態（PROCESSED / FAILED / DEAD）標記。
 * <p>
 * 所有終態轉移皆透過 {@code markState} 條件式 UPDATE（eventId + ownerId + fencingVersion
 * + 目前 status）做 CAS，避免舊租約持有者（stale worker）覆寫新持有者的結果。
 */
public interface ICompensationStateService {

    /**
     * 將 PROCESSING 標記為 PROCESSED（補償成功／無需補償）。
     *
     * @param eventId        補償事件 ID
     * @param ownerId        持有者 ID
     * @param fencingVersion 持有代數
     */
    void markProcessed(UUID eventId, String ownerId, Long fencingVersion);

    /**
     * 將 PROCESSING 標記為 FAILED，並依 attemptCount 計算下次重試時間（退避）。
     *
     * @param eventId        補償事件 ID
     * @param ownerId        持有者 ID
     * @param fencingVersion 持有代數
     * @param attemptCount   目前嘗試次數（用於退避計算）
     * @param errorMessage   錯誤訊息（截斷至 1024 字元）
     */
    void markFailed(UUID eventId, String ownerId, Long fencingVersion, int attemptCount, String errorMessage);

    /**
     * 將 PROCESSING 標記為 DEAD（不可重試錯誤或重試次數耗盡，隔離供人工介入）。
     *
     * @param eventId        補償事件 ID
     * @param ownerId        持有者 ID
     * @param fencingVersion 持有代數
     * @param errorMessage   錯誤訊息（截斷至 1024 字元）
     */
    void markDead(UUID eventId, String ownerId, Long fencingVersion, String errorMessage);
}
