# 🔬 系統性能檢視與瓶頸探針實測報告 (Performance Review & Targeted Bottleneck Probe Report)

- **基準分支**：`origin/master`（Commit `c4f6d6530c0ef56ff7b9b579cda174a0fcccf47f`）
- **檢視工作分支**：`perf/performance-review`
- **檢視日期**：`2026-10-26`
- **檢視範疇**：全專案 9 大模組靜態架構性能審查 + 4 大微服務獨立資料庫（50,000 ~ 200,000 筆真實量級種子資料）× 四象限（快取開關 × 虛擬執行緒開關）× 三階梯（50 / 200 / 500 VUs，共 60 組 k6 實測）+ 6 項 `SharedContainers` 定向瓶頸探針（Targeted Bottleneck Probes）量化實證
- **治理決策紀錄**：[`docs/adr/0023-performance-benchmark-alignment-and-bottleneck-probes.md`](./adr/0023-performance-benchmark-alignment-and-bottleneck-probes.md)

---

## 一、 執行摘要 (Executive Summary)

本次性能檢視針對最新 `origin/master` 進行了**「靜態原始碼深度掃描 + 壓測契約對齊校正 + 60 組四象限 k6 動態實測 + 6 項定向瓶頸探針（Targeted Bottleneck Probes）」**的全方位體檢。

### 1. 揭露舊版壓測「假快失真（False-Fast Illusion）」之兩大歷史盲點
在對齊壓測腳本與資料庫的過程中，我們定位出過去壓測報告呈現「數千 QPS、延遲僅數毫秒且 0% 錯誤率」的兩大失真根源：
1. **壓測路由契約漂移（Benchmark Route Drift）**：
   專案在落實 RESTful 雙軌獲取標準（廢除無分頁 `GET /*/get`）與重構 `com.example.BackendArchitectureLab.Dto.Request.SuperUserRequest`（欄位由 `key` 改為 `bootstrapSecret`）後，`stress-test/k6/*.js` 仍大量呼叫已廢除的 `/api/role/get`、`/api/function/get`、`/api/skill/get`、`/api/project/get`、`/api/company/get`、`/api/user-job-link/get`、`/api/alertCheckLimit/get`。這些端點在數百微秒內直接回傳 `HTTP 404/405`，卻因舊版 `stress-test/k6/common.js` 僅檢查 `r.status < 500` 而被全部計入「`0.00%` 錯誤率的成功請求」，完全未進入 Service、Redis 或 PostgreSQL。
2. **微服務資料庫拆分後壓測種子資料未落庫（Empty-Table Benchmark）**：
   在 Commit `94f146b` 中，`stress-test/*.sql` 共 18 個種子腳本意外被覆寫為 `0 bytes`；且歷史資料（50,000 筆專案、201,954 筆感測資料）僅殘留於舊單體 `postgres` 資料庫，而微服務獨立資料庫（`iam_service`、`competency_service`、`job_service`、`alert_service`）因舊腳本跨庫查詢 `FROM "user"` 失敗而僅有 0 ~ 4 筆基線資料。

### 2. 本次檢視核心發現總覽（依嚴重度分級）

| 優先級 | 編號 | 瓶頸／缺陷標題 | 發生位置（完整套件路徑） | 實測／探針量化影響 |
| :---: | :---: | :--- | :--- | :--- |
| **P0** | **F-01** | **`@ApiPath` 與 `@ApiQuery` 未初始化 `ParameterNameDiscoverer`，導致所有 `GET /{id}` 路徑變數解析為 `null`** | `com.example.BackendArchitectureLab.Config.ApiPathArgumentResolver`<br>`com.example.BackendArchitectureLab.Config.ApiQueryArgumentResolver` | **Probe 4 實證**：未顯式指定 `name="id"` 的 `@ApiPath` 解析結果恆為 `null`，開啟快取時觸發 `Null key returned for cache operation` (HTTP 500)，關閉快取時回傳 `404 Not Found` |
| **P0** | **F-02** | **布隆過濾器預設容量過小 (`10,000`) 在大表 (`50k~200k` 筆) 下產生 `100.0%` 假陽性位元飽和（Bit Saturation），且啟動時單次全表 `addAll` 阻塞達 `2.75 秒`** | `com.example.BackendArchitectureLab.Config.BloomFilterProperties`<br>`com.example.BackendArchitectureLab.Config.BloomFilterInitializer`<br>`com.example.BackendArchitectureLab.Service.Impl.BloomFilterService` | **Probe 3 實證**：預設 `10,000` 容量僅分配 `143,775 bits`，塞入 150,000 筆資料後位元全數飽和，隨機不存在 ID 的**假陽性穿透率高達 `100.0%`（防穿透完全失效）**；擴容至 `500,000` 後假陽性率降至 **`0.0%`** |
| **P0** | **F-03** | **所有大表 (`user` 5萬筆、`project` 5萬筆、`user_job_link` 5萬筆、`aquark_data` 20萬筆) 缺少預設排序欄位 `created_time` 索引，引發全表 `Seq Scan` + Top-N Heapsort 並觸發 500 VUs Gateway 熔斷** | `com.example.BackendArchitectureLab.Dto.Request.PageQuery`<br>四大微服務 Liquibase Baseline Changelogs<br>`com.example.BackendArchitectureLab.Config.GatewayCircuitBreakerConfig` | **Probe 6 & `EXPLAIN ANALYZE` 實證**：5萬筆無索引排序耗時 `13.11~20.24 ms`（20萬筆 `aquark_data` 耗時 `40.69 ms`、掃描 `49 MB` Buffer）；建立 `created_time DESC` 索引後降至 `0.038~0.94 ms`（**加速 `13.9x ~ 359x`**） |
| **P1** | **F-04** | **`CachePenetrationProtectionCache` 單實例僅 10 個公平信號量 (`Semaphore(10, true)`) 且置於快取命中與 Request Collapsing 之前，搭配每次讀取同步 `hasNullMarker` 造成高併發人工排隊** | `com.example.BackendArchitectureLab.Config.CachePenetrationProtectionCache` | **Probe 2 實證**：200 條虛擬執行緒同時讀取 100% 命中的 `@Cacheable(sync=true)` 熱點鍵（`functions::all`），因爭搶 10 個公平許可證，平均延遲飆升至 **`713.63 ms`**（最大 **`763.45 ms`**）；且非 `sync` 的 `get(key)` 在布隆攔截時回傳 `null`（被 Spring 視為 Cache Miss 穿透查庫） |
| **P1** | **F-05** | **Redis 阻塞式 `KEYS` 鍵空間掃描與「孤兒快取清除殘留（Orphan Cache Eviction）」** | `com.example.BackendArchitectureLab.Service.Impl.CacheStatsServiceImpl`<br>`com.example.BackendArchitectureLab.Service.Impl.JobPostingService`<br>`com.example.BackendArchitectureLab.Service.Impl.SkillService`<br>`com.example.BackendArchitectureLab.Service.Impl.UserVoiceUploadService` | **Probe 1 實證**：在 5,200 鍵空間下，`KEYS` + 200 次逐鍵 `HGETALL` 耗時 **`581.01 ms`**（並阻塞 Redis 單執行緒事件迴圈）；改用 `SCAN` + Pipeline 後降至 **`56.60 ms`**（**加速 `10.27x`**） |
| **P1** | **F-06** | **`backend-gateway` 因依賴繼承實際運行於嵌入式 Tomcat 而非純 Reactor Netty，且 `GatewayCircuitBreakerConfig` 滑動視窗過小 (`20` 筆 / `5s` 慢呼叫) 易於高併發突波時連環熔斷** | `backend-gateway/pom.xml`<br>`com.example.BackendArchitectureLab.Config.GatewayCircuitBreakerConfig` | **k6 500 VUs 實證**：僅需前 10 筆完成請求超過 5 秒即觸發 `iamCircuitBreaker` / `alertCircuitBreaker` 進入 `OPEN` 狀態，造成 500 VUs 下 IAM 錯誤率 `30.9%~33.2%`、Alert 錯誤率 `93.3%~99.9%` |
| **P2** | **F-07** | **職缺爬蟲 `JobPostingService.scrapeAndAnalyzeJobs` 採用 $O(N \times M)$ 巢狀線性掃描比對既有職缺** | `com.example.BackendArchitectureLab.Service.Impl.JobPostingService` | **Probe 5 實證**：在 $N=5,000, M=2,000$（$10^7$ 次比對）下，線性掃描耗時 **`216.12 ms`**，改用 `HashMap` 複合鍵索引僅需 **`7.33 ms`**（**加速 `29.47x`**） |
| **P2** | **F-08** | **微服務啟動期 `@PostConstruct` 同步外部網路 I/O 阻塞（`SeleniumJobCrawler` 下載 ChromeDriver、`LineGfRichMenuService` 繪圖與 4 次同步 HTTP 呼叫）** | `com.example.BackendArchitectureLab.Crawler.Impl.SeleniumJobCrawler`<br>`com.example.BackendArchitectureLab.Service.Impl.LineGfRichMenuService` | **實測啟動日誌**：Chrome 版本升級時 `SeleniumJobCrawler` 下載阻塞導致啟動超時；`LineGfRichMenuService` 同步繪製 `2500x1686` 圖片並呼叫 LINE API 使啟動延長 ~15 秒 |
| **P2** | **F-09** | **SSE 串流端點（`GET /*/stream`）與部分 `POST /*/search` 存在跨執行緒 LAZY 載入、N+1 查詢與死快取方法殘留** | `com.example.BackendArchitectureLab.DataAccess.Impl.CompanyDataAccessImpl`<br>`com.example.BackendArchitectureLab.Mapper.CompanyMapper`<br>`com.example.BackendArchitectureLab.Service.Impl.RoleService`<br>`com.example.BackendArchitectureLab.Service.Impl.SkillService` | `CompanyMapper.toVo` 於分頁迴圈中觸發 `company.getWebsites()` LAZY N+1；`RoleService.getRoleListCache`、`SkillService.getSkillListCache`、`CompanyService.getAllCompaniesCache` 已無任何呼叫端 |
| **P2** | **F-10** | **Outbox 補償排程器查詢未加 `FOR UPDATE SKIP LOCKED`，且單筆循序發送 Kafka / Feign** | `com.example.BackendArchitectureLab.Timer.CompensationOutboxScheduler`<br>`com.example.BackendArchitectureLab.Timer.ExternalSyncOutboxScheduler` | 多實例部署時每 5 秒輪詢 `TOP 50` 易重複抓取相同事件，且逐筆阻塞發送限制吞吐上限 |

---

## 二、 靜態架構與程式碼瓶頸深度剖析 (Static Code & Architecture Analysis)

### 1. 【P0 / F-01】自訂 `@ApiPath` 與 `@ApiQuery` 參數解析器漏失參數名稱探索 (`ParameterNameDiscoverer`)

#### (1) 問題位置
- `com.example.BackendArchitectureLab.Config.ApiPathArgumentResolver`（第 38 行）
- `com.example.BackendArchitectureLab.Config.ApiQueryArgumentResolver`（第 34 行）

#### (2) 根因剖析
在 `com.example.BackendArchitectureLab.Config.ApiPathArgumentResolver.resolveArgument` 中，程式碼直接呼叫 `parameter.getParameterName()`：
```text
String name = StringUtils.hasText(apiPath.name()) ? apiPath.name() : parameter.getParameterName();
```
然而在 Spring MVC 架構中，`MethodParameter.getParameterName()` 預設回傳 `null`，**除非曾對該 `MethodParameter` 呼叫 `initParameterNameDiscovery(ParameterNameDiscoverer)`**。Spring 內建的 `PathVariableMethodArgumentResolver`（繼承自 `AbstractNamedValueMethodArgumentResolver`）會在 `getNamedValueInfo()` 中自動注入 `DefaultParameterNameDiscoverer`；但專案自訂的 `ApiPathArgumentResolver` 與 `ApiQueryArgumentResolver` 直接實作 `HandlerMethodArgumentResolver`，並未初始化 `ParameterNameDiscoverer`。

由於絕大多數 Controller 的單筆查詢方法皆寫為 `@ApiPath(description = "...") UUID id`（未顯式填寫 `name = "id"`），例如：
- `com.example.BackendArchitectureLab.Controller.UserController.getUserById`
- `com.example.BackendArchitectureLab.Controller.ProjectController.getProjectById`
- `com.example.BackendArchitectureLab.Controller.CompanyController.getCompanyById`

執行期 `name` 解析結果恆為 `null`，`uriVars.get(null)` 恆回傳 `null`：
- 當開啟 Redis 快取時，`@Cacheable(value = "users", key = "#id", sync = true)` 因 `#id` 為 `null` 直接拋出 `IllegalArgumentException: Null key returned for cache operation`（轉為 HTTP 500）。
- 當關閉快取時，`findById(null)` 查無資料並拋出 `ResourceNotFoundException`（HTTP 404）。

#### (3) 建議修復範例
```java
package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiPath;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.util.StringUtils;

public class ApiPathParameterNameResolutionExample {

    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER =
            DefaultParameterNameDiscoverer.getSharedInstance();

    public String resolveParameterName(MethodParameter parameter, ApiPath apiPath) {
        if (StringUtils.hasText(apiPath.name())) {
            return apiPath.name();
        }
        parameter.initParameterNameDiscovery(PARAMETER_NAME_DISCOVERER);
        String discoveredName = parameter.getParameterName();
        return discoveredName != null ? discoveredName : parameter.getParameter().getName();
    }
}
```

---

### 2. 【P0 / F-02】布隆過濾器預設容量 (`10,000`) 位元飽和失靈與啟動期全表單次 `addAll` 阻塞

#### (1) 問題位置
- `com.example.BackendArchitectureLab.Config.BloomFilterProperties`（第 17 行：`defaultExpectedInsertions = 10000L`）
- `com.example.BackendArchitectureLab.Config.BloomFilterInitializer`（第 37-51 行）
- `com.example.BackendArchitectureLab.Service.Impl.BloomFilterService`（第 55-64、80-92 行）
- `backend-iam-service/src/main/resources/application.yml`（第 58-63 行）
- `backend-competency-service/src/main/resources/application.yml`（第 51-59 行）
- `backend-job-service/src/main/resources/application.yml`（第 51-56 行）

#### (2) 根因剖析
1. **位元陣列嚴重飽和（Bit Saturation）導致 100% 假陽性穿透**：
   各微服務的 `application.yml` 僅宣告了 `cache.bloom-filter.entities`（如 `User:users`、`Project:projects`、`UserSkill:userSkills`、`UserProject:userProjects`、`UserJobLink:userJobLinks`），卻未配置 `overrides` 或調高 `default-expected-insertions`。
   依布隆過濾器數學公式，當 `expectedInsertions = 10,000`、`falseProbability = 0.001` 時，Redisson 配置的位元陣列大小為：
   $$m = -\frac{n \ln p}{(\ln 2)^2} = 143,775 \text{ bits}, \quad k = 10 \text{ 個雜湊函數}$$
   當實際資料量達到 `50,000` 筆（`users`、`projects`、`userJobLinks`）至 `200,005` 筆（`userProjects`）時，寫入的位元次數高達 $1.5 \times 10^6 \sim 2.0 \times 10^6$ 次，位元陣列中接近 **100% 的 bit 全被設為 `1`**：
   $$P(\text{False Positive})\Big|_{N=150,000} = \left(1 - e^{-10 \times 150,000 / 143,775}\right)^{10} \approx \mathbf{99.97\% \sim 100.0\%}$$
   這意味著任何隨機偽造的不存在 UUID 查詢都會被布隆過濾器判定為「可能存在（`true`）」，**快取防穿透機制在大表上 100% 形同虛設**。
2. **啟動期全表 ID 載入與單次巨型 Lua 腳本阻塞**：
   `com.example.BackendArchitectureLab.DataAccess.Impl.BloomFilterDataAccessImpl.findAllEntityIds` 在 `ApplicationRunner` 階段（此時嵌入式 Tomcat 已開啟埠號接收外部流量）一次將 20 萬筆主鍵讀入 JVM Heap，並由 `BloomFilterService.addAll` 單次呼叫 `RBloomFilter.add(Collection)`，耗時達 `2.7 秒` 以上。
3. **`catch (IllegalArgumentException)` 範圍過廣**：
   `com.example.BackendArchitectureLab.Config.BloomFilterInitializer.run` 第 46 行以 `catch (IllegalArgumentException e)` 捕捉整段查庫與 Redis 寫入邏輯，若 `addAll` 因參數異常失敗，會被誤判為 `"Entity [...] 不存在於此服務"` 而靜默吞掉。

---

### 3. 【P0 / F-03】四大微服務核心大表缺乏 `created_time` 排序索引，引發全表掃描與高併發熔斷

#### (1) 問題位置
- `com.example.BackendArchitectureLab.Dto.Request.PageQuery`（第 29 行：`private String sortBy = "createdTime";`）
- `backend-iam-service/src/main/resources/db/changelog/db.changelog-0001-baseline-iam.yaml`
- `backend-competency-service/src/main/resources/db/changelog/db.changelog-0001-baseline-competency.yaml`
- `backend-job-service/src/main/resources/db/changelog/db.changelog-0001-baseline-job.yaml`
- `backend-alert-service/src/main/resources/db/changelog/db.changelog-0001-baseline-alert.yaml`

#### (2) 根因剖析與 PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)` 實證
系統在廢除 `GET /*/get` 後，將所有列表查詢統一收斂至 `POST /*/search`，並繼承 `com.example.BackendArchitectureLab.Dto.Request.PageQuery`。`PageQuery` 預設以 `sortBy = "createdTime"`、`sortDir = "DESC"` 建立 `PageRequest`（對應 SQL `ORDER BY created_time DESC LIMIT 20 OFFSET 0`）。
然而，檢查四大微服務的所有 Liquibase Changelogs，**沒有任何一張表在 `created_time` 欄位上建立 B-Tree 索引**！

我們直接對本機 Docker PostgreSQL 16 容器（`postgres_db_backend`）中已灌入完整壓測種子資料的四大微服務資料庫執行 `EXPLAIN (ANALYZE, BUFFERS)`，結果如下：

| 微服務資料庫與資料表 | 資料筆數 | 查詢語句 (`POST /*/search` 實際產出之 SQL) | 執行計畫 (Execution Plan) | 掃描 Buffer 頁數 (記憶體頻寬) | 單次 DB 執行耗時 (含 `COUNT(*)`) |
| :--- | :---: | :--- | :--- | :---: | :---: |
| `iam_service."user"` | 50,004 筆 | `SELECT ... FROM "user" ORDER BY created_time DESC LIMIT 20` + `SELECT COUNT(*)` | `Seq Scan on "user"` ➔ `Sort (top-N heapsort)` + `Index Only Scan on user_pkey` | `1,670` pages (`13.3 MB`) + `194` pages | **14.02 ms** + **5.51 ms** = **19.53 ms** |
| `iam_service."user"` *(索引對照)* | 50,004 筆 | `SELECT ... FROM "user" ORDER BY id DESC LIMIT 20` *(有 B-Tree 索引)* | `Index Scan Backward using user_pkey` | **`4` pages (`32 KB`)** | **0.039 ms** *(差距 **359 倍**)* |
| `competency_service.project` | 50,000 筆 | `SELECT ... FROM project ORDER BY created_time DESC LIMIT 20` | `Seq Scan on project` ➔ `Sort (top-N heapsort)` | `1,670` pages (`13.3 MB`) | **20.24 ms** |
| `job_service.user_job_link` | 50,000 筆 | `SELECT ... FROM user_job_link ORDER BY created_time DESC LIMIT 20` | `Seq Scan on user_job_link` ➔ `Sort (top-N heapsort)` | `1,670` pages (`13.3 MB`) | **10.26 ms** |
| `alert_service.aquark_data` | 200,000 筆 | `SELECT ... FROM aquark_data ORDER BY created_time DESC LIMIT 20` + `SELECT COUNT(*)` | `Parallel Seq Scan (2 workers)` ➔ `Gather Merge` + `Parallel Index Only Scan` | `5,376` pages (`43.0 MB`) + `772` pages (`6.2 MB`) | **23.27 ms** + **17.42 ms** = **40.69 ms** *(消耗 3 核心 >100ms CPU)* |

**連鎖效應**：當 500 VUs 同時壓測 `POST /api/users/search` 或 `POST /api/aquarkData/search` 時，每秒數百次全表掃描瞬間榨乾 PostgreSQL CPU 與共享記憶體頻寬，使 HikariCP 連線排隊超過 `5.0 秒`，直接觸發 `GatewayCircuitBreakerConfig` 的慢呼叫率熔斷（HTTP 503）。

---

### 4. 【P1 / F-04】`CachePenetrationProtectionCache` 10 許可證公平信號量瓶頸與非 `sync` 布隆契約瑕疵

#### (1) 問題位置
- `com.example.BackendArchitectureLab.Config.CachePenetrationProtectionCache`（第 34、96-99、124-213 行）

#### (2) 根因剖析
1. **`Semaphore(10, true)` 置於快取命中與 Request Collapsing (`activeRedisFetches`) 之前**：
   在 `com.example.BackendArchitectureLab.Config.CachePenetrationProtectionCache` 第 34 行：
   ```text
   private final Semaphore concurrencySemaphore = new Semaphore(10, true);
   ```
   而在 `get(Object key, Callable<T> valueLoader)`（所有標註 `@Cacheable(..., sync = true)` 的方法如 `UserService.getUserById`、`FunctionQueryService.getFunctionListCache`、`ProjectQueryService.getProjectById`、`CompanyService.getCompanyById` 均走此路徑）中，**執行緒必須先取得這 10 個公平信號量之一，才能進入 `activeRedisFetches` 合併請求或讀取 Redis 快取**！
   - 這不僅將整個微服務對單一快取區域（如 `users` 或 `functions`）的併發讀取上限硬鎖在 **10 個執行緒**；
   - 更導致當 500 個虛擬執行緒同時查詢同一個熱點鍵時，原本設計用來將 500 個請求合併為 1 次 Redis 讀取的 `activeRedisFetches`（Request Collapsing），因為外層被 `Semaphore(10, true)` 攔住，最多只能合併 10 個請求，其餘 490 個虛擬執行緒被迫在公平佇列中分 50 批依序排隊！
2. **每次快取讀取額外串聯同步 `hasNullMarker` Redis `EXISTS` 往返**：
   在讀取實際快取鍵前，`get()` 先同步呼叫 `stringRedisTemplate.hasKey("null:" + name + ":" + cacheKey)`，使每次快取命中都必須承受 **2 次串列 Redis 網路往返（`EXISTS null:...` ➔ `GET cache:...`）**。這也是為何在 50~500 VUs 壓測中，象限 D（開啟 Redis 快取）在部分服務上的平均延遲反而高於象限 A/B 的關鍵原因。
3. **非 `sync` 路徑 `get(Object key)` 布隆過濾器攔截回傳值違反 Spring Cache 契約**：
   在第 96-99 行：
   ```text
   if (!bloomFilterMightContain(cacheKey)) {
       recordMiss();
       return null;
   }
   ```
   依據 Spring `org.springframework.cache.interceptor.CacheAspectSupport` 規範，當 `cache.get(key)` 回傳 `null` 時代表 **Cache Miss**，Spring AOP 會**繼續執行目標業務方法（穿透查詢資料庫）**！唯有回傳 `() -> null`（即包裹 `null` 的 `ValueWrapper`）時，Spring 才會將其視為已命中空值並直接回傳 `null` 而不查庫。因此未加 `sync = true` 的 `@Cacheable` 方法即便配置了布隆過濾器，在 `get(Object key)` 中也無法攔截查庫。

---

### 5. 【P1 / F-05】Redis `KEYS` 全量鍵空間掃描與「孤兒快取清除殘留（Orphan Cache Eviction）」

#### (1) 問題位置與靜態盤點清單
專案中共有 **4 個 Service 實作類別（13 處呼叫點）** 直接在業務或管理路徑呼叫阻塞式 `stringRedisTemplate.keys(...)`：

| 類別完整套件路徑 | 行號 | 呼叫之 `KEYS` 模式 | 狀態診斷 |
| :--- | :---: | :--- | :--- |
| `com.example.BackendArchitectureLab.Service.Impl.CacheStatsServiceImpl` | 第 27-33 行 | `stringRedisTemplate.keys("cache:stats:*")` 後迴圈執行 `opsForHash().entries(key)` | **高頻阻塞風險**：`test-alert.js` 壓測 `/api/cache-stats` 時，每筆請求皆觸發全量 `KEYS` 掃描與 $N$ 次逐鍵 `HGETALL` |
| `com.example.BackendArchitectureLab.Service.Impl.JobPostingService` | 第 88, 239, 262, 389, 476 行 | `stringRedisTemplate.keys("jobPostings::*search:*")` | **孤兒快取清除殘留（Orphan Cache Eviction）**：`searchJobPostings()` 並無 `@Cacheable`，Redis 中根本不存在 `jobPostings::*search:*` 鍵，每次職缺寫入卻白白執行全庫 `KEYS` 掃描 |
| `com.example.BackendArchitectureLab.Service.Impl.SkillService` | 第 280, 312, 361, 568, 1122, 1133 行 | `keys("currentUserSkills::*currentsearch:" + userStr + "*")`<br>`keys("skills::*levelsearch:*")` | **孤兒快取清除殘留（Orphan Cache Eviction）**：搜尋快取早已移除，但新增／更新／刪除技能時仍在 `@Transactional` 內執行 `KEYS` 掃描 |
| `com.example.BackendArchitectureLab.Service.Impl.UserVoiceUploadService` | 第 61, 142 行 | `stringRedisTemplate.keys("userVoiceUploads::search:" + userId + ":*")` | **交易內孤兒快取清除**：`saveUpload()` 在資料庫交易內呼叫 `KEYS`，延長交易與連線持有時間，且 `searchUserUploads()` 無對應快取寫入 |

此外，以下 3 個全表快取方法在廢除 `GET /*/get` 後已無任何呼叫端（Dead Cache Methods）：
- `com.example.BackendArchitectureLab.Service.Impl.RoleService.getRoleListCache`（`@Cacheable(value = "roles", key = "'all'", sync = true)`）
- `com.example.BackendArchitectureLab.Service.Impl.SkillService.getSkillListCache`（`@Cacheable(value = "skills", key = "'all'", sync = true)`）
- `com.example.BackendArchitectureLab.Service.Impl.CompanyService.getAllCompaniesCache`（`@Cacheable(value = "companies", key = "'all'", sync = true)`）

---

### 6. 【P1 / F-06】`backend-gateway` 容器型態誤配與 `GatewayCircuitBreakerConfig` 熔斷敏感度分析

#### (1) 問題位置
- `backend-gateway/pom.xml` 與根目錄 `pom.xml`
- `com.example.BackendArchitectureLab.Config.GatewayCircuitBreakerConfig`（第 36-52 行）

#### (2) 根因剖析
1. **Gateway 實際運行於嵌入式 Tomcat 而非純 Reactor Netty**：
   實測啟動日誌（`gw2.log` 第 19、44 行）顯示：
   ```text
   o.s.b.w.embedded.tomcat.TomcatWebServer : Tomcat initialized with port 8000 (http)
   o.s.b.w.embedded.tomcat.TomcatWebServer : Tomcat started on port 8000 (http) with context path ''
   ```
   儘管 `backend-gateway/pom.xml` 在引入 `backend-common` 時排除了 `spring-boot-starter-web`，但根專案 `pom.xml` 的全域 `<dependencies>` 直接宣告了 Servlet / Tomcat 相關啟動器，導致 `backend-gateway` 繼承了 Tomcat WebServer。
2. **斷路器滑動視窗過小 (`slidingWindowSize = 20`) 引發高併發瞬時熔斷**：
   `com.example.BackendArchitectureLab.Config.GatewayCircuitBreakerConfig` 配置如下：
   - `slidingWindowSize(20)`、`minimumNumberOfCalls(10)`
   - `slowCallDurationThreshold(Duration.ofSeconds(5))`、`slowCallRateThreshold(50.0f)`
   - `TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(10))`
   在 500 VUs 極限壓測下，前 20 筆完成的請求中只要有 10 筆因下游 PostgreSQL 全表掃描排隊超過 `5.0 秒`，斷路器即刻跳脫為 `OPEN` 並對後續所有請求直接回傳 `HTTP 503 Service Unavailable (CIRCUIT_BREAKER_OPEN)`。

---

### 7. 【P2 / F-07 ~ F-10】演算法複雜度、啟動期同步 I/O、LAZY N+1 與 Outbox 排程瓶頸

1. **職缺爬蟲 $O(N \times M)$ 線性比對（F-07）**：
   `com.example.BackendArchitectureLab.Service.Impl.JobPostingService.scrapeAndAnalyzeJobs`（第 127-169 行）先執行 `jobPostingDataAccess.findAll()` 載入全部 $N$ 筆既有職缺，接著對每筆爬取到的職缺（共 $M$ 筆）呼叫 `findMatch(existingJobs, scraped)`，內部以 `existingJobs.stream().filter(...)` 逐筆比對公司名稱、職缺標題與發布日期，時間複雜度為 $O(N \times M)$。
2. **啟動期 `@PostConstruct` 同步外部網路 I/O（F-08）**：
   - `com.example.BackendArchitectureLab.Crawler.Impl.SeleniumJobCrawler.init()` 在 `@PostConstruct` 中同步執行 `WebDriverManager.chromedriver().setup()`，若宿主機 Chrome 自動更新，將阻塞主啟動執行緒連線下載驅動程式。
   - `com.example.BackendArchitectureLab.Service.Impl.LineGfRichMenuService.init()` 在 `@PostConstruct` 中使用 `BufferedImage` 繪製 `2500x1686` PNG 並連續發起 4 次阻塞式 HTTP 請求至 `api.line.me`，使 `backend-external-api-service` 啟動時間暴增 15~19 秒。
3. **分頁與串流端點的 LAZY N+1 查詢（F-09）**：
   - `com.example.BackendArchitectureLab.DataAccess.Impl.CompanyDataAccessImpl.searchCompanies` 查詢分頁 `Page<Company>` 時未預載 `websites`，而 `com.example.BackendArchitectureLab.Mapper.CompanyMapper.toVo` 會讀取 `company.getWebsites()`，每頁 20 筆額外觸發 20 次 SQL 查詢（1+20 N+1）。
   - 各 `GET /*/stream`（如 `UserService.streamUsers`）使用 `TransactionTemplate` 包裹 `Stream<User>` 並在 `StreamingResponseBody` 執行緒中逐筆呼叫 `userMapper.toStreamVo(user)`；若底層關聯未透過 `@EntityGraph` 或二次批量查詢預載，將在串流輸出期間逐筆觸發 LAZY SQL。
4. **Outbox 排程器缺少 `FOR UPDATE SKIP LOCKED` 與批次發送（F-10）**：
   - `com.example.BackendArchitectureLab.Timer.CompensationOutboxScheduler` 與 `com.example.BackendArchitectureLab.Timer.ExternalSyncOutboxScheduler` 每 5 秒以一般 `SELECT` 抓取 50 筆 `PENDING` 事件並逐筆同步發送，在水平擴展多實例下缺乏行級跳鎖保護。

---

## 三、 定向瓶頸探針實測數據 (Targeted Bottleneck Probes — `SharedContainers`)

為在不修改 `src/main` 生產程式碼的前提下精確量化上述瓶頸，我們於 `backend-test-support/src/test/java/com/example/BackendArchitectureLab/TargetedBottleneckProbeIT.java` 建立了 6 項基於 `SharedContainers`（PostgreSQL 16 + Redis 7.2 + Kafka + Toxiproxy）的自動化瓶頸探針，實測輸出保存於 `target/k6-reports/targeted-bottleneck-probes.json`：

| 探針編號 | 探針測試目標 | 對應生產類別（完整套件路徑） | 現況實測數據 (Before) | 對照優化實測數據 (After) | 實測加速比／關鍵結論 |
| :---: | :--- | :--- | :--- | :--- | :--- |
| **Probe 1** | Redis 5,200 鍵空間下讀取 200 個 `cache:stats:*` Hash | `com.example.BackendArchitectureLab.Service.Impl.CacheStatsServiceImpl` | `KEYS` + 逐鍵 `HGETALL`：<br>**`581.01 ms`** | `SCAN (count=200)` + `executePipelined`：<br>**`56.60 ms`** | 🚀 **加速 `10.27x`**，且消除 Redis 單執行緒全庫掃描阻塞 |
| **Probe 2** | 200 條虛擬執行緒併發讀取 100% 命中的 `@Cacheable(sync=true)` 熱點鍵 + 非 `sync` 布隆契約檢查 | `com.example.BackendArchitectureLab.Config.CachePenetrationProtectionCache` | 受 `Semaphore(10, true)` 與同步 `hasNullMarker` 限制：<br>平均 **`713.63 ms`**、最大 **`763.45 ms`**（總耗時 `772.85 ms`） | 非 `sync` 路徑 `cache.get("non-existent")` 回傳 **`null`**（而非 `ValueWrapper`） | ⚠️ 證實 10 許可證公平鎖在 100% 快取命中下仍造成 **>700ms 排隊延遲**；且非 `sync` 布隆攔截會被視為 Cache Miss 穿透查庫 |
| **Probe 3** | 布隆過濾器寫入 150,000 筆主鍵，測試 1,000 個隨機不存在攻擊 UUID 之假陽性穿透率 | `com.example.BackendArchitectureLab.Config.BloomFilterProperties`<br>`com.example.BackendArchitectureLab.Service.Impl.BloomFilterService` | 預設容量 `10,000` (`143,775 bits`)：<br>單次寫入阻塞 **`2,748.70 ms`**<br>假陽性穿透率：**`100.0%` (`1000/1000`)** | 擴容至 `500,000` (`7,188,793 bits`)：<br>假陽性穿透率：**`0.0%` (`0/1000`)** | 🛡️ 證實預設 `10,000` 容量在大表下因位元飽和導致**防穿透 100% 失效**，擴容後恢復 **100% 攔截能力** |
| **Probe 4** | `@ApiPath` 未顯式指定 `name` 時解析 `GET /users/{id}` 之 `UUID id` 參數 | `com.example.BackendArchitectureLab.Config.ApiPathArgumentResolver` | 未呼叫 `initParameterNameDiscovery`：<br>`getParameterName() = null`<br>解析結果：**`null`** | 初始化 `DefaultParameterNameDiscoverer` 後：<br>`getParameterName() = "id"`<br>解析結果：**`00000000-0000-0000-0002-000000000001`** | 🐛 100% 重現並證實 `GET /{id}` 路徑參數解析為 `null` 之根因與修復有效性 |
| **Probe 5** | 職缺爬蟲比對 $N=5,000$ 筆既有職缺 × $M=2,000$ 筆新職缺（$10^7$ 次比對） | `com.example.BackendArchitectureLab.Service.Impl.JobPostingService` | $O(N \times M)$ 巢狀 `Stream.filter` 線性掃描：<br>**`216.12 ms`** | $O(N + M)$ `HashMap` 複合鍵索引查找：<br>**`7.33 ms`** | 🚀 **加速 `29.47x`**，完全消除爬蟲批次處理期間的 CPU 尖峰 |
| **Probe 6** | PostgreSQL 50,000 筆資料表執行預設分頁 `ORDER BY created_time DESC LIMIT 20` | `com.example.BackendArchitectureLab.Dto.Request.PageQuery` | 無 `created_time` 索引 (`Seq Scan` + `top-N heapsort`, `1,414` buffers)：<br>平均 **`13.11 ms`** (`EXPLAIN` 實測 `13.461 ms`) | 建立 `idx_probe_created_time_desc` (`Index Scan`, 僅 `4` buffers)：<br>平均 **`0.94 ms`** (`EXPLAIN` 實測 `0.038 ms`) | 🚀 端到端 JDBC 查詢**加速 `13.89x`**，PostgreSQL 內部執行時間由 `13.461 ms` 降至 `0.038 ms`（**加速 `354x`**），Buffer 讀取量降低 **`99.7%`** |

---

## 四、 5 大微服務四象限 × 三階梯 (50 / 200 / 500 VUs) 動態實測結果

### 1. 實測環境與種子資料規模
- **基礎設施**：Docker PostgreSQL 16 (`postgres_db_backend`) + Redis 7.2 (`redis_container`)
- **微服務 JVM 配置**：對齊 `.run/*.run.xml`（`Corretto 21.0.6`, `-Xms128m -Xmx384m -XX:+UseG1GC`）
- **壓測模式**：單一領域服務輪測（Gateway `8000` + 輔助/受測服務），每階梯持續 `5 秒`，使用 `ramping-vus` 1 秒平滑建連避免 Windows TCP Loopback SYN 佇列瞬時溢位。
- **各微服務獨立資料庫真實種子資料規模**：
  - `iam_service`：`user = 50,004`、`user_role = 50,003`、`role = 7`、`function = 116`、`role_function = 140`
  - `competency_service`：`project = 50,000`、`user_skill = 150,010`、`user_project = 200,005`、`project_skill = 33,333`、`user_project_skill = 39,969`、`skill = 100`、`skill_level = 500`
  - `job_service`：`user_job_link = 50,000`、`job_posting = 1,000`、`company = 12`、`company_website = 12`
  - `alert_service`：`aquark_data = 200,000`、`alert_check_limit = 12`
  - `external_api_service`：`bot_config = 1`

### 2. 查詢與快取管線優化前後實測對照（Before vs. After — 500 VUs 極限壓測）

針對初測中發現的 **F-01 ~ F-05** 與 **F-09** 瓶頸，我們已於本分支實作完成以下核心查詢與快取優化：
1. **Liquibase 複合覆蓋索引（Covering Indexes）**：
   - `backend-iam-service`（`db.changelog-0003-add-iam-performance-indexes.yaml`）：新增 `idx_user_created_time_id`、`idx_role_created_time_id`。
   - `backend-competency-service`（`db.changelog-0002-add-competency-performance-indexes.yaml`）：新增 `idx_project_created_time_id`、`idx_skill_created_time_id`、`idx_skill_level_created_time_id`。
   - `backend-job-service`（`db.changelog-0002-add-job-performance-indexes.yaml`）：新增 `idx_user_job_link_created_time_id`、`idx_job_posting_created_time_id`、`idx_company_created_time_id`。
   - `backend-alert-service`（`db.changelog-0002-add-alert-performance-indexes.yaml`）：新增 `idx_aquark_data_trans_time`、`idx_aquark_data_station_trans_time`、`idx_aquark_data_created_time`、`idx_alert_check_limit_created_time`。
2. **兩階段延遲關聯（ID-First Deferred Join）與大表短效計數快取**：
   - 於 `com.example.BackendArchitectureLab.DataAccess.Impl.AquarkDataDataAccessImpl`、`com.example.BackendArchitectureLab.DataAccess.Impl.UserDataAccessImpl`、`com.example.BackendArchitectureLab.DataAccess.Impl.ProjectDataAccessImpl`、`com.example.BackendArchitectureLab.DataAccess.Impl.UserJobLinkDataAccessImpl` 實作兩階段延遲關聯：第一階段僅投影查詢分頁 `id`（觸發 PostgreSQL `Index Only Scan`，將 20 萬筆 `aquark_data` 排序由 `23.27 ms` / `5,376` buffers 降至 **`0.37 ms`** / `5` buffers；5 萬筆 `"user"` 由 `14.02 ms` / `1,670` buffers 降至 **`0.66 ms`** / `4` buffers），第二階段再以 `WHERE id IN (...)` 批次取回當頁 20 筆完整實體與關聯，並對 `>= 1,000` 筆之大表無條件總數啟用 5 秒短效快取，徹底消除 500 VUs 重複掃描全表 `COUNT(*)`。
3. **角色權限計數聚合與企業網站預載（消除三表 `JOIN FETCH` 膨脹與 LAZY N+1）**：
   - 於 `com.example.BackendArchitectureLab.DataAccess.Impl.RoleDataAccessImpl.searchRoles` 改用 `SELECT rf.role.id, COUNT(rf.id) ... GROUP BY rf.role.id` 聚合查詢填入 `Role.functionCount`，取代原本拉取 140 筆 `RoleFunction + Function` 實體的三表 `LEFT JOIN FETCH`。
   - 於 `com.example.BackendArchitectureLab.DataAccess.Impl.CompanyDataAccessImpl.searchCompanies` 新增 `findAllByIdsWithWebsites` 二次批量預載，消除 `CompanyMapper.toVo` 的 LAZY N+1。
4. **`CachePenetrationProtectionCache` Request Collapsing 前置與 `CacheStatsServiceImpl` `SCAN` 管線化**：
   - 於 `com.example.BackendArchitectureLab.Config.CachePenetrationProtectionCache` 將 `activeRedisFetches`（Request Collapsing）移至 `concurrencySemaphore = new Semaphore(10, true)` 之前，使高併發快取命中（如 `functions::all`）直接合併為單次 Redis 讀取並立即回傳，僅於 Cache Miss 時才爭搶信號量。
   - 於 `com.example.BackendArchitectureLab.Service.Impl.CacheStatsServiceImpl` 改用 `SCAN (count=200)` + `executePipelined` 批次 `hGetAll` 並搭配 1 秒記憶體快照快取，消除 `/api/cache-stats` 的阻塞式 `KEYS` 掃描。
   - 同步修復 `com.example.BackendArchitectureLab.Config.ApiPathArgumentResolver`、`com.example.BackendArchitectureLab.Config.ApiQueryArgumentResolver` 之 `ParameterNameDiscoverer` 初始化，以及 `com.example.BackendArchitectureLab.Service.Impl.BloomFilterService.addAll` 超過 `10,000` 筆時之自動分批寫入。

| 微服務模組 (500 VUs 象限 D) | 優化前吞吐量 / P95 / 錯誤率 (Before) | 優化後吞吐量 / P95 / 錯誤率 (After) | 實測改善幅度 |
| :--- | :---: | :---: | :--- |
| **IAM 認證授權 (Port 8002)** | `112.2 req/s` / `6,593 ms` / **`32.04%`** | **`273.4 req/s`** / **`3,425 ms`** / **`0.00%`** | 🚀 **錯誤率歸零 (`32.04%` ➔ `0.00%`)**，吞吐量提升 **`+143.7%`**，P95 延遲降低 **`48.1%`** |
| **Alert 告警感測 (Port 8008)** | `772.9 req/s` *(503 快速失敗)* / **`94.44%`** | **`448.4 req/s`** *(象限 C 達 `561.0 req/s`)* / **`4,012 ms`** / **`0.00%`** | 🚀 **錯誤率歸零 (`94.44%` ➔ `0.00%`)**，P50 僅 **`154.73 ms`**，3,860 筆請求 100% 成功 |
| **Competency 職能專案 (Port 8004)** | `265.5 req/s` / `3,457 ms` / `0.00%` | **`289.1 req/s`** *(象限 C 達 `350.7 req/s`)* / **`2,130 ms`** / **`0.00%`** | 🚀 **P95 延遲降低 `38.4%`**（`3,457 ms` ➔ `2,130 ms`），P99 降低 `44.7%`（`3,959 ms` ➔ `2,190 ms`） |
| **Job 職缺企業 (Port 8006)** | `251.9 req/s` / `2,952 ms` / `0.00%` | **`232.7 req/s`** *(象限 B 200VUs 達 `341.6 req/s`)* / **`2,672 ms`** / **`0.00%`** | 🚀 **P95 延遲降低 `9.5%`**（`2,952 ms` ➔ `2,672 ms`），P99 降低 `21.1%`（`3,560 ms` ➔ `2,808 ms`） |

### 3. 四象限 60 組完整實測數據彙整（優化後全數 `0.00%` 錯誤率）
> 完整表格已透過腳本自動回填至 [`stress-test/壓力測試結果.md`](../stress-test/壓力測試結果.md)。以下摘錄優化後各微服務於 **50 / 200 / 500 VUs** 下四象限之核心表現（**60 組測試全數 `0.00%` 錯誤率**）：

| 微服務模組 | 併發階梯 | 象限 A (無快取+平臺)<br>RPS / P95 / 錯誤率 | 象限 B (無快取+虛擬)<br>RPS / P95 / 錯誤率 | 象限 C (有快取+平臺)<br>RPS / P95 / 錯誤率 | 象限 D (有快取+虛擬)<br>RPS / P95 / 錯誤率 | 動態實測診斷結論 |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **IAM 認證授權**<br>*(Port 8002)* | **50 VUs**<br>**200 VUs**<br>**500 VUs** | `122.6` / `838ms` / `0%`<br>`168.1` / `2,316ms` / `0%`<br>`168.7` / `4,485ms` / **`0%`** | `109.4` / `932ms` / `0%`<br>`159.9` / `2,050ms` / `0%`<br>`188.0` / `4,800ms` / **`0%`** | `126.8` / `836ms` / `0%`<br>`210.6` / `1,929ms` / `0%`<br>`228.2` / `3,728ms` / **`0%`** | `128.3` / `845ms` / `0%`<br>`195.7` / `1,866ms` / `0%`<br>**`273.4` / `3,425ms` / `0%`** | **全象限、全階梯 0.00% 錯誤率**！500 VUs 下象限 D（黃金組合）吞吐量達 `273.4 req/s`，較象限 A（`168.7 req/s`）提升 **+62.1%** |
| **Competency 職能專案**<br>*(Port 8004)* | **50 VUs**<br>**200 VUs**<br>**500 VUs** | `156.1` / `685ms` / `0%`<br>`241.4` / `1,293ms` / `0%`<br>`280.6` / `3,375ms` / **`0%`** | `192.4` / `505ms` / `0%`<br>`303.5` / `899ms` / `0%`<br>`275.1` / `2,124ms` / **`0%`** | `205.0` / `452ms` / `0%`<br>`286.5` / `1,240ms` / `0%`<br>**`350.7` / `2,287ms` / `0%`** | `144.7` / `972ms` / `0%`<br>`223.1` / `1,294ms` / `0%`<br>`289.1` / **`2,130ms`** / **`0%`** | **全象限、全階梯 0.00% 錯誤率**！兩階段延遲關聯使 500 VUs P99 尾端延遲壓低至 `2,190ms`（象限 D），象限 C 吞吐達 `350.7 req/s` |
| **Job 職缺企業**<br>*(Port 8006)* | **50 VUs**<br>**200 VUs**<br>**500 VUs** | `113.5` / `867ms` / `0%`<br>`168.3` / `2,009ms` / `0%`<br>`228.0` / `3,779ms` / **`0%`** | `181.8` / `438ms` / `0%`<br>**`341.6` / `796ms` / `0%`**<br>`276.3` / `2,295ms` / **`0%`** | `174.3` / `463ms` / `0%`<br>`277.2` / `1,138ms` / `0%`<br>**`318.7` / `2,947ms` / `0%`** | `143.8` / `698ms` / `0%`<br>`207.6` / `1,825ms` / `0%`<br>`232.7` / `2,672ms` / **`0%`** | **全象限、全階梯 0.00% 錯誤率**！500 VUs 下黃金組合（象限 D）P99 延遲（`2,808ms`）較全關基準（`5,300ms`）降低 **47.0%** |
| **Alert 告警感測**<br>*(Port 8008)* | **50 VUs**<br>**200 VUs**<br>**500 VUs** | `169.1` / `737ms` / `0%`<br>`339.0` / `1,243ms` / `0%`<br>`494.0` / `2,192ms` / **`0%`** | `301.9` / `503ms` / `0%`<br>`548.3` / `935ms` / `0%`<br>`489.5` / `2,018ms` / **`0%`** | `325.7` / `485ms` / `0%`<br>**`568.0` / `1,061ms` / `0%`**<br>**`561.0` / `1,907ms` / `0%`** | `195.4` / `769ms` / `0%`<br>`390.2` / `1,840ms` / `0%`<br>**`448.4` / `4,012ms` / `0%`** | **全象限、全階梯 0.00% 錯誤率**！20 萬筆 `aquark_data` 延遲關聯與 `/cache-stats` `SCAN` 管線化後，最高吞吐達 **`568.0 req/s`** |
| **External 外部整合**<br>*(Port 8007)* | **50 VUs**<br>**200 VUs**<br>**500 VUs** | `508.1` / `210ms` / `0%`<br>`696.6` / `467ms` / `0%`<br>`773.4` / `1,410ms` / **`0%`** | `425.0` / `214ms` / `0%`<br>`668.3` / `481ms` / `0%`<br>`658.1` / `1,323ms` / **`0%`** | `457.2` / `201ms` / `0%`<br>`735.7` / `441ms` / `0%`<br>`765.9` / `1,300ms` / **`0%`** | `219.9` / `422ms` / `0%`<br>`374.3` / `856ms` / `0%`<br>`444.6` / `1,837ms` / **`0%`** | **全象限、全階梯 0.00% 錯誤率**！最高吞吐達 **`773.4 req/s`**（500 VUs）與 **`735.7 req/s`**（200 VUs 象限 C） |

---

## 五、 結構化優先級改善路線圖 (Prioritized Remediation Roadmap)

以下整理後續建立修復 PR 時可直接落地的具體工程方案（按投資報酬率與風險高低排序）：

### 階段一：P0 緊急修復（消除正確性 Bug、防穿透失靈與全表掃描熔斷）

1. **修復 `@ApiPath` 與 `@ApiQuery` 參數名稱解析（解決 F-01）**：
   - 於 `com.example.BackendArchitectureLab.Config.ApiPathArgumentResolver` 與 `com.example.BackendArchitectureLab.Config.ApiQueryArgumentResolver` 中呼叫 `parameter.initParameterNameDiscovery(DefaultParameterNameDiscoverer.getSharedInstance())`，並以 `parameter.getParameter().getName()` 作為保底，讓所有 `GET /{id}` 端點恢復正常。
2. **為四大微服務大表補齊 `created_time DESC` B-Tree 索引（解決 F-03）**：
   - 新增 Liquibase Changelog，為 `"user"`、`project`、`job_posting`、`user_job_link`、`aquark_data` 建立 `(created_time DESC)` 索引。
   - **預期效益**：單次預設分頁 `POST /*/search` 的 DB 查詢耗時由 `13~40 ms` 直接降至 `< 0.5 ms`，徹底消除 500 VUs 下 IAM 與 Alert 的 Gateway 慢呼叫熔斷。
3. **調整布隆過濾器預設容量與分批預熱機制（解決 F-02）**：
   - 將 `com.example.BackendArchitectureLab.Config.BloomFilterProperties.defaultExpectedInsertions` 調升至 `200,000L`~`500,000L`（或於各服務 `application.yml` 的 `cache.bloom-filter.overrides` 依表規模配置）。
   - 於 `com.example.BackendArchitectureLab.Config.BloomFilterInitializer` 中將 `BloomFilterDataAccessImpl.findAllEntityIds` 與 `bloomFilterService.addAll` 分開 `try-catch`，並以 `10,000` 筆為一個 Chunk 分批寫入，避免啟動期單次巨型 Lua 腳本阻塞。

### 階段二：P1 快取與閘道器核心吞吐量升級

1. **重構 `CachePenetrationProtectionCache` 鎖階層與空值標記（解決 F-04）**：
   - 將 `concurrencySemaphore` 移至 **Cache Miss 之後、實際回源查詢資料庫（`valueLoader.call()`）之前**，讓 Redis 快取命中與 `activeRedisFetches`（Request Collapsing）完全無鎖直通。
   - 將空值快取標記（`NULL_VALUE`）直接存入同一個快取鍵中，省去每次讀取額外的 `hasNullMarker` (`EXISTS null:...`) Redis 網路往返。
   - 將非 `sync` 的 `get(Object key)` 在布隆過濾器判定不存在時的回傳值由 `null` 修正為 `() -> null`，符合 Spring `Cache` 契約。
2. **全面根除生產路徑中的 Redis `KEYS` 呼叫（解決 F-05）**：
   - 移除 `com.example.BackendArchitectureLab.Service.Impl.JobPostingService`、`com.example.BackendArchitectureLab.Service.Impl.SkillService`、`com.example.BackendArchitectureLab.Service.Impl.UserVoiceUploadService` 中共 12 處無對應快取寫入端的「孤兒快取清除（Orphan Cache Eviction）」，並刪除 `RoleService.getRoleListCache`、`SkillService.getSkillListCache`、`CompanyService.getAllCompaniesCache` 三個死快取方法。
   - 將 `com.example.BackendArchitectureLab.Service.Impl.CacheStatsServiceImpl.getAllCacheStats` 改寫為 `SCAN` + `executePipelined`（如 Probe 1 所示，加速 `10.27x`）：
   ```java
   package com.example.BackendArchitectureLab.Service.Impl;

   import org.springframework.data.redis.core.Cursor;
   import org.springframework.data.redis.core.RedisCallback;
   import org.springframework.data.redis.core.ScanOptions;
   import org.springframework.data.redis.core.StringRedisTemplate;

   import java.nio.charset.StandardCharsets;
   import java.util.ArrayList;
   import java.util.List;

   public class CacheStatsScanPipelineExample {

       public List<Object> fetchAllStatsPipelined(StringRedisTemplate stringRedisTemplate) {
           List<String> statKeys = new ArrayList<>();
           ScanOptions scanOptions = ScanOptions.scanOptions().match("cache:stats:*").count(200).build();
           try (Cursor<String> cursor = stringRedisTemplate.scan(scanOptions)) {
               while (cursor.hasNext()) {
                   statKeys.add(cursor.next());
               }
           }
           return stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
               for (String statKey : statKeys) {
                   connection.hashCommands().hGetAll(statKey.getBytes(StandardCharsets.UTF_8));
               }
               return null;
           });
       }
   }
   ```
3. **純化 `backend-gateway` 為 Reactor Netty 並調優斷路器視窗（解決 F-06）**：
   - 排除 `backend-gateway` 繼承自父 `pom.xml` 的 `spring-boot-starter-tomcat` 依賴，確保以純非阻塞 Reactor Netty 運行。
   - 將 `GatewayCircuitBreakerConfig` 的 `slidingWindowSize` 由 `20` 調升至 `100`、`minimumNumberOfCalls` 調升至 `50`，避免高併發瞬間前 10 筆慢查詢即觸發全站熔斷。

### 階段三：P2 演算法、啟動速度與背景排程優化

1. **職缺爬蟲改用 `HashMap` 複合鍵索引比對（解決 F-07）**：
   - 於 `com.example.BackendArchitectureLab.Service.Impl.JobPostingService.scrapeAndAnalyzeJobs` 中預先建立 `Map<String, JobPosting>`，將 $O(N \times M)$ 降至 $O(N + M)$（加速 `29.47x`）。
2. **將啟動期外部 I/O 移至非同步背景或按需初始化（解決 F-08）**：
   - `SeleniumJobCrawler` 改於首次執行爬蟲排程時 Lazy-init `WebDriverManager`；`LineGfRichMenuService` 改由背景執行緒非同步註冊 RichMenu。
3. **補齊 `CompanyDataAccessImpl.searchCompanies` 的 `@EntityGraph(attributePaths = {"websites"})` 與 Outbox `FOR UPDATE SKIP LOCKED`（解決 F-09、F-10）**。

---

## 六、 可重複執行與更新資料之雙模式壓測操作手冊 (Repeatable Benchmark CLI Manual)

本次檢視已全面重構並修復 `stress-test/run-ondemand-benchmarks.ps1`、`stress-test/*.sql` 與 `stress-test/k6/*.js`，並新增 `backend-test-support` 的 `TargetedBottleneckProbeIT`。未來任何架構調優後，僅需執行以下指令即可重刷資料、重跑探針或更新報告：

### 1. 模式一：執行隔離容器（`SharedContainers`）定向瓶頸探針
自動啟動 PostgreSQL 16 + Redis 7.2 + Kafka + Toxiproxy 測試容器，執行 6 項瓶頸探針並輸出 JSON 指標至 `target/k6-reports/targeted-bottleneck-probes.json`：
```powershell
powershell -ExecutionPolicy Bypass -File .\stress-test\run-ondemand-benchmarks.ps1 -RunProbes -SkipK6
```

### 2. 模式二：一鍵重刷本機 Docker 四大微服務壓測種子資料（冪等更新）
自動將 `stress-test/*.sql` 同步至 `postgres_db_backend` 容器，跨 `iam_service`、`competency_service`、`job_service`、`alert_service`、`external_api_service` 執行冪等建表、補齊索引與 5萬~20萬筆種子資料灌入，並執行 `verify_counts.sql` 驗證筆數：
```powershell
powershell -ExecutionPolicy Bypass -File .\stress-test\run-ondemand-benchmarks.ps1 -RefreshDb -SkipK6
```

### 3. 模式三：指定單一服務或單一象限快速重跑壓測並自動更新 `stress-test/壓力測試結果.md`
適用於修正某個微服務（如 `iam` 或 `alert`）後，以輕量秒數（預設 5 秒）快速驗證並自動將最新結果回填至 `stress-test/壓力測試結果.md`：
```powershell
# 僅重跑 IAM 服務於象限 D (快取+虛擬執行緒) 的 50/200/500 VUs 壓測 (5 秒) 並更新報告
powershell -ExecutionPolicy Bypass -File .\stress-test\run-ondemand-benchmarks.ps1 -Quadrant D -Service iam -Duration 5

# 僅重跑 Alert 服務於全四象限 (A,B,C,D) 的壓測且不覆寫 Markdown
powershell -ExecutionPolicy Bypass -File .\stress-test\run-ondemand-benchmarks.ps1 -Quadrant All -Service alert -Duration 5 -NoUpdateMarkdown
```

### 4. 模式四：全量重刷資料庫 + 執行 6 項瓶頸探針 + 跑完 60 組四象限 k6 壓測並更新報告
```powershell
powershell -ExecutionPolicy Bypass -File .\stress-test\run-ondemand-benchmarks.ps1 -RefreshDb -RunProbes -Quadrant All -Service all -Duration 5
```
