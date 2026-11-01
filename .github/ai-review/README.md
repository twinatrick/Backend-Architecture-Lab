# AI Review Trust Boundary

## 目的

AI Review 會讀取 PR 提供的 diff，因此不能讓 PR 提供的 workflow code 同時取得 `GROQ_API_KEY`。

## 信任模型

```text
PR / untrusted code
        |
        | GitHub API: diff / changed files only
        v
Trusted workflow on protected base
        |
        | GROQ_API_KEY
        v
AI Review
        |
        v
Deterministic Aggregator
```

## 強制要求

1. 執行 AI Review Secret 的 workflow 必須來自受保護的 base branch，不能直接由 PR 修改。
2. Trusted reviewer 不得 checkout 後執行 PR 提供的 script。
3. PR context 只能以 GitHub API / patch / raw file content 提供給 AI。
4. AI 輸出的 `decision` / `blocking` 不得直接控制 Merge Gate。
5. Aggregator 必須驗證 batch completeness、schema 與 coverage，缺失時 fail-closed。
6. 若目前 repository 無法保護 workflow 檔案，則 AI Review 只能作為 comment，不能使用高權限 Secret 作為 Merge Gate。

## 建議 GitHub Repository 設定

正式閘門已採 `verified` 工具查證結果及固定 Base／Head SHA 靜態查證，原始模型 Finding 不直接決定合併。查證失敗須 fail-closed，不自動退回舊模型閘門；`shadow` 僅供本地唯讀比較。詳見 [AI Review 規範](../AI_REVIEW.md#7-batch--aggregator) 與 [ADR 0029](../../docs/adr/0029-verified-gate-direct-takeover.md)。

- 保護 `master` / `main`。
- 要求 PR Review 才能修改 `.github/workflows/**`。
- 將 AI Review workflow 的 Secret 放在受保護 Environment。
- 對 workflow 修改啟用必要的人工 approval。
- 使用最小化 `GITHUB_TOKEN` permissions。

## 維運與證據文件

- [發布驗證與待驗收事項](../../docs/ai-review-release-validation.md)
- [本地唯讀比較流程](../../docs/ai-review-shadow-trial.md)
- [CVE／GHSA 漏洞證據與適用性查證](../../docs/ai-review-vulnerability-evidence.md)

## 套件版本存在性查證

- `package_version_inspector.py` 支援 `requirements*.txt` 的精確 `套件==版本` 宣告，
  只查詢官方索引，不安裝、下載或執行套件。
- 預設查詢 PyPI JSON API；額外支援
  `--extra-index-url https://download.pytorch.org/whl/<channel>`（含等號寫法）。
  含 local 標籤的版本優先查詢該 wheel 索引；一般版本在 PyPI 回傳 404 時再查詢。
- wheel 證據僅接受指定 channel 路徑下的 `download.pytorch.org` 與
  `download-r2.pytorch.org` 連結，且不跟隨連結或 HTTP 重新導向。
- 私有來源、替換主索引、巢狀清單、變數及直接 URL 維持 `INCONCLUSIVE`，
  不發起索引查詢。每份清單最多接受 4 個不同的官方額外索引。
- 單一疑點可提供最多 8 行精確套件宣告作為證據，必須全部存在於固定 SHA 的清單內，
  且每筆版本均有官方發布檔案，才會回報 `COMPLIANT` 並排除「版本不存在」主張。
- 逾時、缺項、格式異常與部分版本未查證均維持 `INCONCLUSIVE`；存在不代表安全、
  未撤回或符合目標環境。受信任審查工作流程需待此程式合併到預設分支後才會採用。
