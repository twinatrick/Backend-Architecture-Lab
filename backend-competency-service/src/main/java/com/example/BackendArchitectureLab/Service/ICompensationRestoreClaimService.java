package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.BindingSnapshot;

import java.util.List;
import java.util.UUID;

/**
 * ICompensationRestoreClaimService - 補償還原認領與 fencing token 管理（M-02 拆分）。
 * 承載 restore 流程的原子認領（REQUIRES_NEW）、伺服器端 before-state 權威持久化/比對，
 * 與悲觀寫鎖下的持有者驗證，使 CompensationRestoreService 專注於還原流程編排。
 */
public interface ICompensationRestoreClaimService {

    /**
     * 原子認領補償還原事件（資料庫級 Idempotency Guard + Fencing Token）。
     * 以 eventId 主鍵在獨立交易（REQUIRES_NEW）中進行 atomic claim：
     * - 已存在且狀態為 SUCCESS → 拒絕認領（先前的消費者已完成處理）
     * - 全新 eventId → 以 PROCESSING 插入，並將 caller 提供的 before-state bindings 序列化
     *   持久化為該 eventId 的伺服器端權威來源（persisted before-state authority）
     * - 已存在 FAILED → 以新 token 直接接管（接管前先驗證 projectId 與 bindings 與已持久化者一致）
     * - 已存在 PROCESSING 且租約未到期 → 拒絕（他人仍在使用）
     * - 已存在 PROCESSING 且租約到期 → 僅當新 fencingVersion 更大時由 takeOverClaim CAS 接管
     * - 高並發下 concurrent insert 觸發主鍵衝突時 → 回傳 false 拒絕認領
     * <p>
     * 安全強化：同一 eventId 的後續請求，projectId 或 bindings 與首次認領時持久化的
     * before-state 不一致時，一律拋 IllegalArgumentException（非重試例外，呼叫端轉 DEAD）。
     *
     * @param eventId        補償事件 ID
     * @param projectId      專案 ID
     * @param ownerId        本次認領的處理者唯一識別碼（fencing token 之一）
     * @param fencingVersion 本次認領的代數（單調遞增，stale token 將被拒絕）
     * @param bindings       還原目標綁定快照明細（首次認領時持久化為權威 before-state）
     * @return true 表示成功取得認領權可執行還原，false 表示重複、他人持有中或 token 已過時
     */
    boolean claimRestoreEvent(UUID eventId, UUID projectId, String ownerId, Long fencingVersion,
                              List<BindingSnapshot> bindings);

    /**
     * 以悲觀寫鎖（PESSIMISTIC_WRITE）鎖定認領紀錄並驗證目前仍由指定持有者持有。
     * 鎖自本次查詢起持續持有至交易 commit，阻止其他執行緒的 takeOverClaim CAS；
     * 僅供已取得認領權的還原交易呼叫，於破壞性操作前再次確認 fencing token 未被接管。
     *
     * @param eventId        補償事件 ID
     * @param ownerId        預期持有者
     * @param fencingVersion 預期持有代數
     * @return 認領紀錄存在且持有者相符則回傳 true
     */
    boolean verifyFencingHeld(UUID eventId, String ownerId, Long fencingVersion);
}
