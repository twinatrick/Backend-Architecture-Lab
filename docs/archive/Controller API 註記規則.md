# Controller API 註記規則

## 核心原則
- Controller 的 OpenAPI / Swagger 文件註記**一律使用** `backend-common` 模組 `com.example.BackendArchitectureLab.Annotation.OpenApi` 套件下的標準註記。
- 對 `ApiControllerTag` 及 `ApiOperationOk` / `ApiOperationAuth` / `ApiOperationBadRequest` 之 `summary` / `description` 需以繁體中文填寫，將自動映射至 `Operation.summary` / `Operation.description`。
- `ResponseType.Success / ResponseType.Fail` 的訊息字串（第二參數）亦須以繁體中文填寫。

## 標準註記清單

| 註解 | 使用位置 | 預設回應碼 | 說明 |
|------|----------|-----------|------|
| `@ApiControllerTag` | 類別層（Controller class） | - | 定義文件分組標籤（name / description） |
| `@ApiOperationOk` | 方法層 | 200 / 500 | 一般成功操作 |
| `@ApiOperationAuth` | 方法層 | 200 / 401 / 500 | 需驗證的操作（Bearer） |
| `@ApiOperationBadRequest` | 方法層 | 200 / 400 / 500 | 可能回傳 400 的資料操作 |

- `@ApiControllerTag(name = "Skills", description = "技能管理相關 API")`
- `@ApiOperationOk(summary = "取得技能列表", description = "回傳所有技能。")`

## 禁止事項

- **嚴禁直接使用** `io.swagger.v3.oas.annotations.Operation`（或 `@Swagger`）來標記 Controller 方法，應一律改用上述標準註記（已內嵌 `@Operation` 與 `@ApiResponses`）。

## deprecated 用法
- 當 API 已棄用，於對應標準註記加上 `deprecated = true`，不要使用原生的 `@Operation(deprecated = true)`：

```java
package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.Common.ResponseType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegacyControllerExample {

    @ApiOperationOk(summary = "Legacy", description = "說明", deprecated = true)
    @GetMapping("/legacy")
    public ResponseType<String> legacy() {
        return ResponseType.Success("OK");
    }
}
```

- 同時方法上可使用 `@Deprecated`（編譯器層級標記）。

## 豁免情況（不需註記）

以下類型的 Controller **不需**套用本規則的 OpenAPI 註記：

- **內部 API Controller（`/xxx/inner` 路徑）**：僅供微服務間 Feign 呼叫，不對外暴露，不寫入 Swagger 文件。
- **具 `@Ignore` 註記的公開端點**：如對外 Webhook 回呼（LINE callback）、公開音訊串流等，已由 `@Ignore` 排除 Swagger 文件。

其他所有對外（前端或第三方）的 Controller 一律必須遵守本規則。