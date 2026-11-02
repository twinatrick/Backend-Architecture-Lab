# 雙平台共通 CI 驗證單一來源與變更影響範圍門禁

狀態：Accepted。

## 背景與問題

專案同時維護 GitHub Actions（`.github/workflows/ci.yml`）與 GitLab CI（`.gitlab-ci.yml`）。過去兩邊各自在 YAML 內手寫測試命令與 `pip install` 清單，且多數套件未固定版本、觸發條件與容器環境亦不一致，導致 `backend-ai-py/services/diarization_worker.py` 新增直接依賴 `PyYAML` 時，GitHub Actions 因多安裝 `faster-whisper` 間接帶入 `pyyaml` 而僥倖通過，GitLab CI 則因缺少 `PyYAML` 發生 `ModuleNotFoundError: No module named 'yaml'` 全面失敗。此外，無關檔案（如純文件）變更時亦欠缺一致且安全的影響範圍判定契約。

## 決策與取捨

- **共通驗證與平台專屬驗證明確分界**：
  - **共通 CI 驗證（Shared CI Verification）**：兩平台皆有的三項核心驗證（Python 靜態檢查與單元測試、Java 單元測試與 JaCoCo 覆蓋率＋條件式 Sonar 掃描、Java Testcontainers 端到端整合測試）統一收斂至 `scripts/ci/` 下的三個共用 Bash 入口腳本，兩份 CI YAML 僅負責工作流程編排、傳遞參數、門禁與報告收集。
  - **平台專屬驗證（Platform-Specific Verification）**：僅單邊存在的驗證（GitHub 的 `ai-review-runtime` 乾淨環境匯入驗證、GitLab 的 `portainer-deploy-contract-test`）以及映像發布／Portainer 叢集部署維持於原平台定義，不強行抽共用或複製至另一平台。
- **執行環境與 Python 依賴鎖定**：
  - 兩平台共通驗證統一運行於相同 Linux 容器映像並固定 `@sha256` digest（Java：`maven:3.9.8-eclipse-temurin-21`，Python：`python:3.11-slim`），消除 Runner 宿主機作業系統與預裝套件差異。
  - Python CI 驗證改由 `backend-ai-py/requirements-ci.in` 宣告直接依賴（顯式列入 `PyYAML` 等測試與服務直接相依套件，移除測試全程已 mock 之巨型重依賴），並編譯為固定版本的 `backend-ai-py/requirements-ci.lock` 供雙平台一致安裝；`backend-ai-py/requirements_diarization.txt` 維持獨立供 GPU 子進程使用，並同步補齊直接依賴 `PyYAML`。
- **觸發事件對齊**：
  - 雙平台共通驗證統一於「目標為 `master`／`main` 的 PR／MR、`master`／`main` 分支 push、`v*` 版本標籤、手動執行（`workflow_dispatch`／`web`）」時啟動；一般功能分支尚未開啟 PR／MR 前的 push 不自動觸發共通驗證。
- **變更影響範圍驗證（Impact-Scoped Verification）與閉合失敗全跑（Fail-Closed Full Run）**：
  - 以共用 Python 判定器（`scripts/ci/resolve_ci_impact.py`）按驗證群組（`python`、`java`）計算影響範圍：
    - `backend-ai-py/**` 變更觸發 `python` 驗證群組。
    - Java 模組、`pom.xml`、`mvnw*`、`lombok.config`、`docker/backend/**`、`docker/compose/prototype.yaml` 等變更觸發 `java` 驗證群組（同時涵蓋 Java 單元測試與 Testcontainers 整合測試，不拆開省略）。
    - 共用 CI 腳本（`scripts/ci/**`）或雙平台 CI 設定檔變更時，強制觸發全部共通驗證群組。
    - 純文件或非共通驗證輸入檔案變更時，對應共通驗證群組標記為「不適用（Not Applicable）」並略過執行。
  - 比較基準：PR／MR 比對目標分支 `merge-base..HEAD`（涵蓋整批 PR／MR 所有提交與新增、修改、刪除、改名）；主分支 push 比對推送前後提交；`v*` 標籤、手動觸發、比較基準缺失、全零 SHA、提交 SHA 不一致或 Git 歷史無法解析時，一律強制全跑（Fail-Closed）。
- **門禁、報告與非空測試集守衛（Non-Empty Test Suite Guard）**：
  - 受影響的共通驗證群組必須通過，經共用判定器明確標記為「不適用」且對應同一提交 SHA 的群組允許放行下游打包或部署；任何驗證失敗或非預期略過均強制阻擋。
  - 每次驗證輸出結構化影響判定摘要，Java（含子模組 `**/target/surefire-reports/*.xml`、`**/target/failsafe-reports/*.xml`、`**/target/site/jacoco/`）與 Python（JUnit XML）於成功或失敗時均保留測試報告，且應執行之測試套件若收集到 0 個測試案例立即判定失敗，杜絕空跑假綠燈。

選擇「容器 Digest 固定＋鎖定檔＋共用腳本與影響範圍判定器」，而非僅在 `.gitlab-ci.yml` 補裝單一套件，是因為雙份手寫清單與無防護跳過規則極易再次漂移；代價是新增或升級 Python 測試相依時需同步更新 `requirements-ci.in` 與 `requirements-ci.lock`，並由自動化單元測試持續校驗影響判定規則。
