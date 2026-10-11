# 0016. Dual-Rail Retrieval Standards and Eradication of Unpaginated Endpoints

我們需要在全專案中全面根除無分頁之 `getAll` / `findAll` 對外端點，確立「分頁搜尋軌（POST /search）」與「響應式串流軌（GET /stream?chunkSize=250）」之雙軌實體獲取標準，並於本地代碼稽核與 CI AI Review 中建立未實作存根與架構缺失守門機制。

## Context

在系統各微服務的歷史演進中，實體清單查詢面臨以下架構與穩定性痛點：
1. **無界查詢引發記憶體耗盡與閘道逾時 (Unbounded Listing & OOM/Gateway Timeout)**：
   多個實體 Controller 殘留無條件、無上限之 `getAll` / `findAll` 端點（例如 `GET /api/company/get`、`GET /api/skill/get`、`POST /api/role/get`、`GET /api/external/config`，以及 AquarkData 曾有的 20 萬筆全量拉取）。當資料量隨業務擴增，全量拉取將引發微服務堆疊記憶體崩潰（OOM）或 API 閘道 503/504 逾時中斷。
2. **前後端存取契約不一致 (Inconsistent Retrieval Contracts)**：
   部分實體僅提供分頁查詢，部分實體僅提供全量查詢，部分實體支援 SSE 串流，導致前端清單元件難以統一調度，且大量匯出或資料同步場景缺乏標準的非阻塞通道。
3. **未實作存根與架構邊界缺失缺乏阻擋 (Unimplemented Stubs & Architectural Gaps)**：
   開發過程中常因階段性交付殘留 `throw new UnsupportedOperationException()`、TODO 存根或回傳假空物件（如 `return Collections.emptyList()`），且新增聚合根控制器容易遺漏必備的分頁搜尋或串流端點，缺乏本地靜態秒級守門與 CI 語意深度稽核機制。

評估方案：
1. **方案 A（全面根除公開 getAll + 強制雙軌標準 + 本地/CI 雙層守門門禁 + 燃盡名單 - 推薦）**：
   - 對外 Controller 嚴格禁止公開無分頁端點；Service Impl 與 DataAccess 保留內部 `findAll()` 供系統預熱與快取使用。
   - 搜尋軌：`POST <basePath>/search`，繼承 `BaseSearchQuery`，回傳 `ResponseType<PageResult<Vo>>`，強制宣告 `SearchSortPolicy` 排序白名單與唯讀事務。
   - 串流軌：`GET <basePath>/stream?chunkSize=250` (`text/event-stream`)，回傳 `Flux<ServerSentEvent<List<Vo>>>`，游標遞增排序批次發送，內建心跳保護與 Gateway 穿透路由（`response-timeout: 60000`）。
   - 本地 `check_local.py` 秒級靜態阻擋（`RULE_UNIMPLEMENTED_METHOD_STUB`、`RULE_FORBIDDEN_GETALL_ENDPOINT`、`RULE_MISSING_DUAL_RAIL_RETRIEVAL`）搭配 CI LLM 深度語意審核，並以 `PENDING_DUAL_RAIL_MIGRATION` 燃盡名單推進漸進式重構。
2. **方案 B（僅在 Controller 加預設 LIMIT 截斷 + 允許自由擴充端點）**：
   - 舊 getAll 端點保留但強制後端截斷至 100 筆。
   - 缺點：破壞客戶端對全量資料的預期，且無法支援大資料匯出與同步，未能從根本解決架構一致性問題。

## Decision

我們決定採用 **方案 A**，具體架構規範與決策如下：

1. **無界端點根除紀律 (Unbounded Endpoint Eradication)**：
   - Controller 層嚴禁對外暴露任何無分頁、無上限之清單端點（包括標註為 `getAll`, `findAll`, `getList` 且無分頁參數之 API）。
   - 底層 ServiceImpl 與 DataAccessImpl 內部可視業務需要保留無參 `findAll()` 方法，專供系統開機啟動預熱、本機快取加載及特殊排程聚合使用，嚴禁直接穿透至 HTTP 邊界。
2. **雙軌實體獲取標準 (Dual-Rail Retrieval Standards)**：
   - **搜尋軌 (Search Rail)**：
     - 路徑與方法：`POST <basePath>/search`。
     - 請求結構：必須繼承 `com.example.BackendArchitectureLab.Vo.Search.BaseSearchQuery`（包含 `page`, `size`, `sortBy`, `sortDir`）。
     - 回應結構：必須包裝為 `ResponseType<PageResult<Vo>>`。
     - 安全防護：Service 層必須宣告 `SearchSortPolicy` 排序白名單，且查詢必須以 `transactionExecutor.executeReadOnly` 執行。
   - **串流軌 (Stream Rail)**：
     - 路徑與方法：`GET <basePath>/stream?chunkSize=250`。
     - 回應協定：`MediaType.TEXT_EVENT_STREAM_VALUE` (`text/event-stream`)。
     - 回應型別：`Flux<ServerSentEvent<List<Vo>>>`。
     - 規格約束：`chunkSize` 強制邊界校驗（預設 250，範圍 10 ~ 1000）；底層以主鍵升冪（`Sort.by("id").ascending()`）批次分頁查詢；發送 `chunk` 事件攜帶資料，發送 `complete` 事件標記結束，發送 `error` 事件回報異常；每 15 秒發送 `: keep-alive\n\n` 心跳防止閘道逾時。
     - 閘道設定：Gateway 必須為串流端點設定專屬路由配置 `response-timeout: 60000`，且嚴禁掛載中斷串流連線的 `CircuitBreaker` 熔斷器。
3. **未實作存根守門與架構邊界門禁 (Stub Guard & Static Gate)**：
   - 於 `.github/ai-review/check_java_quality.py` 實作三大確定性靜態規則（違規等級均為 HIGH，阻擋 Commit）：
     - `RULE_UNIMPLEMENTED_METHOD_STUB`：攔截 `throw new UnsupportedOperationException`、殘留 `// TODO: implement` 存根方法及空回傳虛假實作。
     - `RULE_FORBIDDEN_GETALL_ENDPOINT`：攔截 Controller 中回傳集合型別且無分頁之公開端點。
     - `RULE_MISSING_DUAL_RAIL_RETRIEVAL`：針對全域聚合根實體 Controller，若缺少 `POST /search` 或 `GET /stream` 即發出架構缺失阻擋。
   - 於 CI AI Review Prompt 增強未實作與假實作語意審查，由 LLM 深度檢驗業務邏輯是否完整閉環。
4. **雙軌遷移燃盡機制 (Dual-Rail Migration Burn-down)**：
   - 於 `check_rules.py` 維護 `PENDING_DUAL_RAIL_MIGRATION` 燃盡名單。
   - 新增實體之 Controller 必須 100% 遵守雙軌規範，既有遺留控制器（AlertCheckLimit, Company, Skill, SkillLevel, Role, Function, BotConfig 等）隨後續專屬 PR 逐步重構並移出名單，直至完全歸零。

## Consequences

- **全域記憶體與閘道穩定性保障**：徹底杜絕因客戶端無心或惡意全量撈取造成的 OOM 與連線逾時崩潰。
- **一致的前後端互動契約**：所有業務模組之清單畫面統一對接 `/search`，資料匯出與同步統一對接 `/stream`。
- **自動化品質與代碼真實性防護**：本地 pre-commit 與 CI AI Review 聯防阻擋未實作存根與殘留 TODO，杜絕假實作與架構規範退化。
- **重構任務清晰切分**：透過燃盡名單機制，使後續 PR 4 ~ PR 7 得以依微服務獨立推進，降低改動爆炸半徑。
