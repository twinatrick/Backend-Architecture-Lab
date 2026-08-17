package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Entity.CompensationEventLog;

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
     * @param entry 目前認領的處理紀錄
     */
    void markProcessed(CompensationEventLog entry);

    /**
     * 將 PROCESSING 標記為 FAILED，並依 attemptCount 計算下次重試時間（退避）。
     *
     * @param entry        目前認領的處理紀錄
     * @param errorMessage 失敗原因（自動截斷至 1024 字元）
     */
    void markFailed(CompensationEventLog entry, String errorMessage);

    /**
     * 將 PROCESSING 標記為 DEAD（不可重試錯誤或重試次數耗盡，隔離供人工介入）。
     *
     * @param entry        目前認領的處理紀錄
     * @param errorMessage 失敗原因（自動截斷至 1024 字元）
     */
    void markDead(CompensationEventLog entry, String errorMessage);
}
