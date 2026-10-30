# Transaction Compensation System

一個去中心化的交易補償系統，旨在不使用分散式兩階段提交（2PC）的情況下，保證隔離的微服務與外部合作夥伴 API 之間的最終一致性。

## Language

**Compensation (交易補償)**:
當本地業務交易已成功 Commit，但其後續分散式流程（如外部同步）發生異常時，由消費端發起的非同步還原機制，旨在將系統狀態回復至交易前的歷史一致狀態。
_Avoid_: Rollback (僅用於本地資料庫事務的 rollback), Transaction cancel, Undo.

**Outbox Event (Outbox 事件)**:
與業務資料更新在同一個資料庫交易中原子性寫入的事件記錄，旨在為訊息佇列（如 Kafka）提供可靠的至少一次（at-least-once）訊息遞送保證。
_Avoid_: Message queue log, Outgoing queue, Transaction log.

**Dead Letter Topic (死信主題)**:
一個專門的 Kafka 主題，用於隔離那些帶有永久性、不可重試錯誤的訊息，以確保主要的消費分區（partitions）永不被阻塞。
_Avoid_: Retry queue, Error channel.

**Dead State (DEAD 終態)**:
當本地發送端 Outbox 或消費端日誌遭遇暫時性錯誤重試上限（5 次）後被標記的最終狀態，代表該事件已遭隔離，必須靜待人工介入。
_Avoid_: Error status, Failed state, Quarantined state.

**Trust Boundary (信任邊界)**:
隔離不可信 PR 程式碼與可信金鑰環境的分隔機制。
_Avoid_: Security perimeter, Auth zone.

**Trigger Workflow (觸發工作流程)**:
於 PR 生命週期被動觸發、無 Secret、不檢出代碼的輕量工作流程（`ai-review-trigger.yml`）。
_Avoid_: PR runner, Event hook.

**Trusted Review Workflow (受信任審查工作流程)**:
僅自預設分支（`master`）檢出受信任程式碼、受環境保護（`apikey`）並持有效 API 金鑰的審查工作流程（`ai-review-trusted.yml`）。
_Avoid_: Background reviewer, Secret workflow.

**Target Resolution (審查目標解析)**:
自 GitHub Actions 事件（`workflow_run` / `workflow_dispatch`）與傳遞 Artifact 中多重來源解析出唯一 PR 編號與 Head SHA 的機制。
_Avoid_: PR discovery, Target finding.

**TOCTOU Guard (審查時間競態守衛)**:
確保審查起始與結果發布時的 Head SHA 絕對一致的二次校驗防護。
_Avoid_: SHA check, Race lock.

**Fail-Closed (閉合失敗)**:
當審查基礎設施或校驗遭遇任何異常時，強制阻擋或發布 REQUEST_CHANGES，絕不放行（APPROVE）。
_Avoid_: Error stop, Fail safe.

**Integration Test (端到端整合測試)**:
在真實運行的外部相依服務（如 Docker 容器化之 PostgreSQL、Kafka、Redis）中驗證微服務跨邊界業務閉環、分佈式鎖定與交易補償邏輯的自動化測試套件。
_Avoid_: End-to-end mock test, Fake-driven test.

**Singleton Container Lifecycle (單例容器生命週期)**:
在整個 JVM 測試執行期間僅啟動一次的共用容器管理機制，透過靜態單例持有容器實例，避免每個測試類別重複啟動與銷毀帶來的資源浪費與測試卡頓。
_Avoid_: Per-class container, Transient container.

**Dynamic Property Source (動態屬性來源)**:
在 Spring Boot 測試環境啟動時，動態將 Testcontainers 映射出的隨機連接埠與連線資訊注入 Spring ApplicationContext 的屬性配置機制。
_Avoid_: Static test property, Hardcoded port config.

**Diff-Scope Pentest (差異範疇滲透測試)**:
在 PR 審查階段僅針對 Git 變更檔案進行增量式 SAST 與動態沙箱 PoC 驗證的安全測試機制。
_Avoid_: Full repository scan, Static linting, Delta audit.

**Security Gate (安全門禁)**:
當自動化安全審查工具發現具備已驗證 Exploit PoC 且嚴重度達 High/Critical 時，強制阻擋 PR 合併的保護機制。
_Avoid_: Build failure, Error barrier, Quality check.

**API Spec Target Pairing (API 規格靶機配對)**:
將 OpenAPI/Swagger 規格定義與動態服務端點同時提供給 AI 滲透代理，以全面覆蓋隱藏路由與非公開端點的測試模式。
_Avoid_: URL guessing, Blind crawling.

**Quality Gate (品質門禁)**:
在 CI/CD 與本地建置流程中，針對程式碼壞味道、安全弱點（Vulnerabilities & Hotspots）以及未達標之測試覆蓋率（Line Coverage < 80%）進行強制性閾值阻擋的靜態品質防線。
_Avoid_: Code check, Static filter, Lint pass.

**Coverage Report Binding (覆蓋率報告綁定)**:
在多模組 Maven 架構中，將各子模組產生的 JaCoCo XML 測試覆蓋率資料路徑顯式綁定並注入 SonarQube Scanner，以確保排除模組與覆蓋率計算之精確性的機制。
_Avoid_: Manual report upload, Raw metric import.

**Trace Correlation (鏈路日誌關聯)**:
在分散式微服務架構中，以唯一的鏈路識別碼貫穿 HTTP 請求、遠端呼叫與非同步任務日誌的端到端追蹤機制。
_Avoid_: Log linking, Request tagging, Span tracker.

**Log Stream Label (日誌串流標籤)**:
日誌儲存引擎中用於建立倒排索引的低基數維度標籤，用以區隔不同服務與環境的日誌流，避免索引維度爆炸。
_Avoid_: High-cardinality index, Message tag, Payload field.

**Log Masking (日誌脫敏防護)**:
日誌在輸出至儲存端或控制台前，自動對金鑰、密碼及憑證等敏感字串進行模式比對與遮蔽的合規防線。
_Avoid_: Code redaction, Filter drop, Manual sanitize.

**Async Log Buffering (非同步日誌緩衝降級)**:
日誌輸出端採用的非阻塞記憶體環狀緩衝機制，當日誌伺服器斷線或超時時自動丟棄或降級輸出，確保主業務執行緒永不被阻塞。
_Avoid_: Sync logging, Blocking queue, Hard-fail logging.

**Chaos Verification (混沌驗證)**:
在端到端整合測試環境中，主動注入非預期之網路分區、TCP 延遲、資源耗盡或節點暴斃，以自動化檢驗分散式狀態機是否能自愈並維持一致性的工程實踐。
_Avoid_: Destructive test, Random breakdown, Uncontrolled crash.

**Toxic Proxy (故障注入代理)**:
介於應用程式與其基礎設施（如 DB、Kafka、Redis）之間的網路代理管道，由測試套件動態操控以模擬網路延遲、抖動或斷線。
_Avoid_: Mock server, Stub proxy, Traffic sniffer.

**Cascading Failure Defense (微服務防雪崩防禦)**:
在分散式鏈路中結合斷路器（Circuit Breaker）、本機降級快取與線程隔離（Bulkhead），防止單一下游服務異常導致上游調用方資源衰竭的保護機制。
_Avoid_: Retry storm, Hard dependency, Fail cascade.

**Stale Worker Fencing (陳舊工作者隔離防護)**:
當分散式工作者因長時間 GC 停頓或網路隔絕導致租約過期後，藉由資料庫原子單調遞增的代數 Token（Fencing Version）徹底阻絕其甦醒後產生的過期髒寫入。
_Avoid_: Race lock, Lease renewal, Soft check.

**Fail-Closed Authorization (閉合失敗授權)**:
當身分與權限判定服務無法連線且本地快取未命中時，一律視為未授權並立即中斷請求的防衛原則，防止在安全狀態不明時意外放行操作。
_Avoid_: Soft allow, Fail-open, Permissive auth.

**Lease Preemption (租約搶佔接管)**:
當持有分佈式租約之工作者心跳超時或中斷時，由其他待命工作者透過原子條件更新（CAS）強制奪取租約並遞增代數（Version）的自主容錯機制。
_Avoid_: Lock steal, Force override, Dead lock break.

**Toxiproxy Pipeline (故障注入管道)**:
在整合測試生命週期中，透過 TCP 代理層動態插入網路延遲（Latency Toxic）與頻寬中斷（Bandwidth/Down Toxic）的確定性模擬管線。
_Avoid_: Network interceptor, Fake port, Mock socket.

**Pre-commit Compliance Gate (提交前規範通行閘門)**:
在版本控制提交變更前，由本地端確定性稽核引擎執行的強制性守門防線，以阻絕任何違反微服務架構邊界或安全規範的程式碼進入儲存庫。
_Avoid_: Commit linter, Git check, Format hook.

**Local Deterministic Audit (本機確定性規範稽核)**:
在開發端本機環境中執行的離線、零相依、高效率靜態規則掃描機制，與 CI 遠端稽核規則 100% 鏡像對齊，確保提交即合規。
_Avoid_: Remote linting, Cloud audit, Manual checklist.

**Schema Migration (資料庫結構遷移)**:
以版本化變更紀錄持續演進資料表、欄位、索引與約束的治理機制，取代仰賴 ORM 在啟動時隱式調整資料庫結構。
_Avoid_: Auto DDL, Hibernate update, Schema sync.

**Migration Ownership (遷移所有權)**:
在多微服務系統中，對應資料庫結構變更的責任邊界；每個服務僅維護自己擁有之資料庫的遷移紀錄。
_Avoid_: Central migration, Shared schema control, Cross-service DDL.

**Optimistic Concurrency Control (樂觀並發控制)**:
在更新高價值業務資料前，以版本比對偵測並阻止併發覆寫的控制模型。
_Avoid_: Last write wins, Blind overwrite, Silent conflict.

**Gateway Fallback (網關降級回退)**:
當 API Gateway 轉發下游微服務發生逾時、熔斷開啟或連線中斷時，自動將流量導向預設之降級控制器並回應結構化標準錯誤之容錯機制。
_Avoid_: Error forwarding, Dropped connection, Gateway crash.

**Near-Cache Authorization (近端授權快取)**:
在調用端微服務本機記憶體中維護短時效的授權檢驗快取，並在授權中心離線且快取過期時嚴格遵循 Fail-Closed 原則阻擋存取的防禦性快取架構。
_Avoid_: Central-only auth, Remote call on every hit, Unbounded token cache.

**Prototype Cluster Deployment (全微服務叢集原型部署)**:
透過整合式容器編排定義檔（如 compose.prototype.yaml）一鍵拉起包含所有微服務、Python AI 側車及全套中間件之自包含可運行原型環境。
_Avoid_: Partial mock run, Incomplete local stack, Manual service start.

**Reactive Stream Bridge (響應式串流橋接)**:
在以伺服器端容器為基礎的微服務中，以非阻塞連線池與響應式資料流橋接外部長時間運行的非同步 I/O，防止平臺執行緒與底層 Carrier Thread 被外部慢速調用長時間佔用的邊界機制。
_Avoid_: Blocking HTTP client, Thread sleep streaming, Async Servlet polling.

**Backpressure Push Buffer (具背壓之推播緩衝)**:
在即時推播中，取代無界同步連線遍歷，改採具容量限制與明確背壓溢出策略的非阻塞推播管道，防止慢消費者拖垮全域推播佇列與記憶體的保護防線。
_Avoid_: Unbounded session queue, Blocking session write, Infinite memory buffer.

**Slow Consumer Eviction (慢消費者隔離防護)**:
當單一客戶端連線因為網路延遲或處理停滯導致發送緩衝區持續飽和時，系統主動拋棄過期訊框或關閉該連線以保全全域推播吞吐的防衛原則。
_Avoid_: Stalled pipeline, Global wait, Blocking broadcast.

**Hot Multicast Sink (熱發布多播通道)**:
在響應式推播架構中，作為一對多事件派發中心之無鎖併發緩衝區，允許外部非響應式事件來源以非阻塞方式發布訊號，並自動扇出廣播給多個獨立訂閱者的整合機制。
_Avoid_: Message polling loop, Blocking queue per client, Shared sync list.

**Stream Passthrough Route (串流穿透路由)**:
在 API 網關中針對長時間保持的單向串流特別定義的路由通道，關閉 HTTP 回應緩衝區累積與短時超時斷路器，確保每個生成訊框或數據片段零延遲透傳至客戶端的機制。
_Avoid_: Buffered proxy, Short-timeout circuit breaker, Polling fallback.

**Reactive Frame Keep-Alive (響應式訊框心跳保活)**:
在長連線通訊管線中，由伺服端週期性發送協定層心跳訊框或資料註解，用以防止中介代理或負載均衡器因閒置逾時而強制斷開連線的保活防線。
_Avoid_: Heartbeat payload JSON, Client polling ping, Blind idle timeout.

**Undeclared Cache Bypass (未宣告快取放行原則)**:
當快取防穿透保護機制面臨未於布隆過濾器清單明確宣告之快取分區時，直接放行資料庫查詢並杜絕在儲存層動態建立空布隆過濾器，防止誤判穿透導致合法資料被阻絕或引發空指標例外的防禦性原則。
_Avoid_: Blind bloom check, Auto empty filter, False penetration drop.

**Reactive Chunked Streaming (響應式分塊串流)**:
針對萬筆級以上巨量資料集合，廢除一次性全表載入與無界快取巨鍵，改以主鍵排序之分頁游標分批讀取，並透過非阻塞響應式流（Flux）與 SSE 協議以固定批次大小逐塊推播至客戶端的流式傳輸機制。
_Avoid_: Full table in-memory load, Cache BigKey, Sync batch polling.

**Stream Heartbeat Frame (串流心跳保活訊框)**:
在長時間開啟的 SSE 伺服器發送事件通道中，週期性由伺服端發送符合 SSE 規範之協定層註釋訊框（如 `: keep-alive\n\n`），以防止反向代理（如 Caddy）、網關或負載均衡器因閒置超時中斷串流連線的保活機制。
_Avoid_: Heartbeat JSON event, Idle timeout drop, Ping polling.

**Dynamic Model Specification (動態模型規格註冊)**:
在 AI 審查調度體系中，以資料結構顯式宣告各語言模型之最大批次 Tokens、單批檔案上限及輸出 Token 上限的靜態合約機制，取代無界黑箱分發。
_Avoid_: Hardcoded batch size, Blind model dispatch.

**Dual-Condition Batching (雙重批次切分條件)**:
在程式碼變更審查批次建構時，同時依據模型規格的 Token 總量上限與檔案數量上限作為雙重切分條件，防止大批次或多小檔塞入單一批次導致輸出截斷的切分防線。
_Avoid_: Char-only batching, Unbounded file batch, Truncated prompt cut.

**Template Composition Service (模板組合服務架構)**:
在多模型或外部 API 整合層中，以抽象模板方法模式配合子類別建構子注入（Lombok @RequiredArgsConstructor）持有相依性，杜絕繼承手寫建構子呼叫與屬性遺失的健全架構。
_Avoid_: Manual super constructor, Field injection, Loose parameter pass.

**Cache Telemetry Mesh (全微服務快取遙測網格)**:
在全微服務架構中，以事件驅動訊息管線（Kafka cache-stats）集中收集快取命中、未命中、布隆阻擋與空值命中事件，並由專責警報/監控服務非同步聚合入庫的全域觀測體系。
_Avoid_: Direct Redis stats write, Distributed multi-write counter.

**Telemetry Fail-Safe Isolation (遙測故障安全隔離防護)**:
在快取度量指標傳遞管線中，配置短逾時與非阻塞非同步機制。當訊息代理伺服器短暫離線或網路抖動時，度量發送失敗僅記錄日誌，絕對不阻塞執行緒、不拋出例外，確保核心業務查詢流程不受次要監控影響。
_Avoid_: Blocking telemetry send, Hard failure on metrics drop.

**Derived Cache Metrics (快取衍生計算指標)**:
在聚合各快取指標時，直接原子計算之總請求量（total 為 Long）與綜合命中率數值（hitRate 為 Float，範圍 0.0 ~ 1.0，如 0.85f），維持對外介面向下相容的同時消除客戶端重複計算成本。
_Avoid_: Frontend metric calculation, Raw-only counters.

**Repository Reverse Reference Audit (Repository 逆向引用檢索)**:
在本地 Pre-commit 靜態稽核中，針對 Repository 介面所宣告之自訂衍生方法進行全專案逆向呼叫掃描，嚴禁存在未被任何 Service、DataAccess 或測試呼叫的孤島宣告，以貫徹 YAGNI 精簡原則並阻絕死代碼殘留。
_Avoid_: Dead query declaration, Unreferenced repository method, Zombie interface.

**Derived Deletion Transaction Boundary (衍生刪除事務邊界防護)**:
在 Spring Data JPA 資料存取架構中，針對包含 `deleteBy*` / `removeBy*` 之衍生刪除操作，在 DataAccess 與 Service 實作層級強制顯式標註 `@Transactional` 交易邊界，杜絕在非同步監聽或無事務上下文執行緒引發 `TransactionRequiredException` 執行期崩潰的防禦防線。
_Avoid_: Non-transactional derived delete, Implicit transaction assumption, Async delete failure.

**Strongly-Typed Contract Discipline (強型別契約紀律)**:
在 Controller 與 Service 介面規範中，嚴禁公開 API 與服務邊界回傳弱型別之 `Map<Object, Object>` 或非結構化 Map 容器，強制要求採用 Java 17+ `Record` 或專案標準 `Vo` 宣告明確契約，以確保呼叫端、前端與文件生成具備嚴格型別安全性。
_Avoid_: Untyped map return, Dynamic schema passing, Weak contract payload.

**External Platform Status Probe (外部平台執行時狀態探針)**:
在整合多方通訊與 AI 平台的邊界服務中，以故障安全（Fail-Safe）與非阻塞方式探測 JDA（Discord）、LINE 與多模型 API 憑證與閘道連線狀態，並彙總為結構化度量物件的監控機制。
_Avoid_: Blind platform check, Blocking connection probe, Unhandled ping failure.

**Normalized Dialogue History (1:N 規範化對話歷史)**:
在聊天機器人持久化架構中，摒棄關聯資料表中的單一 JSON 陣列或反正規化欄位，將每則訊息以獨立資料庫紀錄（Row）外鍵關聯至主會話，並透過滑動視窗（Sliding Window）動態撈取上下文之規範化模型。
_Avoid_: Monolithic conversation JSON, Unbounded history string, JSON array column.

**On-Demand Message Audit (按需訊息調閱)**:
在管理端點與審計調閱介面中，將會話清單查詢與詳細對話歷史解耦，會話清單僅返回元數據與摘要，而具體歷史訊息依會話識別碼獨立按需調閱，以杜絕 BigKey 傳輸與多餘網路負擔的存取模式。
_Avoid_: Heavy session list payload, All-in-one conversation dump.

**Dual-Rail Retrieval (雙軌實體獲取標準)**:
在系統微服務架構中，針對全域聚合根實體，強制且僅提供「分頁搜尋軌（POST /search）」與「非阻塞串流軌（GET /stream?chunkSize=250）」兩種標準端點，徹底根除無界 getAll 的雙軌存取模型。
_Avoid_: Monolithic getAll, Unbounded findAll endpoint, Inconsistent listing APIs.

**Unbounded Endpoint Eradication (無界端點根除紀律)**:
在對外 Controller 層級嚴格禁止公開無條件、無上限之全量撈取 API，杜絕大資料量導致服務記憶體耗盡（OOM）與 API 閘道逾時（Gateway 503/504）的防護紀律。
_Avoid_: Public findAll, Memory-exhausting listing, Gateway timeout bypass.

**Unimplemented Stub Guard (未實作存根守門門禁)**:
在本地代碼稽核與 CI AI Review 中，雙管齊下自動檢測並阻擋未實作存根（如拋出 UnsupportedOperationException、殘留 TODO 存根、回傳假空物件）及架構缺失（控制器未實作雙軌端點），杜絕假實作與技術債累積。
_Avoid_: UnsupportedOperationException stub, Fake empty return, Forgotten TODO.

**Dual-Rail Migration Burn-down (雙軌遷移燃盡名單)**:
在規範稽核規則中維護既有未遷移 Controller 的暫時容許白名單。所有新增實體控制器必須 100% 遵守雙軌規範，既有遺留控制器則隨重構進度逐一移出名單，實現無痛漸進式收斂。
_Avoid_: Blanket rule exception, Permanent legacy immunity, Uncontrolled architectural drift.

**In-Memory Pagination (記憶體假分頁)**:
當 ORM（如 Hibernate）查詢同時包含一對多集合抓取（Collection Fetch）與分頁參數時，資料庫端無法下達 LIMIT，被迫載入全表資料於應用層記憶體切片的分頁反模式。
_Avoid_: Memory slicing, Client-side paging, False pagination.

**Batch Fetching (實體批次抓取)**:
透過 ORM 批次抓取機制（如 @BatchSize）在存取延遲載入集合時自動以 WHERE id IN (...) 聚合為單一批量查詢，既維持資料庫原生 LIMIT 分頁，又根除 N+1 延遲載入的治理模式。
_Avoid_: Multi-fetch, In-query loading, Ad-hoc loop query.

**Swagger Contract Discipline (Swagger 契約紀律)**:
在對外 API 控制器與資料傳輸模型中，強制要求所有路徑參數、查詢參數、DTO 類別與欄位顯式標註 @Parameter 與 @Schema，嚴格定義 UUID 格式、必填約束與真實範例值，杜絕模糊或未文件化欄位的規範。
_Avoid_: Undocumented parameter, Weak schema, Fuzzy example value.

**RESTful Resource Mapping (RESTful 資源映射規範)**:
在 HTTP API 路由設計中，嚴格廢除 URL 路徑中的動詞後綴（如 /add、/update、/delete），統一採用標準 HTTP 動詞（POST 建立、PUT /{id} 更新、DELETE /{id} 刪除、GET /{id} 取得單筆）映射實體生命週期的架構紀律。
_Avoid_: RPC verb path, Action-based URL, Inconsistent HTTP method.

**Batch Query (批次識別碼查詢)**:
在 HTTP API 設計中，以 `POST /api/{resource}/batch-query` 傳入識別碼集合，單次取得多筆實體並同時回傳陣列、字典視角與遺失清單的端點架構，徹底消除前端 N+1 查詢與 URL 長度限制。
_Avoid_: In-loop single query, Query string ID joining, Multi-GET loop.

**Granular Multi-Key Cache (細粒度多鍵快取)**:
在執行批次查詢時，複用單筆 `GET /{id}` 之細粒度快取 Key 逐筆檢查命中，僅對未命中 ID 聚合單次 SQL 批次載入並回填各自快取，兼顧零快取鍵組合爆炸與高命中率的快取架構。
_Avoid_: Coarse-grained list cache, Serialized collection key, Multi-key combinatorial explosion.

**Four-Tier Call Telemetry (四層架構呼叫樹遙測)**:
貫穿 Controller、Service、DataAccess、Repository 四層架構之階層式呼叫樹追蹤機制，精確捕捉跨層呼叫鏈與下游依賴之耗時分佈。
_Avoid_: Flat method timer, Unscoped profiler, Ad-hoc benchmark print.

**Self-Centric Relative Ratio (父級相對占比)**:
在階層式呼叫樹中，以直接父方法之完整執行耗時為 100% 分母，計算子呼叫佔用父級時間比例的度量模型。
_Avoid_: Global execution percentage, Root-only ratio, Flat duration share.

**Execution Self-Time (節點自身耗時)**:
單一方法內部排除所有子方法呼叫總和後的純內部運算與邏輯處理耗時（Self = Duration - Sum(Children)）。
_Avoid_: Inclusive time, Wall-clock time only, Raw elapsed time.

**Diagnostic Tracing Mode (診斷追蹤模式)**:
由 HTTP 標頭（X-Diagnostic-Trace）或配置中心動態開關觸發之 100% 全量階層呼叫樹日誌與耗時聚合收集機制，平時維持低開銷抽樣。
_Avoid_: Always-on heavy tracing, Production blind debug, Global debug logging.

**POJO/PO/DTO/VO/BO/DAO Separation (架構模型分層職責分離)**:
將專案中各領域資料物件按生命週期與邊界嚴格劃分為 Persistent Object (PO/Entity)、Data Transfer Object (DTO/Request/Query)、View Object (VO)、Business Object (BO)、Messaging Event (Event) 與 Data Access Object (DAO/Repository)，澈底落實關注點分離。
_Avoid_: Anemic monolithic model, Leaking entity to controller, Cross-layer model abuse.

**Request DTO (請求傳輸物件)**:
客戶端發起寫入或操作（POST/PUT/PATCH/DELETE）時傳遞至 API 端點之輸入結構，命名一律以 `*Request` 結尾並收納於各模組之 `Dto/Request/` 套件。
_Avoid_: Inbound VO, Request Vo suffix, Map payload.

**Query DTO (查詢參數物件)**:
客戶端發起檢索、搜尋或分頁（GET/POST search）時傳遞之過濾參數集合，命名一律以 `*SearchQuery` 或 `*Query` 結尾並收納於各模組之 `Dto/Query/` 套件。
_Avoid_: Ad-hoc query params, Raw filter map, Controller-bound entity spec.

**View Object (呈現層視圖物件)**:
呈現層（Web API）回傳前端渲染呈現之資料契約結構，命名一律以 `*Vo` 結尾並收納於各模組之 `Vo/` 套件。嚴禁包含資料庫內部敏感技術欄位與 Lazy 關聯集合。
_Avoid_: Returning PO directly, Exposing internal state, OutVo naming inconsistency.

**Messaging Event (非同步訊息事件)**:
微服務間透過 Kafka 或非同步事件通道傳播之領域事件與日誌訊息載荷，命名一律以 `*Event` 結尾並收納於各模組之 `Event/` 套件。
_Avoid_: Mixing event in Vo package, *Message suffix inconsistency, Raw string payload.

**Controller Contract Traversal (控制器契約依賴遍歷)**:
靜態分析工具透過掃描公開 Controller 端點輸入參數（`@ApiBody`、`@RequestBody`、`*Query`）與回傳值（解包 `ResponseType<T>`、`PageResult<T>` 等）及其巢狀自訂欄位，自動遞迴構建公開 API 契約白名單的機制。
_Avoid_: Regex-only path matching, Blind Vo package tagging, Forcing docs on internal events.

**Benchmark Route Drift (壓測路由契約漂移)**:
當系統對外 API 路由演進（例如廢除無界全量端點改採雙軌標準）後，壓力測試腳本仍保留已廢除之歷史端點路徑，導致壓測流量落入快速失敗（404/405）而產生虛假高吞吐與低延遲的基準失真現象。
_Avoid_: Stale test URL, 404 false benchmark, Broken load script.

**Targeted Bottleneck Probe (定向瓶頸探針)**:
針對靜態架構審查識別出的特定高風險機制（如快取鍵空間掃描、無界串流佇列、非交易延遲載入、Outbox 併發重複選取），以可控資料規模與併發梯度直接量測其物理臨界點與資源退化曲線的精準驗證測試。
_Avoid_: Blind full-system stress, Unfocused load test, Ad-hoc microbenchmark.

**Orphan Cache Eviction (孤兒快取清除殘留)**:
當讀取路徑已移除快取寫入（改為資料庫直通查詢），但寫入與異動路徑仍殘留針對該已廢棄快取命名空間的主動清除操作，造成無收益之額外網路與鍵空間掃描開銷的反模式。
_Avoid_: Dead cache clear, Ghost eviction, Redundant cache flush.

**Keyspace Scan Blocking (鍵空間全量掃描阻塞)**:
在單執行緒事件循環的快取引擎中，於業務請求或交易路徑內執行全量萬用字元鍵名掃描（如 `KEYS pattern*`），導致快取主執行緒在掃描期間凍結並阻塞所有微服務快取讀寫的效能風險。
_Avoid_: Wildcard key lookup, Sync key listing, Pattern delete blocking.

**Quota-Aligned Model Pool (配額對齊模型池)**:
依據雲端推理平台實際授權之單分鐘請求數（RPM）、單分鐘權杖數（TPM）與單日請求數（RPD）配額，精確剔除零配額模型、釋放單批次容量上限（對齊 250K TPM 與實測 Context），並按單日吞吐餘裕與推理能力動態排定候選優先級的模型池治理機制。
_Avoid_: Blind model list, Artificial low token cap, Zero-quota retry loop.

**Dual-Provider Load Offloading (雙供應商主動分流)**:
在進行多批次平行審查時，不再將第二供應商（Groq）僅視為第一供應商（Gemini）全數耗盡後的被動備援，而是依據批次權杖預算將輕量批次主動優先分流至低延遲供應商，並支援將失敗或逾時之大型批次動態拆解為子批次交由第二供應商救援的主動負載均衡策略。
_Avoid_: Cold standby only, Single-provider bottleneck, Idle backup key pool.

**Fast-Demote Transient Spike (瞬態尖峰快速降級)**:
當特定模型端點遭遇雲端服務商高負載尖峰（`HTTP 503`）或單次請求逾時時，立即對該模型執行健康度降級並切換至池內下一候選模型，避免在同一壅塞模型上盲目輪詢所有 API Key 的快速失敗切換機制。
_Avoid_: Exhausting all keys on 503, Blind key rotation on model outage, Long timeout blocking.

**Review Suspicion (審查疑點)**:
由語言模型在批次程式碼審查過程中初步提出、尚未經確定性規則查證器或人工審核驗證之潛在問題觀察，在完成證據核對前不具備直接宣告確定性規範違規的效力。
_Avoid_: Raw LLM finding, Unverified blocker, Hallucinated violation.

**Rule Claim (規則主張)**:
從單一審查疑點拆解而出、綁定單一受管規則或單一業務風險焦點的最小可驗證與可裁定主張單元，確保每條規則結論可獨立查證而不受複合敘述牽連。
_Avoid_: Compound finding, Multi-rule bundle, Ambiguous accusation.

**Rule Inspector (規則查證器)**:
對應特定工程規範條款、接收審查快照與問題座標並自主蒐集必要結構化上下文，進而回傳確定性查證狀態（`VIOLATION`、`COMPLIANT`、`NOT_APPLICABLE`、`INCONCLUSIVE`）與具體證據之專責領域檢查元件。
_Avoid_: Prompt-only clarification, Blind regex lint, Unscoped file dump.

**Confirmed Violation (已確認違規)**:
經對應規則查證器以充分結構化證據確認成立、或經具授權之人工裁定確認成立的正式規範違規，由審查閘門獨立持有，不受語言模型後續漏列或單方撤回影響。
_Avoid_: Unverified LLM claim, Self-certified model violation, Silent finding drop.

**Review Snapshot (審查快照)**:
鎖定單次程式碼審查所依據之基準分支版本、目標提交版本與完整受檢內容之不可變審查視角，確保所有規則查證與呼叫鏈檢索皆基於同一時間點的一致事實。
_Avoid_: Mixed branch grep, Floating head state, Cross-commit reuse.

**Suspicion Eligibility Auditor (疑點資格審核器)**:
針對尚無專屬規則查證器的開放式業務或安全疑點，強制檢驗其引用座標真實性、操作入口、受影響資源、預期保護條件與缺失上下文是否具體可核對，以區分值得人工介入追查之具體疑點與模糊臆測的審核機制。
_Avoid_: Unchecked business suspicion, Vague risk blocking, Free-form LLM escalation.

**Human Claim Verdict (人工主張裁定)**:
由專案負責人明確核准之審核者，針對特定規則主張、審查快照與查證證據所作出的可稽核正式判定（確認違規或判定誤判不成立），與主要分支合併資格及高風險變更之架構安全人工簽核彼此獨立。

**Approved Reviewer (核准審核者)**:
經專案負責人明確授予人工主張裁定資格的人員；身分以固定帳號識別碼辨識，並非由帳號顯示名稱、職稱或模型推測決定。
_Avoid_: Silent comment dismissal, Unscoped override, Mixing false-positive verdict with high-risk sign-off.

**Production Caller Existence (生產呼叫端存在性)**:
在審查快照的生產來源中，存在可無歧義解析至指定方法的靜態呼叫之事實；不包含僅出現在測試來源中的引用，也不代表該呼叫可由外部入口到達。
_Avoid_: Name-only reference, Test-only caller, Runtime reachability proof.

**Entry Reachability (入口可達性)**:
指定方法能否由受檢操作入口沿有效呼叫路徑到達的獨立審查主張，不能僅由呼叫端存在或缺少靜態引用直接推定。
_Avoid_: Caller existence, No-reference equals unreachable.

**Claim Counterevidence (主張反證)**:
足以否定單一規則主張的可核對證據，其成立不要求其他無關搜尋完成，也不會連帶解除其他主張或整場審查的未完成狀態。
_Avoid_: Whole-review approval, Partial search means compliance.
