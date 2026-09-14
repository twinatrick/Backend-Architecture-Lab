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


