# Project Skills Compensation Restore Mechanism

我們需要一種方法，在外部夥伴系統同步失敗等非同步後續流程發生異常時，將 `competency-service` 中的專案成員技能綁定還原至經過驗證的歷史 snapshot 快照。

## Context

`PROJECT_MEMBER_SKILLS_REBIND` 操作在本地是資料庫交易事務（`@Transactional`），但在其成功 commit 後會附加一個不可事務性 Rollback 的外部同步動作。若此外部同步失敗，系統會觸發補償機制（Compensation Event）。由於本地事務已成功 Commit，我們必須執行一個非同步的交易補償，將資料庫中的專案技能狀態回復到重綁定之前的原始模樣。

## Decision

我們決定針對 `PROJECT_MEMBER_SKILLS_REBIND` 補償動作，採用「完全擦除並重建（full-wipe and reconstruct）」的強等冪還原模式。
具體做法如下：
1.  在補償事件的 `beforeState` 中，完整捕獲專案重綁定之前的原始綁定歷史清單明細（包含 `userId`, `skillId`, `levelId`）。
2.  在 `competency-service` 中，暴露一個內網且防重防衝突的 `/project/inner/skills/restore` POST 介面。
3.  在消費補償策略 `ProjectMemberSkillsRebindCompensationStrategy` 中，透過 OpenFeign 用戶端呼叫上述還原端點，並傳遞 `eventId` 作為 `Idempotency-Key` 標頭與專案的樂觀鎖版本 `expectedVersion`。
4.  還原 API 採用 **應用級去重（Atomic Idempotency Claim）** 與 **樂觀並發防護（JPA @Version Guard）** 雙重安全守衛：
    - 以 `REQUIRES_NEW` 獨立交易對 `compensation_restore_log`（以 `eventId` 主鍵 + `@Version` 樂觀鎖）進行原子認領：INSERT 路徑靠主鍵唯一鍵原子（高並發撞鍵 → `DataIntegrityViolationException` → 拒絕認領），UPDATE 路徑靠 `@Version` 樂觀鎖 CAS（並發更新落敗者丟 `ObjectOptimisticLockingFailureException` → 拒絕認領）；僅取得認領權的實例能執行還原。已存在且為 SUCCESS 則跳過。
    - 比對資料庫中專案的當前 `@Version` 樂觀鎖是否等於 snapshot 中預期的 `expectedVersion`。若不一致則拋出 `CompensationConflictException` 拒絕執行（防止覆蓋並行的最新合法修改）。
    - **Commit-time 版本守衛**：僅讀取值不比對不足以封閉 TOCTOU 窗口，還原執行前會 touch Project（更新 `updatedTime` 並 save），使 JPA `@Version` 在交易 commit 時執行真正的 CAS 比對（`UPDATE ... WHERE version=舊值`）；若期間被其他合法交易推進版本，commit 時丟 `OptimisticLockException` → 整個還原 rollback。
5.  還原的狀態生命週期以**獨立交易邊界**切分：認領（PROCESSING）以 `REQUIRES_NEW` 先 commit；還原成功以 `REQUIRES_NEW` 的 `markRestoreSuccess` 標記 SUCCESS；還原失敗（含版本衝突與暫態錯誤）以 `REQUIRES_NEW` 的 `markRestoreFailed` 標記 FAILED 並寫入 `lastError` 失敗原因後再對外 rethrow——確保 FAILED/SUCCESS 不因外層交易 rollback 而遺失。還原本體在 `@Transactional` 中執行：先調用 `userProjectSkillDataAccess.deleteByProjectId(projectId)` 清空該專案的所有現存技能綁定，再依 `beforeState` 記錄逐一 save 重建歷史綁定。

## Consequences

- **強等冪性 (Idempotency)**：還原 API 以資料庫主鍵 + `@Version` 樂觀鎖進行原子認領（`compensation_restore_log`）取代非原子的 exists-then-insert 檢查，避免多實例並行消費重複還原，配合 `Idempotency-Key` 實現高可靠的等冪保證。
- **並發安全性 (Concurrency Safety)**：雙層防護——比對快照 `expectedVersion` 快速失敗，再於 commit 時 touch Project 觸發 `@Version` CAS 封閉 TOCTOU 窗口，杜絕補償機制覆蓋後續合法修改的 Concurrency Risks，保障最終一致性的可靠與安全。
- **不變式 (Invariant)**：此安全模型依賴單一不變式——任何對 `user_project_skill` 的寫入（rebind、restore 或未來新增的直接綁定端點）都必須在同一交易內 bump `Project.version`（touch `Project.updatedTime`）。該不變式在 `ProjectUserBindingService` 的 `doRebindProjectMemberSkills` 與 `restoreMemberSkills` 中明確註記；未遵守此不變式的寫入路徑將重新引入資料覆蓋漏洞。
- **永久衝突隔離**：`CompensationConflictException` 列為 Kafka 不可重試異常，衝突事件直接隔離至 DLT 供人工介入。
- **KISS 原則**：避免了複雜的新舊綁定比對邏輯，代碼極度乾淨簡單。
- **微服務隔離性 (Microservice Isolation)**：維持 `alert-service` 與 `competency-service` 之間清晰、乾淨的服務邊界。
