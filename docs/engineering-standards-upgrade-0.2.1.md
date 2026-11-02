# 後端工程規範 0.2.1 升版紀錄

**狀態：採用 `0.2.1` 正式發布基準。** 本次從 `0.1.0` 升版；`0.2.0` 曾在工作目錄準備，但因集中原版檢查器第 141 行超過本專案 100 字元限制，沒有完成採用提交。集中庫另行發布 `0.2.1` 修正行長後，改用該正式版本完整匯出；未在本地修補受雜湊約束的檔案，也未新增豁免。

## 來源與核准範圍

- 集中庫：<https://github.com/twinatrick/engineering-standards>。
- 正式版本：`0.2.1`，Profile：`backend`，鎖定提交：`d208c9275519a5fac2a96f1ae8035a0ee2254f05`，見 [正式發布](https://github.com/twinatrick/engineering-standards/releases/tag/0.2.1)。
- 已透過 GitHub Release、遠端 tag 及提交中的 catalog 核對版本；Release 非草案、非預發布，catalog 為 `published`，授權、衝突及公開脫敏門禁均已通過。tag 未提供簽章認證，不以本地雜湊宣稱來源已簽署。
- 使用者在本次維護對話核准升版、先備份並處理舊快照換行差異，以及抽離 UUID 快取鍵判斷後追加至 PR #199。核准不包含修改集中庫、產品架構全面重寫或新增 CI／hooks。
- 匯出來源為固定提交的隔離 worktree，以單次 `core.autocrlf=false` 保留原始位元組，不修改全域 Git 設定；`0.2.1` 相較 `0.2.0` 修訂檢查器排版，沒有新增規範義務。暫存的 `0.2.0` 現場另有本機備份，不列為正式採用歷史。

## 舊基準保全

1. 原始現場已另行備份到本機核准的暫存目錄，含逐檔 SHA-256；該備份不納入版本控制。
2. 舊快照七份 Markdown 與舊檢查器僅有 CRLF／LF 差異；經核准後，按舊鎖定提交還原原始位元組，不改舊 manifest 雜湊。
3. 舊 `sources.json` 的 manifest 記錄對應 CRLF 位元組，與集中庫 Git blob 的 LF 位元組不同；已保留符合舊清單的原檔，沒有藉升版改寫舊清單或宣稱這是逐位元組的來源 blob。
4. 還原後的 `0.1.0` 完整性檢查通過，再將有效基準、入口說明、例外紀錄及舊檢查器保存在 [歷史目錄](engineering-standards-history/0.1.0/backend/)。歷史副本不是現行權威；原始現場備份與此已驗證基準用途不同。

## 適用要求與遷移審查

固定來源 `standards/backend/master-*.md` 與既有共通 C-01–C-09 沒有新增要求，沿用既有採用結果；本次逐項審查新增與變更部分，不宣稱重新完成所有業務程式的全面合規認證。

| 要求／變更 | 審查結果與措施 |
| --- | --- |
| C-10 ADP | Maven 模組以直接專案依賴為邊：業務模組依賴 common，並以 test scope 依賴 test-support；test-support、gateway 依賴 common，沒有反向邊。快取元件以欄位／建構子型別及靜態方法呼叫為邊，已確認並解除下述雙向依賴。Entity 型別引用與資料關聯不直接當作元件循環；JPA cascade 為另一受檢圖，目前 Skill、Project、Company 的 cascade 僅單向指向所屬子資料，沒有反向 cascade。 |
| C-11 SDP | 共用 UUID 判斷只依賴 JDK，不反向綁定快取實作或業務編排。穩定性依被依賴關係與變更波及範圍判斷，不因所在目錄為 Util／common 就推定穩定。 |
| C-12 SAP | UUID 格式判斷是無外部 I/O、無可替換策略需求的純工具，保留具體實作，不新增無需求的介面。既有 Service 介面與 Controller → Service → DataAccess → Repository 架構不變。 |
| 後端 `@Lazy`／self 禁令 | 生產 Java 去除註解後未找到 `@Lazy`；本地入口已採用全面禁令，維持事務／快取委派執行器規則。未把測試註解或文件提及當成生產注入。 |
| 授權與治理 | 保留新版授權全文、來源清單與治理文件；候選由集中匯出器按固定提交產生，獨立檢查後才受控切換。未修改固定來源原文或品質工具政策，也未接 CI／hooks。 |
| 專案例外 | 舊紀錄無正式核准例外；升版重審後仍無新增、延續或默認批准的例外，既有工具白名單不視為治理例外。 |

### 已解除的快取元件循環

原始依賴路徑：

```text
com.example.BackendArchitectureLab.Config.CachePenetrationProtectionCache
  → com.example.BackendArchitectureLab.Config.BatchRedisCacheOperations（欄位／建構子依賴）
  → com.example.BackendArchitectureLab.Config.CachePenetrationProtectionCache（呼叫 isUuidKey）
```

這是原始碼元件依賴循環，不宣稱是 Spring Bean 建立循環。改為兩者單向依賴 `com.example.BackendArchitectureLab.Util.CacheKeyValidator`，後者只依賴 JDK `Pattern`。原有 UUID 正規表示式、大小寫與 null 行為不變；新增格式邊界測試，並保留既有單筆／批次快取測試驗證 Bloom 過濾行為。

## 驗證與回復

- 候選先以自帶檢查器驗證版本、Profile、發布門禁與逐檔原始位元組；切換後以正式檢查器再次驗證。
- 正式檢查器可用以下方式只讀核對歷史基準與舊檢查器雜湊，不執行舊檢查器：

```powershell
python scripts/check_engineering_standards.py --require-published
python scripts/check_engineering_standards.py --manifest docs/engineering-standards-history/0.1.0/backend/manifest.json --checker-file docs/engineering-standards-history/0.1.0/backend/check_snapshot.py --require-published
```

- 有效 `0.2.1` 快照、歷史 `0.1.0` 基準及兩者暫存區原始位元組雜湊均通過驗證；本地規範檢查為 0 項阻擋、0 項警告。
- 完整 Maven 單執行緒測試於 `2026-11-02T00:06:43+08:00` 重新執行完成：**1,387 項，Failures: 0、Errors: 0、Skipped: 0，全部九個模組 BUILD SUCCESS**。依該次執行的各模組摘要計算，不累加舊 XML 報告，也不沿用先前測試結果。
- 手動維護的五份入口／升版文件共 29 個本地連結及程式碼圍欄檢查通過；三份固定後端來源全文與鎖定提交的原始位元組一致。差異空白檢查以單次 `cr-at-eol` 設定辨識歷史副本原有 CRLF，不改全域設定或來源內容。
- 若需回復，將本次升版作為獨立提交以一般 Git revert 流程撤回，再核對入口、有效快照及正式檢查器一致。未提交時可從歷史副本受控還原，但須連同根目錄入口一併切換；不可只換 manifest 或單份規範。回復前先保存現場，不自動回退或覆蓋其他修改。
