# Project Skills Compensation Restore Mechanism

我們需要一種方法，在外部夥伴系統同步失敗等非同步後續流程發生異常時，將 `competency-service` 中的專案成員技能綁定還原至經過驗證的歷史 snapshot 快照。

## Context

`PROJECT_MEMBER_SKILLS_REBIND` 操作在本地是資料庫交易事務（`@Transactional`），但在其成功 commit 後會附加一個不可事務性 Rollback 的外部同步動作。若此外部同步失敗，系統會觸發補償機制（Compensation Event）。由於本地事務已成功 Commit，我們必須執行一個非同步的交易補償，將資料庫中的專案技能狀態回復到重綁定之前的原始模樣。

## Decision

我們決定針對 `PROJECT_MEMBER_SKILLS_REBIND` 補償動作，採用「完全擦除並重建（full-wipe and reconstruct）」的強等冪還原模式。
具體做法如下：
1.  在補償事件的 `beforeState` 中，完整捕獲專案重綁定之前的原始綁定歷史清單明細（包含 `userId`, `skillId`, `levelId`）。
2.  在 `competency-service` 中，暴露一個內網且防重防衝突的 `/project/inner/skills/restore` POST 介面。
3.  在消費補償策略 `ProjectMemberSkillsRebindCompensationStrategy` 中，透過 OpenFeign 用戶端呼叫上述還原端點，並傳遞 `eventId` 作為 `Idempotency-Key` 標頭與專案的 `expectedLastUpdatedTime` 時間戳。
4.  還原 API 採用 **應用級去重（Idempotency Guard）** 與 **樂觀並發防護（Concurrency Timestamp Guard）** 雙重安全守衛：
    - 比對 `compensation_restore_log` 去重日誌，若已處理過則直接跳過。
    - 比對資料庫中專案的當前 `updatedTime` 是否等於 snapshot 中預期的 `expectedLastUpdatedTime`。若不一致則拋出 `CompensationConflictException` 拒絕執行（防止覆蓋並行的最新合法修改）。
5.  在防禦通過後，在 `@Transactional` 中執行：先調用 `userProjectSkillDataAccess.deleteByProjectId(projectId)` 清空該專案的所有現存技能綁定，再依 `beforeState` 記錄逐一 save 重建歷史綁定，並寫入去重紀錄。

## Consequences

- **強等冪性 (Idempotency)**：還原 API 配合 `Idempotency-Key` 與 `compensation_restore_log` 實現高可靠的等冪保證，避免 Kafka 網路重試投遞產生的多餘資料庫操作。
- **並發安全性 (Concurrency Safety)**：配合 `expectedLastUpdatedTime` 時間戳守衛，杜絕補償機制覆蓋後續合法修改的 Concurrency Risks，保障最終一致性的可靠與安全。
- **KISS 原則**：避免了複雜的新舊綁定比對邏輯，代碼極度乾淨簡單。
- **微服務隔離性 (Microservice Isolation)**：維持 `alert-service` 與 `competency-service` 之間清晰、乾淨的服務邊界。
