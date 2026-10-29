# 0026. 配額對齊模型池、雙供應商主動分流與高併發快速降級調度

Date: 2026-10-29
Status: Accepted

## Context

在大型 Pull Request（例如涵蓋 100+ 檔案、跨多模組重構的變更）觸發 GitHub Actions `AI Architecture & Security Review` 時，原有的 AI Review 調度管線會耗時超過 30 分鐘甚至逾時，分析遠端執行日誌與配額數據後發現四大結構性瓶頸：

1. **模型池與實測配額脫節（Model Pool Misalignment）**：
   - 原有 `DEFAULT_GEMINI_MODELS` 納入已無配額的模型（如 `gemini-3.7-flash`、`gemini-3.5-flash`），且漏列實際擁有 `5 RPM / 250K TPM / 20 RPD` 配額的 `gemini-2.5-flash`。
   - 原有配置將具備 `250K TPM` 的高配額模型 `gemini-3.5-flash-lite`、`gemini-3.1-flash-lite`（各擁有 `15 RPM / 500 RPD`）人為限制在 `max_batch_tokens = 6,000 ~ 8,000`，導致中大型批次在 `filter_eligible_models` 階段直接將高吞吐的 Flash Lite 模型過濾排除，迫使所有批次擠向僅 `5 RPM / 20 RPD` 的少數模型。
2. **切批過碎與 Groq 池完全閒置（Over-Fragmented Batching & Idle Groq Pool）**：
   - 原有 `AI_REVIEW_MAX_BATCH_CHARS` 預設僅 `24,000` 字元（約 `6,800` Tokens），將 130 個檔案切碎成 30+ 個批次；但該尺寸又剛好略大於 Groq 容量（`5,000` Tokens），且 `orchestrator.py` 僅在所有 Gemini 模型 × 所有 Key 全數耗盡後才呼叫 Groq，加上預設 Groq 模型排在首位的 `groq/compound*` 不支援 `json_object` 模式、`llama-3.3-70b-versatile` 與 `llama-3.1-8b-instant` 已於 2026/08 下架回傳 `404`，導致 10+ 把 Groq API Key 在整場審查中完全閒置或無效重試。
3. **503 尖峰與逾時的盲目輪詢（Blind Key Rotation on Model Spike）**：
   - 當特定 Gemini 模型遭遇雲端 `HTTP 503 High Demand Spike` 或 `120s Read Timeout` 時，`gemini_runner.py` 未對該模型執行降級（`demote`），而是針對同一塞車模型繼續輪詢全部 8 把 API Key × 3 次重試，加上 `batch_runner.py` 錯峰計算 `(worker_idx - 1) * stagger_seconds` 對第 2 波以後的批次仍累加睡眠，造成嚴重延遲。
4. **大型 PR 反降併發度（Inverse Worker Scaling）**：
   - `review.py` 在 `len(batches) > 6` 時反而將 `max_workers` 強制降為 `2`，無法發揮「多把 Gemini Key × 7 個可用 Gemini 模型 + 多把 Groq Key × 3 個可用 Groq 模型」的立體矩陣吞吐力。

## Decision

我們採納 **配額對齊模型池（Quota-Aligned Model Pool）**、**雙供應商主動分流（Dual-Provider Load Offloading）** 與 **瞬態尖峰快速降級（Fast-Demote Transient Spike）** 三位一體調度架構：

1. **對齊 Google AI Studio 實測配額重構 `DEFAULT_GEMINI_MODELS`**：
   - 剔除零配額模型，僅保留實測有配額之核心模型與別名：
     - **Tier 1 主力深度推理層**（`5 RPM / 250K TPM / 20 RPD`）：`gemini-3.8-flash`、`gemini-3.6-flash`、`gemini-3-flash-preview`、`gemini-2.5-flash`、`gemini-flash-latest`，統一配置 `max_batch_tokens = 30,000`、`max_files = 8`、`max_output_tokens = 8,192`。
     - **Tier 2 高吞吐併發主力層**（`15 RPM / 250K TPM / 500 RPD` 與 `10 RPM / 250K TPM`）：`gemini-3.5-flash-lite`、`gemini-3.1-flash-lite`、`gemini-2.5-flash-lite`、`gemini-flash-lite-latest`，統一提升至 `max_batch_tokens = 24,000`、`max_files = 6`、`max_output_tokens = 8,192`。
2. **更新 Groq 現役模型池**：
   - 將已退役或不支援 `json_object` 模式的模型從預設候選移除，改為實測支援 JSON Object 模式且各擁獨立 `8,000 TPM / 1,000 RPD` 配額的三個現役模型：`openai/gpt-oss-120b`、`qwen/qwen3.8-27b`、`openai/gpt-oss-20b`（`max_batch_tokens = 5,500`、`max_files = 3`、`max_output_tokens = 2,048`）。
3. **雙軌智慧切批與主動雙供應商分流（Dual-Track Batching & Offloading）**：
   - `batching.py` 將預設 `AI_REVIEW_MAX_BATCH_CHARS` 提升至 `80,000`（對齊 `20,000` Tokens / `6` 檔），使 Tier 1 與 Tier 2 全數 9 個 Gemini 候選模型皆可承接標準批次，將大型 PR 的總批次數壓縮 60% 以上；同時支援雙軌切批，將領域內小型變更優先打包為符合 `GROQ_CAPACITY`（`<= 5,500` Tokens、最多 `3` 檔）的輕量批次。
   - `orchestrator.py` 對符合 `GROQ_CAPACITY` 且標記 `prefer_groq=True` 的輕量批次，**主動優先分流至 Groq 執行**（若 Groq 失敗則無縫退回 Gemini）；對中大型批次優先使用 Gemini，若失敗則由 `batch_runner.py` 自動拆分為子批次並交由 Groq 救援。
4. **提升平行 Worker 數、修正首波錯峰、收斂逾時與 503/Timeout 立即模型降級**：
   - `review.py` 在多批次（`len(batches) > 4`）時預設開啟 `6` 個平行 Worker（可由 `AI_REVIEW_MAX_WORKERS` 覆寫），且 `batch_runner.py` 僅對首波啟動之 Worker（`worker_idx <= actual_workers`）執行錯峰延遲。
   - 單次 HTTP 請求逾時由 `120s` 收斂至 `60s`。
   - `gemini_runner.py` 遭遇 `HTTP 503`（伺服器高負載尖峰）時，最多在同模型重試 1 次即呼叫 `gemini_model_pool.demote(model_name)` 並立即跳轉至下一候選模型；遭遇連線逾時亦立即 `demote` 並切換模型，徹底消除同模型 8 Key × 3 次盲目輪詢。
   - Workflow 與 Python 輸出全面啟用 `PYTHONUNBUFFERED=1` 與即時 `flush=True`。

## Consequences

- **審查耗時大幅縮短**：130 檔規模的大型 PR 批次數由 30+ 批縮減至約 12～16 批，搭配 6 個平行 Worker 與 Gemini + Groq 雙池併發，審查時間由 30+ 分鐘降至約 2～5 分鐘。
- **消除單一模型塞車雪崩**：任一模型發生 `503 High Demand Spike` 時可在數秒內完成降級並平滑轉移至其他可用模型或 Groq 池。
- **即時可觀測性**：GitHub Actions 日誌即時輸出每個批次選用的供應商、模型、耗時與重試狀態，不再因緩衝而長時間空白。
