# 部署環境變數、容器網路與存取控制設定手冊

本文件說明 `Backend-Architecture-Lab` 微服務叢集在容器化部署（`compose.prototype.yaml` / `compose.yaml`）與 CI/CD 流水線（`.gitlab-ci.yml`）中之**環境變數注入規範**、**容器名稱互連機制**以及**受控網路綁定策略（`ALLOW_HOST`）**。

> 🔒 **資安治理原則**：版本控制庫內嚴禁硬編碼任何真實外部／內網 IP、資料庫密碼或第三方 API 金鑰。所有環境相關參數均透過環境變數注入，請具備部署權限之維運人員依本手冊於 CI/CD 平台或主機環境配置。

---

## 一、受控連接埠綁定機制（僅允許本機與 Tailscale / 指定內網）

為防止容器連接埠因綁定 `0.0.0.0` 而暴露於公網或未授權區網，同時讓授權維運人員能透過 Tailscale 等虛擬專網直接檢視容器狀態（如 Loki、Nacos、MinIO、各微服務 Actuator），`compose.prototype.yaml` 採用**雙介面變數綁定**設計：

```yaml
ports:
  - "${LOCAL_BIND_IP:-127.0.0.1}:8000:8000"
  - "${ALLOW_HOST:-127.0.0.2}:8000:8000"
```

### 運作原理
1. **本機回環恆常可用 (`LOCAL_BIND_IP`)**：
   - 預設綁定 `127.0.0.1`，確保主機上的反向代理（如 Caddy）與本機健康檢查腳本始終可透過 `127.0.0.1` 存取服務。
2. **受控內網動態綁定 (`ALLOW_HOST`)**：
   - 當未注入 `ALLOW_HOST` 時，預設退回本機第二回環位址 `127.0.0.2`（屬於 `127.0.0.0/8` Loopback 網段，僅限本機存取且不與 `127.0.0.1` 發生連接埠衝突）。
   - 當維運人員於 CI/CD 環境變數設定 `ALLOW_HOST=<YOUR_TAILSCALE_IP>` 時，Docker 會自動將連接埠同時綁定至 `127.0.0.1` 與該 Tailscale 介面 IP，達成**「只允許 Tailscale 與本機存取」**的安全隔離。

---

## 二、容器名稱與內部通訊矩陣

在 `backend-architecture-lab-net` 橋接網路內，所有服務一律透過明確的**容器名稱**進行內部連線，禁止在容器內部使用 `localhost` 或 `127.0.0.1` 呼叫其他容器：

| 服務角色 | 標準容器名稱 (`container_name`) | 容器名稱覆寫變數 | 內部通訊位址與連接埠 | 宿主機受控綁定埠 |
| :--- | :--- | :--- | :--- | :--- |
| **API Gateway** | `backend-architecture-lab-gateway` | `GATEWAY_CONTAINER_NAME` | `http://backend-architecture-lab-gateway:8000` | `8000` |
| **IAM 服務** | `backend-architecture-lab-iam-service` | `IAM_CONTAINER_NAME` | `http://backend-architecture-lab-iam-service:8002` | `8002` |
| **Competency 服務** | `backend-architecture-lab-competency-service` | `COMPETENCY_CONTAINER_NAME` | `http://backend-architecture-lab-competency-service:8004` | `8004` |
| **Job 服務** | `backend-architecture-lab-job-service` | `JOB_CONTAINER_NAME` | `http://backend-architecture-lab-job-service:8006` | `8006` |
| **External API 服務** | `backend-architecture-lab-external-api-service` | `EXTERNAL_CONTAINER_NAME` | `http://backend-architecture-lab-external-api-service:8007` | `8007` |
| **Alert 服務** | `backend-architecture-lab-alert-service` | `ALERT_CONTAINER_NAME` | `http://backend-architecture-lab-alert-service:8008` | `8008` |
| **PostgreSQL 16** | `backend-architecture-lab-postgres` | `POSTGRES_CONTAINER_NAME` | `backend-architecture-lab-postgres:5432` | `5432` |
| **Redis 7** | `backend-architecture-lab-redis` | `REDIS_CONTAINER_NAME` | `backend-architecture-lab-redis:6379` | `6379` |
| **Kafka 7.2.1** | `backend-architecture-lab-kafka` | `KAFKA_CONTAINER_NAME` | `backend-architecture-lab-kafka:9092` | `9092` |
| **Zookeeper** | `backend-architecture-lab-zookeeper` | `ZOOKEEPER_CONTAINER_NAME` | `backend-architecture-lab-zookeeper:2181` | `2181` |
| **Nacos 2.5.1** | `backend-architecture-lab-nacos` | `NACOS_CONTAINER_NAME` | `backend-architecture-lab-nacos:8848` | `8848`, `9848` |
| **Loki 3.0.0** | `backend-architecture-lab-loki` | `LOKI_CONTAINER_NAME` | `http://backend-architecture-lab-loki:3100` | `3100` |
| **MinIO** | `backend-architecture-lab-minio` | `MINIO_CONTAINER_NAME` | `http://backend-architecture-lab-minio:9000` | `9000`, `9001` |

---

## 三、CI/CD 與部署環境變數設定清單

有權限之管理員請於 CI/CD 平台（如 GitLab `Settings -> CI/CD -> Variables`）或部署環境 `.env` 中配置以下變數：

### 1. 網路與主機設定變數

| 變數名稱 | 預設值 (Fallback) | 建議屬性 | 用途說明 |
| :--- | :--- | :--- | :--- |
| `ALLOW_HOST` | `127.0.0.2` | Masked: 否 | 允許外部存取容器之指定網卡 IP（請填入伺服器之 `<YOUR_TAILSCALE_IP>`） |
| `LOCAL_BIND_IP` | `127.0.0.1` | Masked: 否 | 宿主機本機回環綁定 IP |
| `TESTCONTAINERS_HOST_OVERRIDE` | `172.17.0.1` | Masked: 否 | CI 整合測試容器存取宿主機之 Docker Bridge IP |
| `AI_PY_URL` | `http://host.docker.internal:5001` | Masked: 否 | Python AI 語音服務位址（如 `http://<AI_WORKER_IP>:5001`） |
| `AI_PY_SERVICE_URL` | 同 `AI_PY_URL` | Masked: 否 | `external-api-service` 專用之 Python AI 服務位址 |

### 2. 資料庫與中介軟體認證變數

| 變數名稱 | 建議屬性 | 用途說明 |
| :--- | :--- | :--- |
| `POSTGRES_USER` | Masked: 否 | PostgreSQL 資料庫使用者名稱 |
| `POSTGRES_PASSWORD` | **Masked: 是** | PostgreSQL 資料庫密碼（所有微服務透過此變數連線） |
| `POSTGRES_DB` | Masked: 否 | PostgreSQL 預設資料庫名稱 |
| `REDIS_PASSWORD` | **Masked: 是** | Redis 認證密碼（選填，若啟用 `requirepass` 時配置） |
| `MINIO_USER` | Masked: 否 | MinIO Root User / Access Key |
| `MINIO_PASSWORD` | **Masked: 是** | MinIO Root Password / Secret Key |
| `NACOS_USERNAME` | Masked: 否 | Nacos 服務發現認證帳號（選填） |
| `NACOS_PASSWORD` | **Masked: 是** | Nacos 服務發現認證密碼（選填） |
| `GRAFANA_ADMIN_USER` | Masked: 否 | Grafana 管理員帳號 |
| `GRAFANA_ADMIN_PASSWORD` | **Masked: 是** | Grafana 管理員密碼 |

### 3. 應用程式安全金鑰與第三方 API 變數

| 變數名稱 | 建議屬性 | 用途說明 |
| :--- | :--- | :--- |
| `JWT_SECRET_USE` | **Masked: 是** | 微服務共用 JWT 簽章金鑰（須為至少 256-bit 之 Base64 字串） |
| `SUPERUSER_KEY` | **Masked: 是** | 初始化超級管理員端點 (`/api/auth/superuser`) 驗證金鑰 |
| `GEMINI_API_KEY` | **Masked: 是** | Google Gemini LLM API 金鑰 |
| `DEEPSEEK_API_KEY` | **Masked: 是** | DeepSeek LLM API 備援金鑰 |
| `GROQ_API_KEY` | **Masked: 是** | Groq LLM API 備援金鑰 |
| `GITHUB_MODELS_API_KEY` | **Masked: 是** | GitHub Models API 備援金鑰 |
| `LINE_CHANNEL_SECRET` | **Masked: 是** | LINE Bot Channel Secret |
| `LINE_CHANNEL_ACCESS_TOKEN` | **Masked: 是** | LINE Bot Channel Access Token |
| `LINE_GF_CHANNEL_SECRET` | **Masked: 是** | LINE 女友機器人 Channel Secret |
| `LINE_GF_CHANNEL_ACCESS_TOKEN` | **Masked: 是** | LINE 女友機器人 Channel Access Token |
| `LINE_DIARY_CHANNEL_SECRET` | **Masked: 是** | LINE 語音日記機器人 Channel Secret |
| `LINE_DIARY_CHANNEL_ACCESS_TOKEN` | **Masked: 是** | LINE 語音日記機器人 Channel Access Token |
| `DISCORD_GF_TOKEN` | **Masked: 是** | Discord 女友機器人 Bot Token |
| `DISCORD_DIARY_TOKEN` | **Masked: 是** | Discord 語音日記機器人 Bot Token |

---

## 四、部署後連通性與日誌驗證步驟

當設定好 `ALLOW_HOST=<YOUR_TAILSCALE_IP>` 並完成 CI/CD 部署後，可於同一 Tailscale 網段內執行以下指令驗證：

```powershell
# 設定受測主機之 Tailscale IP 變數
$TARGET_HOST = "<YOUR_TAILSCALE_IP>"

# 1. 驗證 Loki 日誌中心就緒狀態
Invoke-RestMethod -Uri "http://$TARGET_HOST:3100/ready"

# 2. 驗證 Nacos 服務註冊清單（應包含 gateway 與 5 大業務微服務）
Invoke-RestMethod -Uri "http://$TARGET_HOST:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=20"

# 3. 驗證 Gateway 健康檢查
Invoke-RestMethod -Uri "http://$TARGET_HOST:8000/actuator/health"

# 4. 查詢 Loki 內之四層遙測 (FOUR_TIER_TELEMETRY) 與慢請求日誌
$start = [DateTimeOffset]::UtcNow.AddHours(-1).ToUnixTimeMilliseconds() * 1000000
$query = [uri]::EscapeDataString('{app=~".+"} |~ "HTTP 請求耗時|FOUR_TIER_TELEMETRY"')
Invoke-RestMethod -Uri "http://$TARGET_HOST:3100/loki/api/v1/query_range?query=$query&start=$start&limit=50"
```
