# AI Code Review Contract

> `開發規範.md` 是唯一規則來源；本文件只定義 CI 如何執行 AI Review、如何判定風險，以及何時阻擋 PR。

## 1. Review 目標

AI Review 必須優先檢查：

1. Security / Authentication / Authorization
2. BOLA / IDOR / Resource Ownership
3. Microservice Boundary / Data Ownership
4. Data Integrity / Transaction / Concurrency
5. Functional Correctness
6. API Contract / Permission / OpenAPI
7. SOLID / DRY / KISS / YAGNI / Coupling
8. Java / Python 專案規範
9. Test / Regression Risk

Style 與可選重構最後處理，不得掩蓋高風險問題。

## 2. Review Scope

AI 必須先分析 PR diff，再依依賴關係向外追查必要 context：Controller、Service、DataAccess、Repository、Entity、VO、Mapper、Feign、Permission、Config、Exception、Test。

不得因無關的既有技術債要求本 PR 大規模重構；只有在 PR 新增、放大、暴露或觸發該問題時才納入。

## 3. 規範判定

所有規範判斷以 `開發規範.md` 為準。`AGENTS.md` 可提供執行與專案 context，但不得取代 `開發規範.md`。

若一般最佳實務與專案規範衝突，以專案規範為準。

## 4. 必查項目

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

## 5. Severity

- **CRITICAL**：權限繞過、敏感資料洩漏、重大注入、重大資料破壞等。必須阻擋。
- **HIGH**：明確 BOLA、Architecture Boundary violation、Permission violation、重大功能錯誤等。必須阻擋。
- **MEDIUM**：明顯 maintainability / coupling / error-handling 問題。預設不阻擋，除非 CI policy 明確設定。
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
- Blocking

不得以「感覺不好」、「建議重構」、「可能有問題」作為沒有證據的阻擋理由。

## 7. Decision

`REQUEST_CHANGES` 條件：存在 CRITICAL / HIGH，或有明確 Security、Authorization、BOLA、Microservice Boundary、Permission、Functional Bug。

否則：`APPROVE`。

MEDIUM / LOW 可列在報告中，但不得阻擋。

## 8. AI 輸出格式

```markdown
# AI Code Review

## Decision
APPROVE | REQUEST_CHANGES

## Summary
- Files Reviewed: N
- Critical: N
- High: N
- Medium: N
- Low: N

## Findings

### [HIGH] Title
**Location**: `full/package/path/File.java:123`
**Rule**: `開發規範.md — <section>`
**Evidence**: ...
**Risk**: ...
**Recommendation**: ...
**Confidence**: HIGH
**Blocking**: YES

## Passed Checks
- ...

## Review Conclusion
...
```

## 9. CI 原則

可以由 compiler、test、lint、AST 或 deterministic script 精確判斷的規則，優先由 CI 執行；AI 主要負責架構、資安、功能與設計推理。

AI Review 不代表測試通過；CI 仍必須獨立執行 Maven、Python、coverage 與其他 deterministic checks。
