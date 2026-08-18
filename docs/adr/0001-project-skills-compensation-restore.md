# Project Skills Compensation Restore Mechanism

我們需要一種方法，在外部夥伴系統同步失敗等非同步後續流程發生異常時，將 `competency-service` 中的專案成員技能綁定還原至經過驗證的歷史 snapshot 快照。

## Context

`PROJECT_MEMBER_SKILLS_REBIND` 操作在本地是資料庫交易事務（`@Transactional`），但在其成功 commit 後會附加一個不可事務性 Rollback 的外部同步動作。若此外部同步失敗，系統會觸發補償機制（Compensation Event）。由於本地事務已成功 Commit，我們必須執行一個非同步的交易補償，將資料庫中的專案技能狀態回復到重綁定之前的原始模樣。

## Decision

我們決定針對 `PROJECT_MEMBER_SKILLS_REBIND` 補償動作，採用「完全擦除並重建（full-wipe and reconstruct）」的強等冪還原模式。
具體做法如下：
1.  在補償事件的 `beforeState` 中，完整捕獲專案重綁定之前的原始綁定歷史清單明細（包含 `userId`, `skillId`, `levelId`）。
2.  在 `competency-service` 中，將整套補償引擎（Kafka Consumer、Reclaimer 監控與 Strategy）全部部署於本服務內，直接透過 JVM 本機呼叫 `ICompensationRestoreService.restoreMemberSkills`，不再對外暴露 HTTP 內網端點，徹底消除微服務間的反向依賴循環與不必要的攻擊面。
3.  在消費補償策略 `ProjectMemberSkillsRebindCompensationStrategy` 中，直接注入本機還原服務，並傳遞 `eventId`、專案的樂觀鎖版本 `expectedVersion`（強制必填），以及 fencing token 資訊 `ownerId` / `fencingVersion`。
4.  還原 Service 採用 **應用級去重（Atomic Idempotency Claim）**、**樂觀並發防護（JPA @Version Guard）** 與 **Fencing Token 全鏈路** 三重安全守衛：
    - 以 `REQUIRES_NEW` 獨立交易對 `compensation_restore_log`（以 `eventId` 主鍵 + `@Version` 樂觀鎖）進行原子認領：INSERT 路徑靠主鍵唯一鍵原子（高並發撞鍵 → `DataIntegrityViolationException` → 拒絕認領），UPDATE 路徑靠 `@Version` 樂觀鎖 CAS（並發更新落敗者丟 `ObjectOptimisticLockingFailureException` → 拒絕認領）；僅取得認領權的實例能執行還原。已存在且為 SUCCESS 則跳過。認領紀錄一併記下目前持有者的 `ownerId` 與 `fencingVersion`。
    - **Fencing Token 認領守衛**：`CompensationEventProcessor` 認領時產生新 `ownerId`（UUID）並使 `fencingVersion` 單調遞增，隨事件鏈路原樣傳遞至還原端。`claimRestoreEvent` 對已存在的紀錄：租約未到期（PROCESSING）→ 拒絕認領（仍有處理者）；`FAILED` 或 PROCESSING 租約到期 → 接管皆要求新 `fencingVersion` 嚴格大於現值（SQL `takeOverClaim` 與應用層檢查共用相同代數不變式），stale token（`>=` 現值）一律拒絕，防止舊代數持有者搶奪新租約。
    - 比對資料庫中專案的當前 `@Version` 樂觀鎖是否等於 snapshot 中預期的 `expectedVersion`。若不一致則拋出 `CompensationConflictException` 拒絕執行（防止覆蓋並行的最新合法修改）。
    - **破壞性操作前 re-validation（結合 DB 級悲觀鎖）**：touch Project 之前先以 `findByIdForUpdate` 對認領紀錄 row 持 `PESSIMISTIC_WRITE` 悲觀寫鎖（鎖持有至外層還原交易 commit），並同時再次驗證 token 確認仍由本次 `ownerId + fencingVersion` 持有。鎖定後其他執行緒的 `takeOverClaim` CAS 會被資料列鎖阻塞，待本交易 commit 後其 predicate（狀態已非可接管）重新評估即失敗，使舊 token 在資料庫層真正失效，封閉 check-then-act 的 TOCTOU 窗口——舊持有者即使在讀取後才被接管，也無法執行破壞性刪除。此驗證必須排在 touch 之前：若租約已被新代數接管而早退 return 時，交易尚未有任何 dirty 寫入，不會白 bump `Project.version`，避免下一持有者因版本不符而誤判衝突。
    - **Commit-time 版本守衛**：僅讀取值不比對不足以封閉 TOCTOU 窗口，還原執行前會 touch Project（更新 `updatedTime` 並 save），使 JPA `@Version` 在交易 commit 時執行真正的 CAS 比對（`UPDATE ... WHERE version=舊值`）；若期間被其他合法交易推進版本，commit 時丟 `OptimisticLockException` → 整個還原 rollback。
5.  還原的**結果標記與還原資料同交易原子 commit**：認領（PROCESSING）以 `REQUIRES_NEW` 先 commit；還原成功後於同一交易內呼叫 `markRestoreSuccess`（`REQUIRED` 加入外層交易，取代原先 `REQUIRES_NEW` + `afterCommit`），SUCCESS 與還原資料同 commit、同 rollback——commit 失敗時 SUCCESS 一併回滾、log 維持 PROCESSING，待租約到期由 reclaimer 回收重試，避免「log=SUCCESS 但實際未還原」的補償永久遺失情境，同時消除「restore 已 commit 但 SUCCESS 標記遺失」的 crash window；還原失敗（含暫態錯誤與永久驗證失敗）以 `afterCompletion`（狀態非 COMMITTED）回呼觸發 `REQUIRES_NEW` 的 `markRestoreFailed` 標記 FAILED 並寫入 `lastError` 後再對外 rethrow——延後到鎖釋放後再標記，避免外層持悲觀鎖時開新交易更新同一 row 造成死鎖。`markRestoreSuccess` / `markRestoreFailed` 皆透過 `markRestoreState` 以 `ownerId + fencingVersion` 為 WHERE 條件的 CAS 更新，token 已被更新代數接管則不覆寫（`log.warn`），確保只有最新一代持有者能標記結果。
    另於版本守衛（`expectedVersion` 比對）失敗時加入**冪等還原比對（crash window 復原）**：若目前該專案的技能綁定已等於還原目標（`isBindingsAlreadyRestored` 以 `userId + skillId + levelId` 集合比對，null 視為空），代表先前的 restore 已 commit 成功（並已 bump `Project.version`）只是 SUCCESS 標記遺失，經 reclaim 重新認領後直接於同一交易標記 SUCCESS 而不重跑破壞性還原，避免第二度還原因版本已推進而誤判 `CompensationConflictException`（落入「實際成功、狀態 FAILED/DEAD」的不一致）。
    還原本體在 `@Transactional` 中執行：先調用 `userProjectSkillDataAccess.deleteByProjectId(projectId)` 清空該專案的所有現存技能綁定，再依 `beforeState` 記錄逐一 save 重建歷史綁定。
6.  **過期租約回收排程 + FAILED 事件重試迴圈**：`CompensationLeaseReclaimer`（competency，`@Scheduled` 預設 60 秒，可調 `compensation.consumer.reclaim-delay-ms`）**兩階段掃描**：① 租約已過期的 `PROCESSING` 紀錄（`findTop50ByStatusAndLeaseUntilBefore`）、② 已達 `nextAttemptAt` 的 `FAILED` 紀錄（`findTop50ByStatusAndNextAttemptAtBefore`，每次至多 50 筆）。前者以 `reclaimLease` CAS、後者以 `retryClaim` CAS 重新認領（皆產生新 `ownerId` 並使 `fencingVersion` 遞增），再從 stored `payload` 反序列化並呼叫 `processReclaimed` 重跑補償；單一事件失敗僅 `log.warn` 不中斷整批。`markFailed` 會寫入 `nextAttemptAt = now + retryBackoffMs * attemptCount`（線性退避，`compensation.consumer.retry-backoff-ms` 預設 60000）——確保 **FAILED 事件即使 Kafka 已無 redelivery 也能被排程重新領取**（閉環：reclaimer 奪取 → transient 失敗 → FAILED → nextAttemptAt 到期 → retryClaim → 再次執行，重試耗盡仍由 `maxAttempts` 封存 `DEAD`）。`payload` 欄位於 `claimEventLog` 認領時序列化完整 `CompensationEvent` 存入，且**所有回復路徑統一以 persisted payload 為 authoritative source**（含 `retryClaim` 重試與租約過期 `reclaimLease` 接手，皆由 `replayFromPersistedPayload` 反序列化）；persisted payload 損毀無法還原時直接標記 `DEAD` 分流，不退回 Kafka 重新投遞的 payload，避免同一 `eventId` 兩套內容不一致。
7.  **本機 Service 依賴注入**：`ProjectMemberSkillsRebindCompensationStrategy` 以建構子注入（Constructor Injection，透過 Lombok `@RequiredArgsConstructor` 與 `private final`）本機 `ICompensationRestoreService`，契合專案全面採用建構子注入之規範。
8.  **消費端終態寫入的 fencing CAS**：`compensation_event_log` 的 `CompensationEventLog` 未使用 `@Version`，若終態（`PROCESSED` / `FAILED` / `DEAD`）以裸 `save()` 寫入，租約到期被接管（`reclaimLease` / `retryClaim` 產生新 `ownerId` 並遞增 `fencingVersion`）後，舊 worker 回頭寫終態會以舊 attemptCount / fence 覆蓋新持有者的認領狀態。因此三個終態標記統一改用 `markState` 條件式 UPDATE（CAS）：WHERE `eventId + ownerId + fencingVersion + status = PROCESSING`，更新失敗（updated=0）代表 token 已為 stale 或狀態已轉移，僅 `log.warn` 不寫入——確保只有最新一代持有者能標記結果，與 `compensation_restore_log` 的 `markRestoreState` 分層對應。`markProcessed` 順帶清空 `nextAttemptAt`（已成功，不再排程重試），`markDead` 亦清空（終態不再重試）。
9.  **Outbox 平行發佈**：`CompensationOutboxWorker` 以可組態並行度（`compensation.outbox.publish-parallelism`，預設 4，實際 `min(批次筆數, 並行度)`）平行發佈整批事件，替代串行的 `publish().get(ackTimeoutSeconds)`——避免單一慢 Kafka 依序拖垮整批、使批次最差等待由 `batchSize × ackTimeout`（最壞 200s）降至約 `ackTimeout`（10s），降低過長佔用 scheduler thread 導致租約打穿的風險。每筆仍各自以 `CompletableFuture` 逾時控制並走既有原子狀態 UPDATE（`markSent` / `markFailed` / `markDead`），不改變 CAS 語意。

### Token 角色對照

| 標頭 / 欄位 | 角色 | 是否機密 | 是否可重複 |
|---|---|---|---|
| `ownerId` | **worker 身份**：目前持有認領租約的處理者唯一識別碼（UUID） | 否（屬租約控制） | 否（每次接管重新產生） |
| `fencingVersion` | **lease 世代**：單調遞增的代數，僅最新一代能執行還原與標記結果 | 否（屬租約控制） | 否（每次接管 +1） |
| `eventId` | **事件身份**：跨重送的冪等鍵 | 否 | 是（同一事件固定） |

## Consequences

- **強等冪性 (Idempotency)**：還原 Service 以資料庫主鍵 + `@Version` 樂觀鎖進行原子認領（`compensation_restore_log`）取代非原子的 exists-then-insert 檢查，避免多實例並行消費重複還原，配合 `eventId` 實現高可靠的等冪保證。
- **並發安全性 (Concurrency Safety)**：雙層防護——比對快照 `expectedVersion` 快速失敗，再於 commit 時 touch Project 觸發 `@Version` CAS 封閉 TOCTOU 窗口，杜絕補償機制覆蓋後續合法修改的 Concurrency Risks，保障最終一致性的可靠與安全。
- **Fencing Token 防止舊租約覆寫**：`compensation_event_log` 與 `compensation_restore_log` 各持有獨立的 `ownerId + fencingVersion`；接管路徑（`reclaimLease`、`retryClaim`、`takeOverClaim`）與結果標記（`markState`、`markRestoreState`）皆以 token 作為 CAS 條件，還原交易期間對 `compensation_restore_log` row 持 `PESSIMISTIC_WRITE` 悲觀鎖使接管在 DB 層被阻塞/失效。確保 crash recovery 後由最新一代持有者接手，舊持有者既無法執行破壞性還原也無法覆寫新結果（含消費端終態寫入）。
- **Crash Recovery 自動化**：`CompensationLeaseReclaimer` 排程定期回收滯留 `PROCESSING` 的過期租約並從 `payload` 還原事件重跑；`markFailed` 寫入 `nextAttemptAt`（線性退避）使 `FAILED` 事件即使 Kafka 無 redelivery 也能由排程以 `retryClaim` CAS 重新接手重試，直到 `maxAttempts` 封存 `DEAD`——避免事件永久卡死在 PROCESSING 或 FAILED；單一失敗不阻塞整批回收。
- **終態寫入競態防護**：消費端三個終態標記以 `markState`（token + status CAS）寫入，舊 worker 在被接管後回寫終態會被拒絕，確保 attemptCount / fence 帳務不被 stale 覆寫。
- **Outbox 平行發佈降低延遲**：批次以可組態並行度平行發佈，Kafka 卡住時批次最差等待由 200s 降至約 10s，避免長期佔用 scheduler thread 打穿 outbox 租約。
- **SUCCESS 一致性保證**：SUCCESS 與還原資料於同一交易內原子標記（`REQUIRED` 取代 `REQUIRES_NEW` + `afterCommit`）——commit 失敗時 SUCCESS 一併回滾（log 維持 PROCESSING 由 reclaimer 回收重試），杜絕「log=SUCCESS 但實際未還原」的補償永久遺失情境，同時消除「restore 已 commit 但 SUCCESS 標記遺失」的 crash window；配合悲觀鎖移除持鎖內 `REQUIRES_NEW` 標記以避免死鎖。reclaim 時若綁定已等於目標則冪等標記 SUCCESS 復原先前版本殘留的 PROCESSING 紀錄。
- **不變式 (Invariant)**：此安全模型依賴單一不變式——任何對 `user_project_skill` 的寫入（rebind、restore 或未來新增的直接綁定端點）都必須在同一交易內 bump `Project.version`（touch `Project.updatedTime`）。該不變式在 `ProjectUserBindingService` 的 `doRebindProjectMemberSkills` 與 `restoreMemberSkills` 中明確註記；未遵守此不變式的寫入路徑將重新引入資料覆蓋漏洞。
- **永久衝突隔離**：`CompensationConflictException` 列為 Kafka 不可重試異常，衝突事件直接隔離至 DLT 供人工介入。
- **KISS 原則**：避免了複雜的新舊綁定比對邏輯，代碼極度乾淨簡單。
- **微服務隔離性 (Microservice Isolation)**：補償邏輯封裝於 `competency-service` 內部，維持清晰乾淨的微服務邊界，無反向循環依賴。
