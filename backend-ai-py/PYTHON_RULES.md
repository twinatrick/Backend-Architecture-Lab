# Python 專案語法規則（backend-ai-py）

本文件為本專案 Python 程式碼的強制語法與結構規範，所有新增或修改的 Python 檔案必須遵守。

## 1. Import 規則（最高優先）

- **所有 import 一律置於檔案最頂端**，嚴禁在函數內部、條件區塊或 try 區塊中撰寫 import。
- Import 區塊依序排列，以空行分隔三類：
  1. 標準函式庫（`import os`、`import json`）
  2. 第三方套件（`import numpy`、`from fastapi import APIRouter`）
  3. 本專案模組（`from config import settings`、`from utils.mock_detection import _is_mock`）
- 同類別內依字母順序排列。
- 例外情況：測試檔案（`tests/`）中為配合 `sys.modules` Mock 機制的局部 import，可放置於檔案後段，但需加 `# noqa: E402` 註解說明。

## 2. 命名慣例

- 函數與變數：`snake_case`（如 `sound_to_text`、`stt_input_file`）
- 類別：`PascalCase`（如 `STTService`）
- 常數：`UPPER_SNAKE_CASE`（如 `MAX_SEGMENT_SECONDS`）
- 模組內部私有函數：前綴單底線 `_`（如 `_get_whisper_model`）
- 禁止單字母變數（迴圈變數 `i` 除外）

## 3. 縮排與格式

- 一律使用 **4 個空格**縮排，禁止 Tab。
- 行長上限 100 字元。
- 字串優先使用雙引號 `"..."`；f-string 使用 `f"..."`。
- 函數定義與重要區塊之間以空行分隔，保持可讀性。

## 4. 型別與註解

- 函數參數與回傳值應加上型別註釋（如 `def sound_to_text(file_path: str, language: str) -> str:`）。
- 註解使用繁體中文。
- 不得撰寫無意義的註解，註解應解釋「為什麼」而非「做什麼」。

## 5. 單一職責與檔案規模

- 每個檔案應具備單一職責，禁止將不相關功能集中於單一檔案。
- 檔案超過約 300 行時，應評估拆分成獨立模組（如 `services/` 下按 SenseVoice、Whisper、語者分離、排版等職責拆分）。
- 模組間依賴方向：上層模組依賴下層模組，禁止循環依賴。

## 6. 錯誤處理

- 使用 `try-except` 捕捉具體例外，禁止裸 `except:`。
- 對外呼叫失敗時應有 fallback 或明確錯誤訊息輸出，不得靜默吞掉例外。
- **禁止 `except ...: pass`（靜默吞例外）**：捕捉例外後必須至少記錄一筆 `logging.warning(...)`（含例外內容），再決定是否繼續執行。
- **可選依賴（Optional Dependency）載入模式**：若某套件為可選依賴（如 Nacos 套件），禁止在函數內 import；應於**模組頂部**以 `try` / `except ImportError` 載入並設為 `None`，使用處再檢查是否為 `None`：

```python
try:
    from nacos import NacosClient
except ImportError:
    NacosClient = None
```
