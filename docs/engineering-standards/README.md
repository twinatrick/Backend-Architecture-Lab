# 工程規範快照：後端正式接軌

**狀態：`0.1.0` 正式發布，已正式接軌。** 現行根目錄 `開發規範.md`、`AGENTS.md` 及審查合約與本快照保持對齊。

| 欄位 | 內容 |
| --- | --- |
| 集中權威來源 | <https://github.com/twinatrick/engineering-standards> |
| 本地集中庫 | `../../../engineering-standards`（相對本文件所在目錄） |
| 發布版本／Profile | `0.1.0`／`backend` |
| 標準庫 commit | `c882cc38ed778b099ee3fdf5f2205ade40f2a4a3` |
| 原始後端基準 | `cb0d97685b2d3b8b1aaafa0a08ba858ee31163ae` |
| 精確檔案清單 | [manifest.json](manifest.json)，逐檔列來源路徑及 SHA-256 |

## 完整本地規範

- [共通基準](standards/common.md)＋[後端 Profile](standards/backend.md)
- [後端原始規範全文](standards/backend/master-rules.md)、[原始 AGENTS 全文](standards/backend/master-agents.md)、[原始 AI 審查合約全文](standards/backend/master-review.md)
- [採用／升版／例外流程](governance/adoption.md)、[原始來源與 SHA](sources.json)、[授權狀態](LICENSES.md)
- [本專案例外](exceptions.md)

全文保留原始固定提交的內容；BaseSearchQuery 繼承路徑與 FQN 極窄例外均已獲使用者裁決並落實，本專案與集中標準一致。原文中的相對連結／執行指令以其來源專案為背景，不應在此快照子目錄直接執行。完整盤點、衝突證據與待核准提案請查集中庫 `docs/inventory.md`、`docs/findings.md`、`proposals/backlog.md`。

不要直接修改 `standards/`、`governance/` 或清單來消除檢查錯誤。正式變更回集中庫審查，再核對真實來源提交並核准升版；例外另列，不改規範原文。

## 手動唯讀驗證

從專案根目錄執行：

```powershell
python scripts/check_engineering_standards.py
python scripts/check_engineering_standards.py --require-published
```

目前一般完整性檢查與 `--require-published` **均應通過（exit code 0）**。雜湊不認證來源：若檔案與清單一起被改動仍可能通過，正式匯入／升版需另外從可信遠端驗證 commit 與發布關係。本次不接 CI／hooks、不自動更新。
