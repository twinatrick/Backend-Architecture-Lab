package com.example.BackendArchitectureLab.Service;

import java.util.UUID;

/**
 * ICompensationRestoreStateService - 補償還原認領日誌的狀態標記管理（M-02 拆分）。
 * 封裝 SUCCESS / FAILED 標記的交易所用語意（REQUIRED 加入還原交易、REQUIRES_NEW 獨立 commit），
 * 使 CompensationRestoreService 專注於還原流程編排，狀態標記與失敗排程集中於此。
 */
public interface ICompensationRestoreStateService {

    /**
     * 於現行交易內標記補償還原認領日誌為 SUCCESS（以 REQUIRED 加入還原交易，隨 commit 一起持久化）。
     *
     * @param eventId        補償事件 ID
     * @param ownerId        持有者（須與認領紀錄相符）
     * @param fencingVersion 持有代數（須與認領紀錄相符）
     */
    void markRestoreSuccess(UUID eventId, String ownerId, Long fencingVersion);

    /**
     * 以獨立交易 (REQUIRES_NEW) 標記補償還原認領日誌為 FAILED 並記錄失敗原因。
     *
     * @param eventId        補償事件 ID
     * @param ownerId        持有者（須與認領紀錄相符）
     * @param fencingVersion 持有代數（須與認領紀錄相符）
     * @param reason         失敗原因，寫入 lastError 欄位
     */
    void markRestoreFailed(UUID eventId, String ownerId, Long fencingVersion, String reason);

    /**
     * 排程於外層交易回滾後以獨立交易標記 FAILED；非交易環境下直接標記。
     * 供還原流程中任何會觸發交易回滾的例外使用，確保失敗狀態與錯誤訊息不因回滾而遺失。
     *
     * @param eventId        補償事件 ID
     * @param ownerId        持有者（須與認領紀錄相符）
     * @param fencingVersion 持有代數（須與認領紀錄相符）
     * @param e              觸發失敗的例外
     */
    void scheduleMarkRestoreFailed(UUID eventId, String ownerId, Long fencingVersion, Exception e);
}
