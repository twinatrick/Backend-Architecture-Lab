# 0013. Kafka Cache Telemetry Mesh and Fail-Safe Isolation

我們決定在全微服務架構中：
1. 建立以 Kafka 為骨幹的快取度量收集網格（Cache Telemetry Mesh），透過 `backend-common` 自動裝配機制統一為各微服務提供 `cacheStatsKafkaTemplate`。
2. 實作度量遙測之故障安全隔離防護（Telemetry Fail-Safe Isolation），配置 1500ms 短逾時與非阻塞非同步回呼，確保遙測異常永不影響業務查詢。
3. 補齊 `backend-competency-service` 中 `getProjectMemberSkills` 之防雪崩快取與 4 大異動清理切面。
4. 於 `backend-alert-service` 之 `/api/cache-stats` 端點原子計算總量（`total`）與命中率（`hitRate`）衍生指標。

## Context

在快取架構與可觀測性審查中，發現以下關鍵缺陷：
1. **快取監控指標跨服務全面失效**：
   - 全局防穿透快取切面 `CachePenetrationProtectionCache` 依賴 `KafkaCacheStatsPublisher` 發送事件至 `cache-stats` 主題。
   - `KafkaCacheStatsPublisher` 宣告了 `@ConditionalOnBean(name = "cacheStatsKafkaTemplate")`，但全系統僅有 `backend-alert-service` 定義了該 Bean。
   - `iam`、`competency`、`job`、`external-api` 四個微服務因缺少 Bean 實例，切面自動降級為 No-Op 空實作 `(cn, f) -> {}`，導致 Redis `cache:stats:*` 永遠只有 alert 服務自己的數據，無法觀測任何核心業務熱點快取。
2. **`projectMemberSkills` 端點漏標快取**：
   - `RedisConfig` 雖為 `projectMemberSkills` 分區配置了 30 分鐘 TTL，但 `ProjectUserBindingService.getProjectMemberSkills` 未標註 `@Cacheable`，造成大量多表關聯查詢直穿資料庫。
3. **快取監控缺乏衍生指標**：
   - 監控端點僅回傳原始計數，前端看板需自行處理各項數值之總量與百分比計算，存在重複運算與表示歧義風險。

## Decision

我們決定實施全域快取遙測與加固方案：

1. **集中式 Kafka 遙測自動裝配 (Cache Telemetry Mesh AutoConfiguration)**：
   - 於 `backend-common` 新增 `CacheStatsKafkaAutoConfiguration`，在類別路徑存在 `KafkaTemplate` 時自動註冊 `cacheStatsKafkaTemplate`（若容器中尚未存在該 Bean）。
   - 在 `backend-iam-service` 與 `backend-external-api-service` 的 `pom.xml` 中引入 `spring-kafka`，使全微服務天然具備快取指標發布能力。
2. **遙測故障安全隔離防護 (Telemetry Fail-Safe Isolation)**：
   - 在自動裝配之生產者屬性中強制注入 `ProducerConfig.MAX_BLOCK_MS_CONFIG = 1500`，杜絕 Broker 斷線時預設 60 秒的高延遲阻塞。
   - 重構 `KafkaCacheStatsPublisher`，以非阻塞 `send(...).whenComplete(...)` 發布事件，並攔截所有潛在例外，保證核心業務 API 零受害、零延遲。
3. **補齊 `projectMemberSkills` 快取生命週期**：
   - 於 `ProjectUserBindingService.getProjectMemberSkills(UUID)` 標註 `@Cacheable(value = "projectMemberSkills", key = "#projectId", sync = true)`。
   - 於專案成員與技能異動之 4 大關鍵路徑（`doRebindProjectMemberSkills`、`bindUsersToProject`、`deleteProject`、`restore`）標註 `@CacheEvict(value = "projectMemberSkills", key = "#projectId")`，維護強一致性。
4. **服務端衍生快取指標計算 (Derived Cache Metrics)**：
   - `CacheStatsServiceImpl` 聚合 Redis Hash 時，原子計算 `total = hits + misses + bloom_rejects + null_hits`（型別為 Long）與數值命中率 `hitRate = hits / total`（型別為 Float，範圍 0.0 ~ 1.0）。
   - 維持頂層資料結構不變，將衍生指標作為鍵值合併回傳，提供完整向下相容性。

## Consequences

- **全微服務快取觀測性 100% 覆蓋**：所有微服務之依 ID 查詢與熱點 API 命中、穿透與布隆攔截數據皆能即時送達並呈現於監控看板。
- **高可用與故障隔離**：即使 Kafka 叢集停機或網路斷線，快取讀寫與業務查詢依舊流暢運行，遙測管線具備完整的 Fail-Safe 保障。
- **資料庫負載顯著降低**：高頻且多表關聯之 `projectMemberSkills` 受到防穿透防雪崩快取保護，大幅減輕關聯查詢與資料庫 I/O。
