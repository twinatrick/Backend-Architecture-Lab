# 0005. Centralized Logging and Distributed Tracing Architecture with Grafana Loki

我們需要為多模組微服務架構建立輕量化、資源友善的集中式日誌管理（Centralized Logging）與分散式鏈路追蹤（Distributed Tracing）體系，以解決跨服務請求日誌分散、排查困難以及傳統 ELK 方案過於沉重的問題。

## Context

專案目前包含 API Gateway 與 5 個核心微服務（IAM、Competency、Job、External API、Alert），各服務各自將日誌輸出至本地標準輸出，缺乏統一的聚合檢索與跨服務關聯機制。
在面臨跨服務 Feign 調用、Reactive 異步路由與 Kafka 事件驅動流程時，排查分散式錯誤往往需要人工比對多個容器或實例的日誌，效率低且難以掌握完整調用全貌。
評估既有日誌方案：
1. **ELK (Elasticsearch + Logstash + Kibana)**：全文檢索能力強大，但 JVM 記憶體與磁碟開銷沉重（通常需 2GB~4GB+），嚴重衝擊本機開發與單執行緒 CI 測試效能。
2. **Grafana Loki + Promtail / Loki4j**：採用「僅索引標籤（Labels）、不對全文建倒排索引」的輕量架構，記憶體開銷僅約百餘 MB，且與專案現有之 Prometheus 指標可天然在 Grafana 內透過 Trace ID 無縫聯動。

## Decision

我們決定建構基於 Grafana Loki 與 Micrometer Tracing 的集中式日誌與鏈路追蹤體系：

1. **集中式日誌引擎與視覺化 (`compose.yaml` & Provisioning)**：
   - 採用 `grafana/loki:3.0.0` 作為集中式日誌儲存後端，監聽 Port `3100`，並配置具名 Volume `loki_data` 與 7 天 (168h) 自動清理機制。
   - 部署 `grafana/grafana:11.0.0` 於 Port `3000`，並透過 `grafana/provisioning/datasources/` 自動掛載 Loki 與 Prometheus 資料來源，實現開箱即用。
2. **應用層非同步直連與降級保護 (`loki4j` in `backend-common`)**：
   - 於 `backend-common` 引入 `com.github.loki4j:loki-logback-appender`，由各微服務統一繼承共用 `logback-spring.xml`。
   - Appender 設定為非阻塞模式 (`neverBlock=true`) 與記憶體佇列緩衝；當 Loki 服務離線或網路超時時，自動靜默降級至控制台輸出，絕不阻塞 API 主業務執行緒。
3. **低基數標籤索引與 JSON 結構化載荷 (Cardinality Control)**：
   - 嚴格控制 Loki 索引標籤維度，僅保留 `app`（服務名稱）、`env`（運行環境）與 `level`（日誌等級）。
   - 將高基數欄位（`traceId`、`spanId`、`userId`、`thread`、`logger`、`message`、`exception`）封裝於 JSON 結構體中，透過 LogQL `| json` 進行即時解構過濾。
4. **W3C 分散式鏈路追蹤 (Micrometer Tracing & Context Propagation)**：
   - 引入 `io.micrometer:micrometer-tracing-bridge-otel`，統一生成與傳播符合 W3C 標準之 `traceparent`。
   - 於 Gateway (WebFlux) 啟用 Reactor Context 自動傳播，並在 `backend-common` 配置 `TaskDecorator` 確保 `@Async` 與線程池內 MDC `traceId` / `spanId` 完整繼承。
5. **日誌安全脫敏機制 (Log Masking)**：
   - 在 `backend-common` 實作自訂 Logback 脫敏轉換器，自動對包含 `password`、`token`、`authorization` 等敏感欄位之日誌內容進行正則遮蔽（`******`），防止機密洩漏至集中日誌庫。

## Consequences

- **輕量與極致效能 (Lightweight & Resource-Friendly)**：日誌收集與儲存資源開銷極低（Loki 記憶體佔用 < 200MB），完全避免本機開發環境與 CI 測試卡頓。
- **全鏈路可觀測性 (End-to-End Traceability)**：跨微服務 HTTP、Feign 與異步執行緒統一攜帶 `traceId`，在 Grafana 中可依據 Trace ID 秒級定位跨服務調用鏈路。
- **高可用與容錯性 (Fail-Safe Buffering)**：日誌後端異常時具備非同步環狀緩衝與靜默降級，確保主業務流程與 API 回應時間不受任何影響。
- **合規性與安全性 (Data Security & Compliance)**：底層強制日誌脫敏，阻絕敏感密碼與權限 Token 洩入日誌平台。
- **查詢語法適應 (Query Trade-off)**：針對未建立標籤的內文搜尋需仰賴 LogQL 行過濾（Line Filter），在百萬級每秒日誌場景下效能低於倒排索引，但在中小規模與微服務架構下具備最佳性價比。
