# 0008. Pre-commit Compliance Gate and Local Deterministic Linting

我們需要建立一套基於 Git Hooks 的本地確定性規範通行防線（Pre-commit Compliance Gate），以確保微服務架構隔離、Controller 註記、依賴注入以及敏感資訊等專案規範在開發者或 Agent 提交變更的瞬間即被強制驗證並阻擋違規。

## Context

專案於《開發規範.md》及 `AGENTS.md` 中明定了一系列微服務架構與程式碼品質規範（包括微服務實體隔離、禁止自我 Feign、Controller 統一 OpenAPI 註記、全建構子注入 `@RequiredArgsConstructor`、禁止完全限定名稱 FQN、以及金鑰安全防護）。
然而，過往的交付流程存在重大痛點：
1. **驗證斷層與延遲反饋**：開發端或 Agent 在本地通常僅執行 Maven 單元測試與 JaCoCo 覆蓋率檢查，無法在 Commit 前對照《開發規範.md》進行靜態語法與架構分層稽核。
2. **CI 遠端阻擋代價過高**：現有規範稽核邏輯雖然實作於 `.github/ai-review/`，但僅綁定於 GitHub Actions CI 流程，違規程式碼往往在 Push 並開啟 Pull Request 後才被 CI AI Review 標記為阻擋（HUMAN_REVIEW_REQUIRED），造成反覆修正、提交與重跑 CI 的時間與資源浪費。
3. **部分核心規則存在檢測盲區**：既有 `check_java.py` 僅攔截 `@Operation` 而漏檢原生 `@Tag`、僅攔截 `@Autowired` 而未攔截手寫建構子、且未檢測 FQN 禁寫條款。

評估方案：
1. **方案 A (Git 原生 core.hooksPath + Python 輕量稽核引擎 推薦)**：使用 Git 原生 `core.hooksPath = .githooks` 機制納管 Pre-commit Hook，並以 Python 3 標準庫實作離線、零相依、毫秒級的 `check_local.py` 檢查工具。
2. **方案 B (Maven Checkstyle / Spotless / PMD 插件)**：在 `pom.xml` 中引入重量級 Java 程式碼風格外掛。缺點是每次啟動 JVM 耗時 5~10 秒，且無法針對 Git 差異（Diff-Aware）進行增量檢查，亦無法同時覆蓋 Python 側車與 GitHub Workflow 規範。
3. **方案 C (純遠端 CI 稽核 Gate)**：維持現狀，完全由 GitHub Actions 在 PR 開啟時執行。缺點是無法在本地第一時間提供即時防饋，且容易造成污染的 commit 歷史被推送至遠端。

## Decision

我們決定採用 **方案 A**，在版本控制層與本地稽核層建立完整通行閘門：

1. **零外部依賴之本機確定性 CLI (`.github/ai-review/check_local.py`)**：
   - 僅依賴 Python 3 標準庫，零額外套件安裝開銷，執行耗時小於 200ms。
   - 支援增量（`git diff --cached`）與全量（`--all`）掃描，即時輸出帶有《開發規範.md》對應條款與修復指引的結構化違規報告。
   - 遵循嚴格阻擋原則：發現 HIGH 或 MEDIUM 違規時以 Exit Code 1 終止，實體阻擋 `git commit`；LOW 等級則印出提示並放行。
2. **Git 原生 Hooks 版本化納管 (`.githooks/pre-commit`)**：
   - 將 Hook 腳本置於受 Git 版控納管的 `.githooks/` 目錄，透過 `git config core.hooksPath .githooks` 啟用。
   - 智慧探測本機 Python 環境（`python3` / `python`），阻擋不合規提交；並保留 Git 原生 `--no-verify` 作為極端狀況下的緊急逃生通道。
3. **靜態規則庫覆蓋率加固 (`check_java.py` & `check_rules.py`)**：
   - **OpenAPI 註解全面收斂**：在 Controller 類別中全面攔截所有原生 `io.swagger.v3.oas.annotations.*`（包含 `@Tag` 與 `@Operation`），強制引導至 `backend-common` 封裝之自訂註解。
   - **全建構子注入防護**：檢測 Controller 與 Service 類別中的手寫多載建構子，強制要求使用 Lombok `@RequiredArgsConstructor` + `private final`；並對 `@Configuration` 與測試類別提供結構化豁免白名單。
   - **完全限定名稱 (FQN) 禁寫掃描**：掃描 Java 原始碼中的長路徑型別，強制要求頂部顯式 import，豁免 package/import 行與註解。
4. **規範與工作流防護落實 (`AGENTS.md`)**：
   - 明文規範 Agent 與開發者在執行 `git commit` 前，除單執行緒 Maven 測試外，必須先確認本地 Python 規範稽核 Exit Code 為 0。

## Consequences

- **左移防護 (Shift-Left Quality Gate)**：將架構與安全規範的阻擋點由 CI 遠端拉回本地暫存區，確保進入 Commit 歷史與 PR 的程式碼皆符合專案標準。
- **本地 CI 規則 100% 鏡像 (Rule Parity)**：本地 CLI 與遠端 CI 共用同一套 `static_checks.py` 規則庫，杜絕「本地測試全綠、CI 審查報錯」的落差。
- **維護約束**：開發環境本機必須具備 Python 3 執行檔（專案既有 Python 側車環境已滿足此要求）。
