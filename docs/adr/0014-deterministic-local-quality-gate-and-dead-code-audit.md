# 0014. Deterministic Local Quality Gate and Dead Code Audit

我們需要在本地端 Pre-commit 靜態稽核機制（`check_local.py` / `check_java.py`）中擴充確定性質檢防線，以杜絕「Repository 孤島死代碼殘留」、「JPA 衍生刪除缺失交易邊界」以及「弱型別回傳容器破壞契約封裝」等品質缺陷進入版本控制與審查流程。

## Context

在微服務架構演進與資料庫重構歷程中（例如將會話 JSON 欄位重構為 1:N 獨立對話訊息表），系統面臨以下典型程式碼品質風險與技術債累積痛點：
1. **重構衍生孤島死代碼 (Dead Query Declarations)**：
   當透過新方法（如分頁 `findBy...Pageable`）取代既有寫死數量之衍生方法（如 `findTop20By...Desc`）時，若開發者或 Agent 遺漏清理舊介面宣告，該方法將淪為無人引用的殘留死代碼（Zombie Methods），違反《開發規範.md》§5.8 YAGNI 精簡原則。
2. **JPA 衍生刪除缺少事務保護 (Missing Transactional Boundaries)**：
   Spring Data JPA 規定衍生刪除（`deleteBy*` / `removeBy*`）必須處於活躍的資料庫事務環境中。在非同步事件監聽器（如 JDA Discord 事件、LINE Webhook）中呼叫若未於 DataAccess 或 Service 層宣告 `@Transactional`，將於執行階段拋出 `TransactionRequiredException` 崩潰。
3. **弱型別回傳容器破壞邊界契約 (Weakly-Typed Map Return Types)**：
   業務層或對外 API 介面若使用 `Map<Object, Object>` 或非結構化 Map 回傳資料，破壞編譯期型別安全性，使前端與呼叫端無法取得明確欄位契約。

評估方案：
1. **方案 A（擴充本機 Python 確定性靜態規則庫 - 推薦）**：於 `check_rules.py` 與 `check_java.py` 實作「Repository 逆向引用全域檢索（Reverse Reference Audit）」、「`deleteBy*` 交易邊界標註檢查」以及「Controller / Service 弱型別 Map 回傳阻擋」，並由 `check_local.py` 在暫存區檢查時強制以 HIGH 級別阻擋（Exit Code 1）。
2. **方案 B（僅於單元測試中由 ArchUnit 反射檢測）**：撰寫 ArchUnit 規則進行類別掃描。缺點是每次啟動 JVM 耗時長（5~15 秒），無法輕量即時地在 `git commit` 前針對 Git Staged Diff 進行毫秒級阻擋。
3. **方案 C（純依賴人工 Code Review）**：由 Reviewer 或 AI 審查在 GitHub PR 階段指出。缺點為反饋延遲，且容易造成反覆修改與污染 commit 紀錄。

## Decision

我們決定採用 **方案 A**，將品質防線全面左移並強化至本機確定性靜態稽核工具：

1. **Repository 逆向引用檢索 (Repository Reverse Reference Audit)**：
   - 掃描所有被修改或新增的 Repository 介面檔案（`*Repository.java`），提取其宣告之自訂方法名稱（排除 Spring Data JPA 內建之 `findById`, `save`, `deleteById` 等通用方法）。
   - 在專案原始碼（`src/main/java` 與 `src/test/java`）中進行反向引用比對。
   - 若該方法除了 Repository 宣告自身外，全專案呼叫次數為 0，直接判定為孤島死代碼，觸發 HIGH 級別違規阻擋。
2. **衍生刪除事務邊界防護 (Derived Deletion Transaction Boundary)**：
   - 在 `*DataAccessImpl.java` 與 `*ServiceImpl.java` 中，檢測是否包含 `deleteBy` 或 `removeBy` 衍生刪除邏輯。
   - 強制要求該類別或其對應方法必須顯式標註 `@Transactional`，否則觸發 HIGH 級別違規阻擋。
3. **強型別契約紀律 (Strongly-Typed Contract Discipline)**：
   - 在 Controller 與 Service 介面及實作之公開方法簽名中，嚴禁使用 `Map<Object, Object>`、`Map<?, ?>` 或 `Map<String, Object>` 作為回傳型別。
   - 強制要求採用 Java 17+ `Record` 或專案標準 `Vo` 封裝，豁免底層框架配置與通用公用程式（如 `RedisConfig`、`JwtUtil`）。
4. **阻擋層級與回饋**：
   - 上述違規統一列為 **HIGH** 級別，發現任何違規立即以 Exit Code 1 中斷提交，並輸出具體問題行數與修復建議。

## Consequences

- **零死代碼進入版控 (Zero Dead Query Leaks)**：介面廢棄或重構時遺漏刪除的方法在本地暫存時立即被捕獲，保持 Repository 介面絕對精簡。
- **杜絕執行期交易缺失異常 (Zero Transaction Required Failures)**：在編碼階段即鎖定所有衍生刪除的事務邊界，徹底消除非同步回呼執行緒呼叫 JPA 刪除的執行期崩潰。
- **嚴格維持強型別架構 (Compile-Time Type Safety)**：各微服務對外與對內契約維持清晰明確的資料結構，提高整體代碼庫之可讀性與穩定度。
