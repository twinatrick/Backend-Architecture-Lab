# Project Skills Compensation Restore Mechanism

我們需要一種方法，在外部夥伴系統同步失敗等非同步後續流程發生異常時，將 `competency-service` 中的專案成員技能綁定還原至經過驗證的歷史 snapshot 快照。

## Context

`PROJECT_MEMBER_SKILLS_REBIND` 操作在本地是資料庫交易事務（`@Transactional`），但在其成功 commit 後會附加一個不可事務性 Rollback 的外部同步動作。若此外部同步失敗，系統會觸發補償機制（Compensation Event）。由於本地事務已成功 Commit，我們必須執行一個非同步的交易補償，將資料庫中的專案技能狀態回復到重綁定之前的原始模樣。

## Decision

我們決定針對 `PROJECT_MEMBER_SKILLS_REBIND` 補償動作，採用「完全擦除並重建（full-wipe and reconstruct）」的強等冪還原模式。
具體做法如下：
1.  在補償事件的 `beforeState` 中，完整捕獲專案重綁定之前的原始綁定歷史清單明細（包含 `userId`, `skillId`, `levelId`）。
2.  在 `competency-service` 中，暴露一個內網且防重防衝突的 `/project/inner/skills/restore` POST 介面。
3.  在消費補償策略 `ProjectMemberSkillsRebindCompensationStrategy` 中，透過 OpenFeign 用戶端呼叫上述還原端點，並傳遞 `eventId` 作為 `Idempotency-Key` 標頭、專案的樂觀鎖版本 `expectedVersion`，以及 fencing token 標頭 `X-Fencing-Owner` / `X-Fencing-Version`。
4.  還原 API 採用 **應用級去重（Atomic Idempotency Claim）**、**樂觀並發防護（JPA @Version Guard）** 與 **Fencing Token 全鏈路** 三重安全守衛：
    - 以 `REQUIRES_NEW` 獨立交易對 `compensation_restore_log`（以 `eventId` 主鍵 + `@Version` 樂觀鎖）進行原子認領：INSERT 路徑靠主鍵唯一鍵原子（高並發撞鍵 → `DataIntegrityViolationException` → 拒絕認領），UPDATE 路徑靠 `@Version` 樂觀鎖 CAS（並發更新落敗者丟 `ObjectOptimisticLockingFailureException` → 拒絕認領）；僅取得認領權的實例能執行還原。已存在且為 SUCCESS 則跳過。認領紀錄一併記下目前持有者的 `ownerId` 與 `fencingVersion`。
    - **Fencing Token 認領守衛**：`CompensationEventProcessor` 認領時產生新 `ownerId`（UUID）並使 `fencingVersion` 單調遞增，隨事件鏈路原樣傳遞至還原端。`claimRestoreEvent` 對已存在的 `PROCESSING` 紀錄：租約未到期 → 拒絕認領（仍有處理者）；租約已到期 → 僅當新 `fencingVersion` 更大時以 `takeOverClaim` CAS 接管，stale token（`>=` 現值）一律拒絕，防止舊代數持有者搶奪新租約。
    - 比對資料庫中專案的當前 `@Version` 樂觀鎖是否等於 snapshot 中預期的 `expectedVersion`。若不一致則拋出 `CompensationConflictException` 拒絕執行（防止覆蓋並行的最新合法修改）。
    - **Commit-time 版本守衛**：僅讀取值不比對不足以封閉 TOCTOU 窗口，還原執行前會 touch Project（更新 `updatedTime` 並 save），使 JPA `@Version` 在交易 commit 時執行真正的 CAS 比對（`UPDATE ... WHERE version=舊值`）；若期間被其他合法交易推進版本，commit 時丟 `OptimisticLockException` → 整個還原 rollback。
    - **破壞性操作前 re-validation（結合 DB 級悲觀鎖）**：刪除現存綁定之前以 `findByIdForUpdate` 對認領紀錄 row 持 `PESSIMISTIC_WRITE` 悲觀寫鎖（鎖持有至外層還原交易 commit），並同時再次驗證 token 確認仍由本次 `ownerId + fencingVersion` 持有。鎖定後其他執行緒的 `takeOverClaim` CAS 會被資料列鎖阻塞，待本交易 commit 後其 predicate（狀態已非可接管）重新評估即失敗，使舊 token 在資料庫層真正失效，封閉 check-then-act 的 TOCTOU 窗口——舊持有者即使在讀取後才被接管，也無法執行破壞性刪除。
5.  還原的**結果標記延後至外層交易 commit/rollback 之後**，並以獨立交易邊界執行：認領（PROCESSING）以 `REQUIRES_NEW` 先 commit；還原成功後以 `afterCommit` 回呼觸發 `REQUIRES_NEW` 的 `markRestoreSuccess` 標記 SUCCESS（僅在外層還原交易真正 commit 成功後才標記，commit 失敗則不寫入、log 維持 PROCESSING，待租約到期由 reclaimer 回收重試，避免「log=SUCCESS 但實際未還原」的事件永久不再補償）；還原失敗（含暫態錯誤）以 `afterCompletion`（狀態非 COMMITTED）回呼觸發 `REQUIRES_NEW` 的 `markRestoreFailed` 標記 FAILED 並寫入 `lastError` 後再對外 rethrow——延後到鎖釋放後再標記，避免外層持悲觀鎖時開新交易更新同一 row 造成死鎖。`markRestoreSuccess` / `markRestoreFailed` 皆透過 `markRestoreState` 以 `ownerId + fencingVersion` 為 WHERE 條件的 CAS 更新，token 已被更新代數接管則不覆寫（`log.warn`），確保只有最新一代持有者能標記結果。還原本體在 `@Transactional` 中執行：先調用 `userProjectSkillDataAccess.deleteByProjectId(projectId)` 清空該專案的所有現存技能綁定，再依 `beforeState` 記錄逐一 save 重建歷史綁定。
6.  **過期租約回收排程**：消費端認領後若在標記完成前 crash 且不再被 Kafka 重新投遞，`compensation_event_log` 會永久停留在 `PROCESSING`。`CompensationLeaseReclaimer`（alert，`@Scheduled` 預設 60 秒）定期以 CAS（`reclaimLease`，產生新 `ownerId` 並遞增 `fencingVersion`）重新認領租約已過期的紀錄，再從 stored `payload`（認領時以 `ObjectMapper` 序列化的完整 `CompensationEvent`）反序列化原事件並重新執行補償；單一事件失敗不中斷整批，下一輪重試。

## Consequences

- **強等冪性 (Idempotency)**：還原 API 以資料庫主鍵 + `@Version` 樂觀鎖進行原子認領（`compensation_restore_log`）取代非原子的 exists-then-insert 檢查，避免多實例並行消費重複還原，配合 `Idempotency-Key` 實現高可靠的等冪保證。
- **並發安全性 (Concurrency Safety)**：雙層防護——比對快照 `expectedVersion` 快速失敗，再於 commit 時 touch Project 觸發 `@Version` CAS 封閉 TOCTOU 窗口，杜絕補償機制覆蓋後續合法修改的 Concurrency Risks，保障最終一致性的可靠與安全。
- **Fencing Token 防止舊租約覆寫**：`compensation_event_log` 與 `compensation_restore_log` 各持有獨立的 `ownerId + fencingVersion`；接管路徑（`reclaimLease`、`takeOverClaim`）與結果標記（`markRestoreState`）皆以 token 作為 CAS 條件，還原交易期間對 `compensation_restore_log` row 持 `PESSIMISTIC_WRITE` 悲觀鎖使接管在 DB 層被阻塞/失效。確保 crash recovery 後由最新一代持有者接手，舊持有者既無法執行破壞性還原也無法覆寫新結果。
- **Crash Recovery 自動化**：`CompensationLeaseReclaimer` 排程定期回收滯留 `PROCESSING` 的過期租約並從 `payload` 還原事件重跑，避免 Kafka 不再投遞時事件永久卡死；單一失敗不阻塞整批回收。
- **SUCCESS 一致性保證**：SUCCESS 標記延後至外層還原交易 `afterCommit`，commit 失敗不寫入 SUCCESS（log 維持 PROCESSING 由 reclaimer 回收重試），杜絕「log=SUCCESS 但實際未還原」的補償永久遺失情境；配合悲觀鎖移除持鎖內 `REQUIRES_NEW` 標記以避免死鎖。
- **不變式 (Invariant)**：此安全模型依賴單一不變式——任何對 `user_project_skill` 的寫入（rebind、restore 或未來新增的直接綁定端點）都必須在同一交易內 bump `Project.version`（touch `Project.updatedTime`）。該不變式在 `ProjectUserBindingService` 的 `doRebindProjectMemberSkills` 與 `restoreMemberSkills` 中明確註記；未遵守此不變式的寫入路徑將重新引入資料覆蓋漏洞。
- **永久衝突隔離**：`CompensationConflictException` 列為 Kafka 不可重試異常，衝突事件直接隔離至 DLT 供人工介入。
- **KISS 原則**：避免了複雜的新舊綁定比對邏輯，代碼極度乾淨簡單。
- **微服務隔離性 (Microservice Isolation)**：維持 `alert-service` 與 `competency-service` 之間清晰、乾淨的服務邊界。
