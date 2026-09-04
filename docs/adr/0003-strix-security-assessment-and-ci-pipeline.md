# 0003. Strix AI Security Assessment and CI Pipeline Architecture

我們需要為後端微服務架構建立自動化、具備真實漏洞 PoC 驗證能力之 AI 滲透測試與安全審查流程，同時確保 CI/CD 執行效率與受信任金鑰邊界。

## Context

後端微服務系統包含 IAM 認證、職能管理、任務調度、警報及外部整合等多個業務模組，存在複雜之 RBAC 權限控管、分散式事件與公開/內部 API 端點。
傳統的靜態程式碼分析（SAST）往往產生大量假陽性（False Positives），且無法驗證漏洞是否能被實際利用（例如垂直/水平越權 IDOR、認證繞過或邏輯漏洞）。
為了在開發與 CI/CD 階段早期發現並驗證高危安全漏洞，我們引進開源 AI 滲透測試代理工具 Strix（`usestrix/strix`）。

## Decision

我們決定建構整合 Strix 之安全審查體系：

1. **本地工具鏈與隔離沙箱**：
   - 本地開發環境採用 `uv tool install strix-agent` 進行獨立虛擬環境隔離安裝，便於維護與版本升級。
   - 預載官方 Docker 沙箱映像檔 `ghcr.io/usestrix/strix-sandbox:1.3.0`，所有漏洞驗證與 PoC 執行均在隔離沙箱中完成。
2. **CI/CD 受信任安全工作流程與金鑰池調度 (`.github/workflows/strix-security-scan.yml`)**：
   - 建立獨立之安全審查工作流程，受 GitHub Environment（`apikey`）保護，並嚴格遵循受信任邊界（Trust Boundary）原則：僅允許 `push: branches: [master, main]`（合併後基線掃描）與 `workflow_dispatch`（手動指定目標調度），避免在不可信 PR 生命週期中暴露金鑰。
   - 引入金鑰池調度器（`.github/scripts/strix_key_runner.py`），自動探索環境中的多把 Gemini / Groq API 金鑰並進行輕量健康檢查（探測是否遭 Google 標記洩漏或暫停），動態挑選可用金鑰並在 CI 日誌中遮蔽（`::add-mask::`），同時以全陣列參數（`shell=False`）呼叫 Strix 執行檔以徹底杜絕指令注入。
   - 實施安全門禁（Security Gate）：當發現具備已驗證 Exploit PoC 且嚴重度達 `HIGH` 或 `CRITICAL` 時，發出告警並阻擋流程。
3. **黑盒、白盒與灰盒三模式支援與本地一鍵腳本 (`run-strix.ps1`)**：
   - **白盒 (White-Box)**：直接針對本地或倉庫原始碼進行靜態與動態結合分析。
   - **黑盒 (Black-Box)**：指向本地運行的後端服務（`http://localhost:8000`），並搭配 OpenAPI 聚合規格（`/v3/api-docs`）進行精準路由測試。
   - **灰盒 (Gray-Box)**：透過 `run-strix.ps1` 自動向 `/api/v1/auth/signup` 或 `/api/v1/auth/login` 取得測試 JWT Access Token，模擬登入使用者進行 IDOR 與越權漏洞探測。
4. **API 規格靶機配對 (API Spec Target Pairing)**：
   - 結合 Springdoc OpenAPI 聚合端點，在黑盒/灰盒掃描時自動配對 API 規格，避免盲目爬蟲，確保所有微服務宣告路由獲得全面測試。

## Consequences

- **安全性 (Security)**：漏洞報告均附帶可重現的 Exploit PoC 與修復建議，有效杜絕假陽性；金鑰嚴格隔離於受信任環境。
- **效率 (Efficiency)**：PR 階段採用 Diff-Scope 快速掃描，大幅減少 LLM API 呼叫成本與 CI 等待時間。
- **靈活性 (Flexibility)**：開發者可於本地使用 `run-strix.ps1` 針對不同情境（白/黑/灰盒）隨時發起即時安全評估。
