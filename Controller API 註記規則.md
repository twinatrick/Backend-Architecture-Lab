# Controller API 註記規則

## 核心原則
- Controller 的 OpenAPI / Swagger 文件註記**一律使用** `backend-common` 模組 `com.example.BackendArchitectureLab.Annotation.OpenApi` 套件下的標準註記。
- 對 `ApiControllerTag` 及 `ApiOperationOk` / `ApiOperationAuth` / `ApiOperationBadRequest` 之 `summary` / `description` 需以繁體中文填寫，將自動映射至 `Operation.summary` / `Operation.description`。

## 標準註記清單

| 註解 | 使用位置 | 預設回應碼 | 說明 |
|------|----------|-----------|------|
| `@ApiControllerTag` | 類別層（Controller class） | - | 定義文件分組標籤（name / description） |
| `@ApiOperationOk` | 方法層 | 200 / 500 | 一般成功操作 |
| `@ApiOperationAuth` | 方法層 | 200 / 401 / 500 | 需驗證的操作（Bearer） |
| `@ApiOperationBadRequest` | 方法層 | 200 / 400 / 500 | 可能回傳 400 的資料操作 |

- `@ApiControllerTag(name = "Skills", description = "Backend API endpoints - Skill management")`
- `@ApiOperationOk(summary = "Get skills", description = "Returns all skills.")`

## 禁止事項

- **嚴禁直接使用** `io.swagger.v3.oas.annotations.Operation`（或 `@Swagger`）來標記 Controller 方法，應一律改用上述標準註記（已內嵌 `@Operation` 與 `@ApiResponses`）。

## deprecated 用法
- 當 API 已棄用，於對應標準註記加上 `deprecated = true`，不要使用原生的 `@Operation(deprecated = true)`：

```java
@ApiOperationOk(summary = "Legacy", description = "...", deprecated = true)
public ResponseType<String> legacy() { ... }
```

- 同時方法上可使用 `@Deprecated`（編譯器層級標記）。