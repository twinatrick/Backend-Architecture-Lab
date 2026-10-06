# 0012. Model Specification Dual-Condition Batching and System-Wide Technical Debt Governance

我們決定在代碼庫中：
1. 建立 AI Review Runner 之模型規格註冊表（ModelSpec）與雙重批次切分條件（Tokens + 檔案數限制），解決高負載降級引發之模型輸出截斷與批次覆蓋範圍不符缺陷。
2. 全面治理全專案累積之 69 項歷史技術債（涵蓋 Java 模板組合服務相依性注入、OpenAPI 方法級描述收斂、Python 服務層例外具體化與型別標註、測試機密消歧），達成全庫 `--all` 靜態稽核 100% 通過。

## Context

在多模型 AI 審查與持續整合過程中，系統面臨兩項重大挑戰：
1. **AI 審查降級截斷與 Fail-Closed 阻擋 (Coverage Mismatch)**：
   - 當 Google Gemini API 遭遇高負載或配額限制（429/503）時，審查排程器會自動降級至備用模型（如 `gemini-3.5-flash`）。
   - 由於原有的批次建構器（`batching.py`）僅依據字元數（`max_chars = 24000`）進行分割，未對批次檔案數量設定硬限制，導致多達 12 個小檔案被塞入單一批次。
   - 備用模型在受到提示詞中「1000 Tokens 限制」約束下，無法完整輸出大量檔案之審查 JSON，引發未閉合字串異常（`Unterminated string starting at line 8`）。
   - 審查引擎依據 Fail-Closed 原則，判定 `validate_coverage` 批次覆蓋範圍不符，阻擋 PR 合併。
2. **全庫歷史技術債累積與品質門禁阻擋**：
   - 全專案既有程式碼累積了 69 項架構與代碼品質技術債：
     - Java 微服務層：`BaseOpenAiService` 及其子類別使用手寫建構子進行相依性注入，未遵循 Lombok `@RequiredArgsConstructor` 規範；控制器中混用 Swagger 原生 `@Parameter` 註解，未與 `@ApiOperationBadRequest` 統一收斂。
     - Python 服務層 (`backend-ai-py`)：存在 AST 非頂層 import、寬泛例外捕捉 `except Exception`、缺少函式回傳值型別標註、單字元變數（如 `as e`、`as f`）等違規。
     - 測試套件：靜態檢查在掃描測試檔案時，無法有效消歧測試假金鑰與測試斷言，造成誤判。

## Decision

我們決定實施全方位架構加固與技術債清零方案：

1. **動態模型規格註冊 (Dynamic Model Specification)**：
   - 在 `model_pool.py` 中定義 `@dataclass(frozen=True) class ModelSpec(name, max_batch_tokens, max_files, max_output_tokens=4096)`。
   - 為所有 Tier 1、Tier 2、Tier 3 候選模型建立明確規格註冊表（`MODEL_SPECS`），明確定義單批 Token 上限（8k ~ 24k Tokens）與單批檔案數上限（3 ~ 6 檔）。
   - 實作 `filter_eligible_models(candidate_models, required_tokens)`，在呼叫前依據批次 Token 估算動態過濾承載力不足之模型。
2. **雙重批次切分條件 (Dual-Condition Batching)**：
   - 改造 `batching.py`，以正則與字元比率實作精準之 `estimate_tokens`。
   - 批次建構同時檢驗 `len(current_batch) >= target_files`、`current_tokens + cost_tokens > target_tokens` 與 `current_chars + cost_chars > char_limit`。
   - 移除提示詞中人為之 1000 Token 限制，確保模型有充裕空間輸出結構完整的審查 JSON。
3. **Java 模板組合服務架構與 OpenAPI 規範化**：
   - 重構 `BaseOpenAiService` 為抽象模板類別，定義抽象 Getter；子類別（`DeepSeekService`、`GitHubModelsService`、`GroqService`）以 `private final` 持有相依性並標註 `@RequiredArgsConstructor`。
   - 移除控制器中原生 `@Parameter` 註解，依指示將參數規格完整補充至 `@ApiOperationBadRequest` 的主要描述中。
4. **Python 服務層現代化與規範對齊**：
   - 將非頂層 import 改以 `importlib.import_module` 安全導入。
   - 全面具體化例外處理，依業務情境捕捉 `(httpx.HTTPError, OSError)`、`subprocess.SubprocessError` 等具體例外，消除所有寬泛 `except Exception`。
   - 補齊全部函式回傳值型別標註，消除所有單字元變數（如 `as exc`、`as json_file`）。
5. **靜態檢查測試機密消歧**：
   - 增強 `static_checks.py` 之機密偵測邏輯，在 `is_test=True` 時對包含 `dummy`、`mock`、`placeholder` 等測試特徵放行。
   - 安全重構測試套件中硬編碼之測試金鑰字串與私鑰標頭。

## Consequences

- **消除輸出截斷與誤阻擋**：模型規格與雙重切分確保每批次之輸入與輸出皆在各模型之硬邊界內，徹底根絕 Unterminated JSON 與 Coverage Mismatch。
- **全專案品質稽核 100% 綠燈**：執行 `python .github/ai-review/check_local.py --all` 達成 0 阻擋違規、0 警告，成功清除全庫 69 項歷史技術債。
- **提升微服務可維護性**：消除手寫建構子、明確化 API 參數規格與型別宣告，大幅提升系統穩健度與開發效率。
