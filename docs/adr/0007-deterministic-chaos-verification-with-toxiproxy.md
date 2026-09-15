# 0007. Deterministic Distributed Chaos Verification with Toxiproxy and Testcontainers

我們需要建立一套基於真實網路故障注入的確定性混沌整合測試體系（Deterministic Chaos Verification），以客觀鐵證檢驗 Transactional Outbox、SAGA 還原狀態機、Fencing Token 與微服務韌性防線在極端故障情境下的自愈保證。

## Context

專案已完成諸多進階分散式架構機制（包括 Transactional Outbox、租約搶佔接管、基於單調遞增 `fencing_version` 的幽靈寫入防護、SAGA 交易補償日誌與快取防擊穿防穿透）。
然而，現有測試體系存在嚴重的驗證空缺：
1. **過度依賴 Mockito 模擬**：例如 `CompensationOutboxWorkerTest` 僅在記憶體中 Mock 資料庫行為，無法檢驗當工作者於「已向 Kafka 發送訊息、但尚未寫入 DB `markSent`」瞬間暴斃時的真實行為。
2. **缺乏網路異常與真實並發競態考驗**：當工作者 A 遭遇 15 秒長 GC 停頓導致租約過期被工作者 B 接管時，工作者 A 甦醒發出的過期更新是否真能被 PostgreSQL 的條件更新（`AND fencing_version = 1`）精確拒絕，過去從未有自動化測試予以證實。
3. **Happy Path 覆蓋侷限**：整合測試 `CompensationSagaEndToEndIT` 僅驗證無異常時的標準流程，缺少網路分區、TCP 延遲與重置等極端場景。

評估方案：
1. **方案 A (Testcontainers + Toxiproxy 網路代理 推薦)**：透過 Shopify 開源的 Toxiproxy 容器作為應用程式與 DB / Kafka / Redis 之間的 TCP 代理通道，由測試程式碼動態注入微秒級延遲、重置連線或阻斷網路。
2. **方案 B (AOP / ByteBuddy 切面偽造異常)**：在 Java 程式碼內部透過 AOP 攔截並丟出異常。無法模擬真實的 OS/TCP 連線中斷與網路分區。
3. **方案 C (Docker Stop / Kill 容器強制重啟)**：直接對 Testcontainers 容器執行 Kill。開銷龐大、無法細粒度控制特定請求或封包，極易導致 CI 測試超時。

## Decision

我們決定採用 **方案 A**，在測試基礎設施層實作確定性混沌驗證套件：

1. **靜態單例故障代理容器 (`SharedContainers` & Toxiproxy)**：
   - 於 `backend-test-support` 引入 `org.testcontainers:toxiproxy:1.20.4`。
   - 在單例容器管理器中部署 `ghcr.io/shopify/toxiproxy:2.9.0`，為 PostgreSQL (5432)、Kafka (9092)、Redis (6379) 建立代理通道（Proxy Channels）。
2. **獨立混沌測試基底 (`BaseChaosIntegrationTest`)**：
   - 建立獨立基底類別繼承既有 `BaseTestcontainersIntegrationTest`，提供便於控制故障的 API（如 `injectKafkaLatency`、`cutDatabaseConnection`、`restoreAllToxics`）。
   - 與一般高速整合測試（Direct-connect）環境完全隔離，避免代理抖動干擾日常 E2E 測試。
3. **測試環境租約窗口縮減 (Time-Window Compression)**：
   - 在混沌測試中將分散式租約（Lease Duration）從預設的 300 秒縮短至 2 秒，重試指數退避縮短至 200ms。
   - 使「網路中斷 -> 租約過期 -> Worker 搶佔接管 -> Fencing Token 拒絕」的完整閉環在 10~15 秒內確定性收斂完畢。
4. **三大核心混沌用例覆蓋 (`*ChaosIT.java`)**：
   - **用例 1 (Outbox Crash & At-least-once)**：在 Kafka 發送後中斷連線，驗證新 Worker 接管重送，消費端依 `eventId` 冪等去重，補償流程無重複副作用。
   - **用例 2 (Stale Worker Fencing Defense)**：模擬 Worker A 遭網路分區延遲，Worker B 搶佔租約並遞增代數至 2；Worker A 甦醒後寫入 SQL 被 CAS 條件阻斷，回傳更新行數為 0，杜絕幽靈寫入。
   - **用例 3 (Cascading Failure Defense)**：以 Toxiproxy 拔除 IAM 連線，斷言 Caffeine 本地快取持續服務已鑑權用戶，未命中者被斷路器快速阻絕，微服務連線池與線程完全健康。

## Consequences

- **分散式一致性客觀鐵證 (Verifiable Resilience Evidence)**：以自動化端到端測試證明分散式補償與防禦機制在崩潰下的自愈性，徹底消除架構假設。
- **CI 友好且無抖動 (CI-Friendly Execution)**：藉由 Toxiproxy 精確控制與時間窗口壓縮，混沌測試能在 15 秒內完成，且不拖慢 CI 建置管線。
- **維護代價 (Maintenance Overhead)**：需額外維護 Toxiproxy 容器映像檔與 TCP 端口映射邏輯。
