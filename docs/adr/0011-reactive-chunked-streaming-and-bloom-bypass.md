# 0011. Reactive Chunked Streaming and Undeclared Bloom Filter Bypass

我們決定在全微服務架構中：
1. 實施「未宣告快取放行原則 (Undeclared Cache Bypass)」，修復未配置布隆過濾器之快取分區誤判穿透引發之 500 NPE 缺陷。
2. 廢除萬筆級大數據實體（使用者、專案、職缺關聯、職缺發布）之一次性無分頁查詢與無界快取巨鍵（BigKey），全面導入「響應式分塊串流 (Reactive Chunked Streaming)」與專屬網關穿透路由。

## Context

在壓測資料集（包含 50,000 筆使用者、50,000 筆專案、50,000 筆職缺關聯等）載入線上環境後，既有架構暴露了兩項嚴重穩定性問題：
1. **布隆過濾器防穿透誤殺與 500 NPE**：
   - `CachePenetrationProtectionCache` 只要判斷 key 為 UUID 格式，便無條件向 Redisson 請求 `bloomFilterService.mightContain(name, key)`。
   - 若該快取名稱（例如 `projectSkills`）未在設定檔 `bloomFilterProperties.getEntityCacheMap()` 宣告，Redisson 會動態在 Redis 建立全新之空布隆過濾器，導致所有合法查詢一律被誤判為不存在而攔截回傳 null。
   - 業務層（如 `ProjectSkillService`）未做防禦性判空，直接調用 `.getData()` 拋出 `NullPointerException`，回傳 HTTP 500。
2. **大數據無分頁全表查詢與網關 503 超時**：
   - 原有的 `getAllUser`、`project/get`、`user-job-link/get`、`job-posting/get` 採取無分頁全表查詢，並搭配 `@Cacheable(key = "'all'")` 嘗試在 Redis 寫入數十 MB 的快取巨鍵（BigKey 反模式）。
   - 當面對 5 萬筆數據時，全表查詢在關聯加載時引發 15 萬次 N+1 延遲載入查詢風暴，執行時間嚴重超出 API Gateway 預設之 3 秒熔斷閾值，自動觸發熔斷回退（CircuitBreaker Fallback）並回應 HTTP 503 (`GATEWAY_TIMEOUT`)。

評估方案：
1. **方案 A (未宣告快取放行 + 響應式分塊 SSE 串流 - 採納)**：
   - 核心布隆服務新增 `isConfigured(cacheName)`：未宣告者一律視為放行（回傳 true），絕不在 Redis 建立空過濾器；業務層全面補齊 null 防禦。
   - 廢除萬筆級大表之 `getAll` 端點與 `'all'` 快取巨鍵；改提供基於主鍵升序排序游標之 `GET /api/<entity>/stream?chunkSize=250`，以 Server-Sent Events (SSE) 分塊推播，並內建 15 秒心跳保活註釋訊框。
   - Gateway 配置獨立之串流穿透路由，放寬超時至 60 秒且旁路短時 CircuitBreaker。
2. **方案 B (強制全快取分區補齊布隆過濾器 + 傳統分頁查詢)**：
   - 缺點：動態關聯組合之 key 難以在啟動時預載布隆過濾器；傳統分頁需要前端多次往返發起數百次 HTTP 請求，延遲與連線開銷大。
3. **方案 C (全域放寬 Gateway 熔斷超時至 60 秒)**：
   - 缺點：治標不治本，5 萬筆數據仍會佔用大量伺服器記憶體與平臺線程，且 Redis BigKey 隨時可能引發網路與記憶體阻塞。

## Decision

我們決定採用 **方案 A**：

1. **未宣告快取放行原則 (Undeclared Cache Bypass)**：
   - 在 `IBloomFilterService` 與 `BloomFilterService` 增加 `boolean isConfigured(String cacheName)` 檢查。
   - 若該快取名稱未在 `bloomFilterProperties.getEntityCacheMap()` 宣告，`mightContain` 直接回傳 `true` 放行查庫，`add` 與 `addAll` 安全略過，不向 Redis 發起多餘無效命令。
   - 在業務層（如 `ProjectSkillService.getProjectSkills`）補齊防禦性判空，當快取包裝器或資料為 null 時安全回退空清單。
2. **徹底廢除 BigKey 與一次性全表端點**：
   - 自 `backend-iam-service`、`backend-competency-service`、`backend-job-service` 徹底移除 `getAllUser`、`project/get`、`user-job-link/get`、`job-posting/get` 控制器映射與 Service 方法。
   - 徹底移除 Redis `@Cacheable(key = "'all'")` 與 `@CacheEvict(key = "'all'")` 巨鍵定義，徹底杜絕 Redis BigKey 記憶體爆炸風險。
3. **響應式分塊串流架構 (Reactive Chunked Streaming)**：
   - 各模組引入 `spring-boot-starter-webflux`，以非阻塞 `Flux<ServerSentEvent<List<T>>>` 提供 `GET /api/<entity>/stream?chunkSize=250`（chunkSize 限制 10~1000）。
   - 資料庫查詢層透過主鍵 UUID 升序排序游標（`Sort.by("id").ascending()`）分批查詢，消除分頁漂移與記憶體溢出。
   - 串流協定採用標準具名事件：`chunk`（資料片段）、`complete`（串流完成）、`error`（異常捕捉），並結合每 15 秒定時發送之 SSE 註釋心跳訊框（`: keep-alive\n\n`）維持長連線活性。
4. **Gateway 串流穿透路由配置**：
   - 在 `backend-gateway` 之 `application.yml` 前置宣告 `iam-stream`、`competency-stream`、`job-stream` 路由。
   - 設定 `response-timeout: 60000` 與 `connect-timeout: 10000`，且不掛載 CircuitBreaker 熔斷器，確保 SSE 數據零延遲即時透傳至客戶端。

## Consequences

- **消除 500 NPE 異常**：未宣告布隆過濾器之快取分區不再被誤判阻絕，合法資料順暢查庫，高可用性大幅提升。
- **根絕 503 網關熔斷與 OOM**：萬筆級數據不再一次性塞入記憶體，前端以串流方式平滑消費數據，網關響應延遲由 >3s 降至首區塊 <100ms。
- **Redis 記憶體健康化**：消滅所有 10~15MB 之 `'all'` BigKey，徹底降低 Redis 複製延遲與集群抖動風險。
