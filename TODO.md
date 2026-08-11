# TODO

## Issue #33 refactor: 優化程式輸出結構，符合 SOLID / DRY / KISS / YAGNI 標準

分支：`refactor/issue-33-solify`

### 已完成
- [x] FQN → import 全限定名修正（commit 9e0dfa4）
- [x] `impl` → `Impl` 套件改名（commit 9e0dfa4）
- [x] P0-1 拆分 `ProjectService` → `ProjectCommandService` / `ProjectQueryService` / `ProjectUserBindingService` / `ProjectSkillService`
- [x] P0-2 拆分 `FunctionService` → `FunctionCommandService` / `FunctionHierarchyService` / `FunctionQueryService`（另收斂三層權限查詢為 `getFunctionByPath`）
- [x] P0-3 `UserService` 密碼編碼抽出 `encodePasswordIfNecessary`
- [x] P0-4 建立 `SearchSortPolicy` 統一排序欄位白名單與方向驗證（8 個 Service 收斂）

### 剩餘
- [ ] P0-5 重構 `AquarkDataService`（alert-service）：拆分 Query / Filter / Aggregation / Insert / Update / Cache / Mapping 職責，評估 V1~V7 版本欄位改為 `Map<Integer, Float> values`
- [ ] P1-1 STT Router 拆分（`backend-ai-py/routers/stt.py`）：STTRouter → SttService →（AudioStorage / AudioConverter / SttEngine）
- [ ] P1-2 TTS Service 拆分（`backend-ai-py/services/tts_service.py`）：TtsService / GptSovitsClient / FallbackTtsEngine / VoiceSampleProvider
- [ ] P1-3 TTS 精確 Exception Boundary（Timeout / External Service Error / HTTP Error），禁止 `except Exception: return fallback(...)` 吞掉 programming/config error
- [ ] 全部測試驗證：`rtk ./mvnw test`（全 8 模組）+ `pytest`（backend-ai-py）

### 已取消
- [x] P2-2 移除 `AlarmService` 未使用方法（`saveAlarm()` / `logAlarm()`）— 使用者決策：暫時保留

### 附註
- P1-4 `formatter_service.py`、P2-1 `common.py` 已不存在於專案中，跳過
- MapStruct Mapper expression 內 FQN（`UserMapper` / `CompanyMapper` / `JobPostingMapper` / `UserJobLinkMapper`）刻意保留，列入未來測試項目
