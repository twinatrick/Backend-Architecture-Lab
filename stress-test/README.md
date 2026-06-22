# 壓力測試資料與 JMeter 測試腳本

## 資料量總覽

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

## 測試分組

為降低記憶體壓力，依**服務領域**拆成 4 組獨立測試，每組只啟動必要的微服務：

| 測試腳本 | 測試範圍 | 所需服務 | 預設併發 |
|---------|---------|---------|---------|
| `test-iam.jmx` | users/search, role/get, function/get | IAM + Gateway | 500 |
| `test-competency.jmx` | skill/get, project/search, skill/personal/add, project/personal/add | IAM + Competency + Gateway | 500 |
| `test-job.jmx` | company/get, company/add, job-posting/search | IAM + Job + Gateway | 500 |
| `test-alert.jmx` | aquarkData/getColumnNameList | IAM + Alert + Gateway | 200 |

## 前置條件

### 1. 啟動基礎設施

```bash
docker compose -f compose.yaml up -d
```

確認 PostgreSQL、Redis、Nacos 等服務均正常運行。

### 2. 載入壓力測試資料

Database-per-Service 架構下，需分別載入各資料庫：

```bash
# 依序載入各服務資料庫
psql -U postgres -d iam_service -f stress-test/run_iam.sql
psql -U postgres -d competency_service -f stress-test/run_competency.sql
psql -U postgres -d job_service -f stress-test/run_job.sql
psql -U postgres -d alert_service -f stress-test/run_alert.sql
```

> 若仍需使用單一資料庫（monolith 模式），可用 `psql -U postgres -d postgres -f stress-test/run_all.sql`

### 3. 編譯專案

```bash
# 確認 Java 21 已設定後編譯
./mvnw install -DskipTests
```

## 服務啟動指令

### 各服務 JAR 路徑與 Port

| 服務 | JAR 路徑 | Port | 建議 Heap |
|------|---------|------|----------|
| IAM | `backend-iam-service/target/backend-iam-service-0.0.1-SNAPSHOT.jar` | 8002 | `-Xmx384m` |
| Competency | `backend-competency-service/target/backend-competency-service-0.0.1-SNAPSHOT.jar` | 8004 | `-Xmx512m` |
| Job | `backend-job-service/target/backend-job-service-0.0.1-SNAPSHOT.jar` | 8006 | `-Xmx384m` |
| Alert | `backend-alert-service/target/backend-alert-service-0.0.1-SNAPSHOT.jar` | 8008 | `-Xmx256m` |
| Gateway | `backend-gateway/target/backend-gateway-0.0.1-SNAPSHOT.jar` | 8000 | `-Xmx256m` |

### 啟動範例（以 IAM 為例）

```bash
# 有快取模式（預設）
java -Xmx384m -jar backend-iam-service/target/backend-iam-service-0.0.1-SNAPSHOT.jar --server.port=8002

# 無快取模式（加 --spring.cache.type=none）
java -Xmx384m -jar backend-iam-service/target/backend-iam-service-0.0.1-SNAPSHOT.jar --server.port=8002 --spring.cache.type=none
```

> **注意**：Gateway 是最後啟動的服務，等所有後端服務就緒後再啟動 Gateway。

### 測試各組所需的服務啟動對照

| 測試組 | 需啟動的服務 | 啟動指令（依序） |
|-------|-------------|----------------|
| **IAM 測試** | IAM + Gateway | `java -Xmx384m -jar backend-iam-service.jar --server.port=8002`<br>`java -Xmx256m -jar backend-gateway.jar --server.port=8000` |
| **Competency 測試** | IAM + Competency + Gateway | 加上 `java -Xmx512m -jar backend-competency-service.jar --server.port=8004` |
| **Job 測試** | IAM + Job + Gateway | 加上 `java -Xmx384m -jar backend-job-service.jar --server.port=8006` |
| **Alert 測試** | IAM + Alert + Gateway | 加上 `java -Xmx256m -jar backend-alert-service.jar --server.port=8008` |

> **不需要啟動的服務**：External API Service、AI-PY 服務在所有測試中均未使用，請勿啟動以節省記憶體。

## 執行 JMeter 測試（GUI 模式）

### 1. 開啟 JMeter GUI

執行 `jmeter.bat`（或 `jmeter`）開啟圖形介面。

### 2. 載入測試腳本

- 選單 `File` → `Open`
- 選擇對應的 `.jmx` 檔案（如 `stress-test/test-iam.jmx`）

### 3. 設定使用者變數（可選）

在 Test Plan 面板中，可調整以下變數：

| 變數 | 預設值 | 說明 |
|------|--------|------|
| `THREADS` | 500 (Alert 預設 200) | 併發使用者數 |
| `DURATION` | 300 | 測試持續時間（秒） |
| `RAMP_UP` | 30 (Alert 預設 15) | 漸進啟動時間（秒） |

### 4. 執行測試

- 點擊綠色 **Start** 按鈕（▶）
- 觀察下方 Listener 面板的即時結果

### 5. 有快取 vs 無快取

**有快取測試：**
- 服務正常啟動（Redis 正常連線）
- **保持 Warm-up Thread Group 啟用**（預設啟用），先填充快取再跑主要測試
- 執行完畢後觀察快取命中效果

**無快取測試：**
- 服務啟動時加上 `--spring.cache.type=none`
- **停用 Warm-up Thread Group**（右鍵 → Disable）
- 所有請求直接穿透到資料庫

### 6. 匯出結果

在每個 Listener 面板：

1. 右鍵點擊 Listener 名稱（如「彙總報告 - Summary Report」）
2. 選 `Save Table Data` → 選擇儲存路徑
3. 存檔為 CSV 格式（如 `測試結果-IAM-有快取.csv`）

建議匯出 Summary Report 和 Aggregate Report 兩種。

## JMeter UI 結果查看說明

### Summary Report（彙總報告）
| 欄位 | 說明 |
|------|------|
| Label | 請求名稱 |
| #Samples | 請求總數 |
| Average | 平均回應時間（ms） |
| Min | 最小回應時間（ms） |
| Max | 最大回應時間（ms） |
| Std. Dev. | 標準差 |
| Error % | 錯誤率 |
| Throughput | 吞吐量（req/s） |
| Received KB/s | 接收速率 |
| Sent KB/s | 傳送速率 |
| Avg. Bytes | 平均回應大小 |

### Aggregate Report（聚合報告）
| 欄位 | 說明 |
|------|------|
| 50% Line (Median) | 50% 請求在此時間內完成 |
| 90% Line | 90% 請求在此時間內完成 |
| 95% Line | 95% 請求在此時間內完成 |
| 99% Line | 99% 請求在此時間內完成 |
| 100% Line | 最大回應時間 |

### View Results Tree（結果樹）
- 查看單筆請求的詳細 Request/Response 內容
- 適合除錯，大量測試時建議關閉（已預設停用）

## 測試順序建議

### Round 1：有快取測試
1. 確認 Redis 容器運行中
2. 啟動各組需要的服務
3. 開啟對應 `.jmx`（Warm-up 保持啟用）
4. 點擊 Start 執行
5. 儲存結果

### Round 2：無快取測試
1. 停止所有服務（Ctrl+C）
2. 重新啟動服務，加上 `--spring.cache.type=none`
3. 開啟對應 `.jmx`（**停用** Warm-up Thread Group）
4. 點擊 Start 執行
5. 儲存結果

## 對比指標

比較有/無快取的結果時，重點關注：

| 指標 | 快取改善方向 |
|------|------------|
| Average (ms) | 有快取應大幅降低 |
| Throughput (req/s) | 有快取應顯著提升 |
| 90%/99% Line (ms) | 有快取尾端延遲應降低 |
| Error % | 不應因快取增加錯誤 |

## 測試帳號

| 帳號 | 密碼 | 角色 |
|------|------|------|
| admin@tsmc.com | password | 系統管理員 |
| hr@tsmc.com | password | HR |
| engineer@tsmc.com | password | 工程師 |
| employee000001@tsmc.com | password | 部門主管（每 50 人一位） |

## 注意事項

- 所有 SQL 檔案都使用 `ON CONFLICT`，可重複執行不報錯
- 各檔案已包含 `BEGIN/COMMIT` 交易管理
- 測試帳號密碼統一為 `password`（BCrypt）
- 測試資料約佔用 **500MB ~ 1GB** 資料庫空間
- **Warm-up Thread Group** 僅在「有快取」測試時啟用，無快取測試時請停用
- 每組測試完成後，請確實停止所有 Java 程序再啟動下一組，避免記憶體累積
