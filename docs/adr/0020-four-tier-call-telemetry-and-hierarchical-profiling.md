# 0020. Four-Tier Call Telemetry and Hierarchical Profiling Architecture

我們需要在全微服務四層架構（Controller、Service、DataAccess、Repository）中統一建立階層式呼叫樹遙測與耗時占比分析機制：採用非同步 AOP 切面攔截跨層呼叫鏈，以直接父方法耗時為 100% 分母計算各子呼叫之父級相對占比（Self-Centric Relative Ratio），並精確計算節點自身耗時（Execution Self-Time），同時提供逐次呼叫樹與同方法聚合表雙重視角；透過 Grafana + Loki 儲存結構化單筆 JSON 呼叫日誌、Tempo 提供分散式追蹤跨越微服務邊界，支援 HTTP 標頭 `X-Diagnostic-Trace` 觸發全量診斷模式，並落實遙測故障安全隔離（Telemetry Fail-Safe Isolation），使效能排查與瓶頸定位精確至毫秒與具體相依呼叫。

## Context

在微服務架構中，單一 HTTP 請求穿透 Controller、Service、DataAccess 至 Spring Data JPA Repository 四層架構。在效能排查與瓶頸診斷過程中，開發與維運團隊面臨以下痛點：

1. **扁平化日誌難以識別各層真實負擔 (Lack of Hierarchical Breakdown)**：
   - 現有日誌僅能記錄 HTTP 請求總耗時（例如 `RequestTimingFilter` 輸出 700ms），無法直觀辨識這 700ms 是消耗在 Service 的業務邏輯計算、DataAccess 的組裝轉換，還是底層資料庫或外鍵查詢。
2. **巢狀呼叫耗時重複計算與分母失真 (Duration Overlap & Ambiguous Baselines)**：
   - 跨層方法為巢狀呼叫（Controller 包 Service，Service 包 DataAccess，DataAccess 包 Repository）。若直接累加各層方法耗時，會導致父方法將子方法耗時重複計入；若將所有子方法與全域總時間計算百分比，深層相依的比例極小難以分析。因此必須以「直接父方法」為 100% 基準（父級相對占比），並扣除下游子呼叫獲得純內部運算之「節點自身耗時」（Self-Time）。
3. **N+1 與迴圈呼叫缺乏統計彙總 (N+1 Query Invisibility)**：
   - 當 Service 或 DataAccess 在迴圈中重複呼叫同一 Repository 查詢時，單純的呼叫樹會產生數十至數百個重複節點，日誌冗長且不易統計整體累計影響。需在樹狀結構外額外提供同方法聚合統計視角（包含呼叫次數、總耗時、最大耗時與佔父方法比例）。
4. **Spring Data JPA 介面繼承方法攔截盲區 (Inherited Repository Method Interception)**：
   - Repository 多數直接繼承 `JpaRepository`（如 `findById`、`findAllById`、`save`），若 AOP 切點僅指定專案 Repository package execution，Spring 產生的動態代理常無法攔截繼承方法，造成資料存取底層觀測遺漏。
5. **監控不能反向拖垮業務交易 (Telemetry Fail-Safe Obligation)**：
   - 遙測與計時邏輯屬次要觀測基礎設施，在任何情況下（如記憶體不足、序列化失敗、Tempo 離線）絕對不允許阻斷業務執行緒或干擾測試。

評估方案：
1. **方案 A（Grafana + Loki + Tempo 雙軌視覺化 + 結構化 JSON 單筆輸出 + 診斷模式 - 推薦）**：
   - 由 `backend-common` 統一提供 `FourTierTelemetryAspect` 與 `DiagnosticTraceFilter`。
   - 請求處理完成時，於 `finally` 區塊輸出單筆包含階層呼叫樹（時序視角）與各父節點下方法聚合（統計視角）的結構化 JSON 日誌至 Loki。
   - 支援 HTTP 標頭 `X-Diagnostic-Trace: true` 即時觸發 100% 診斷追蹤，平時維持低開銷；底層以 W3C TraceContext 與 Tempo 保持跨服務鏈路關聯。
   - 採用介面階層切點 `target(org.springframework.data.repository.Repository)` 確保完整攔截 JPA 繼承方法。
2. **方案 B（僅在 Controller 與 Repository 印分散日誌）**：
   - 缺點：日誌打散在多行，無法計算父子相對占比與 Self-Time，且高並發下交錯難以閱讀。
3. **方案 C（全域常態性全量追蹤）**：
   - 缺點：在正式生產環境造成額外 CPU 與日誌儲存負擔，違反低基數與非必要開銷最小化原則。

## Decision

我們決定採用 **方案 A**，具體架構規範如下：

1. **統一父級相對占比與自身耗時計算契約 (Self-Centric Relative Ratio & Self-Time Contract)**：
   - 樹狀結構中，根節點（Controller）自身相對基準為 100%。
   - 任意子節點之 `parentPercentage = (durationNanos / parent.durationNanos) * 100.0`。
   - 任意節點之自身耗時 `selfNanos = durationNanos - sum(children.durationNanos)`，自身占比 `selfPercentage = (selfNanos / durationNanos) * 100.0`。
2. **時序呼叫樹與方法統計雙重視角 (Dual-Perspective Tree & Method Aggregations)**：
   - 呼叫樹保留 chronological order 完整反映呼叫時序與巢狀深度。
   - 每個父節點維護 `methodAggregations` 字典：以 `className.methodName` 聚合記錄 `calls`（次數）、`totalDurationNanos`、`maxDurationNanos`、以及佔直接父方法之百分比。
3. **雙層與介面階層切面覆蓋 (Dual-Tier & Interface Hierarchy Aspect)**：
   - 切面設定 `@Order(1)`，優先於業務權限檢查 `@Order(2)` 執行，以真實反映包含授權檢查在內的呼叫者實際等待時間。
   - Repository 切點統一採用 `target(org.springframework.data.repository.Repository) && execution(* org.springframework.data.repository.Repository+.*(..))`，保證 Spring Data JPA 原生繼承方法（如 `findById`）無遺漏納入追蹤。
4. **輸出機制與容錯隔離 (Output Lifecycle & Fail-Safe Isolation)**：
   - 正常請求在 Filter `finally` 階段產出單筆包含 `traceId`、`spanId`、呼叫樹與聚合數據的結構化 JSON。
   - 異常中斷時即時記錄最後失敗現場節點，並保留整體父層異常狀態。
   - 所有切面與 Filter 內部的遙測運算一律以 `try-catch (Throwable t)` 包覆，若遙測發生任何異常僅記錄 WARN，嚴禁拋出例外影響業務或使測試失敗。
   - Filter `finally` 區塊無條件執行 `CallTreeContext.clear()`，徹底防範 ThreadLocal 記憶體洩漏。
5. **雙軌觸發與 Tempo 離線靜默降級 (Dual-Rail Trigger & Tempo Graceful Degradation)**：
   - 支援 HTTP Request Header `X-Diagnostic-Trace: true` 單次請求強制 100% 診斷模式，亦支援配置 `performance.diagnostic-trace.enabled=true`。
   - 當本機或測試環境未啟動 Tempo 容器時，OTLP 匯出靜默降級並丟棄 Span，保證單執行緒 CI 測試與離線開發 100% 通過。

## Consequences

### Positive
- **瓶頸定位直覺迅速**：一眼看清 Controller 總時間中 Service 佔比、Service 中各 DataAccess/Repository 佔比與自身耗時。
- **消滅隱性 N+1 盲區**：迴圈內呼叫同一查詢的方法次數與累計時間被顯式聚合，立即暴露高頻查詢。
- **零業務程式碼入侵**：全模組由 `backend-common` AOP 切面與 Filter 驅動，既有業務 Controller、Service、DataAccess 零修改即可獲益。
- **開發與線上彈性診斷**：前端或效能測試只需在 Header 附帶 `X-Diagnostic-Trace: true` 即可對特定請求進行外科手術級別的深入剖析。

### Negative
- 在診斷模式下，深度巢狀呼叫需要維護棧與節點物件，增加極微小記憶體分配（通常小於 100KB），但在一般生產模式維持低抽樣或按需觸發，對吞吐量無實質影響。
