# AI Review 唯讀影子試用

## 試用邊界

這是本地分支的試用入口，不是正式閘門切換，也不是部署驗收通過。
預設只取得 PR 資訊與批次計畫；明確加入 `--run` 才會呼叫模型並消耗配額。

- 不發布 PR 留言、Review、Commit Status 或人工裁定。
- 網路層僅允許 GitHub／PyPI HTTPS 讀取；執行模式額外允許兩家模型 API 的 POST。
  重新導向亦經過同一檢查，不只是跳過最外層發布函式。
- 全程單一 Worker，不開啟本機平行批次。
- 固定完整 Base／Head SHA，準備前後及執行前後重新核對；版本變動則失敗，不能套用舊結果。
- 工具來自目前本地分支，PR 內容僅當資料，不 checkout、import 或執行 PR 程式。
- 不自動讀取 `.env`，也不將 CLI Token 寫入檔案。請在目前終端設定憑證，不要貼到留言或文件。
- 裁定分支與獨立憑證尚未部署；本次試用不代表真實裁定寫入／回讀已驗收。

## 1. 準備計畫：不呼叫模型

先設定有讀取該 Repo 權限的 `GH_TOKEN`；建議使用專用唯讀憑證。
`--output` 必須指向不存在的新目錄，避免覆寫先前證據。報告請放專案外的核准暫存目錄。

```powershell
F:\conda\envs\backend-ai-py\python.exe .github/ai-review/shadow_trial.py --repo twinatrick/Backend-Architecture-Lab --pr 183 --output F:\AICache\Temp\opencode\ai-review-trial-183-prepare
```

輸出：

- `manifest.json`：固定 SHA、工具版本摘要、完整檔案與批次清單。
- `outcome.json`：只有 `PREPARED_NOT_EXECUTED`，不會產生假成功的查證報告。

準備入口會核對 GitHub 的 `changed_files` 數量、批次完整覆蓋及無重複；資料不足拒絕建立成功計畫。

## 2. 執行影子試用：會消耗模型配額

另在目前終端設定 `GROQ_API_KEY` 或 `GEMINI_API_KEY`。保持 `AI_REVIEW_CLAIM_GATE_MODE=shadow`。
使用另一個新輸出目錄，避免將準備結果與執行結果混在一起。

```powershell
F:\conda\envs\backend-ai-py\python.exe .github/ai-review/shadow_trial.py --repo twinatrick/Backend-Architecture-Lab --pr 183 --output F:\AICache\Temp\opencode\ai-review-trial-183-run --run
```

此命令會重新準備當下 PR 快照，不是重播先前目錄；請以新 `manifest.json` 的 SHA 為準。
成功時新增 `comparison.json`，包含舊演算法比較結果、影子候選、工具稽核與版本資訊。
比較中的舊結果是本地重跑，**不是 GitHub 上既有 Review 的原始結果或正式判決**。
失敗時 `outcome.json` 為 `FAILED`，Exit Code 為 1；既有 manifest 只表示曾準備，不表示審查成功。

## 3. 人工驗收清單

1. 確認 Base／Head 與工具版本；PR 有更新必須重跑。
2. 逐項核對影子移除的 Finding，是否有實際原始碼或官方版本證據。
3. 確認真實違規仍保留，不被模型漏列刪除。
4. 對 `INCONCLUSIVE` 分清必要上下文缺失、能力不足及真正待人工疑點；不能把未知當合規。
5. 檢查完整檔案覆蓋、預算耗盡與故障是否明確呈現，不得用部分結果批准整個 PR。
6. 記錄誤報減少量、留下的未知項目與人工負擔；不要只看測試數或最終決策。
7. 成功執行狀態是 `EXECUTED_NOT_APPROVED_FOR_ROLLOUT`，仍需另行批准才可正式接管。

## 已實際準備的樣本

2026-10-30 對 PR #183 完成唯讀準備：43 檔、13 批，沒有模型請求或 GitHub 寫入。
當次 Head 為 `b5d63fcbfb3ca9cf8382716a93ad7bce3ee090d0`，
Base 為 `a0eb39597b0e48f77d62e5c9e9344a04ac54f953`。
這只是準備驗證，不是完整 PR 模型試用通過。
