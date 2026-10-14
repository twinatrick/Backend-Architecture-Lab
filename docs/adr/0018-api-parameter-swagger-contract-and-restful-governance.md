# 0018. API Parameter Swagger Contract and RESTful Architecture Governance

我們需要在全專案中確立 API 輸入參數文件齊備性、Swagger/OpenAPI 契約嚴謹度以及 RESTful 路由標準架構：全面強制為 Controller 路徑/查詢參數與 Request/Response 模型標註 `@Parameter` 與 `@Schema`，徹底清理異常與模糊欄位命名（無相容包袱），全面移除帶動詞後綴之歷史 RPC 路由並替換為標準 RESTful HTTP 方法（POST/PUT/DELETE/GET），確保後端 OpenAPI 規格與前端對接契約達到嚴格的強型別與通用語言一致性。

## Context

在系統微服務架構持續迭代過程中，API 契約與端點設計面臨以下品質與維護性痛點：

1. **Swagger 契約缺失與弱型別模糊 (Weak Schema & Missing Parameter Docs)**：
   - 多數 Controller 方法的 `@PathVariable` 與 `@RequestParam` 缺少 `@Parameter` 註解，導致 Swagger UI 與前端代碼生成工具（如 openapi-generator / swagger-typescript-api）無法推導欄位繁體中文業務語義與範例值。
   - 審計欄位 `createdBy` 與 `updatedBy` 在模型註解中標註為 `example = "admin"`，與底層實際儲存之 UUID（如 `"00000000-0000-0000-0000-000000000001"`）存在契約衝突，導致前端校驗與類型推導混淆。
2. **異常與誤導性欄位命名包袱 (Obscure Naming & Legacy Burden)**：
   - 歷史程式碼中存在模糊、過度簡化或誤導性的欄位名稱（例如 `CriteriaAPIFilter.string`、`CriteriaAPIFilter.large` / `small`、`SuperUserRequest.key`、`RoleOutVo.roleArr` 等）。
   - 若採用 `@JsonAlias` 妥協方案，將使程式碼庫長久背負歷史包袱與心智負擔。
3. **RPC 動詞路由與 RESTful 混雜 (RPC Action Endpoints Anti-pattern)**：
   - 各模組 Controller 充斥 `/add`、`/create`、`/update`、`/delete`、`/saveUser` 等 RPC 式動詞路徑，違背現代 RESTful 架構慣例，造成前端呼叫路徑不統一且 Swagger 分組雜亂。

評估方案：
1. **方案 A（激進治理：一刀切清除歷史包袱，全面推行 RESTful 與標準 Schema - 推薦）**：
   - 欄位直接更名為準確的領域語言（如 `stringValue`, `greaterThan`, `lessThan`, `bootstrapSecret`, `roles`），不保留 `@JsonAlias`。
   - 廢除動詞路徑，全面改為標準 HTTP Method（POST 建立、PUT /{id} 更新、DELETE /{id} 刪除、GET /{id} 單筆查詢）。
   - 全面強制補齊 `@Parameter` 與 `@Schema`，UUID 欄位宣告 `format = "uuid"`，必填欄位宣告 `REQUIRED`。
   - 提供純本地前端遷移手冊 `API_MIGRATION_GUIDE_FOR_FE.md`（不上 Git），指引前端 AI 快速無痛完成對接。
2. **方案 B（保守相容：保留動詞路徑與 @JsonAlias）**：
   - 保留舊動詞端點並宣告 `@Deprecated`，新舊並存。
   - 缺點：端點數量翻倍，Swagger 規格冗餘，技術債長久殘留。

## Decision

我們決定採用 **方案 A**，具體架構治理規範如下：

1. **全面強制 OpenAPI 參數文件化 (Universal Parameter & Schema Documentation)**：
   - 所有 Controller 方法之 `@PathVariable` 與 `@RequestParam` 必須顯式標註 `@Parameter(description = "...", example = "...")`。
   - 所有 Request DTO、Vo、Query 物件類別與欄位必須標註 `@Schema`，識別碼與外鍵統一宣告 `format = "uuid"`。
   - 審計欄位 `createdBy` 與 `updatedBy` 統一定位為使用者 UUID，範例值一律使用標準 Admin UUID。
2. **清理異常命名與回歸通用語言 (Eradication of Obscure Naming)**：
   - 欄位命名必須精準反映領域意義，直接更名且嚴禁保留 `@JsonAlias` 等妥協包袱。
3. **RESTful 路由標準一刀切規範 (Universal RESTful Resource Mapping)**：
   - 嚴格廢除 URL 中的 `/add`, `/create`, `/save`, `/update`, `/delete`, `/get/{id}`。
   - 統一使用標準 HTTP 動詞：`POST /api/{resources}`、`PUT /api/{resources}/{id}`、`DELETE /api/{resources}/{id}`、`GET /api/{resources}/{id}`。
4. **規範與文件同步沉澱 (Dual-Track Documentation)**：
   - 修訂《開發規範.md》納入第 3.2 節與第 3.3 節法典標準。
   - 編寫純本地前端 AI 遷移手冊 `API_MIGRATION_GUIDE_FOR_FE.md`（保持未追蹤，不上 Git），確保 Git 歷史純淨無污染。

## Consequences

- **OpenAPI 規格精準度與型別安全性提升**：Swagger UI 與自動生成的前端 SDK 具備 100% 準確之型別、必填校驗與繁體中文說明。
- **清除歷史技術包袱**：徹底根除動詞混雜與模糊欄位命名，專案架構達到高度統一與乾淨。
- **維護前端遷移平穩性**：透過純本地遷移指引，輔助前端 AI 高效自動更新 API 呼叫點。
