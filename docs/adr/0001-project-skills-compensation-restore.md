# Project Skills Compensation Restore Mechanism

我們需要一種方法，在外部夥伴系統同步失敗等非同步後續流程發生異常時，將 `competency-service` 中的專案成員技能綁定還原至經過驗證的歷史 snapshot 快照。

## Context

`PROJECT_MEMBER_SKILLS_REBIND` 操作在本地是資料庫交易事務（`@Transactional`），但在其成功 commit 後會附加一個不可事務性 Rollback 的外部同步動作。若此外部同步失敗，系統會觸發補償機制（Compensation Event）。由於本地事務已成功 Commit，我們必須執行一個非同步的交易補償，將資料庫中的專案技能狀態回復到重綁定之前的原始模樣。

## Decision

我們決定針對 `PROJECT_MEMBER_SKILLS_REBIND` 補償動作，採用「完全擦除並重建（full-wipe and reconstruct）」的強等冪還原模式。
具體做法如下：
1.  在補償事件的 `beforeState` 中，完整捕獲專案重綁定之前的原始綁定歷史清單明細（包含 `userId`, `skillId`, `levelId`）。
2.  在 `competency-service` 中，暴露一個內網、且天然等冪的 `/project/inner/skills/restore` POST 介面。
3.  在消費補償策略 `ProjectMemberSkillsRebindCompensationStrategy` 中，透過 OpenFeign 用戶端呼叫上述還原端點。
4.  還原 API 將在 `@Transactional` 中執行：先調用 `userProjectSkillDataAccess.deleteByProjectId(projectId)` 清空該專案的所有現存技能綁定，再依 `beforeState` 記錄逐一 save 重建歷史綁定。

## Consequences

- **強等冪性 (Idempotency)**：還原 API 採用抹平重建方式，在補償事件可能發生重複消費（at-least-once）的場景下，重複調用仍會保證資料庫狀態 100% 回復到歷史 snapshot 狀態，防止業務重複副作用。
- **KISS 原則**：避免了複雜的新舊綁定比對邏輯，代碼極度乾淨簡單。
- **微服務隔離性 (Microservice Isolation)**：維持 `alert-service` 與 `competency-service` 之間清晰、乾淨的服務邊界。
