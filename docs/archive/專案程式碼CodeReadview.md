# 專案程式碼品質檢查報告

> 專案：Backend-Architecture-Lab  
> 檢查基準：SOLID、DRY、KISS、YAGNI、高內聚低耦合、Boy Scout Rule  
> 狀態：初步架構審查  
> 最後更新：2026-08-11

---

# 1. 程式碼品質標準

## 1.1 SOLID

### SRP — Single Responsibility Principle

一個類別或函式應該只負責一件事，且只有一個被修改的原因。

檢查重點：

- Service 是否同時負責多種不同業務？
- Class 是否存在多個互不相關的修改原因？
- 是否出現 God Class / God Service？
- Controller 是否包含 Business Logic？
- Service 是否直接處理 Infrastructure 細節？

---

### OCP — Open/Closed Principle

軟體實體應該對擴充開放，對修改封閉。

檢查重點：

- 新增功能是否必須修改大量既有程式？
- 是否大量使用 `if/else` 或 `switch` 判斷不同實作？
- 是否可以透過 Strategy / Factory / Polymorphism 擴充？
- 是否存在大量硬編碼的 Provider / Type 判斷？

---

### LSP — Liskov Substitution Principle

子類別必須能夠完全替換父類別，且不破壞原有功能。

檢查重點：

- 子類別是否改變父類別預期行為？
- 是否出現不合理的 `UnsupportedOperationException`？
- Override 後是否破壞原本 contract？
- 子類別是否需要大量特殊處理才能工作？

---

### ISP — Interface Segregation Principle

不應強迫客戶端依賴自己不使用的方法。

檢查重點：

- Interface 是否過度龐大？
- Implementer 是否被迫實作不需要的方法？
- 是否應該拆成多個專用 Interface？

---

### DIP — Dependency Inversion Principle

高層模組不應依賴低層模組，兩者都應依賴抽象。

檢查重點：

- Service 是否直接依賴 Repository？
- Domain/Application Layer 是否直接依賴 Infrastructure？
- 是否透過 Interface / Port 隔離實作？
- 是否可以替換外部服務而不修改核心業務邏輯？

---

# 2. 開發效率與簡潔原則

## 2.1 DRY — Don't Repeat Yourself

同一項知識應該只有一個明確且權威的實作。

檢查重點：

- 是否存在複製貼上的 Business Logic？
- 相同 validation 是否散落各處？
- 相同 mapping 是否重複？
- 相同錯誤處理是否重複？
- 相同 configuration 是否散落各 Service？
- 修改一個規則是否需要修改多個檔案？

---

## 2.2 KISS — Keep It Simple, Stupid

程式碼應保持簡單，避免不必要的抽象與過度工程。

檢查重點：

- 是否有過度複雜的流程？
- 是否存在過深的巢狀結構？
- 是否使用不必要的 Reflection？
- 是否存在過度抽象？
- 是否可以用簡單方式解決卻使用複雜設計？
- 方法是否做太多事情？

---

## 2.3 YAGNI — You Aren't Gonna Need It

不要為了未來可能需要而提前實作現在不需要的功能。

檢查重點：

- 未使用的方法
- 未使用的 Class
- 未使用的 Interface
- TODO / FIXME 但長期未實作
- 預留但沒有實際需求的功能
- 死程式碼
- 不必要的 Extension Point

---

# 3. 架構與維護標準

## 3.1 高內聚 High Cohesion

同一個 Module / Class / Service 中的功能應高度相關。

檢查重點：

- 一個 Service 是否處理多個 Domain？
- 方法之間是否沒有共同目的？
- 是否存在 God Service？
- 是否存在 `common` / `utils` 垃圾桶式 Class？

---

## 3.2 低耦合 Low Coupling

不同模組之間應降低不必要的依賴。

檢查重點：

- 一個 Service 是否依賴大量其他 Service？
- 是否直接依賴 Infrastructure？
- 修改 A 是否容易影響 B？
- 是否存在跨 Module implementation dependency？
- 是否存在 circular dependency？

---

## 3.3 Boy Scout Rule

每次修改程式碼時，應讓程式碼比修改前更乾淨。

檢查重點：

- 發現 dead code 是否刪除？
- 發現重複邏輯是否整理？
- 發現錯誤命名是否修正？
- 發現明顯 Code Smell 是否順手改善？
- 是否避免新增更多技術債？

---

# 4. 檢查結果總覽

| 原則 | 狀態 | 評價 |
|---|---|---|
| SRP | 🔴 嚴重 | 多個 Service 過度肥大 |
| OCP | 🟡 注意 | 部分功能仍透過修改既有 Service 擴充 |
| LSP | 🟢 / 未確認 | 目前沒有足夠證據判定違反 |
| ISP | 🟢 / 未確認 | 目前沒有明確違反 |
| DIP | 🟢 / 🟡 | DataAccess abstraction 整體方向良好 |
| DRY | 🔴 嚴重 | 多處存在重複邏輯 |
| KISS | 🔴 嚴重 | Service Blob、Reflection、複雜 Pipeline |
| YAGNI | 🟠 注意 | 存在 dead code / 未使用邏輯 |
| 高內聚 | 🔴 嚴重 | 多個 Service 責任過度集中 |
| 低耦合 | 🟡 注意 | 分層尚可，但部分 Service dependency 過多 |
| Boy Scout Rule | 🟠 注意 | 有明顯清理與重構空間 |

---

# 5. 問題清單

## P0 — 高優先級

---

## P0-1 ProjectService 過度肥大

### 檔案

`backend-competency-service/.../Service/impl/ProjectService.java`

### 違反原則

- SRP
- 高內聚
- KISS
- 低耦合

### 問題

ProjectService 同時負責：

- Project CRUD
- User ↔ Project Binding
- User existence validation
- Project Cache
- Project Search
- Current User Project Query
- Project Skill Query
- Personal Project
- Project Skill Mapping
- Security Context
- Transaction
- Cache Eviction
- 跨服務 User Validation

因此存在多個修改原因。

### 建議

拆成：

```text
ProjectService
ProjectQueryService
ProjectCommandService
ProjectUserBindingService
ProjectSkillService