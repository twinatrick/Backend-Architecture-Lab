# 專案程式碼品質與安全檢測評估報告
**Project Code Quality and Security Assessment Report**

- **檢測日期**：2026-08-31
- **評估專案**：`BackendArchitectureLab` (Spring Boot 3.x / Java 21 微服務架構實驗平台)
- **有效代碼行數 (NCLOC)**：17,969 行
- **檢測工具鏈**：
  1. **SonarQube LTS Community** (靜態代碼分析、可靠性、架構味道與代碼覆蓋率閘門)
  2. **Strix AI Penetration Agent v1.5.3** (自主 AI 滲透測試、GitLeaks 機敏資訊偵測、Semgrep SAST 與 Sandbox 動態驗證)

---

## 1. 執行摘要 (Executive Summary)

本次檢測針對後端核心程式碼、微服務模組、安全過濾器與基礎設施進行全方位的靜態品質分析與動態 AI 滲透安全掃描。

| 檢測維度 | 評估指標 / 等級 | 檢測結果狀態 | 重點摘要 |
| :--- | :--- | :--- | :--- |
| **SonarQube Quality Gate** | **PASSED (OK)** | 🟢 達標通過 | 專案符合預設品質閘門門檻 |
| **安全性評級 (Security)** | **Grade A (0.0)** | 🟢 優良 | 0 個已知安全漏洞 (Vulnerabilities) |
| **維護性評級 (Maintainability)** | **Grade A (1.0)** | 🟢 優良 | 技術債務：4 天 6 小時，Code Smells：682 個 |
| **可靠性評級 (Reliability)** | **Grade D (4.0)** | 🔴 需優先修復 | 發現 **23 個 Bug**（包含 1 個 Critical、19 個 Major、3 個 Minor） |
| **單元測試覆蓋率 (Coverage)** | **54.5%** | 🟡 待補強 | 核心業務邏輯仍有部分分支未覆蓋（排除 Entity/Vo/Mapper） |
| **代碼重複率 (Duplications)** | **1.9%** | 🟢 優良 | 重複代碼塊僅佔整體 1.9%，模組抽取良好 |
| **Git 機敏資訊洩漏 (Gitleaks)** | **0 Leaks** | 🟢 安全 | Git Commit 歷史與當前工作區無 Secret/Token 外洩 |
| **AI 滲透測試 (Strix DAST)** | **0 實質漏洞** | 🟢 安全 (排除誤報) | Strix 回報之 `app.js` JWT 漏洞經深度溯源確診為 **AI 幻覺誤報** |

---

## 2. SonarQube 靜態代碼分析與 23 個 Bug 深入剖析

SonarQube 本地實例運行於 `http://localhost:9000` (帳號: `admin` / 密碼: `admin`)。  
掃描顯示專案整體的架構設計良好、無直接注入類別之安全性漏洞，但在**多執行緒控制**與**邊界空值防護**上存在 23 個可靠性缺陷，分類詳解如下：

### 2.1 Bug 分類與問題統計

```text
Bug 分類統計：
├─ java:S2142 (8 處, Major)    : 攔截 InterruptedException 後未重設執行緒中斷狀態
├─ java:S2259 (5 處, Major)    : 潛在 NullPointerException (NPE) 未防護
├─ java:S2201 (3 處, Major)    : 方法回傳值被忽略 (如 File.mkdirs())
├─ java:S2583 (3 處, Major)    : 條件表達式恆為 true 或 false
├─ java:S2184 (2 處, Major)    : 整數算術乘法溢位 (Integer Overflow)
├─ java:S2222 (1 處, Critical) : Lock 未在 finally 區塊中釋放
└─ java:S3599 (1 處, Minor)    : 使用雙括號初始化 (Double Brace Initialization)
```

---

### 2.2 核心 Bug 清單與程式碼溯源

#### 🔴 Critical: Lock 未在 `finally` 釋放 (`java:S2222`)
* **問題檔案**：分散式鎖或併發鎖定控制邏輯中。
* **風險說明**：若鎖定區間內拋出非預期 RuntimeException，該鎖將永久無法被釋放，引發系統死鎖 (Deadlock)。
* **修復方案**：一律遵循標準鎖定樣式：
  ```text
  lock.lock();
  try {
      // 業務邏輯
  } finally {
      lock.unlock();
  }
  ```

#### 🟠 Major: `InterruptedException` 未恢復中斷標誌 (`java:S2142` - 共 8 處)
* **問題位置**：
  * `com.example.BackendArchitectureLab.Crawler.SeleniumJobCrawler`
  * 爬蟲重試與非同步 Worker 處理邏輯
* **風險說明**：當 `Thread.sleep()` 被中斷時捕獲了 `InterruptedException`，但僅印出日誌而未呼叫 `Thread.currentThread().interrupt()`，導致上層的執行緒池管理員（如 ExecutorService）無法得知該任務已被取消，阻礙優雅關機 (Graceful Shutdown)。
* **修復方案**：捕獲後立即補上 `Thread.currentThread().interrupt();`。

#### 🟠 Major: 潛在 `NullPointerException` 空指標異常 (`java:S2259` - 共 5 處)
* **問題位置**：
  * `com.example.BackendArchitectureLab.Crawler.SeleniumJobCrawler`（如在 catch/retry 區塊未驗證 `lastException` 是否為 null 即直接存取其成員）。
* **風險說明**：在例外處理流程中若發生二度 NPE，將導致錯誤排查上下文丟失且程式中斷。
* **修復方案**：在存取可為空物件前使用 `Objects.nonNull()` 或 Optional 防護。

#### 🟠 Major: 忽略關鍵方法回傳值 (`java:S2201` - 共 3 處)
* **問題位置**：本地檔案或暫存目錄生成邏輯。
* **風險說明**：呼叫 `new File(...).mkdirs()` 或字串操作時未檢查 boolean 回傳值，當作業系統權限不足或磁碟空間滿載時，後續檔案寫入將失敗崩潰。
* **修復方案**：檢查建立結果，失敗時記錄日誌或拋出適當之業務異常。

#### 🟠 Major: 整數乘法溢位 (`java:S2184` - 共 2 處)
* **風險說明**：計算時間長度（如 `hours * 3600 * 1000`）時以 `int` 計算後才賦值給 `long`，當數值較大時會在賦值前發生 32-bit 整數溢位。
* **修復方案**：在計算因子前加上 `L` 後綴（例如 `hours * 3600 * 1000L`）。

---

## 3. Security Hotspots 安全熱點審查 (共 10 處)

SonarQube 標記了 10 處需進行人工架構審查的安全熱點：

| 類別 / 規則 | 所在類別 | 風險說明 | 處置建議 |
| :--- | :--- | :--- | :--- |
| **ReDoS 攻擊風險** (`java:S5852`) | `com.example.BackendArchitectureLab.Filter.InnerEndpointBlockFilter` | 正規表達式可能存在回溯爆炸問題 | 簡化 URL 比對模式或改用 `AntPathMatcher` |
| **動態 SQL 格式化** (`java:S2077`) | `com.example.BackendArchitectureLab.TestSupport.DatabaseCleaner` | 測試工具中使用字串拼接執行清庫 SQL | 此為 TestSupport 本地清庫工具，不暴露於生產，維持現狀或加上白名單校驗 |
| **弱偽隨機數** (`java:S2245`) | `com.example.BackendArchitectureLab.Config.RedisConfig` | 使用 `java.util.Random` 生成重試間隔或抖動 | 若非安全敏感 Token 生成，用於快取抖動可接受；若涉及安全請改用 `SecureRandom` |
| **控制台未受控輸出** (`java:S1148`) | <ul><li>`DiscordGfListener`</li><li>`LineGfService`</li><li>`LearnService`</li><li>`PhoneticConvertService`</li></ul> | 捕獲例外後直接呼叫 `e.printStackTrace()`，可能在容器標準輸出造成日誌混亂或洩漏內部堆疊資訊 | 全面重構為 Lombok `@Slf4j` 並使用 `log.error("...", e)` |

---

## 4. Strix AI 滲透測試與安全審計深入分析

### 4.1 掃描配置與執行成果
* **模式**：Diff-Scope 白盒與灰盒掃描 (`strix -n --scan-mode quick -t ./ --scope-mode diff --diff-base origin/master`)。
* **GitLeaks 敏感資訊掃描**：`0 leaks found`，專案無硬編碼金鑰外洩。
* **Semgrep SAST 靜態比對**：`0 findings`，無 OWASP Top 10 常見模式漏洞。

### 4.2 Strix DAST 報告誤報 (False Positive) 溯源與澄清
* **Strix 產生之疑似報告**：
  * 報告宣稱在 `app.js` 中發現 `jwt.decode()` 未驗證簽名（Algorithm Confusion / Signature Bypass）。
* **實質架構比對與溯源結果**：
  * 🔴 **確診為 AI 幻覺 / 誤報**。
  * **證據 1**：本專案為純 Java 21 / Spring Boot 3.x 企業架構，專案中根本不存在任何 `app.js` 或 Node.js 執行檔。
  * **證據 2**：本專案 JWT 解析位於 `com.example.BackendArchitectureLab.Security.JwtAuthenticationFilter` 與 `JwtAuthenticationToken`，底層採用 `org.jose4j.jwt.consumer.JwtConsumer` 進行標準 HMAC-SHA256 密碼學簽名校驗，任何偽造或篡改 Token 均會直接觸發 `InvalidJwtException` 並回傳 HTTP 401。

---

## 5. 自動化檢測工具鏈與本地維運指南

為保持專案可持續之高品質，本次已於儲存庫中完整建置本地與 CI/CD 自動化工具：

### 5.1 SonarQube 本地一鍵檢測
* **啟動與檢測指令**：
  ```powershell
  .\run-sonar.ps1
  ```
* **工作流程**：
  1. 自動驗證 Docker 守護行程與環境變數 `JAVA_NOW_HOME`。
  2. 自動啟動 `docker-compose.sonarqube.yaml`。
  3. 以單執行緒限制 (`-DforkCount=1`) 執行 JaCoCo 測試與覆蓋率收集。
  4. 執行 `./mvnw sonar:sonar` 將指標推送至本機 SonarQube Server。

### 5.2 Strix AI 本地安全掃描
* **執行指令**：
  ```powershell
  # 白盒模式 (Diff-Scope)
  .\run-strix.ps1 -Mode white -DiffBase origin/master

  # 灰盒模式 (啟動本地 Spring Boot 後針對 OpenAPI 進行動態探測)
  .\run-strix.ps1 -Mode gray -ApiDocs "http://localhost:8000/v3/api-docs"
  ```

### 5.3 CI/CD 品質與安全閘門
* `.github/workflows/ci.yml`：在 PR 與 push 時自動執行單元測試並綁定 SonarQube 檢測。
* `.github/workflows/strix-security-scan.yml`：建立具備 trusted `apikey` 保護邊界之 AI 滲透掃描工作流程。

---

## 6. 修復建議與後續改善路線圖 (Remediation Roadmap)

```text
修復優先級與路線圖：
├── [P0] 立即修復 (Reliability): 
│   ├── 修復 1 個 Lock 釋放機制 (避免 Deadlock)
│   ├── 修復 8 個 InterruptedException 執行緒中斷標誌恢復
│   └── 補強 5 個 NPE 空指標防護與 3 個 mkdirs 回傳值判斷
├── [P1] 安全熱點強化 (Security Hotspots):
│   ├── 將 4 個 Service 中的 e.printStackTrace() 替換為 Slf4j 結構化日誌
│   └── 檢視 InnerEndpointBlockFilter 正規表達式，防範潛在 ReDoS
└── [P2] 測試覆蓋率提升 (Quality Gate):
    └── 針對核心 Service 補寫單元測試與邊界測試，將覆蓋率由 54.5% 推升至 80% 以上
```

---
*報告產出時間：2026-08-31 | 評估者：OpenCode AI Code Analysis Agent*
