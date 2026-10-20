# 後端 Profile

適用：Backend-Architecture-Lab 的 Java 微服務與 Python AI 側車。須同時讀取共通基準與下列完整來源，不以本頁摘要取代原文。

- [固定 master 完整工程規範](backend/master-rules.md)：微服務資料隔離、分層與建構子注入、權限、OpenAPI、模型與 API 契約、Python、品質、雙軌搜尋／SSE、Liquibase／樂觀鎖、快取韌性與提交資源限制。
- [固定 master 完整 Agent 指南](backend/master-agents.md)：執行環境、安全與驗證要求。
- [固定 master 完整 AI 審查合約](backend/master-review.md)：信任邊界、Finding 契約、severity／confidence 與確定性彙整門檻。

工具留在原專案：Maven／JaCoCo、`.github/ai-review/`、`.githooks/`、CI 與 Docker 設定。工具落實程度見 `docs/findings.md`，不能因尚未自動檢查就視為沒有規範。

## 已裁決一致性說明（0.1.0-draft.1）

1. **Query DTO 基底路徑**：全域聚合根搜尋軌繼承之查詢基底物件，統一以現行架構 `com.example.BackendArchitectureLab.Dto.Query.BaseSearchQuery` 為準（雙軌規範章節舊路徑筆誤已修正，程式碼維持現狀）。
2. **完全限定名稱（FQN）**：原始碼一律禁止直接在方法簽名、變數宣告或實例化物件時寫入 FQN，必須顯式 `import` 並使用 Simple Name；極窄例外僅限「同一檔案內同時使用同 Simple Name 的兩個不同類別（如 `java.util.Date` 與 `java.sql.Date` 並存）」以消除歧義。Agent 指南已與根規範同步。

本 Profile 為草案，衝突處理詳見盤點，不宣稱已完成來源規範一致化。
