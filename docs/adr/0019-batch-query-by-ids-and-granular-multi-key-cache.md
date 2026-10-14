# 0019. Batch Query by IDs and Granular Multi-Key Cache Architecture

我們需要在全專案 11 大核心實體中統一建立以 ID 批次獲取資料的端點與細粒度快取機制：採用 `POST /api/{resource}/batch-query` 路由與 `BatchIdsRequest`，底層複用單筆 `GET /{id}` 快取 Key 進行逐筆快取命中檢查，未命中者以單次 SQL `IN (...)` 批量載入並回填單筆快取，回應採用雙視角 `BatchResultVo<T>`（同時提供 `items` 清單與 `itemMap` 字典，並回報 `missingIds`），徹底消除前端 N+1 查詢與 URL 長度限制，兼顧快取高命中率與資料一致性。

## Context

在微服務前後端分離架構中，前端表格與關聯渲染面臨以下效能與契約痛點：

1. **前端 N+1 查詢與連鎖 HTTP 阻塞 (Frontend N+1 Query Anti-Pattern)**：
   - 當前端呈現列表資料時，經常需要根據多筆外鍵識別碼取得關聯實體的詳細資料（例如專案成員使用者詳情、職缺所屬公司詳情等）。若缺乏批次查詢端點，前端被迫在迴圈中連續發送 N 次 `GET /{id}` HTTP 請求，引發嚴重的網路往返延遲、連線池排隊與 Gateway 負載壓力。
2. **GET 查詢參數 URL 長度限制與快取爆炸 (Query Parameter Length Limit & Cache Key Explosion)**：
   - 若採用 `GET ?ids=id1,id2,id3...`，當 ID 數量多時極易超出瀏覽器與反向代理（Caddy/Nginx）的 URL 長度限制（2048 字元）。
   - 若將整個 ID 集合串接為快取鍵（如 `users::id1_id2_id3`），會導致快取鍵組合爆炸（Combinatorial Explosion），命中率趨近於零，且任一筆資料更新時難以精準失效。
3. **資料遺失感知缺失 (Silent Data Inconsistency)**：
   - 當請求包含已被刪除或不存在的 ID 時，若後端僅回傳現存陣列，前端無法輕易得知哪些 ID 遺失，增加了比對複雜度。

評估方案：
1. **方案 A（細粒度多鍵快取 + POST /batch-query + 雙視角 BatchResultVo - 推薦）**：
   - 採用 `POST /api/{resource}/batch-query`，使用 `@ApiBody BatchIdsRequest` 封裝最多 100 個 ID，突破 URL 長度限制。
   - 複用既有單筆 `GET /{id}` 快取命名空間（如 `users::id`、`projects::id`），逐筆檢查快取。
   - 僅針對快取未命中的 ID 發起單次 SQL `findAllById` 批量查詢，並將查得實體回填至各自單筆快取。
   - 回傳 `BatchResultVo<T>`，同時封裝 `items` 陣列、`itemMap` 外鍵反查字典、`missingIds` 遺失識別碼陣列與統計數據。
2. **方案 B（粗粒度集合快取）**：
   - 將整包 ID 列表序列化作為快取鍵。
   - 缺點：快取組合爆炸，命中率極低，且與單筆更新/刪除之 CacheEvict 無法聯動。

## Decision

我們決定採用 **方案 A**，具體架構規範如下：

1. **統一路由契約與安全邊界 (Universal Endpoint & Guardrail)**：
   - 全專案 11 大核心業務實體統一暴露：`POST /api/{resource}/batch-query`。
   - 請求參數採用 `@ApiBody BatchIdsRequest`（或字串識別碼 `BatchStringIdsRequest`），宣告 `@Size(max = 100)` 進行嚴格數量防護。
   - 區分讀寫語義：Function 實體的批次儲存/異動端點正式命名為 `POST /api/function/batch-save`，與查詢端點 `batch-query` 嚴格職責分離。
2. **細粒度快取機制 (Granular Multi-Key Cache-Aside with BatchCacheHelper)**：
   - 透過通用 `BatchCacheHelper`，遍歷請求 ID 先行命中單筆快取。
   - 未命中 ID 彙整後單次呼叫 DataAccess `findAllById`，查詢結果以 `@CachePut` / `cache.put(id, vo)` 回填至對應單筆快取，使後續 `GET /{id}` 亦能無縫共享快取紅利。
3. **雙視角全能型回應封裝 (Dual-Perspective BatchResultVo)**：
   - `items`：保留查詢成功之實體清單。
   - `itemMap`：以實體 ID 為 Key 的查找字典，便於前端 O(1) 進行外鍵資料關聯渲染。
   - `missingIds`：明確回報請求存在但資料庫已不存在之 ID 清單，增強前端資料一致性感知。

## Consequences

### Positive
- **消滅前端 N+1**：前端獲取多筆關聯資料從 N 次 HTTP 請求降為 1 次。
- **快取命中率最大化**：批次查詢與單筆查詢共享底層同一份細粒度快取，快取池零重複且高複用。
- **外鍵對照極度簡化**：前端可直接透過 `itemMap[foreignKey]` 快速取值，省去前端 `Array.find()` 的計算消耗。
- **防止 URL 截斷**：以 POST Request Body 傳輸識別碼陣列，天然支援大批量 ID 傳遞。

### Negative
- 多筆查詢可能包含部分快取未命中，需由 `BatchCacheHelper` 進行聚合與拆解，引入些許記憶體處理開銷（約 1~2ms），但相比多次網路 RTT 可忽略不計。
