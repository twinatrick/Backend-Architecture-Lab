# 0015. Bot Dialogue Normalization and Platform Status Mesh

我們需要在 `backend-external-api-service` 中將對話機器人歷史記錄由單一 JSON 欄位重構為 1:N 規範化關聯訊息表，提供 Discord 完整管理端點與按需調閱介面，並實作跨通訊與 AI 平台之執行時狀態探針。

## Context

在既有的外部通訊機器人服務中，面臨以下架構與維運痛點：
1. **JSON 單一巨欄位與儲存膨脹 (Monolithic Conversation JSON)**：
   Discord 與 LINE 會話歷史原先以 JSON 字串儲存在主會話表單一欄位中。隨著使用者對話累積，該欄位急劇膨脹成為資料庫 BigKey，且每次對話讀寫都需要整包序列化與反序列化，缺乏行級（Row-level）檢索、審計及分頁能力。
2. **缺乏管理控制與對話歷史按需調閱 (Missing Management API & On-Demand Audit)**：
   前端後台無法直接查詢 Discord 會話清單、啟用狀態、訂閱項目，亦無法按需調閱特定會話的歷史對話，只能盲目等待 Bot 自動回應。
3. **外部平台連線狀態黑箱 (Opaque Platform Runtime Health)**：
   Discord (JDA)、LINE 與多模型 AI（Gemini, DeepSeek, Groq 等）的連線與金鑰配置狀態散落於環境變數與背景執行緒中，缺乏統一監控端點探查平台連線是否正常、Bot 是否在線（ONLINE/OFFLINE/NOT_CONFIGURED）。

評估方案：
1. **方案 A（1:N 規範化拆表 + 按需調閱 + Fail-Safe 狀態探針 - 推薦）**：
   - 將對話訊息自會話主表抽離為獨立的 1:N 訊息表（`discord_gf_message` 與 `line_gf_message`），外鍵關聯並配置 `ON DELETE CASCADE`。
   - LLM 上下文採用最近 N 筆滑動視窗（Sliding Window）撈取，管理端點則按需獨立調閱歷史訊息。
   - 建立 `DiscordManagementController` 與 `IExternalPlatformStatusService`，以故障安全機制探測各通訊與模型平台的連線與配置狀態。
2. **方案 B（維持 JSON 欄位 + 定時背景任務輪詢 Ping）**：
   - 保持資料庫 schema 不變，改以定時排程向各平台發送探針寫入快取。
   - 缺點：JSON 膨脹問題依然存在，排程探測增加多餘網路負擔且無法即時反映當前連線狀態。

## Decision

我們決定採用 **方案 A**，具體架構實作如下：

1. **1:N 訊息資料表重構 (Normalized Dialogue History)**：
   - 分別建立 `discord_gf_message` 與 `line_gf_message` 資料表，包含 `session_id`, `role`, `content`, `sender_name` 及審計時間戳。
   - 主會話表刪除 `conversation_history` 巨欄位，徹底杜絕單欄位膨脹問題。
   - 設定外鍵約束 `ON DELETE CASCADE` 與 `(session_id, created_time)` 複合索引，保證級聯清理效率與按時序檢索效能。
2. **按需調閱與滑動視窗策略 (On-Demand Message Audit & Sliding Window)**：
   - 對話處理時，僅透過 `findRecentMessages(sessionId, limit)` 撈取最新 20 則歷史訊息反轉為時間正序，作為 Prompt 傳送至 LLM。
   - 管理端點 `/external/discord/sessions/{id}/messages` 獨立按需調閱完整歷史紀錄，落實元數據與訊息分離傳輸。
3. **Discord 管理控制層 (Discord Management Mesh)**：
   - 提供 `GET /external/discord/sessions`（支援伺服器與狀態篩選）、`GET /external/discord/sessions/{id}`、`PATCH /sessions/{id}/toggle` 及 `DELETE /sessions/{id}`。
   - 提供 `GET /external/discord/subscriptions` 與 `DELETE /subscriptions/{id}` 管理語音日記等伺服器頻道訂閱。
   - 全面標註 `@RequirePermission(layer = "BotConfig")` 權限守衛。
4. **故障安全平台狀態探針 (External Platform Status Probe)**：
   - 實作 `GET /external/config/status`，聚合探查 Discord（JDA 閘道狀態與 Ping）、LINE（Token 與 Secret 配置狀態）及 AI（Gemini, DeepSeek, Groq 憑證有效性）。
   - 探針採用 Fail-Safe 設計，遇到 Bean 未裝配或平台斷線時自動優雅降級標記為 `NOT_CONFIGURED` 或 `OFFLINE`，絕不拋出未捕獲例外。

## Consequences

- **資料庫行級規範化與高效清理**：各會話對話歷史轉為行級結構，支持單筆訊息審計、統計，且會話刪除時自動外鍵級聯清理所有關聯訊息。
- **大幅縮減資料傳輸與 Token 開銷**：對話處理嚴格控制在 20 則滑動視窗以內，管理端點會話清單查詢不再攜帶巨量歷史內容。
- **可觀測性顯著提升**：前端後台可一鍵探測 Discord JDA 與各 AI 提供商的即時健康與連線狀態，方便維運人員即時排查網路或 Token 異常。
