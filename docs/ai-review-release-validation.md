# 規則工具式 AI Review 上線驗收紀錄

## 結論

**尚未具備正式啟用資格。** 本紀錄對應未提交工作目錄的驗證，不是部署證明。
正式入口維持只接受 `shadow`；不得以設定環境變數繞過缺口。
使用者已批准遠端部署準備，以及必要的 commit、push 與建立 PR。
合併 `master` 與正式閘門切換仍未授權；部署準備不等於正式啟用。

## 已實測範圍

- 完整 Python 測試套件，包含單元、整合介面與對抗案例；具體總數以當次 pytest 輸出為準。
  本輪最新實測為 **339 passed**；此數字不等於正式部署驗收通過。
- 對工作目錄中的 AI Review Python 原始碼與測試逐檔執行共用靜態規範檢查，
  不以舊暫存區的檢查冒充最新程式驗證。
- 檢查全部 AI Review Python 模組的靜態 import 圖無環；不宣稱能涵蓋動態 import。
- 確認既有 `check_rules` 的字串規則均能取得查證器；
  **有登錄不等於具備完整語意查證能力**，未知或未支援情境仍須保留待判定狀態。
- 裁定指令拒絕重複、未知、殘缺參數；取代裁定必須帶新判決；
  非布林授權、錯誤欄位型別、缺少審查者與損壞紀錄不得當成有效裁定。
- 待人工裁定的合資格疑點不受模型低嚴重度／低信心降級而漏擋。
- 切面查證必須核對指定呼叫行；同方法其他行的違規不能作為該行的確認證據。
- Java 大括號次序異常不能被視為完整解析；
  未找到 `@Lazy` 不代表混合的切面自調用主張已證明合規。
- 裁定紀錄分支必須啟用 `enforce_admins`，禁止管理員繞過單一寫入身分限制；
  未提供、非布林或未啟用的設定均拒絕。
- 主張識別碼使用完整 SHA-256，綁定路徑、行號、規則、證據及受信任規則程式／政策摘要。
  工具版本更新後，舊主張的人工裁定不得直接套用；指令的 `v1` 僅是紀錄格式版本。

## 正式上線的必要缺口

| 面向 | 當前狀態與通過要求 |
|---|---|
| 原生工具對話 | 已接通兩供應商傳輸、真實能力探測、共享狀態與備援；Groq 往返成功，Gemini 失敗候選不啟用。仍需正式部署的多輪／備援驗收 |
| 裁定追加紀錄 | 已接通受信任寫入工作流程、追加與 SHA 比對、唯讀消費；尚缺遠端受保護分支、獨立讀寫憑證及真實寫入／裁定回讀驗收 |
| 審核者授權 | 依 ADR 0028 改為受信任名單與 GitHub 固定 ID；僅核准負責人，不推導合併資格。身分不符、未列名、API／設定異常均拒絕 |
| 變更歸責 | 已實作固定 Base/Head 映射及局部規則 Base 重驗；無法證明的新影響鏈維持待人工查證，不能自動宣告既有債或本次違規 |
| 固定版本靜態檢查 | 影子管線只讀固定 Head SHA，不讀本機檔案；樹截斷、缺內容、期限／預算不足與未完成符號解析明列未完成，不偽造合規 |
| Java 與規則語意 | 有限詞法分析不能代替完整型別／繼承／跨檔符號解析；需補齊已承諾範圍並驗證未知情境不誤判 |
| 真實影子驗收 | 需在固定 SHA 的真實 PR 執行，逐項比對新舊結論、人工裁定與模型備援；Mock 不替代此項 |
| 正式切換 | 缺口關閉、外部驗收通過後仍須使用者明確批准；不能僅移除 `enforce` 拒絕條件 |

## 實際環境檢查

唯讀 GitHub API 確認儲存庫為 `twinatrick/Backend-Architecture-Lab`，
預設分支為 `master`；目前 CLI 帳號具有管理及推送權限。
已以實際 API 回應確認 `twinatrick` 的固定 ID 為 `130625933`，
並通過目前核准名單的正式身分驗證函式；這項裁定資格不再依賴主要分支合併權限。
名單只取受信任檢出，PR 中的名單變更不會立即取得資格；移除資格者的舊紀錄不再被消費。
名單也納入工具版本摘要；帳號改名不改固定 ID，同名帳號換 ID 不繼承授權。

- 傳統 `branches/master/protection` API 回傳 HTTP 404：`Branch not protected`。
- `rules/branches/master` 回傳 `deletion` 與 `non_fast_forward` 規則。
- 未更動 GitHub Ruleset、權限、分支、留言或遠端狀態。
- 已安全讀取本地供應商設定完成 Groq `openai/gpt-oss-120b` 真實工具往返；未輸出密鑰。
- 固定 PR #182 Head SHA `4b6132c4d9d4` 的單項歷史 FQN 誤報，經正式呼叫入口實測：
  原始 Finding 保留、影子 Finding 移除、稽核為 `COMPLIANT`。這只是單項 smoke test，**不是全 PR 或部署端到端驗收**。
- `ai-review-verdicts` 保護 API 回傳 404；未建立分支、寫入裁定或設定 Secrets。
- 儲存庫擁有者類型為個人帳號（`User`）。GitHub 官方說明的傳統
  「Restrict who can push to matching branches」只適用於組織儲存庫，
  因此目前以 `restrictions.users` 驗證單一寫入者的部署方案不能直接套用此儲存庫。
  不得省略該限制冒充保護完成；須先確定相容的保護方案及寫入身分。
  依據：[GitHub 受保護分支說明](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches#restrict-who-can-push-to-matching-branches)。
- 兩個受信任工作流程已明確建立 Python 虛擬環境並安裝固定版本的 `requests`，
  不依賴 GitHub Runner 恰好預裝套件；依賴清單亦納入工具版本摘要。
- 另對 PR #182 的固定 Base／Head、130 個變更檔執行唯讀靜態影子驗證，
  使用 90 秒驗證期限：返回四項候選與四項稽核，均為 `INCONCLUSIVE`。
  必要來源或呼叫端上下文未完成時未當成合規；此結果不是全 PR 模型對話或部署驗收。

## 工程規範快照完整性

`python scripts/check_engineering_standards.py --require-published` 在目前 Windows
工作目錄回傳失敗：七個快照文件及檢查器的原始位元組雜湊不符。
唯讀計算確認，這八項僅將 CRLF 轉成 LF 即與 manifest 的 SHA-256 相符。

已補 `.gitattributes`，避免後續檢出轉換快照換行，並要求檢查器使用 LF；
**未改寫快照、manifest 或檢查器，也未放寬雜湊驗證**。
另以 `git archive HEAD` 將原始 Git 位元組安全匯出至獨立暫存目錄，執行原檢查器仍失敗。
因此不能歸因為「僅目前工作目錄的自動換行」；Git 內的快照／檢查器位元組本身也未通過清單驗證。
`.gitattributes` 不會修復已記錄的位元組，亦不能視為此問題已解決。
依禁止直接修改快照的規範，須經集中標準庫／正式快照更新程序處理，不能自行轉換檔案或放寬檢查器。

## 已授權準備、但尚待完成的部署操作

1. 建置專用 `ai-review-verdicts` 分支，禁止刪除／強推並限制明確寫入身分，
   且啟用 `enforce_admins`，確保管理員也受保護限制。
2. 提供分離的唯讀與寫入憑證及受保護的 `ai-review-verdict-ledger` 環境；
   設定 `AI_REVIEW_VERDICT_LEDGER_WRITER`，確認讀取端無寫入憑證。
3. 程式經明確授權提交並合入受信任分支後，才啟用裁定記錄工作流程並進行真實端到端驗收。
4. 完成全 PR 影子差異核對、人工裁定重播及模型備援驗收後，再單獨審議正式切換；
   現有 `enforce` 拒絕保護不得提前移除。

## 驗證邊界

本次沒有 Java 功能變更。已使用 `.env` 指定的 Java 21，依 `.run` 設定優先規則檢查環境後，
以 `rtk ./mvnw test "-DforkCount=1" "-Dsurefire.useFile=false"` 單一 JVM 循序執行：
九個 Reactor 模組皆為 `SUCCESS`，最終 `BUILD SUCCESS`，耗時 2 分 55 秒。
第一次執行因工具 120 秒逾時而未完成，未列作通過；上述為確認無殘留程序後的完整重跑結果。
未宣稱整個儲存庫的 `check_local.py --all` 零違規。
既有基線 Java 問題與此分支的 Python 上線驗收分開記錄，不擅自修改。
