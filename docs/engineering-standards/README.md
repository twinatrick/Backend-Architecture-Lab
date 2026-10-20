# 工程規範快照：後端接軌預覽

**狀態：草案，未發布，未正式採用。** 現行根目錄 `開發規範.md`、AGENTS 及審查合約仍有效。

| 欄位 | 內容 |
| --- | --- |
| 預定集中來源 | <https://github.com/twinatrick/engineering-standards>（尚未建立公開遠端） |
| 本地集中庫 | `../../../engineering-standards`（相對本文件所在目錄） |
| 草案版本／Profile | `0.1.0-draft.1`／`backend` |
| 標準庫 commit | `null`；尚未 commit，不填虛構 SHA，也不是 master／latest 鎖定 |
| 原始後端 master | `cb0d97685b2d3b8b1aaafa0a08ba858ee31163ae` |
| 精確檔案清單 | [manifest.json](manifest.json)，逐檔列來源路徑及 SHA-256 |

## 完整本地規範

- [共通基準](standards/common.md)＋[後端 Profile](standards/backend.md)
- [後端原始規範全文](standards/backend/master-rules.md)、[原始 AGENTS 全文](standards/backend/master-agents.md)、[原始 AI 審查合約全文](standards/backend/master-review.md)
- [採用／升版／例外流程](governance/adoption.md)、[原始來源與 SHA](sources.json)、[授權狀態](LICENSES.md)
- [本專案例外](exceptions.md)

全文保留原始固定提交的內容；其中舊路徑及 FQN 措辭仍有待裁決差異。原文中的相對連結／執行指令以其來源專案為背景，不應在此快照子目錄直接執行。完整盤點、衝突證據與待核准提案請查集中庫 `docs/inventory.md`、`docs/findings.md`、`proposals/backlog.md`。

不要直接修改 `standards/`、`governance/` 或清單來消除檢查錯誤。正式變更回集中庫審查，再核對真實來源提交並核准升版；例外另列，不改規範原文。

## 手動唯讀驗證

從專案根目錄執行：

```powershell
python scripts/check_engineering_standards.py
python scripts/check_engineering_standards.py --require-published
```

第一個只驗證本地完整性；第二個在目前草案階段**應失敗**。雜湊不認證來源：若檔案與清單一起被改動仍可能通過，正式匯入／升版需另外從可信遠端驗證 commit 與發布關係。本次不接 CI／hooks、不自動更新。

正式採用前仍需：來源散布／授權確認、衝突裁決、公開前人工脫敏、真實集中庫 commit 與明確採用核准。不得以本預覽取代現行規範。
