# AI Code Review Contract

> `開發規範.md` 是唯一專案規則來源；本文件只定義 CI 如何執行 AI Review、Review 範圍、Finding 格式與 Gate 原則。

## 1. Review 目標

AI Review 必須優先檢查：

1. CI / GitHub Actions / Supply Chain（若 PR 修改 `.github/**`）
2. Security / Authentication / Authorization
3. BOLA / IDOR / Resource Ownership
4. Microservice Boundary / Data Ownership
5. Data Integrity / Transaction / Concurrency
6. Functional Correctness
7. API Contract / Permission / OpenAPI
8. SOLID / DRY / KISS / YAGNI / Coupling
9. Java / Python 專案規範
10. Test / Regression Risk

Style 與可選重構最後處理，不得掩蓋高風險問題。

## 2. Review Scope

AI 必須先分析 PR diff，再依依賴關係向外追查必要 context：Controller、Service、DataAccess、Repository、Entity、VO、Mapper、Feign、Permission、Config、Exception、Test。

若 PR 修改 `.github/**`，必須審查 workflow permissions、Secrets、Action pinning、untrusted input、shell injection、artifact/cache、fail-open、review bypass 與 workflow supply-chain 風險。

不得因無關的既有技術債要求本 PR 大規模重構；只有在 PR 新增、放大、暴露或觸發該問題時才納入。

## 3. 規範判定

所有專案規範判斷以 `開發規範.md` 為準。若 PR 同時修改 `開發規範.md`，Review 必須同時檢查該規範變更本身；不得因 PR 修改規範就自動降低原有安全或架構要求。

若一般最佳實務與專案規範衝突，以專案規範為準。

## 4. 必查項目

### CI / Supply Chain

- Workflow `permissions` 必須遵循最小權限。
- PR 不可信輸入不得直接形成 shell command、script 或 expression injection。
- `pull_request` workflow 不得讓 PR 可控程式碼取得高敏感 Secrets。
- 第三方 Actions 優先 pin 到不可變 SHA；若使用 tag 必須有明確理由。
- 檢查 `pull_request_target`、artifact、cache、GITHUB_TOKEN、environment approval 與 workflow dispatch 的信任邊界。
- Review workflow 不得因缺少 batch、timeout、artifact 或 AI response 而 fail-open。
- AI 輸出的 `blocking`、`decision` 不得直接成為 Merge Gate 的唯一依據；最終 Gate 必須由 deterministic CI policy 計算。
- 修改 `.github/workflows/**` 時，必須視為高風險變更並納入 Review。

### Security / Authorization

- 驗證登入者身份與操作權限。
- 對 `/users/{id}`、`/projects/{id}`、`/skills/{id}`、`/jobs/{id}` 等資源 API 檢查 Resource Ownership。
- 不得只相信 client 傳入的 userId / path id。
- 管理端點檢查 `@RequirePermission` 與權限字典一致性。
- 不得把 token、password、secret、API key 或敏感個資寫入 log。

### Microservice

- Service 只操作自己的資料；跨服務使用 Feign + VO。
- 禁止跨服務直接查其他 Service DB。
- 禁止 IAM 依賴業務 Service。
- 檢查 A → B → A 循環依賴。
- 檢查不必要的 self-Feign。

### Layering

- Controller → Service → DataAccess / Repository。
- Controller 不得包含 Business Logic、Repository、EntityManager。
- Controller 不得直接使用 Entity；Service interface 回傳 VO。
- Mapper 僅由 Service Impl 使用。

### API / Permission

- 檢查 `開發規範.md` 所要求的 Controller OpenAPI annotations。
- 檢查 Permission Dictionary ↔ `@RequirePermission` 雙向一致性。
- 檢查 permission naming 是否符合規範。

### Quality

- SOLID、DRY、KISS、YAGNI。
- 高內聚、低耦合。
- 避免 God Service、循環依賴、重複 Business Logic、無需求抽象。
- 遵守 Boy Scout Rule，但不得藉此擴大 PR scope。

### Python

只對 `backend-ai-py` 套用 Python 規則：import 順序、命名、格式、type hints，以及禁止 bare `except` / `except Exception: pass` 等規範。

## 5. Severity / Confidence

- **CRITICAL**：權限繞過、敏感資料洩漏、重大注入、重大資料破壞等。deterministic Gate 必須阻擋。
- **HIGH**：明確 BOLA、Architecture Boundary violation、Permission violation、CI Secret/Supply-chain boundary violation、重大功能錯誤等。deterministic Gate 必須阻擋。
- **MEDIUM**：明顯 maintainability / coupling / error-handling 問題。預設不阻擋。
- **LOW**：可讀性、命名、小型 refactor 建議。不阻擋。

Confidence：`HIGH` / `MEDIUM` / `LOW`。LOW confidence 不得單獨阻擋 PR。

## 6. Finding Contract

每個 Finding 必須包含：

- Location
- Problem
- Rule（引用 `開發規範.md` 的章節或明確規則）
- Evidence
- Risk
- Recommendation
- Severity
- Confidence

**不得輸出 `Blocking` 或 `Decision` 欄位。** AI 只能提出 Finding；是否阻擋由 Aggregator 的 deterministic policy 決定。

## 7. Batch / Aggregator

每個 Batch 只負責找問題，不產生 Final Verdict。

Aggregator 必須：

1. 驗證所有預期 Batch 都產生結果。
2. 驗證每個 Batch JSON schema。
3. Deduplicate Findings。
4. 重新依 `severity + confidence + policy` 計算 blocking。
5. Batch 缺失、AI response invalid、artifact 缺失、coverage 不完整時一律 fail-closed。
6. 最終只由 deterministic policy 決定 `APPROVE` / `REQUEST_CHANGES`。

## 8. 語言要求

所有 AI 產生的自然語言內容必須使用繁體中文（zh-TW）。禁止使用簡體中文。

程式碼、Class、Method、Annotation、檔案路徑、API 路徑與標準技術名詞可以保留原文。

## 9. CI 原則

可以由 compiler、test、lint、AST 或 deterministic script 精確判斷的規則，優先由 CI 執行；AI 主要負責架構、資安、功能與設計推理。

AI Review 不代表測試通過；CI 仍必須獨立執行 Maven、Python、coverage 與其他 deterministic checks。

AI Review workflow 本身屬於高風險 CI 資產；執行 PR Review 的 trusted workflow 不得 checkout 後執行 PR 提供的 script，也不得讓 PR 可控 workflow 直接接觸 Review Secret。
