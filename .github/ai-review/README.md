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

- 保護 `master` / `main`。
- 要求 PR Review 才能修改 `.github/workflows/**`。
- 將 AI Review workflow 的 Secret 放在受保護 Environment。
- 對 workflow 修改啟用必要的人工 approval。
- 使用最小化 `GITHUB_TOKEN` permissions。
