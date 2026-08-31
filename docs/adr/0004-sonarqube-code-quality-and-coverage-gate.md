# 0004. SonarQube Code Quality and Test Coverage Gate Architecture

我們需要為多模組微服務架構建立標準化、自動化的靜態程式碼品質分析與測試覆蓋率品質門禁（Quality Gate），以確保軟體架構品質並防止壞味道與漏洞滲入生產環境。

## Context

專案目前包含多個 Maven 微服務模組（如 IAM、Competency、Job、Alert、External API 等），並透過 JaCoCo 設定了 80% 的測試覆蓋率基準。
然而，純 JaCoCo 檢查主要針對行覆蓋率（Line Coverage），缺乏對程式碼複雜度（Cognitive Complexity）、技術債（Technical Debt）、安全性弱點（Vulnerabilities/Security Hotspots）以及重復程式碼（Duplications）的全面持續監控與視覺化。
為了提供本地開發者即時的品質回饋，並在 CI/CD 流程中建立統一的品質防線，我們決定整合 SonarQube 靜態分析平台。

## Decision

我們決定建構整合 SonarQube 之品質分析與門禁體系：

1. **獨立容器化部署 (`docker-compose.sonarqube.yaml`)**：
   - 本地採用 `sonarqube:lts-community` 映像檔部署於 Port `9000`。
   - 保持與主業務相依基礎設施（`compose.yaml`）隔離，採獨立 Compose 檔案與具名 Volume（`sonarqube_data`, `sonarqube_extensions`, `sonarqube_logs`）確保分析歷史與配置持久化。
   - 配置 `SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true` 以相容本地開發與 Docker Desktop 環境。
2. **Maven 插件與 JaCoCo 報告整合 (`pom.xml`)**：
   - 於根目錄 `pom.xml` 引入 `org.sonarsource.scanner.maven:sonar-maven-plugin:5.0.0.4389`。
   - 配置 `sonar.java.coveragePlugin=jacoco` 與 `sonar.coverage.jacoco.xmlReportPaths`，精確指向各模組測試產出之 JaCoCo XML 報告路徑。
   - 與既有 JaCoCo 排除規則保持嚴格一致，將 `Entity`、`Mapper`、`Vo`、`Config`、`Annotation`、`Security`、`TestSupport` 及 `*Application` 等資料/設定類別排除於覆蓋率計算外。
3. **本地一鍵執行與單執行緒保護 (`run-sonar.ps1`)**：
   - 腳本自動讀取 `.env` 中的 `java-now-home` 配置 `JAVA_HOME`。
   - 自動檢測 SonarQube 伺服器健康狀態，若未啟動則自動叫起 Docker 容器並等待其就緒。
   - 強制以單一執行緒模式（`-DforkCount=1`）依序執行單元測試並產生報告，避免多核心平行測試導致 CPU 過載與本地操作卡頓。
   - 測試全數通過後自動觸發 `sonar:sonar` 並輸出 Dashboard 連結。
4. **CI/CD 工作流程整合 (`.github/workflows/ci.yml`)**：
   - 於 GitHub Actions 的 `build` 任務中，在單元測試與 JaCoCo 檢查完成後，依據 `SONAR_TOKEN` 機密的存在性條件式執行 SonarQube / SonarCloud 掃描。
   - 搭配完整 Git 歷史檢出（`fetch-depth: 0`），確保品質門禁與變更指標計算之精確度。

## Consequences

- **品質防護 (Quality Assurance)**：全面覆蓋程式碼壞味道、安全弱點、技術債與真實覆蓋率，並於 CI 與本地建立雙重品質門禁。
- **一致性 (Consistency)**：SonarQube 覆蓋率排除清單與 JaCoCo 規則完全同步，避免指標計算失真。
- **效能與資源保護 (Resource Guard)**：掃描與測試維持單執行緒限制，確保開發者本機環境流暢運作。
- **無縫整合 (Seamless Integration)**：本地開發者可隨時透過 `run-sonar.ps1` 進行一鍵分析，CI 流程亦具備無縫條件式推送能力。
