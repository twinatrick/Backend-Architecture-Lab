# 0002. Testcontainers 端到端整合測試架構設計

為了解決跨微服務的分散式 SAGA 補償、Kafka Outbox 發佈閉環、Redisson 分散式鎖以及多層快取防穿透在純 Unit Test (Mock) 下難以驗證真實行為的問題，我們決定導入基於 Testcontainers 的端到端整合測試架構，建立獨立模組 `backend-test-support`，並透過單例容器與 Maven Failsafe 進行測試分層。

## Context

當前系統包含多個微服務（`backend-iam-service`、`backend-competency-service`、`backend-job-service`、`backend-external-api-service`、`backend-alert-service`）以及底層基礎模組（`backend-common`）。各微服務依賴真實的外部中介軟體進行關鍵業務處理：
1. **SAGA 分散式補償與 Outbox 模式**：依賴 PostgreSQL 交易持久化與 Kafka 訊息收發閉環。
2. **感測器即時告警與快取指標流**：依賴 Kafka 即時消費與 Redis 快取統計計數。
3. **高並發分散式鎖與多層快取**：依賴 Redisson Mutex 與 Redis 鍵值生命週期管理。
4. **跨服務認證與業務調用**：依賴 IAM JWT 發行與 Feign Client 跨服務端點串接。

先前僅依靠單元測試（H2 Database 與 Mockito）無法真實反映 PostgreSQL 語法相容性、Kafka Broker Rebalance 與重試機制、以及 Redis 分散式鎖的死鎖與並發防穿透行為。此外，本地執行測試時需嚴格控制 CPU 與執行緒資源（`forkCount=1`），避免多核心平行測試導致系統卡頓。

## Decision

1. **獨立測試基礎設施模組 (`backend-test-support`)**：
   - 建立獨立的 Maven 子模組，集中管理 Testcontainers（PostgreSQL 16、Kafka KRaft、Redis 7）依賴與共用工具。
   - 實作 `SharedContainers` 單例模式：在 JVM 啟動時一次性啟動所有共用容器，所有整合測試類別共享同一組容器實例。
   - 透過 `init-dbs.sql` 在 PostgreSQL 容器啟動時初始化 5 個獨立資料庫（`iam_service`、`competency_service`、`job_service`、`external_api_service`、`alert_service`），確保微服務資料隔離。
   - 提供 `BaseTestcontainersIntegrationTest` 基底類別，封裝 `@DynamicPropertySource` 屬性動態注入與 `@AfterEach` 資料狀態清理（資料庫 Truncate、Redis `FLUSHDB`、隨機化 Kafka Consumer Group）。

2. **測試分層與命名規範**：
   - 單元測試（`*Test.java`）：保持快速、以 H2/Mock 執行，由 `./mvnw test` 預設執行。
   - 整合測試（`*IT.java`）：透過 `maven-failsafe-plugin` 綁定至 `integration-test` profile，使用 `./mvnw verify -Pintegration-test` 執行。
   - 遵循單一執行緒規範：設定 `forkCount=1` 與 `reuseForks=true`。

3. **四核心整合測試範疇**：
   - `backend-competency-service`：`CompensationSagaEndToEndIT`（SAGA 補償與 Outbox 閉環）。
   - `backend-alert-service`：`AlertKafkaAndRedisFlowIT`（感測器告警與 Redis 指標流）。
   - `backend-common`：`RedissonCacheProtectionIT`（Redisson 分散式鎖與六層快取防穿透）。
   - `backend-test-support`：`UserToCompetencyE2EIT`（跨服務認證與業務流程）。

4. **非同步斷言標準**：
   - 統一採用 `Awaitility` 進行非同步輪詢斷言（超時時間 5~10 秒），取代不可靠的 `Thread.sleep`。

## Consequences

### Positive
- **高保真驗證**：在真實的 PostgreSQL、Kafka、Redis 環境中執行測試，能精準捕捉並發競態、交易回滾、訊息延遲與分散式鎖問題。
- **環境一致性**：開發者本機與 CI/CD（GitHub Actions）執行完全相同的容器化測試，杜絕「在我電腦上可以跑」的環境差異。
- **無負擔資源管理**：單例容器避免了每次測試重啟容器的昂貴開銷，單執行緒限制保證開發機流暢運作。

### Negative / Trade-offs
- **首次執行下載映像檔**：初次執行整合測試時需下載 Docker Images（PostgreSQL、Kafka、Redis），耗費初始網路頻寬與磁碟空間。
- **CI 執行時間增加**：相較於純單元測試，執行 Testcontainers 整合測試需額外耗費數分鐘，因此在 CI 工作流中劃分為獨立階段執行。
