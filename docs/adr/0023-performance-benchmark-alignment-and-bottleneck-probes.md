# ADR 0023：壓力測試路由契約對齊、雙模式基準刷新與定向瓶頸探針治理

狀態：已接受（Accepted）

當系統全面落實「雙軌實體獲取標準（Dual-Rail Retrieval）」與「RESTful 資源映射規範」並根除歷史無分頁 `/get` 端點後，既有壓力測試套件若未同步演進，將發生「壓測路由契約漂移（Benchmark Route Drift）」——壓測請求打向已廢除路由並快速回傳 404/405，造成吞吐量虛高與延遲失真；同時，單靠 HTTP 端點全量壓測難以單獨剝離並量化「鍵空間全量掃描阻塞（Keyspace Scan Blocking）」、「孤兒快取清除殘留（Orphan Cache Eviction）」、「SSE 串流背壓與跨執行緒延遲載入」及「Outbox 併發重複選取」等深層機制瓶頸。我們決定：(1) 將所有 k6 壓測腳本與認證初始化載荷嚴格對齊最新公開 Controller RESTful 契約（`POST /search`、`POST /batch-query`、`GET /{id}`、`bootstrapSecret`），並恢復及維護冪等之壓測種子 SQL；(2) 建立支援「隨選單一服務輪測（On-Demand Lifecycle）」與「一鍵重刷種子資料並回填 Markdown 報告」之雙模式自動化壓測管線；(3) 引入基於單例測試容器（`SharedContainers`）之「定向瓶頸探針（Targeted Bottleneck Probe）」，在不修改生產業務碼的前提下，可重複驗證並量化各項架構瓶頸與四象限（快取開關 × 虛擬執行緒開關）真實表現。
