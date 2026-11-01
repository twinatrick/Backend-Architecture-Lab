# 後端 API 升級與 RESTful 路由遷移對接指引手冊 (FE Integration Guide)

本手冊專供前端開發工程師與前端 AI 使用，詳細紀錄後端 API 架構治理後的所有端點變更、欄位更名、參數規範與對接方式。

---

## 📌 核心變更總覽

1. **全面 RESTful 標準化**：淘汰過去的非標準 RPC 動詞後綴（如 `/add`, `/update`, `/delete`, `/saveUser` 等），全面遵循 HTTP Method 語意：
   - 新增：`POST /api/{resource}`
   - 修改：`PUT /api/{resource}/{id}`
   - 刪除：`DELETE /api/{resource}/{id}`
   - 詳情：`GET /api/{resource}/{id}`
   - 分頁搜尋：`POST /api/{resource}/search`
   - SSE 串流：`GET /api/{resource}/stream?chunkSize=250`
2. **DTO 欄位異常命名激進更名**：移除歷史技術債與不合語意之欄位名稱。
3. **稽核欄位格式標準化**：`createdBy` 與 `updatedBy` 全面標準化為不可變的 **UUID** 字串格式（例如 `00000000-0000-0000-0000-000000000001`）。

---

## 🔄 1. API 路由端點新舊對照表 (Endpoint Mapping)

### 1.1 專案管理 (Competency - Project)
| 業務動作 | 舊版 API (已廢除) | 新版 RESTful API (唯一標準) | HTTP Method |
| :--- | :--- | :--- | :--- |
| 新增專案 | `/api/project/add` | `/api/project` | `POST` |
| 更新專案 | `/api/project/update` (Body 含 id) | `/api/project/{id}` (Path 帶 id, Body 帶內容) | `PUT` |
| 刪除專案 | `/api/project/delete` (Body 或 Query) | `/api/project/{id}` | `DELETE` |
| 專案詳情 | `/api/project/{id}` | `/api/project/{id}` | `GET` |
| 專案分頁搜尋 | `/api/project/search` | `/api/project/search` | `POST` |

### 1.2 技能管理 (Competency - Skill)
| 業務動作 | 舊版 API (已廢除) | 新版 RESTful API (唯一標準) | HTTP Method |
| :--- | :--- | :--- | :--- |
| 新增技能 | `/api/skill/add` | `/api/skill` | `POST` |
| 更新技能 | `/api/skill/update` | `/api/skill/{id}` | `PUT` |
| 刪除技能 | `/api/skill/delete` | `/api/skill/{id}` | `DELETE` |
| 技能詳情 | `/api/skill/{id}` | `/api/skill/{id}` | `GET` |
| 技能分頁搜尋 | `/api/skill/search` | `/api/skill/search` | `POST` |

### 1.3 技能等級管理 (Competency - SkillLevel)
| 業務動作 | 舊版 API (已廢除) | 新版 RESTful API (唯一標準) | HTTP Method |
| :--- | :--- | :--- | :--- |
| 新增技能等級 | `/api/skill/level/add` | `/api/skill/level` | `POST` |
| 更新技能等級 | `/api/skill/level/update` | `/api/skill/level/{id}` | `PUT` |
| 刪除技能等級 | `/api/skill/level/delete` | `/api/skill/level/{id}` | `DELETE` |
| 依技能查等級 | `/api/skill/level/get/{skillId}` | `/api/skill/level/by-skill/{skillId}` | `GET` |
| 等級詳情 | `/api/skill/level/{id}` | `/api/skill/level/{id}` | `GET` |

### 1.4 公司管理 (Job - Company)
| 業務動作 | 舊版 API (已廢除) | 新版 RESTful API (唯一標準) | HTTP Method |
| :--- | :--- | :--- | :--- |
| 新增公司 | `/api/company/add` | `/api/company` | `POST` |
| 更新公司 | `/api/company/update` | `/api/company/{id}` | `PUT` |
| 刪除公司 | `/api/company/delete/{id}` | `/api/company/{id}` | `DELETE` |
| 公司詳情 | `/api/company/{id}` 或 `/api/company/get/{id}` | `/api/company/{id}` | `GET` |
| 公司分頁搜尋 | `/api/company/search` | `/api/company/search` | `POST` |

### 1.5 職缺管理 (Job - JobPosting)
| 業務動作 | 舊版 API (已廢除) | 新版 RESTful API (唯一標準) | HTTP Method |
| :--- | :--- | :--- | :--- |
| 新增職缺 | `/api/job-posting/add` | `/api/job-posting` | `POST` |
| 更新職缺 | `/api/job-posting/update` | `/api/job-posting/{id}` | `PUT` |
| 刪除職缺 | `/api/job-posting/delete/{id}` | `/api/job-posting/{id}` | `DELETE` |
| 職缺詳情 | `/api/job-posting/{id}` | `/api/job-posting/{id}` | `GET` |
| 職缺分頁搜尋 | `/api/job-posting/search` | `/api/job-posting/search` | `POST` |

### 1.6 使用者職缺連結 (Job - UserJobLink)
| 業務動作 | 舊版 API (已廢除) | 新版 RESTful API (唯一標準) | HTTP Method |
| :--- | :--- | :--- | :--- |
| 新增投遞紀錄 | `/api/user-job-link/add` | `/api/user-job-link` | `POST` |
| 更新投遞紀錄 | `/api/user-job-link/update` | `/api/user-job-link/{id}` | `PUT` |
| 刪除投遞紀錄 | `/api/user-job-link/delete/{id}` | `/api/user-job-link/{id}` | `DELETE` |
| 投遞詳情 | `/api/user-job-link/{id}` | `/api/user-job-link/{id}` | `GET` |
| 查詢個人投遞 | `/api/user-job-link/user/{userId}` | `/api/user-job-link/user/{userId}` | `GET` |

### 1.7 告警門檻管理 (Alert - AlertCheckLimit)
| 業務動作 | 舊版 API (已廢除) | 新版 RESTful API (唯一標準) | HTTP Method |
| :--- | :--- | :--- | :--- |
| 新增告警門檻 | `/api/alertCheckLimit/add` | `/api/alertCheckLimit` | `POST` |
| 更新告警門檻 | `/api/alertCheckLimit/update` | `/api/alertCheckLimit/{id}` | `PUT` |
| 刪除告警門檻 | `/api/alertCheckLimit/delete` | `/api/alertCheckLimit/{id}` | `DELETE` |
| 門檻詳情 | `/api/alertCheckLimit/{id}` | `/api/alertCheckLimit/{id}` | `GET` |

### 1.8 機器人設定管理 (External - BotConfig)
| 業務動作 | 舊版 API (已廢除) | 新版 RESTful API (唯一標準) | HTTP Method |
| :--- | :--- | :--- | :--- |
| 新增 Bot 設定 | `/api/external/config` | `/api/external/config` | `POST` |
| 更新 Bot 設定 | `/api/external/config/{id}` | `/api/external/config/{id}` | `PUT` |
| 刪除 Bot 設定 | `/api/external/config/{id}` | `/api/external/config/{id}` | `DELETE` |
| 設定詳情 | `/api/external/config/{id}` | `/api/external/config/{id}` | `GET` |

### 1.9 個人專案管理 (Competency - PersonalProject)
| 業務動作 | 舊版 API (已廢除) | 新版 RESTful API (唯一標準) | HTTP Method | 說明 |
| :--- | :--- | :--- | :--- | :--- |
| 新增個人專案 | `/api/project/personal/add` | `/api/project/personal` | `POST` | Body 傳入 `PersonalProjectRequest` |
| 更新個人專案 | `/api/project/personal/update/{projectId}` | `/api/project/personal/{projectId}` | `PUT` | Path 帶 `projectId`，Body 傳入 `PersonalProjectRequest` |
| 刪除個人專案 | `/api/project/personal/delete/{projectId}` | `/api/project/personal/{projectId}` | `DELETE` | Path 帶 `projectId` |
| 取得個人專案技能 | `/api/project/personal/{projectId}/skills` | `/api/project/personal/{projectId}/skills` | `GET` | 維持不變 |
| 綁定個人專案技能 | `/api/project/personal/{projectId}/skill/bind` | `/api/project/personal/{projectId}/skill/bind` | `POST` | 維持不變 |
| 更新個人專案技能等級 | `/api/project/personal/{projectId}/skill/{skillId}/level` | `/api/project/personal/{projectId}/skill/{skillId}/level` | `PUT` | 維持不變 |
| 解除個人專案技能綁定 | `/api/project/personal/{projectId}/skill/{skillId}` | `/api/project/personal/{projectId}/skill/{skillId}` | `DELETE` | 維持不變 |

### 1.10 個人技能管理 (Competency - PersonalSkill)
| 業務動作 | 舊版 API (已廢除) | 新版 RESTful API (唯一標準) | HTTP Method | 說明 |
| :--- | :--- | :--- | :--- | :--- |
| 新增個人技能 | `/api/skill/personal/add` | `/api/skill/personal` | `POST` | Body 傳入 `PersonalSkillRequest` |
| 修改個人技能 | `POST /api/skill/personal/update?skillId={skillId}` | `/api/skill/personal/{skillId}` | `PUT` | `skillId` 改為 Path 參數，Body 傳入 `PersonalSkillRequest` |
| 修改個人技能綁定等級 | `POST /api/skill/personal/update-level?skillId={skillId}` | `/api/skill/personal/{skillId}/level` | `PUT` | `skillId` 改為 Path 參數，Body 傳入 `PersonalSkillLevelRequest` |
| 解除個人技能綁定 | `POST /api/skill/personal/delete?skillId={skillId}` | `/api/skill/personal/{skillId}` | `DELETE` | `skillId` 改為 Path 參數，不需 Query String |

### 1.11 個人職缺收藏綁定 (Job - UserJobBinding)
| 業務動作 | 舊版 API (已廢除) | 新版 RESTful API (唯一標準) | HTTP Method | 說明 |
| :--- | :--- | :--- | :--- | :--- |
| 收藏（綁定）職缺 | `/api/user/bindings/job/add/{jobPostingId}` | `/api/user/bindings/job/{jobPostingId}` | `POST` | Path 帶 `jobPostingId` |
| 取消收藏職缺 | `/api/user/bindings/job/{jobPostingId}` | `/api/user/bindings/job/{jobPostingId}` | `DELETE` | 維持不變 |
| 查詢已收藏職缺列表 | `/api/user/bindings/job` | `/api/user/bindings/job` | `GET` | 維持不變 |

### 1.12 IAM 新增、更新與刪除端點 (User / Role / Function)

新增與更新不再使用 `/create`、`/saveUser`、`/add`、`/update` 或 `WithFunctions` 路徑：

| 資源 | 新增 | 更新 | 說明 |
| :--- | :--- | :--- | :--- |
| 使用者 | `POST /api/users` | `PUT /api/users/{id}` | 更新以路徑中的使用者 UUID 為準。 |
| 角色 | `POST /api/role` | `PUT /api/role/{id}` | 同步處理 `functionIds`；更新時 `null` 保留、`[]` 清空、有值則覆蓋權限綁定。 |
| 功能節點 | `POST /api/function` | `PUT /api/function/{id}` | 批次變更另使用 `POST /api/function/batch`，取代 `/saveAllFunction`。 |

刪除端點及保護邊界如下：
| 業務動作 | 新增/標準 RESTful API | HTTP Method | 行為與錯誤碼說明 |
| :--- | :--- | :--- | :--- |
| 刪除使用者 | `/api/users/{id}` | `DELETE` | 單向清除該使用者的 `UserRole` 並刪除 `User`（不會反向影響 `Role`）。<br>⚠️ 禁止刪除系統預設管理員 `admin`（回傳 `400 FORBIDDEN_SYSTEM_ADMIN`）或刪除當前登入者自身（回傳 `400 FORBIDDEN_SELF_DELETE`）。 |
| 刪除角色 | `/api/role/{id}` | `DELETE` | 單向清除該角色的 `UserRole` 與 `RoleFunction` 並刪除 `Role`（不會反向刪除 `User` 或 `Function`）。<br>⚠️ 禁止刪除系統保留角色 `admin`、`user`（回傳 `400 FORBIDDEN_SYSTEM_ROLE`）。 |
| 刪除權限節點 | `/api/function/{id}` | `DELETE` | 自動向下（BFS）收集該節點下所有第 2、3 層子孫節點，先清除關聯 `RoleFunction` 再一次刪除整棵子樹（不會反向刪除 `Role`）。 |

### 1.13 全專案 11 大實體批次查詢 API (全新支援，消滅前端 N+1)
下列 11 類資源支援 `POST /api/{resource}/batch-query` 批次載入詳情（單次最多 100 筆，回傳 `items`、`itemMap` 字典與 `missingIds`），詳細請求與回應契約見 [批次查詢 API 規格](docs/batch-query-api.md)：
| 業務實體 | 批次查詢端點 (POST) | Payload 格式 | 說明 |
| :--- | :--- | :--- | :--- |
| 使用者 | `/api/users/batch-query` | `{ "ids": ["uuid", ...] }` | 批次依 ID 獲取使用者詳情 |
| 角色 | `/api/role/batch-query` | `{ "ids": ["uuid", ...] }` | 批次依角色 UUID 獲取詳情與功能清單，不接受角色名稱 |
| 功能節點 | `/api/function/batch-query` | `{ "ids": ["uuid", ...] }` | 批次依 ID 獲取功能節點 |
| 專案 | `/api/project/batch-query` | `{ "ids": ["uuid", ...] }` | 批次依 ID 獲取專案詳情 |
| 技能 | `/api/skill/batch-query` | `{ "ids": ["uuid", ...] }` | 批次依 ID 獲取技能詳情 |
| 技能等級 | `/api/skill/level/batch-query` | `{ "ids": ["uuid", ...] }` | 批次依 ID 獲取等級詳情 |
| 公司 | `/api/company/batch-query` | `{ "ids": ["uuid", ...] }` | 批次依 ID 獲取公司詳情 |
| 職缺 | `/api/job-posting/batch-query` | `{ "ids": ["uuid", ...] }` | 批次依 ID 獲取職缺詳情 |
| 求職關聯 | `/api/user-job-link/batch-query`| `{ "ids": ["uuid", ...] }` | 批次依 ID 獲取投遞紀錄 |
| 告警門檻 | `/api/alertCheckLimit/batch-query`| `{ "ids": ["uuid", ...] }` | 批次依 ID 獲取水質告警門檻 |
| 機器人設定 | `/api/external/config/batch-query`| `{ "ids": ["uuid", ...] }` | 批次依 ID 獲取 Bot 配置 |

---

## 🏷️ 2. Request / Response 欄位更名對照表 (Field Renaming)

### 2.1 CriteriaApiFilter (感測器篩選條件)
- **影響端點**：`POST /api/aquarkData/search` 的 filters 陣列
- **現行契約**：以 `com.example.BackendArchitectureLab.DataAccess.specification.CriteriaApiFilter` 為準，不能將舊的數值上下限直接改名成比較旗標。
  | 欄位 | 型別 | 說明 |
  | :--- | :--- | :--- |
  | `columnName` | `String` | 實體欄位名稱，例如 `temperature`；不是 `column`。 |
  | `type` | `int` | `0` 字串、`1` 數值、`2` 日期、`3` 布林。 |
  | `stringValue` | `String` | 字串比對值，取代舊 `string`。 |
  | `doubleValue` | `double` | 數值門檻；上下限分別建立一個條件。 |
  | `date` | `Date` | 日期比對值。 |
  | `booleanValue` | `boolean` | 布林比對值。 |
  | `greaterThan` / `lessThan` | `boolean` | 比較方向，不是數值上下限；每個條件只選一個方向。 |
  | `equal` | `boolean` | 是否包含等於；數值條件未指定比較方向時，`true` 為等於、`false` 為不等於。 |
  | `like` | `boolean` | 字串是否使用模糊比對。 |

  多個條件以 AND 組合。無條件查詢可使用分頁 `/search` 或分塊 `/stream`；內部清單方法 `com.example.BackendArchitectureLab.Service.Impl.AquarkDataQueryService.getAquarkDataWithFilter` 不接受 `null` 或空條件，不能將此限制誤套到分頁搜尋。

### 2.2 SuperUserRequest (建立超級管理員)
- **影響端點**：`POST /api/auth/superuser`
- **欄位更名**：
  | 舊欄位名稱 (已廢除) | 新欄位名稱 (唯一標準) | 型別 | 說明 |
  | :--- | :--- | :--- | :--- |
  | `key` | `bootstrapSecret` | `String` | 管理員啟動引導金鑰 |

### 2.3 UserVo (使用者傳輸物件)
- **影響端點**：`POST /api/users`、`PUT /api/users/{id}`、`POST /api/users/search`
- **欄位更名**：
  | 舊欄位名稱 (已廢除) | 新欄位名稱 (唯一標準) | 型別 | 說明 |
  | :--- | :--- | :--- | :--- |
  | `roleArr` | `roles` | `List<String>` | 角色 ID 清單 (UUID 字串陣列) |

### 2.4 FunctionTransVo (功能樹批次變更)
- **影響端點**：`POST /api/function/batch`
- **欄位更名**：
  | 舊欄位名稱 (已廢除) | 新欄位名稱 (唯一標準) | 型別 | 說明 |
  | :--- | :--- | :--- | :--- |
  | `deleteFunction` | `toDelete` | `List<FunctionVo>` | 欲刪除的功能清單 |
  | `saveMainFunction` | `toSave` | `List<FunctionVo>` | 欲儲存的主要功能清單 |
  | `saveFunctionNewChild`| `toSaveChildren`| `List<FunctionVo>` | 欲儲存的子功能清單 |

### 2.5 RoleVo (角色列表與詳情)
- **回應型別**：`com.example.BackendArchitectureLab.Vo.RoleVo`，不再使用舊名稱 `RoleOutVo`。
- **影響端點**：`POST /api/role/search` 與 `GET /api/role/{id}`
- **欄位說明**：
  - `POST /api/role/search`（列表）：`functionIds` 永遠為空陣列 `[]`，請讀取 `functionCount`（整數）直接顯示權限數量！
  - `GET /api/role/{id}`（詳情）：回傳完整的 `functionIds` 陣列供權限編輯勾選。

---

## 💡 3. 前端常見調用模式重構範例 (TypeScript / Axios)

### 範例 1：更新專案 (PUT RESTful)
```typescript
// 舊寫法 (已廢除)
// await api.post('/api/project/update', { id: project.id, name: '新專案' });

// 新標準寫法
await api.put(`/api/project/${project.id}`, {
  name: '新專案',
  description: '專案說明',
  // 注意：URL 已經帶了 id，Payload 內可不帶或一致
});
```

### 範例 2：刪除職缺 (DELETE RESTful)
```typescript
// 舊寫法 (已廢除)
// await api.post('/api/job-posting/delete', { id: jobId });

// 新標準寫法
await api.delete(`/api/job-posting/${jobId}`);
```

### 範例 2.1：個人技能新增、修改、改等級與解除綁定 (PersonalSkill RESTful)
```typescript
// 1. 新增個人技能
// 舊寫法 (已廢除): await api.post('/api/skill/personal/add', payload);
await api.post('/api/skill/personal', payload);

// 2. 修改個人技能主資料 (skillId 改放 Path)
// 舊寫法 (已廢除): await api.post(`/api/skill/personal/update?skillId=${skillId}`, payload);
await api.put(`/api/skill/personal/${skillId}`, payload);

// 3. 修改個人技能綁定等級 (skillId 改放 Path)
// 舊寫法 (已廢除): await api.post(`/api/skill/personal/update-level?skillId=${skillId}`, { skillLevelId });
await api.put(`/api/skill/personal/${skillId}/level`, { skillLevelId });

// 4. 解除個人技能綁定 (skillId 改放 Path)
// 舊寫法 (已廢除): await api.post(`/api/skill/personal/delete?skillId=${skillId}`);
await api.delete(`/api/skill/personal/${skillId}`);
```

### 範例 2.2：個人專案與職缺收藏 (PersonalProject & UserJobBinding RESTful)
```typescript
// 個人專案：新增 / 修改 / 刪除
await api.post('/api/project/personal', payload);
await api.put(`/api/project/personal/${projectId}`, payload);
await api.delete(`/api/project/personal/${projectId}`);

// 職缺收藏：綁定 / 取消綁定
// 舊寫法 (已廢除): await api.post(`/api/user/bindings/job/add/${jobPostingId}`);
await api.post(`/api/user/bindings/job/${jobPostingId}`);
await api.delete(`/api/user/bindings/job/${jobPostingId}`);
```

### 範例 3：Aquark 數值範圍搜尋 (CriteriaApiFilter)
```typescript
// 舊寫法 (已廢除)
// filters: [{ column: 'temp', large: 20.5, small: 30.0 }]

// 新標準寫法
const query = {
  page: 0,
  size: 20,
  sortBy: 'trans_time',
  sortDir: 'desc',
  filters: [
    {
      columnName: 'temperature',
      type: 1,
      doubleValue: 20.5,
      greaterThan: true,
      equal: true
    },
    {
      columnName: 'temperature',
      type: 1,
      doubleValue: 30.0,
      lessThan: true,
      equal: true
    }
  ]
};
const res = await api.post('/api/aquarkData/search', query);
```

此範例搜尋 `20.5 ≤ temperature ≤ 30.0`。時間排序可用 `trans_time` 或別名 `transTime`，不可使用 `time`。

---

## 🛡️ 4. 稽核 ID 與建立者說明
- 全系統所有 Entity 的 `createdBy` 與 `updatedBy` 均為使用者的不可變 **UUID**（如 `00000000-0000-0000-0000-000000000001`）。
- 前端若需顯示建立者姓名或帳號，可透過全局使用者快取 Map 依 `userId` 進行 O(1) 反查渲染。
