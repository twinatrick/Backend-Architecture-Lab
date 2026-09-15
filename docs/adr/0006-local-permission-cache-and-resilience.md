# 0006. Local Permission Cache and Microservice Cascading Failure Defense

我們需要為跨服務權限校驗建立具備本地快取、斷路器隔離與統一降級保護的防禦架構，以根除身分鑑權服務（IAM）單點延遲或宕機時引發的全系統微服務連鎖雪崩（Cascading Failure）。

## Context

在目前的微服務架構中，所有業務服務（Competency, Job, Alert, External API）廣泛採用 `@RequirePermission` 註解保護 API 端點。
其底層防護邏輯由 `backend-common` 的 `DefaultPermissionValidator` 實作，透過同步 Feign HTTP 呼叫向 `iam-service` 的 `/role/inner/validate` 端點發起遠端鑑權。

既有實作存在嚴重的可靠性與穩定性隱患：
1. **無快取開銷 (Zero Caching Overhead)**：每次 API 請求皆觸發跨網路 RPC 往返，造成額外 10~30ms 延遲並加劇 IAM 負載。
2. **單點連鎖崩潰 (Single Point Cascading Outage)**：若 `iam-service` 遭遇高負載、長 GC、重啟或網路分區，所有微服務調用線程將在 Feign 阻塞直至超時，迅速耗盡 Tomcat 與連線池資源，導致全系統所有業務介面集體噴出 503 `FEIGN_ERROR`。
3. **Gateway 邊界無防護**：API Gateway 路由未配置熔斷與降級過濾器，下游服務卡死時直接導致 Gateway Netty 佇列堆積。

評估方案：
1. **方案 A (Caffeine Local Cache + Resilience4j Circuit Breaker + Fail-Closed)**：微服務本地維護 30 秒 TTL 的記憶體快取，搭配斷路器保護 Feign 呼叫。未命中且服務離線時快速 Fail-Fast (503)。
2. **方案 B (JWT Payload 內嵌完整權限列表)**：將權限完全放進 Token。雖然無跨服務網路呼叫，但 Token 體積膨脹，且管理員調整權限無法於 Token 有效期內收回，違反動態授權原則。
3. **方案 C (Redis 集中式快取)**：雖能跨實例共用，但每次權限檢查仍存在網路 I/O，且 Redis 故障時仍存在雪崩風險。

## Decision

我們決定採用 **方案 A**，為微服務調用鏈路建立深度防禦體系（Defense-in-Depth）：

1. **Caffeine 本地高效快取 (`backend-common`)**：
   - 於 `backend-common` 引入 Caffeine，在授權驗證層維護容量上限 10,000 筆、寫入後 30 秒（TTL = 30s）自然過期的本地快取。
   - 快取鍵由 `email + ":" + module + ":" + layer + ":" + action` 複合組成，提供奈秒級別的本機記憶體檢索。
   - 接受 30 秒最終一致性（Eventual Consistency）窗口，保持微服務完全自治與解耦。
2. **Resilience4j 斷路器與線程防護**：
   - 使用 Resilience4j Circuit Breaker 包裹 `RoleClient.validatePermission` 呼叫。
   - 滑動窗口大小設為 10，失敗率閾值設為 50%，慢調用閾值 2 秒；當 IAM 故障時斷路器迅速進入 `OPEN` 狀態。
3. **嚴格閉合失敗原則 (Fail-Closed Authorization)**：
   - 當快取未命中且 Feign 呼叫失敗或斷路器 `OPEN` 時，遵循安全第一原則，一律拋出 `ServiceUnavailableException` (HTTP 503, `FEIGN_ERROR`)。
   - 於 10ms 內 Fail-Fast 中斷請求，絕不放行任何未經核可的操作，同時釋放執行緒。
4. **Gateway Reactive 斷路器與統一 JSON 降級 (`backend-gateway`)**：
   - 在 API Gateway 路由整合 `SpringCloudCircuitBreakerFilterFactory` 與 Reactive Resilience4j。
   - 當下游路由逾時（5 秒）或熔斷時，轉發至專屬的 WebFlux `FallbackController`，產出符合系統規範的統一 `ErrorResponseVo` 結構體（HTTP 503, `code: "SERVICE_UNAVAILABLE"`, `traceId`）。

## Consequences

- **徹底消除連鎖雪崩 (Zero Cascading Collapse)**：IAM 故障時微服務不再產生執行緒堆疊，既有授權使用者持續正常操作，新請求迅速回傳 503 阻止連鎖惡化。
- **大幅縮短 API 延遲 (Latency Reduction)**：快取命中時權限判定自 10~30ms 驟降至 < 0.1ms。
- **嚴格合規安全邊界 (Security Preserved)**：貫徹 Fail-Closed 原則，任何故障情境下絕無權限漏洞洩漏。
- **最終一致性取捨 (Trade-off)**：權限撤回有最長 30 秒的傳播延遲，符合分散式系統設計標準。
