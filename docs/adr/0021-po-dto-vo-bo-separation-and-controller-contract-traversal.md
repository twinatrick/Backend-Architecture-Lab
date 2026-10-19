# 0021. PO/DTO/VO/BO Architectural Separation and Controller Contract Traversal

我們需要在全微服務架構中徹底落實資料傳輸模型與領域物件的職責分離（POJO / PO / DTO / VO / BO / DAO），並建立「Controller 契約依賴圖遍歷（Controller Contract Traversal）」的動態白名單靜態稽核機制：將所有 HTTP 輸入模型嚴格歸位至 `Dto/Request/`（命名以 `*Request` 結尾）與 `Dto/Query/`（命名以 `*Query` 結尾），`Vo/` 專門收納 Presentation 呈現層輸出模型（命名以 `*Vo` 結尾），非同步訊息統一收納至 `Event/`（命名以 `*Event` 結尾），業務運算中間物件收納至 `Bo/`；靜態代碼檢測工具動態遍歷公開 Controller 端點輸入參數與輸出型別，僅對真正暴露於對外契約的公開模型強制要求標註 `@ApiDto` 與 `@ApiField`，徹底解決內部模型被誤判強制標註 OpenAPI 文件的架構矛盾。

## Context

在先前的專案演進中，資料傳輸物件與視圖模型存在以下架構痛點：

1. **套件職責邊界混淆 (Package Responsibility Ambiguity)**：
   - 所有輸入請求（`*Request`）、查詢參數（`*SearchQuery`）、呈現視圖（`*Vo`）、非同步訊息（`CompensationEvent`、`CacheStatsEvent`、`AlarmMessage`）甚至業務中間模型（`BindingSnapshot`）與 JPA 查詢條件（`CriteriaAPIFilter`）全數混雜堆放於 `Vo/` 套件中，違反關注點分離與領域模型劃分原則。
2. **命名規範矛盾與非對稱性 (Naming Suffix Inconsistency)**：
   - 部分輸入請求被命名為 `*Vo`（例如 `TtsRequestVo`、`ChatRequestVo`），造成「名為 VO 實為 Request」的認知混亂。
   - 角色輸出視圖模型被命名為 `RoleOutVo`，與全專案其餘 `UserVo`、`CompanyVo`、`FunctionVo` 之命名模式不對稱。
   - 告警訊息以 `AlarmMessage` 命名，與共用層之 `*Event` 命名脫節。
3. **靜態稽核規則盲目與誤判 (Static Review False Positives)**：
   - 既有 `.github/ai-review/check_openapi_dto.py` 僅以簡單路徑包含 `/Vo/` 或後綴包含 `Vo.java`、`Query.java`、`Filter.java` 進行模式匹配，強制要求類別與非靜態欄位 100% 標註 `@ApiDto` 與 `@ApiField`。
   - 導致非 HTTP 傳輸之 Kafka 事件、內部安全 Filter、內部 Snapshot 或純 Service 運算模型全數被誤判違規，阻礙 CI 正常通行。
4. **`@Ignore` 語意混淆與契約邊界釐清 (Clarification of @Ignore vs Contract Scope)**：
   - 專案中的 `@Ignore` 註解僅代表 Spring Security 匿名存取白名單（`permitAll()`，如登入端點 `/auth/login`），絕不代表該 API 契約可以豁免 OpenAPI 文件與欄位註解規範。唯一合法豁免 OpenAPI 文件者僅有微服務內部 Feign 通訊之 `/xxx/inner` 端點。

評估方案：
1. **方案 A（徹底分立 DTO/VO/BO/Event + Controller 契約依賴圖動態遍歷白名單 - 推薦）**：
   - 建立 `Dto/Request/`（輸入請求體）、`Dto/Query/`（檢索與分頁）、`Vo/`（呈現層視圖）、`Event/`（訊息載荷）、`Bo/`（業務中間模型）、`DataAccess/specification/`（內部資料庫查詢過濾）。
   - 全面校正類別命名：`TtsRequest`、`ChatRequest`、`RoleVo`、`AlarmEvent`、`CriteriaApiFilter`。
   - 升級 `.github/ai-review/check_openapi_dto.py` 與 `check_openapi_models.py`：動態掃描所有非 `/inner` 的公開 Controller 端點輸入（`@ApiBody`、`@RequestBody`、`*Query`）與輸出（解包 `ResponseType<T>`、`PageResult<T>`、`List<T>`），並遞迴遍歷自訂物件欄位構建 `PublicApiModelSet`。僅有出現在契約白名單者需遵守 `@ApiDto` 與 `@ApiField` 規範；內部模型全面自動豁免。
2. **方案 B（僅在代碼審查腳本以檔名白名單硬編碼忽略）**：
   - 缺點：治標不治本，目錄結構仍持續混亂，未來新增事件或內部模型仍需手動更新硬編碼排除列表。
3. **方案 C（全部物件皆強制補齊 OpenAPI 文件）**：
   - 缺點：對內部 Kafka 事件、內部快照標註 `@ApiDto` 不僅毫無意義，更會污染 Swagger 文件並增加維護成本。

## Decision

我們決定採用 **方案 A**，具體架構決策如下：

1. **嚴格確立 POJO / PO / DTO / VO / BO / DAO 六層架構模型規範**：
   - **PO (Persistent Object)**：`Entity/`（ORM 資料庫映射實體，嚴禁暴露於 Controller 或 Service 介面簽名）。
   - **Request DTO**：`Dto/Request/`（客戶端寫入或操作之輸入請求載荷，統一以 `*Request` 命名）。
   - **Query DTO**：`Dto/Query/`（客戶端搜尋、檢索或分頁之過濾參數，統一以 `*Query` 或 `*SearchQuery` 命名）。
   - **View Object (VO)**：`Vo/`（Presentation 呈現層專屬視圖模型，統一以 `*Vo` 命名，嚴禁包含資料庫敏感欄位）。
   - **Messaging Event**：`Event/`（微服務間 Kafka 非同步事件與訊息載荷，統一以 `*Event` 命名）。
   - **Business Object (BO)**：`Bo/`（業務邏輯運算或歷史快照中間模型，如 `BindingSnapshot`）。
   - **DAO (Data Access Object)**：`Repository/` 與 `DataAccess/`（封裝資料庫查詢與條件規格 `CriteriaApiFilter`）。
2. **類別命名全域標準化校正**：
   - `TtsRequestVo` 更名為 `TtsRequest` 並移入 `backend-common/.../Dto/Request/`。
   - `ChatRequestVo` 更名為 `ChatRequest` 並移入 `backend-common/.../Dto/Request/`。
   - `RoleOutVo` 標準化更名為 `RoleVo`（駐留於 `backend-common/.../Vo/`），補齊 `@ApiDto` 與完整 `@ApiField` 欄位註解。
   - `AlarmMessage` 更名為 `AlarmEvent` 並移入 `backend-alert-service/.../Event/`。
   - `CriteriaAPIFilter` 更名為 `CriteriaApiFilter` 並移入 `backend-alert-service/.../DataAccess/specification/`。
3. **Controller 契約依賴圖動態遍歷 (Controller Contract Traversal)**：
   - 靜態稽核工具動態解析所有非 `/inner` 之 Controller 端點：
     - 輸入型別：解析 `@ApiBody`、`@RequestBody`、查詢參數（如 `*Query`）。
     - 輸出型別：解包 `ResponseType<T>`、`PageResult<T>`、`List<T>` 等泛型包裝器。
     - 遞迴解析：若模型欄位包含自訂物件型別，遞迴展開並納入白名單。
   - 僅名列 `PublicApiModelSet` 白名單之類別強制檢查 `@ApiDto` 與 `@ApiField`；白名單外之類別（非 Controller 傳輸物件）自動免檢。
4. **OpenAPI 標註語法紀律**：
   - **Java 21 Record**：允許直接於 Record Component 上標註 `@ApiField`。
   - **Enum 列舉**：必須標註 `@ApiDto` 描述列舉意義；列舉常數**豁免**標註 `@ApiField`（由 OpenAPI 框架自動產生 values 列表）。
   - **@Ignore 端點不豁免**：標註 `@Ignore` 之匿名端點仍屬對外 API 契約，其傳入與傳出模型必須具備完整 `@ApiDto` 與 `@ApiField` 規格。

## Consequences

### Positive
- **職責分明且專業嚴謹**：目錄結構與類別後綴 100% 反映真實架構職責，消滅「名為 VO 實為 Request」或「事件混雜於視圖目錄」的架構混亂。
- **精準靜態稽核**：動態依賴圖確保「需要文件的對外 API 一個不漏，不對外暴露的內部模型一個不擾」，根絕假警報。
- **維護性與可讀性提升**：新進工程師可直接透過套件路徑（`Dto/Request/`、`Dto/Query/`、`Vo/`、`Event/`、`Bo/`）準確定位元件。
- **Swagger 文件純淨完整**：公開端點規格完備且繁體中文註解清晰，不包含任何內部領域事件或快照模型。

### Negative
- **重構範圍廣泛**：全專案 700+ 個原始碼檔案之 `import` 引用與套件宣告經歷一次性全域重構。
- **跨服務 Feign Client 契約需同步**：調用端（如 `PermissionCheckFeignClient`）需同步採用標準化之 `RoleVo` 與 `Dto/Request/` 物件。
