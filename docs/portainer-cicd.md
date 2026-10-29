# GitLab CI 與 Portainer 部署交接

GitHub 仍是開發與 PR 的主要平台；既有同步程序將合併後的提交同步至 GitLab。
測試與建置仍由 GitLab CI 執行，部署改呼叫 Portainer API，不再直接執行 `docker compose up`。

## 啟用條件與順序

1. 先審查並合併此 PR；尚未完成以下設定時，`deploy-cluster` 不會建立。
2. 確認遠端已存在 `backend-managed` Compose Stack，Endpoint ID 為 `3`。
   Stack 必須保留既有資料卷、埠、記憶體限制及 Nacos 持久化設定。
3. 在 GitLab 專案設定以下 CI/CD 變數；**不可將 Token 寫入 Git 或 PR**：

   | 變數 | 用途 |
   |------|------|
   | `PORTAINER_API_URL` | 可由 Runner 存取、結尾為 `/api` 的網址；優先使用 HTTPS 與可信任憑證 |
   | `PORTAINER_API_KEY` | 專用 API Token，設為 Masked、Protected，並使用可用的最小權限 |
   | `PORTAINER_DEPLOY_ENABLED` | 完成接管與驗證後才設為 `true`；其他值皆停用部署 |

4. 確認 GitLab 預設分支受到保護，Runner 與 Portainer 管理同一個 Docker daemon。
   本次沿用同主機本機映像，不新增 Registry，也不沿用維護時的臨時管理 Token。
5. 確認舊版 `deploy-cluster` 沒有待執行或可重試的工作，再恢復維護期間暫停的 Runner。
   已建立的舊 Pipeline 不會因 PR 合併而換成新設定，應取消舊部署工作，勿重試。
6. 對合併後的最新主分支執行一次完整 Pipeline，確認測試、建置與部署成功。

## 部署範圍與安全限制

`portainer-deploy-contract-test` 會在 CI 使用替身命令驗證更新範圍與失敗保護，
不連線正式環境。Linux 本機需先安裝 Bash、jq、Python 與 PyYAML，執行
`python scripts/test_portainer_ci.py`。

- 建置工作使用 `backend-image-build` 資源鎖，將六個應用映像標上完整提交 SHA。
- 部署工作使用 `backend-production` 資源鎖，提交必須仍為主分支最新提交。
- 僅修改既有 Stack 的六個應用映像；不新增資料庫、不重設 Nacos、不刪除資料卷。
- API 更新停用 Pull 與 Prune；中介軟體映像、網路、埠與持久化設定不變。
- 目前接管 Stack 以 JSON 格式儲存（JSON 也是合法 YAML）。若人工改成一般 YAML，
  Pipeline 會安全失敗，需先調整解析方式，不會以儲存庫中的舊 Compose 覆蓋它。
- 保留既有 Portainer `Env` 變數；私有 Stack 內容不輸出至日誌，
  不上傳為 CI Artifact，暫存檔限權並在工作結束時清除。
- 成功條件包含六個容器的實際映像 ID、Stack 所屬與執行狀態，及四個中介軟體健康檢查。
  無應用健康檢查的服務只能確認執行狀態，**不能取代端到端業務驗證**。
- API 更新逾時或驗證失敗，先在 Portainer 查看實際狀態，勿盲目重試或自動回復資料卷。

## 回復方式

先將 `PORTAINER_DEPLOY_ENABLED` 改為非 `true`，停止新部署。
使用 Portainer 將六個應用映像改回上一個成功提交 SHA；保留所有資料卷與中介軟體設定。
不要重新啟用舊版直接操作 Compose 的工作，也不要執行 `down -v` 或刪除匿名卷。
資料庫若已有新版結構或資料變動，映像回退不保證相容，需先評估再操作。
