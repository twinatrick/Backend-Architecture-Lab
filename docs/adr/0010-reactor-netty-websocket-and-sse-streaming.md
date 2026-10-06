# 0010. Reactor Netty WebSocket and SSE Streaming

我們決定在保留現有 Spring Data JPA 與虛擬執行緒架構的前提下，引入 Spring 生態系之 Project Reactor 與 Reactor Netty 響應式管線，升級 `backend-alert-service` 之 WebSocket 告警廣播與 `backend-external-api-service` 之 AI 逐字串流（Server-Sent Events）。

## Context

本專案各微服務已於 Java 21 全面啟用虛擬執行緒（`spring.threads.virtual.enabled: true`），有效解決了傳統阻塞 I/O（如 PostgreSQL JDBC 查詢）的平臺執行緒耗盡問題。
然而，在兩大極端 I/O 場景中，既有架構面臨效能與架構瓶頸：
1. **即時遙測告警 (Alert WebSocket)**：`AlarmWebSocket.java` 採用傳統 Jakarta WebSocket 搭配 `Collections.synchronizedSet` 遍歷推播。在高併發連線與慢客戶端（Slow Consumer）存在時，缺乏非阻塞背壓機制，容易導致連線堆疊、記憶體積壓與鎖爭用。
2. **AI 逐字生成 (AI SSE Streaming)**：`backend-external-api-service` 原先全面使用同步阻塞之 `RestTemplate` 呼叫外部 LLM，且未支援逐字串流推播。當多個使用者同時發起耗時數十秒之 AI 生成時，無法向前端進行 SSE 串流推播，且客戶端離線時無法及時將取銷信號傳遞至上游終止計費。

評估方案：
1. **方案 A (雙協定混合與漸進式響應式增強 - 推薦)**：保留 Tomcat 作為 REST/JPA 主容器，在 `backend-alert-service` 引入 Spring Reactive `WebSocketHandler` 與 `Sinks.Many` 熱多播管道（搭配 `onBackpressureDrop` 背壓）；在 `backend-external-api-service` 引入非阻塞 `WebClient` 與 `Flux<ServerSentEvent<String>>` 實現 SSE 串流。
2. **方案 B (全面重構為 Spring WebFlux + R2DBC)**：全面替換 Tomcat 與 JPA。缺點為 R2DBC 無法無縫銜接複雜關聯查詢與現有交易機制，重構代價極高。
3. **方案 C (獨立部署原生 Netty WebSocket/SSE 伺服器)**：在服務內部開啟獨立 Netty 埠口（如 8009）。缺點為破壞現有單一埠口規範與 Gateway 路由拓撲。

## Decision

我們決定採用 **方案 A**，在現有模組內部進行漸進式響應式增強：

1. **`backend-alert-service` 響應式 WebSocket 與背壓防禦**：
   - 引入單例 `AlarmReactiveSink`（基於 `Sinks.Many<AlarmMessage>` 之 `multicast().directBestEffort()`），作為 Kafka 消費端向 WebSocket 廣播的無鎖熱多播通道。
   - 徹底移除過往的 `AlarmWebSocket.java` 與靜態注入反模式；`KafkaConsumerService` 透過建構子注入 `AlarmReactiveSink` 進行單一管線廣播，徹底根絕雙重訊息推播問題。
   - 實作響應式 `AlarmWebSocketHandler`，各客戶端訂閱時自動掛載 `onBackpressureDrop()` 背壓策略，優先保證最新水情數據推送，主動拋棄落後的歷史訊息，防範慢消費者拖垮系統。
   - 掛載 30 秒自動 Ping 協定訊框保活機制（`Reactive Frame Keep-Alive`），偵測死連線並釋放資源。
   - 針對 WebSocket 跨來源請求實施嚴格網域白名單校驗（CSWSH 防禦），透過 `app.websocket.allowed-origins` 設定檔注入來源列表，廢除萬用字元通配。
2. **`backend-external-api-service` WebClient 與 SSE 串流升級**：
   - 引入基於 Reactor Netty 之 `WebClient` 配置，提供具連線池管理與逾時保護之非阻塞 HTTP 客戶端。
   - 在 `IChatService` 與 `ChatService` 中實作 `Flux<String>` 串流生成邏輯，並對外提供標準 SSE 端點（`MediaType.TEXT_EVENT_STREAM_VALUE`）。
   - 端點統一納入使用者身分認證（`SecurityUtil.requireCurrentUserId()`）與 `@ApiOperationAuth` OpenAPI 標記，嚴格防杜未認證外部調用耗盡運算資源。
   - 綁定 Reactive Cancel 訊號，客戶端關閉連線時自動觸發取消語意，終止上游非同步調用。
3. **`backend-gateway` 穿透路由與超時隔離**：
   - 增加 `/ws/**` 路由轉發至 `lb:ws://alert-service`，由 Gateway (8000 埠) 統一收斂對外入口。
   - 針對 `/api/ai/stream/**` 串流路由獨立配置 60 秒長超時，關閉回應緩衝區以實現 Token 即時沖刷，並將其與一般短時 REST API 熔斷器隔離開來。

## Consequences

- **極致長連線吞吐**：WebSocket 告警廣播消除同步鎖遍歷，慢客戶端被背壓策略自動隔離，Kafka 消費線程無阻塞。
- **改善 AI 使用者體驗與成本**：SSE 逐字推播大幅縮短首字延遲（TTFT），客戶端離線取銷機制避免無效 Token 生成浪費。
- **架構零破壞相容**：全專案維持既有 PostgreSQL JPA 資料庫事務、Liquibase 遷移與 Java 21 虛擬執行緒基座不變，達成精準且低風險之架構升級。
