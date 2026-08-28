# 0002. Trusted AI Review Boundary and Target Resolution Architecture

我們需要確保 AI Review 系統在 PR 生命週期中 100% 穩定被觸發並正確審查，同時絕對不破壞受信任執行邊界（Trust Boundary）。

## Context

PR #62 等外部與內部 PR 在被觸發時，曾遭遇 `AI Review Trigger` 成功但後續受信任工作流程中斷或目標解析失敗的情形。調查發現成因包含：
1. `ai-review-trusted.yml` 缺少 `statuses: write` 權限，導致發布 Commit Status 時遭遇 403 觸發 fail-closed。
2. GitHub Actions `workflow_run` 事件 payload 中的 `pull_requests` 陣列常態性為空，僅依賴單一 API 反查容易在特定邊界條件下受阻。
3. PR Review API 在特定情境（如自身審查 PR）回傳 HTTP 422 時未進行容錯分流，導致本已成功的 Issue 留言與 Commit Status 被誤判為全面失敗。

## Decision

我們決定維持並強化受信任審查邊界架構：
1. **堅持雙工作流程分離**：
   - `ai-review-trigger.yml`（由 `pull_request` 觸發，無權限、不 checkout 代碼、不存取 secrets）負責記錄觸發並以 Artifact 形式安全傳遞 `pr-metadata.json`。
   - `ai-review-trusted.yml`（由 `workflow_run` 在 `master` 上觸發，具備受保護環境 `apikey` 與必要寫入權限）下載 Artifact 並執行受信任代碼。
2. **多重來源 Target Resolution + 嚴格二次驗證**：
   - 目標解析優先級：`pr-metadata.json` → `workflow_run.pull_requests` → `inputs` (手動 dispatch) → GitHub Commit SHA API 反查。
   - 所有解析結果一律強制透過 GitHub API `GET /pulls/{pr_number}` 進行 state==open、base branch (master/main)、repository match 與 head SHA 一致性之二次強校驗。
3. **補齊 GitHub Actions 權限**：
   - 在 `ai-review-trusted.yml` 中補齊 `statuses: write` 權限。
4. **結構化 Failure Visibility 與 API 422 容錯**：
   - 結構化區分錯誤碼（`TARGET_RESOLUTION_FAILED`、`TARGET_VALIDATION_FAILED`、`TOCTOU_CONFLICT`、`AI_PROVIDER_FAILED`、`REVIEW_EXECUTION_FAILED`、`REVIEW_PUBLISH_FAILED`）。
   - 當 PR Review API 回傳 422 且 Issue 留言與 Commit Status 成功發布時視為可見性已達成，避免流程假性崩潰。

## Consequences

- **安全性 (Security)**：不可信 PR 程式碼絕無可能接觸 `GROQ_API_KEY` 或 `GEMINI_API_KEY`，執行代碼始終來自 `master` 分支。
- **穩定性 (Reliability)**：徹底解決 `workflow_run` payload 遺失 `pull_requests` 造成的解析中斷，並消除 403 權限錯誤。
- **透明度 (Visibility)**：開發者能從 Commit Status 與 PR 留言精確得知失敗階段。
