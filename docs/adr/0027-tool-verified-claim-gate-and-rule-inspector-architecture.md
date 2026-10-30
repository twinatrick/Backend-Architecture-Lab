# 27. 規則查證器多輪對話與工具確認型 AI 審查閘門架構

Date: 2026-10-30
Status: Accepted

人工主張裁定的授權資格已由 [ADR 0028](0028-explicit-approved-reviewer-identity.md)
取代為專案負責人明確核准的固定帳號身分；下列合併權限條件保留為原始決策脈絡。

部署策略已由 [ADR 0029](0029-verified-gate-direct-takeover.md) 取代為新閘門直接試行接管；
以下影子模式預設及切換前置條件保留為原始決策脈絡，不代表目前正式入口仍使用舊引擎。

## Context

在既有 AI 審查管線（`ADR-0008`、`ADR-0014`、`ADR-0016`、`ADR-0026`）中，`review.py` 將語言模型直接輸出的 `llm_findings` 與確定性靜態掃描 `static_findings` 串接後交由 `evaluate()` 判定阻擋。然而，在大規模重構或跨檔案變更（如 IAM 單向級聯刪除與四層有向無環依賴治理）中，局部 Diff 視角容易導致語言模型對確定性工程規範產生高信心誤報（Hallucinated / Unverified Blockers）：

1. **對已工具化之確定性規範產生誤判**：例如將 `String normalizedParent = parentId.trim()` 誤判為 FQN 完全限定名稱違規、將注入 `IRoleDataAccess` 誤判為直接注入 `RoleRepository`、將方法名 `addPersonalSkill` 誤判為 HTTP URL 路徑動詞後綴、或憑空捏造原始碼中不存在的私有 `@CachePut` 自調用方法。
2. **單純靠長提示詞澄清存在極限**：當規範條款眾多時，在系統提示詞中堆疊豁免說明不僅消耗大量 Token，亦無法提供模型在局部 Hunk 之外所需的檔案頂部 `import` 清單、類別階層註解（如 `@RestController`、`@RequestMapping`、`@RequirePermission`）或方法完整簽名。
3. **空靜態結果不等於特定疑點合規**：既有靜態掃描僅回傳違規清單，當模型提出某處疑點時，系統缺乏針對該特定座標與規則主張進行結構化查證、區分「已針對此處確認合規（`COMPLIANT`）」與「超出單點證明能力待人工裁定（`INCONCLUSIVE`）」並進入下一步對話澄清的閉環機制。

## Decision

我們決定將 AI 審查管線演進為「**主動確定性檢查 ＋ 按需規則查證器（Rule Inspector）多輪對話 ＋ 規則主張（Rule Claim）證據閘門**」架構，並採分階段影子執行（Shadow Execution）上線：

1. **規則登錄表（`RuleRegistry`）與專責規則查證器（`RuleInspector`）**：
   - 確定性工程規範登錄於 `RuleRegistry` 並綁定對應的 `RuleInspector` 類別與查證能力宣告（`can_prove_compliance`）。
   - 第一階段優先為 **FQN（含同名類別衝突消歧）、四層依賴與同模組歸屬、切面自調用（含方法切面註解與內部呼叫解析）、RESTful 路由動詞段、以及 Repository/DataAccess `@Transactional` 邊界** 提供專責 `RuleInspector`；其餘確定性規則由 `DeterministicFileRuleInspector` 封裝，且嚴格遵守「空靜態結果不等於合規」原則（無法單點證明合規者一律回傳 `INCONCLUSIVE`）。
   - 每個 `RuleInspector` 針對特定疑點座標與 `Review Snapshot`（鎖定 PR Base/Head SHA，並支援按需提取未修改之同模組依賴檔案）回傳四態結果之一：
     - `VIOLATION`：確認違規，附帶確定性原始碼證據。
     - `COMPLIANT`：已針對該疑點與檢查範圍完成正反向核對並確認合規。
     - `NOT_APPLICABLE`：該規則不適用於目標檔案、層級或未受本次 PR 變更影響之既有程式碼。
     - `INCONCLUSIVE`：缺少完整檔案快照或超出靜態／有限符號解析可確認範圍，保留待人工裁定。

2. **規則主張（`Rule Claim`）拆解、獨立持有與證據閉環**：
   - 模型提出的審查疑點（`Review Suspicion`）在進入查證前透過 `split_into_rule_claims()` 拆解為獨立可裁定的 `Rule Claim`（含確定性雜湊 `claim_id`），並對 `rule`、`problem`、`recommendation` 強制執行規則歸屬識別。
   - **已確認違規獨立持有**：凡經 `RuleInspector` 確認為 `VIOLATION`（或待決之 `INCONCLUSIVE`）之主張，由系統鎖定於 `locked_non_disputed` 獨立保存；後續針對爭議主張（`COMPLIANT`／`NOT_APPLICABLE`）進行多輪澄清對話時，即使模型漏列、回傳空清單或回傳格式異常，系統亦絕不丟失已鎖定之違規與待決主張。
   - 模型對 `COMPLIANT`／`NOT_APPLICABLE` 主張要求複查時，必須提供不同於原主張的新座標或新原始碼證據，不得僅原封不動重複相同主張。

3. **未工具化業務疑點之資格審核（`Suspicion Eligibility Auditor`）**：
   - 針對尚無專屬確定性工具的開放式業務邏輯或安全疑點，由 `Suspicion Eligibility Auditor` 檢驗座標存在性、證據識別字真實性（防範捏造程式碼）、以及是否屬於本次 PR 變更行或變更識別字影響鏈。捏造證據或未受影響之既有程式碼歸為 `NOT_APPLICABLE`；缺乏具體機制的模糊猜測標記為 `SPECULATION_ONLY` 並降為非阻擋建議；具體可核對之變更範圍疑點則保留為 `PENDING_HUMAN_VERDICT`。

4. **共享分層查證預算、可稽核人工裁定（`Human Claim Verdict`）與影子模式預設**：
   - 查證過程受共享 `VerificationBudget` 約束（單一主張最多複查 2 次、單批次最多 16 次工具呼叫與 32K Tokens 額外上下文、全場審查上限 15 分鐘）。
    - 結構化裁定指令（`/ai-verdict`）必須核對 PR Base/Head SHA 與主要分支（`master`）合併授權資格；無法驗證授權則 Fail-Closed 拒絕套用。同主張矛盾裁定須明確撤回或取代，不接受模型自行消除。`SUPERSEDE` 必須指定 `supersedes` 與 `replacement_decision`，不得僅刪除舊裁定。
   - **影子執行預設（Shadow Mode by Default）**：預設 `AI_REVIEW_CLAIM_GATE_MODE=shadow`，於 `batch_data` 並列產出 `shadow_verified_findings` 與 `claim_verification_log` 而不改寫正式閘門使用的 `findings`；待回歸與影子比對確認無誤後，方由環境變數切換為 `enforce` 正式啟用。

## Implementation Status（實作狀態，非完成宣告）

本 ADR 的 Accepted 表示目標設計已確認，**不表示全部能力已完成或可正式啟用**。

- 正式入口已依 ADR 0029 改為只採已查證結果；缺失查證明確阻擋，不回退舊引擎。高風險人工簽核維持獨立。
- 已實作同版完整內容讀取、局部 Patch 與完整內容隔離、證據核對、四態結果、主張保留，以及拆批共用查證預算。額外上下文以 UTF-8 位元組數保守計費，不宣稱為模型精確 Token 計數。
- Java 分析目前是保守的詞法／有限結構分析，並非完整 Java AST 或跨檔案符號解析；不支援的語法與資訊不足情境必須保留為 `INCONCLUSIVE`。
- 已接通 Groq／Gemini 原生工具傳輸、往返能力探測與跨模型保留狀態；實際 Groq 工具往返成功，Gemini 的失敗候選不得宣告支援。這不代表所有候選模型或正式工作流程均已驗收。
- 已接通 Base/Head 位置映射與局部語法規則之 Base 重驗；跨檔影響鏈無法證明時保留 `INCONCLUSIVE`，不自行豁免。固定 SHA 影子靜態檢查不讀本機 `master`；不完整呼叫端搜尋與尚未解析的引用仍明列待查證。
- 已實作裁定追加寫入入口、預設停用的受信任工作流程與唯讀消費端；遠端受保護分支及獨立憑證尚未建置，不能宣稱端到端完成。主要分支授權已支援實際觀察到的刪除／禁止強推 Ruleset；其他未支援規則仍拒絕認定。
- 上述缺口補齊並完成對抗測試、影子結果審查與使用者明確批准前，不開放正式閘門切換；不得以單元測試通過替代上線資格。
- 上線驗證與環境限制另記錄於 [驗收紀錄](../ai-review-release-validation.md)；紀錄只描述當次實測，不視為正式啟用核准。

## Consequences

- **優點**：
  - 大幅降低語言模型因局部 Diff 視野受限而對 FQN、四層架構依賴、RESTful 路由、切面自調用與 JPA Repository 規則產生的高信心誤判。
  - 確保工具確認之真實違規不受模型後續對話遺漏影響，並嚴格區分 `COMPLIANT` 與 `INCONCLUSIVE`，消除「空掃描結果誤判為合規」的漏報風險。
  - 預設以影子模式（Shadow Mode）安全驗證新舊閘門差異，維持 `ADR-0002` 受信任執行邊界與 `ADR-0012` Fail-Closed 安全承諾。
- **缺點與權衡**：
  - 跨檔案複雜多型呼叫或動態反射仍超出靜態查證範圍，將回傳 `INCONCLUSIVE` 交由人工裁定。
