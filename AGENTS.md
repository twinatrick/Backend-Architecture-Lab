# OpenCode Agent Instructions

## Language
- **必須使用繁體中文**與使用者進行溝通、撰寫說明與註解 (MUST use Traditional Chinese for communication and documentation).
## Build & Run Environment
- **Java 環境變數自動配置 (優先級極高)**：
  - 在執行任何需要 Java 21 的指令（如 `./mvnw test`）之前，**必須**先讀取專案根目錄下的 `.env` 檔案。
  - 檢查 `.env` 中是否存在 `JAVA_NOW_HOME` 或 `java-now-home` 變數。
  - **若存在**：直接讀取該路徑，並將其設定為當前終端機的環境變數（例如 `$env:JAVA_HOME="<該路徑>"`），**嚴禁**再向使用者詢問。
  - **若不存在**：才可詢問使用者當前的 Java 21 路徑，拿到路徑後除了暫存到環境變數，也請協助將其寫入 `.env` 中的 `JAVA_NOW_HOME` 以供未來使用。
- **環境設定讀取優先權 (重要)**：
  - 在執行任何啟動或測試指令前，請先檢查當前專案目錄下是否存在 **`.run` 資料夾**。
  - **若存在 `.run` 目錄，必須優先讀取其中的配置檔案 (如 XML)**，並將其內嵌的環境變數、Spring Profiles 或啟動參數作為最高優先級應用於後續的執行環境中。
  - 若不存在，則退回參考標準的 `.env.example` 進行設定。若在 Docker 內執行後端，需設定 `APP_IN_DOCKER=true`。
- **Infrastructure**: 必須先執行 `docker compose -f compose.yaml up -d` 啟動基礎服務 (PostgreSQL, Redis, Kafka, Zookeeper)。
- **Local Dev Server**: 使用 `./mvnw spring-boot:run` 啟動，預設運行於 port `8000`。
- **本地執行緒與資源限制 (避免卡頓)**：
  - 在本機執行 Maven 建置、測試（如 `./mvnw test`）或批次任務時，**嚴禁開啟多執行緒並行建置/測試**（例如禁止使用 `-T` 參數進行多線程平行編譯或多執行緒 surefire 測試）。
  - 必須維持**單一執行緒 (Single Thread)** 循序執行，確保 CPU 與系統資源不過載，避免影響使用者在本地端操作其他應用程式導致卡頓。

## Testing & Quality

- **Test Command**: 專案標準指令為 `./mvnw test`。測試預設使用 H2 in-memory database。
  - **【Token 節省黑魔法與單執行緒限制 (Windows/CLI 必讀)】**：
    為了讓底層的 RTK 盾牌能完美看穿 Maven 繁重的日誌包裝，並精簡 JUnit 的重複輸出以節省 90% 以上的 Token，同時避免多核心平行測試卡死本地系統：
    1. 在調用終端機執行測試時，**禁止**直接下達 `./mvnw test`。
    2. **必須優先使用以下原生指令包裹**：`rtk ./mvnw test "-DforkCount=1" "-Dsurefire.useFile=false"`。
    3. 強制使用 `"-DforkCount=1"` 維持單一 JVM 行程，防止 Surefire 依 CPU 核心數平行啟動多個行程導致卡頓；`"-Dsurefire.useFile=false"` 迫使日誌輸出至主終端機由 RTK 極致壓縮。
  - **Coverage**: `./mvnw jacoco:report`。Jacoco 覆蓋率報告位於 `target/site/jacoco/index.html`。
  - **Coverage Rules**: 專案設定了最低 80% 的覆蓋率要求 (`BUNDLE` 級別)。注意：多數的對外介面與資料存取層 (Controller,
    Entity, mapper 等) 在 `pom.xml` 中被設定排除覆蓋率計算。
  - **Commit Rule**: 當 `./mvnw test` 未全部通過時，**禁止 commit**。必須先確認測試全部通過（Failures: 0, Errors: 0），才能執行 git commit。
  - **Mockito Warning**: 測試已在 Maven Surefire 中設定 `-XX:+EnableDynamicAgentLoading` 來消除 Java 21 下的警告。

## Architecture & Code Conventions
- **實作前必須先調查既有風格與慣例 (Pre-flight Investigation)**: 在新增或修改功能前，**必須先調查**既有程式碼的風格與慣例，包含但不限於：Entity 欄位型別、Repository 方法簽名、既有 Service 是否有 Interface、Mapper/Vo 所在模組位置、依賴注入風格、日期型別使用方式。不得預設假設或憑空猜測，應以 master 分支或同模組既有檔案為準。
- **Master Branch 為最終依據**: 在判斷程式碼行為是否合理時，**必須優先參考 `master` 分支的實作**，因為 master 是經過 Review 後的結論。若當前分支與 master 有歧異，以 master 為準。
- **Base Package**: `com.example.BackendArchitectureLab` (注意大小寫)
- **Generators**: 專案大量使用 MapStruct 與 Lombok，Maven 已設定對應的 Annotation Processors。
- **Package Quirks**: 請遵守現有的 Package 命名與大小寫慣例：
  - 首字母大寫: `Aop`, `Vo`, `Entity`, `Repository`, `Service`, `Timer`, `Util`, `WebSocket`,`Annotation`, `Config`, `Controller`, `Dataaccess`, `Exception`, `Filter`, `Mapper`
- **Dependency Injection**: 專案全面採用 **建構子注入 (Constructor Injection)** 作為預設注入方式，統一透過 Lombok 的 `@RequiredArgsConstructor` 搭配 `private final` 欄位宣告依賴，**嚴禁**在生產程式碼中使用 `@Autowired` 進行欄位注入（除 `@Autowired(required = false)` 等特殊可選注入或 Spring Test 測試類別外）。
- **Service 層與 Mapper 使用規範 (重要)**：
  - Mapper 僅可在 Service Impl 層中使用，Controller **嚴禁**注入或呼叫 Mapper
  - Service 介面方法簽名必須回傳 Vo（如 `UserVo`、`BotConfigVo`），嚴禁回傳 Entity
  - Service 實作內部透過 Mapper 進行 Entity ↔ Vo 雙向轉換
  - Controller 只與 Service 介面及 Vo 型別互動，Controller 程式碼中不得出現 Entity 型別
- **Entity 使用規範 (重要)**:
  - Entity 僅在 Repository、DataAccess 及 Service Impl（經 Mapper 轉換後）中使用，**嚴禁**傳遞至 Controller 層或作為 API 回傳型別
- **開發規範 (唯一規則文件)**: **絕對必須遵守** `開發規範.md`（此為唯一規則來源，合併自微服務分類/Permission/Controller 註記/Python 規則/程式碼品質標準（CodeReadview），原文於 `docs/archive/`）。重點包含：模組資料隔離、跨服務 Feign Client 呼叫、Service 層禁止操作 EntityManager、三層權限設計（`@RequirePermission`）、Controller OpenAPI 標準註記（`Annotation/OpenApi`）、Python 語法規範（import 置頂/禁止單字母變數/禁止 `except: pass`）、程式碼品質標準（SOLID/DRY/KISS/YAGNI/高內聚低耦合/Boy Scout Rule）。
- **範例一律使用完整套件路徑**: 文件或說明中引用程式碼位置時，一律寫完整套件路徑（如 `com.example.BackendArchitectureLab.Service.Impl.RoleService`），嚴禁寫縮寫路徑（如 `RoleService.java`）。
- **原始碼中禁止使用完全限定名稱 (Fully Qualified Name, FQN)**：在 Java、Python 等原始碼檔案中，一律禁止直接在方法簽名、變數宣告或實例化物件時寫入長路徑的 FQN（例如 `java.util.Date`、`java.util.Optional`、以及微服務內部的實體或異常長路徑）。所有依賴的類別必須在檔案頂部撰寫顯式 `import`，並在程式碼內部僅使用簡潔的類別名稱 (Simple Name) 來維持代碼美觀與高度可讀性。*(備註：前條「範例一律使用完整套件路徑」僅適用於向使用者回報或文件描述中，原始碼編寫仍需遵守本 FQN 禁止規則。)*
- **Markdown 程式碼範例規範 (IDE 語法相容)**：撰寫或維護 `*.md` 技術文件中的 ````java` 區塊時，**必須宣告完整的外層 `class` / `interface` / `record` 與方法結構**，嚴禁直接撰寫裸語句（如裸 `switch`、裸 `if`、局部變數宣告等），避免觸發 IDE（如 IntelliJ IDEA）的 Java Language Injection 語法錯誤（如 `'class' or 'interface' expected`）。抽象片段或非完整程式碼一律使用通用標籤（如 ````text` 或 ````）。

## Git & Version Control (嚴格規定)
- **絕對禁止擅自 Commit/Push (CRITICAL)**：在任何情況下，Agent **絕對不可以**在未經使用者明確指示或同意的情況下，自動執行 `git commit`、`git push` 或任何修改 Git 歷史紀錄的操作。
- **敏感檔案保護**：執行任何 Git 相關操作前，必須檢查並確保 `.env` 等包含機密/本地設定的檔案已正確列入 `.gitignore`，嚴禁將其加入版本控制。
- **禁止提交日誌與暫存檔案 (Pre-commit 優先檢查)**：
  在每次執行 `git commit` 前，**必須優先檢查**工作目錄中是否包含 `*.log`、`*.err` 等日誌/錯誤檔案，
  以及 `TODO.md` 等非原始碼文件。若發現這些檔案被暫存 (staged) 或存在於 Git 追蹤清單中，
  **必須立即中斷 commit 流程**，先執行 `git rm --cached` 將其從追蹤中移除，
  並確認 `.gitignore` 已涵蓋對應模式，才能繼續提交動作。
  **例外情況**：若該檔案在 `README.md` 中有明確提及或作為專案必要文件說明，則不在此限。
