# 0017. JPA Pagination Batch Size Governance Over Collection EntityGraph

我們需要在全專案中確立 JPA/Hibernate 分頁查詢（回傳 `Page<T>`）之效能治理原則：嚴格禁止在分頁查詢中掛載包含一對多/多對多（`@OneToMany` / `@ManyToMany`）集合關聯之 `@EntityGraph` 或 `JOIN FETCH`，一律採用資料庫原生 `LIMIT / OFFSET` 真分頁，並透過實體級 `@BatchSize` 自動批次載入關聯集合，徹底根除記憶體假分頁（HHH90003004）與大表全表掃描效能黑洞。

## Context

在系統微服務的高效能查詢實踐中，當資料表規模達到數萬至數十萬筆（如 IAM 服務之 50,000 筆使用者大表）時，分頁搜尋面臨以下嚴重架構與效能陷阱：

1. **集合關聯抓取觸發記憶體假分頁 (HHH90003004: In-Memory Pagination Hazard)**：
   開發者為了避免轉 VO 時延遲載入（Lazy Loading）觸發 N+1 查詢，容易直覺在 Repository 的分頁方法（如 `Page<User> findAll(Specification, Pageable)`）掛載 `@EntityGraph(attributePaths = {"roles", "roles.role"})`。
   然而，JPA/Hibernate 規範中，一對多關聯的 `LEFT JOIN` 會使結果集列數（Rows）大於實體數（例如一個使用者有多個角色即會膨脹為多列）。若直接下達 SQL `LIMIT`，將破壞分頁計數準確性。因此 Hibernate 核心引擎會**強行拔除 SQL 的 `LIMIT / OFFSET` 語法**，並拋出警告：
   `HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory`。
   這導致每次前端發起單頁分頁搜尋時，資料庫會執行無限制之全表掃描（50,000 筆資料全部撈回 JVM 堆疊記憶體），耗時高達 2.5 秒，並在高並發時直接塞滿連線池引發 API Gateway 10 秒 503 熔斷。
2. **多對一與一對多關聯之混淆 (Confusion between @ManyToOne and @OneToMany Fetch)**：
   在其他微服務（如 JobService、CompetencyService）中，`@EntityGraph` 抓取的均為 `@ManyToOne`（多對一單一外鍵實體，如 JobPosting -> Company），不會造成結果集列數膨脹，因此資料庫可正常執行原生 `LIMIT`。但此模式被盲目套用至 `@OneToMany` 集合關聯時，即會引發災難性的記憶體分頁。

評估方案：
1. **方案 A（資料庫原生分頁 + 實體級 `@BatchSize` 批次載入 - 推薦）**：
   - 移除 Repository 分頁方法（回傳 `Page<T>`）上的集合級 `@EntityGraph`。
   - 資料庫層產出標準 `LIMIT ? OFFSET ?`（或 `offset ? rows fetch first ? rows only`），僅檢索分頁所需之少量資料列（如 20 筆，耗時 < 3ms）。
   - 轉 VO 讀取 `@OneToMany` 延遲關聯時，由實體上配置的 `@BatchSize(size = 50)` 自動發出單一 `WHERE id IN (?, ?...)` 批次查詢（耗時 < 2ms），完全消除 N+1，總耗時降至 20~30ms。
2. **方案 B（兩階段查詢 Two-Phase Query）**：
   - 先透過 Specification 僅查詢分頁 ID 清單 `Page<UUID>`，再以 `findAllByIdIn` 搭配 `@EntityGraph` 載入完整實體。
   - 缺點：侵入性高、代碼樣板多，且未能善用 Hibernate 既有之 `@BatchSize` 機制。

## Decision

我們決定採用 **方案 A**，具體架構規範如下：

1. **JPA 分頁查詢禁用集合抓取準則 (Collection Fetch Prohibition in Pagination)**：
   - 凡回傳型別為 `Page<T>`、或方法簽名接受 `Pageable` 參數之 Spring Data JPA Repository 方法，**嚴格禁止**配置包含 `@OneToMany` 或 `@ManyToMany` 集合屬性之 `@EntityGraph`，亦嚴禁於 JPQL 中撰寫 `JOIN FETCH e.collection`。
   - 單筆查詢（回傳 `Optional<T>` 或單一物件，如 `findById`）若無分頁參數，則允許配置包含集合屬性之 `@EntityGraph`。
2. **集合關聯全面推行 `@BatchSize` 治理 (Universal @BatchSize Governance)**：
   - 所有實體之一對多延遲載入集合（如 `User.roles`），必須宣告 `@BatchSize(size = 50)`。
   - 當分頁查詢回傳實體清單後，在事務邊界（`transactionExecutor.executeReadOnly`）內存取集合屬性時，Hibernate 會自動依據 batch size 聚合為極少數 `WHERE in (...)` 查詢，兼顧資料庫原生分頁與避免 N+1。
3. **分頁查詢 SQL 健全性驗證 (Pagination SQL Sanity Assertion)**：
   - 所有分頁搜尋之整合與單元測試，必須確認 Hibernate 產生之 SQL 包含原生分頁關鍵字（`offset` / `limit`），且嚴禁在日誌中出現 `HHH90003004` 警告。

## Consequences

- **徹底根治大表分頁效能黑洞**：50,000 筆使用者之分頁搜尋耗時由 2,500ms 驟降至 20~30ms，徹底杜絕 Gateway 10 秒 503 逾時。
- **防止記憶體溢出（OOM）**：避免高頻率分頁請求將全表數萬筆資料載入 JVM 堆疊記憶體做假分頁。
- **確立清晰之關聯載入心智模型**：團隊明確區分「多對一單物件可使用 EntityGraph」與「一對多集合必須由 BatchSize 治理」的架構分界。
