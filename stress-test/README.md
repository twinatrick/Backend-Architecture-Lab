# 壓力測試資料與 JMeter 測試腳本

> 📌 **環境與執行邊界聲明**：
> - **CI/CD 驗證（GitHub Actions）**：聚焦於單元測試與 Testcontainers E2E 整合測試（功能與分散式邏輯正確性），**不執行**極限壓力測試。
> - **本機/獨立壓測環境**：本目錄下之 JMeter 腳本與 PowerShell 自動化套件專為**本地指令驅動**設計，支援 **50 / 200 / 500** 併發階梯與四象限（快取開關 × 虛擬執行緒開關）交叉對比。

---

## 一、 資料量總覽

| 資料表 | 筆數 | 說明 |
|--------|------|------|
| role | 5 | 系統管理員、HR、部門主管、工程師、一般員工 |
| function | 22 | 6 大模組 + 16 項子功能 |
| skill | 100 | 半導體/IC設計/軟體/AI/管理等領域 |
| skill_level | ~500 | 每技能 1-5 級（初學者~權威） |
| user | 50,003 | 3 管理員 + 50,000 員工 |
| user_role | ~50,003 | 每位員工綁定一個角色 |
| project | 50,000 | 10 大類別輪換產生 |
| project_skill | ~80,000 | 專案技能關聯 |
| company | 12 | 台積電、聯發科、聯電、日月光等 |
| company_website | 12 | 各公司官網 |
| job_posting | 1,000 | 各公司職缺 |
| user_skill | ~150,000 | 員工技能關聯（每人約 3 項） |
| user_project | ~250,000 | 員工專案關聯（每人約 5 項） |
| user_project_skill | ~60,000 | 專案成員技能關聯 |
| user_job_link | ~50,000 | 員工收藏職缺 |
| aquark_data | 200,000 | 20 個站點 × 10,000 筆時間序列 |

---

## 二、 測試分組與併發階梯

### 1. 測試分組（按領域微服務隔離）
為降低本機資源負載，依**服務領域**拆成獨立測試單元，每組只啟動必要的微服務：

| 測試腳本 (JMeter / k6) | 測試範圍 | 所需服務 | 建議階梯併發 |
|---|---|---|:---:|
| `test-iam` | users/search, role/get, function/get | IAM + Gateway | 50 / 200 / 500 |
| `test-competency` | skill/get, project/search, skill/personal/add | IAM + Competency + Gateway | 50 / 200 / 500 |
| `test-job` | company/get, job-posting/search, bindings/job | IAM + Job + Gateway | 50 / 200 / 500 |
| `test-alert` | aquarkData/getColumnNameList, cache-stats | IAM + Alert + Gateway | 50 / 200 / 500 |
| `test-external` | external/config, external/usage/summary | IAM + External + Gateway | 50 / 200 / 500 |
| `test-suite` | 5 大服務綜合加權全鏈路 | 全部服務 + Gateway | 50 / 200 / 500 |

### 2. 併發階梯物理意義
* **50 併發（日常高負載）**：50 < 200（Tomcat 執行緒充足），評估快取對 HikariCP 連線池排隊的降載收益。
* **200 併發（飽和臨界分水嶺）**：200 = 200（Tomcat 平臺執行緒滿載），評估 Java 21 虛擬執行緒對 I/O 阻塞的調度優勢。
* **500 併發（極限過載壓測）**：500 > 200（平臺執行緒嚴重溢出），評估快取與虛擬執行緒雙開下的極限系統吞吐量。

---

## 三、 前置條件

### 1. 啟動基礎設施
```bash
docker compose -f compose.yaml up -d
```
確認 PostgreSQL、Redis、Kafka 等服務均正常運行。

### 2. 載入壓力測試資料
Database-per-Service 架構下，需分別載入各資料庫：
```bash
psql -U postgres -d iam_service -f stress-test/run_iam.sql
psql -U postgres -d competency_service -f stress-test/run_competency.sql
psql -U postgres -d job_service -f stress-test/run_job.sql
psql -U postgres -d alert_service -f stress-test/run_alert.sql
```

### 3. 編譯專案
```bash
./mvnw install -DskipTests
```

---

## 四、 服務啟動指令（4 大測試模式）

### 各服務 JAR 路徑與 Port

| 服務 | JAR 路徑 | Port | 建議 Heap |
|------|---------|:---:|:---:|
| IAM | `backend-iam-service/target/backend-iam-service-0.0.1-SNAPSHOT.jar` | 8002 | `-Xmx384m` |
| Competency | `backend-competency-service/target/backend-competency-service-0.0.1-SNAPSHOT.jar` | 8004 | `-Xmx512m` |
| Job | `backend-job-service/target/backend-job-service-0.0.1-SNAPSHOT.jar` | 8006 | `-Xmx384m` |
| Alert | `backend-alert-service/target/backend-alert-service-0.0.1-SNAPSHOT.jar` | 8008 | `-Xmx256m` |
| Gateway | `backend-gateway/target/backend-gateway-0.0.1-SNAPSHOT.jar` | 8000 | `-Xmx256m` |

### 4 大啟動模式 CLI 對照範例（以 IAM 服務為例）

```bash
# 模式 1：【黃金組合】有快取 + 虛擬執行緒（預設最佳架構）
java -Xmx384m -jar backend-iam-service/target/backend-iam-service-0.0.1-SNAPSHOT.jar --server.port=8002

# 模式 2：【純虛擬執行緒】無快取 + 虛擬執行緒
java -Xmx384m -jar backend-iam-service/target/backend-iam-service-0.0.1-SNAPSHOT.jar --server.port=8002 --spring.cache.type=none

# 模式 3：【純快取優化】有快取 + 平臺執行緒
java -Xmx384m -jar backend-iam-service/target/backend-iam-service-0.0.1-SNAPSHOT.jar --server.port=8002 --spring.threads.virtual.enabled=false

# 模式 4：【全關基準】無快取 + 平臺執行緒
java -Xmx384m -jar backend-iam-service/target/backend-iam-service-0.0.1-SNAPSHOT.jar --server.port=8002 --spring.cache.type=none --spring.threads.virtual.enabled=false
```

> **注意**：Gateway 是最後啟動的服務，等所有後端服務就緒後再啟動 Gateway。

---

## 五、 本地超輕量零失真壓測：Grafana k6 (推薦 🚀)

> 💡 **為什麼推薦在本機使用 k6 而非 JMeter？**
> - **JMeter（JVM 多執行緒）**：每個執行緒佔用 ~1MB 記憶體，500 併發需耗費 500MB~1GB 記憶體與大量 CPU 核心排程，在同一台本機執行容易與 Spring Boot、PostgreSQL、Redis 產生**同機資源爭奪 (Resource Starvation)**，導致測出虛高的延遲毛刺。
> - **Grafana k6（Go 協程非同步架構）**：基於 Go Goroutine 與非同步事件循環，500 VUs（虛擬使用者）僅耗費 **約 20~30MB 記憶體**，CPU 消耗極低，能精準反映微服務真實處理延遲，達到 **0 本機失真**。

### 1. 安裝 k6
```bash
# Windows (winget)
winget install k6 --source winget

# Windows (Chocolatey)
choco install k6

# macOS (Homebrew)
brew install k6

# Docker 容器化執行（免安裝 CLI）
docker run --net=host -i grafana/k6 run - < stress-test/k6/test-suite.js
```

### 2. k6 壓測腳本一覽 (`stress-test/k6/`)
- `common.js`：JWT 登入認證、全域 BaseURL、標準 SLA 閾值（P95 < 50ms）與摘要統計。
- `test-iam.js`：IAM 使用者查詢、角色與功能權限樹壓測。
- `test-competency.js`：Competency 專案搜尋、技能等級矩陣壓測。
- `test-job.js`：Job 職缺與公司多條件搜尋壓測。
- `test-alert.js`：Alert 感測器指標與即時快取統計壓測。
- `test-external.js`：External API Bot 配置與使用量統計壓測。
- `test-suite.js`：全鏈路微服務混合加權壓測（35% IAM, 25% Comp, 20% Job, 10% Alert, 10% External）。

### 3. 微服務隨選生命週期壓測：On-Demand 批次腳本 (`run-ondemand-benchmarks.ps1` 🌟)
專為本地資源保護設計，**依序啟動單一微服務 -> 執行 50/200/500 階梯壓測 -> 立即銷毀釋放資源**，徹底杜絕多個服務背景常駐佔用 CPU 與執行緒：

```powershell
# 執行全量 5 大微服務隨選生命週期階梯壓測 (50, 200, 500 VUs)
.\stress-test\run-ondemand-benchmarks.ps1 -ConcurrencyLevels 50, 200, 500 -Duration 10
```

### 4. 一鍵常駐全自動矩陣壓測：Master 批次腳本 (`run-all-benchmarks.ps1`)
支援**一次填寫多個併發階梯（預設 `50, 200, 500`）**，全自動依序壓測已啟動之微服務，並在終端機與檔案產出即時對照矩陣報告（Markdown & CSV）：

```powershell
# 1. 預設一鍵跑完全部微服務在 50, 200, 500 併發階梯 (有快取 + 虛擬執行緒)
.\stress-test\run-all-benchmarks.ps1

# 2. 自訂併發階梯 (例如 50 與 100) 並只測試 Competency 服務
.\stress-test\run-all-benchmarks.ps1 -Service competency -ConcurrencyLevels 50, 100

# 3. 自動對比「有快取 vs 無快取」在 50, 200, 500 階梯下的全鏈路表現
.\stress-test\run-all-benchmarks.ps1 -Service suite -CacheMode both -ConcurrencyLevels 50, 200, 500

# 4. 全象限極限對比 (有/無快取 × 虛擬/平臺執行緒 × 50/200/500 VUs)
.\stress-test\run-all-benchmarks.ps1 -Service all -CacheMode both -ThreadModel both
```

### 5. 單輪指定條件壓測腳本 (`run-k6-stress.ps1`)
```powershell
# 1. 執行 50 併發 (日常高負載，有快取 + 虛擬執行緒)
.\stress-test\run-k6-stress.ps1 -VUs 50 -Duration 30

# 2. 執行 200 併發 (Tomcat 平臺執行緒飽和分水嶺)
.\stress-test\run-k6-stress.ps1 -VUs 200 -Duration 30

# 3. 執行 500 併發 (極限過載壓測)
.\stress-test\run-k6-stress.ps1 -VUs 500 -Duration 60

# 4. 執行無快取對照組
.\stress-test\run-k6-stress.ps1 -VUs 200 -WithCache $false

# 5. 指定微服務單元 (如 iam, competency, job, alert, external, suite 或 all)
.\stress-test\run-k6-stress.ps1 -Scenario iam -VUs 50
```

---

## 六、 本地 JMeter 自動化壓測腳本執行 (PowerShell)

專案亦保留標準 JMeter 壓測腳本，支援自訂併發量與一鍵產生聚合對照報告：

```powershell
# 1. 執行 200 併發（有快取模式）
.\stress-test\run-with-cache.ps1 -Threads 200 -Duration 60

# 2. 執行 50 併發（日常高負載，有快取模式）
.\stress-test\run-with-cache.ps1 -Threads 50 -Duration 60

# 3. 執行 200 併發（無快取模式對照）
.\stress-test\run-without-cache.ps1 -Threads 200 -Duration 60

# 4. 指定平臺執行緒標記輸出
.\stress-test\run-with-cache.ps1 -Threads 200 -VirtualThreads $false
```

---

## 七、 執行 JMeter 測試（GUI 模式）

### 1. 開啟與載入
- 執行 `jmeter.bat`（或 `jmeter`）開啟圖形介面。
- 選單 `File` → `Open` 選擇對應的 `.jmx` 檔案（如 `stress-test/test-iam.jmx`）。

### 2. 設定使用者變數
在 Test Plan 面板中，可調整以下變數：

| 變數 | 預設值 | 建議設定 | 說明 |
|------|:---:|:---:|------|
| `THREADS` | 500 | `50` / `200` / `500` | 併發使用者數 |
| `DURATION` | 60 | 60 ~ 300 | 測試持續時間（秒） |
| `RAMP_UP` | 30 | 10 ~ 30 | 漸進啟動時間（秒） |

### 3. 查看關鍵指標
- **Average (ms)**：平均回應時間，有快取 + 虛擬執行緒應降至最低。
- **P99 Line (ms)**：99% 尾端延遲，反映高併發下的毛刺與排隊情況。
- **Throughput (req/s)**：每秒處理請求數（TPS）。
- **Error %**：除防禦性限流外，核心業務接口錯誤率應保持 0%。

---

## 八、 測試帳號

| 帳號 | 密碼 | 角色 |
|------|------|------|
| admin@tsmc.com | password | 系統管理員 |
| hr@tsmc.com | password | HR |
| engineer@tsmc.com | password | 工程師 |
| employee000001@tsmc.com | password | 部門主管（每 50 人一位） |

---

## 九、 注意事項

- 所有 SQL 檔案都使用 `ON CONFLICT`，可重複執行不報錯。
- 各檔案已包含 `BEGIN/COMMIT` 交易管理。
- 測試資料約佔用 **500MB ~ 1GB** 資料庫空間。
- **Warm-up Thread Group** 僅在「有快取」測試時啟用，無快取測試時請停用。
- 每組測試完成後，請確實停止所有 Java 程序再啟動下一組，避免記憶體累積。
