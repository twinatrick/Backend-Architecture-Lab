# 24. IAM 單向關聯清除、權限子樹向下級聯刪除與雙層權限快取一致性治理

日期: 2026-10-29

## 狀態

Accepted

## 背景 (Context)

在 `backend-iam-service` 的近期演進與問題排查中，發現以下影響系統權限完整性與可用性的結構性缺陷：

1. **中介實體反向級聯導致骨牌式連鎖清空**：
   `com.example.BackendArchitectureLab.Entity.RoleFunction` 在指向 `Role` 與 `Function` 的 `@ManyToOne` 關聯上配置了 `cascade = CascadeType.ALL`，且 `Role` 與 `Function` 的 `@OneToMany` 亦配置了 `cascade = CascadeType.ALL`。當透過 JPA 實體移除（如 `RoleService.deleteRole` 的 `deleteAll(Iterable)`，或 `RoleFunctionRepository.deleteAllByFunctionInAndRoleIn` 等未加 `@Modifying @Query` 的 Spring Data 派生刪除）刪除任一 `RoleFunction` 時，Hibernate 會反向級聯刪除父表 `Function` 與 `Role`，再順著 `Function.roleFunctions` 連鎖刪除 `admin` 角色及其綁定的全系統所有權限。
2. **權限樹刪除未處理子孫節點與全域同名衝突**：
   `Function` 以 `parent` 欄位（儲存父節點 UUID 字串）構成三層樹狀結構（模組 $\rightarrow$ 功能 $\rightarrow$ 動作）。原 `FunctionCommandService.deleteFunction` 僅刪除目標節點本身而未向下級聯刪除子孫節點，導致產生孤兒子權限（Orphan Functions）；同時 `addFunction` 僅以 `name` 檢查全域唯一性而忽略 `parent`，導致啟動初始化 `InitAndCheckService` 在建立第二個 `"View"` 葉節點（如 `Iam:Role:View`）時拋出重複例外並中斷預設權限綁定。
3. **雙層快取（Redis L2 + Caffeine L1）於刪除與解綁時失步**：
   `RoleService` 的解綁方法與刪除操作未完整清除 Redis 的 `userPermissionsVo`、`userInfo`、`users` 快取，且未同步驅逐 `LocalPermissionValidatorImpl`（10 分鐘 Caffeine 本地快取）與 `CustomUserDetailsService`（60 秒 Caffeine 本地快取），造成角色或權限異動後 `/users/infoVo` 與權限驗證結果長期不一致。

## 決策 (Decision)

我們決定對 IAM 領域模型與資料存取層實施以下架構治理：

1. **確立單向關聯清除（Unidirectional Join Cleanup）與顯式 JPQL Bulk Delete**：
   - 徹底移除 `RoleFunction` 中 `@ManyToOne` 上的 `cascade = CascadeType.ALL`，並移除 `User`、`Role`、`Function` 主實體 `@OneToMany` 上的 `CascadeType.ALL` / `REMOVE`。
   - 所有針對 `UserRole` 與 `RoleFunction` 的刪除操作，一律透過 Repository 層標註 `@Modifying(clearAutomatically = true, flushAutomatically = true) @Query("delete from ...")` 的顯式 JPQL 批次刪除執行，確保只單向清除中介關聯表，絕不反向波及主實體。
2. **實施權限子樹向下級聯刪除（Subtree Cascade Deletion）與同層名稱唯一性（Sibling Name Uniqueness）**：
   - `FunctionCommandService.addFunction` 改為依 `(name, parent)`（將 `null` 正規化為 `""`）驗證同層兄弟節點名稱唯一性。
   - 單筆與批次刪除 `Function` 時，透過廣度優先搜尋（BFS，`findAllByParentIn` 搭配防環集合）向下收集所有子孫節點 ID，先批次清除整棵子樹的 `RoleFunction` 綁定，再一次批次刪除整棵子樹的 `Function` 節點。
3. **建立系統保留角色與預設管理員保護邊界（System Reserved Role & Principal）**：
   - 禁止刪除或更名系統核心角色（`admin`、`user`）。
   - 補齊 `DELETE /users/{id}` 端點，執行單向清除 `UserRole` 後刪除 `User`，並強制禁止刪除預設 `admin` 帳號、禁止當前登入者自刪、以及禁止拔除預設 `admin` 帳號的 `admin` 角色綁定。
4. **統一雙層快取主動驅逐機制**：
   - 於 `LocalPermissionValidator` 與 `CustomUserDetailsService` 暴露本地快取失效方法，並在所有使用者、角色、權限的新增／修改／刪除／綁定／解綁操作中，與 Redis `@CacheEvict` 同步觸發清除。

## 後果 (Consequences)

### 正面影響
- **根除權限與角色意外消失問題**：任何角色刪除、功能刪除或啟動自檢重綁定皆只單向操作中介表，徹底消除反向級聯刪除父實體的風險。
- **確保三層權限樹結構完整性**：不同模組／功能可正常建立同名動作葉節點（如 `View`、`Edit`），且刪除父節點時自動清理整棵子樹與其角色綁定，不留孤兒節點。
- **快取與資料庫強一致**：角色綁定或解綁後，Redis L2 快取與 JVM 內 Caffeine L1 快取立即同步失效，`/users/infoVo` 與 `@RequirePermission` 即時反映最新權限。

### 負面影響與取捨
- 由於移除了實體層的 `@OneToMany(cascade = CascadeType.ALL)`，所有主實體刪除流程必須在 Service 層明確呼叫中介表的 DataAccess 批次刪除方法；此取捨以少量的顯式呼叫換取完全可預測的 SQL 執行行為與領域安全性。
