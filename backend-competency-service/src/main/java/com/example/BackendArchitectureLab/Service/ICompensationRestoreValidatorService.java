package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.BindingSnapshot;

import java.util.List;
import java.util.UUID;

/**
 * ICompensationRestoreValidatorService - 補償還原的綁定驗證與冪等比對（M-02 拆分）。
 * 封裝還原前驗證（membership / skill / level 檢核）與「目前綁定是否已等於還原目標」
 * 的冪等比對，使 CompensationRestoreService 專注於還原流程編排。
 */
public interface ICompensationRestoreValidatorService {

    /**
     * 冪等還原比對：判斷該專案目前的技能綁定是否已等於還原目標（binding snapshot）。
     *
     * @param projectId 專案 ID
     * @param bindings  還原目標快照明細（null 視為空集合）
     * @return 目前綁定與目標相等則回傳 true
     */
    boolean isBindingsAlreadyRestored(UUID projectId, List<BindingSnapshot> bindings);

    /**
     * 還原前驗證綁定明細（於 DELETE 等破壞性操作之前呼叫）。
     * 任何不符皆拋出 IllegalArgumentException（非重試例外），使 malformed / 越權 payload 直接轉為 DEAD。
     *
     * @param projectId 專案 ID（membership 驗證基準）
     * @param bindings  歷史綁定快照明細（型別化 DTO）
     */
    void validateBindingsForRestore(UUID projectId, List<BindingSnapshot> bindings);
}
