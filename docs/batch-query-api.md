# 批次查詢 API 規格

本文件說明 11 類資源的批次載入契約；端點一覽亦列於 [README](../README.md#2-完整-api-端點清單)。

## 適用端點與權限

所有端點均為 `POST`，經 Gateway 存取，需登入及對應資源的 `View` 權限。

| 服務 | Gateway 路徑 |
|---|---|
| IAM | `/api/users/batch-query` |
| IAM | `/api/role/batch-query` |
| IAM | `/api/function/batch-query` |
| Competency | `/api/skill/batch-query` |
| Competency | `/api/skill/level/batch-query` |
| Competency | `/api/project/batch-query` |
| Job | `/api/company/batch-query` |
| Job | `/api/job-posting/batch-query` |
| Job | `/api/user-job-link/batch-query` |
| External API | `/api/external/config/batch-query` |
| Alert | `/api/alertCheckLimit/batch-query` |

## 請求

JSON body 使用 `ids` 陣列，非空且單次最多 100 筆；均傳入 UUID 字串。尤其角色查詢使用角色 UUID，**不是** `ADMIN` 等角色名稱。

```json
{
  "ids": [
    "4e28569a-055e-459e-acd7-d76f014fd801",
    "550e8400-e29b-41d4-a716-446655440000"
  ]
}
```

請求型別為 `com.example.BackendArchitectureLab.Dto.Request.BatchIdsRequest`；角色端點使用 `com.example.BackendArchitectureLab.Dto.Request.BatchStringIdsRequest`，但 Service 仍會將字串轉成 UUID，不能依 DTO 名稱或舊範例推斷為角色名稱查詢。前端應在送出前去重、排除空值並檢查 UUID 格式；超過 100 筆時分批請求。

## 回應

外層為 `com.example.BackendArchitectureLab.Vo.ResponseType`，`data` 為 `com.example.BackendArchitectureLab.Vo.BatchResultVo`；`items` 與 `itemMap` 的值是各資源對應的 Vo，不是 Entity。

| `data` 欄位 | 意義 |
|---|---|
| `items` | 已找到的 Vo，依去重後的請求 ID 順序排列。 |
| `itemMap` | 以 ID 字串為鍵的 Vo 字典；前端可依 ID 直接取值。 |
| `missingIds` | 未找到的 ID 字串，不會在 `items` 中以 `null` 佔位。 |
| `totalRequested` | 去除重複及空值後的 ID 數量。 |
| `totalFound` | 實際找到的數量，即 `items` 長度。 |

例如兩筆合法 UUID 均未找到時，回應的核心欄位如下（外層 `message` 依端點而異）：

```json
{
  "code": 200,
  "data": {
    "items": [],
    "itemMap": {},
    "missingIds": [
      "4e28569a-055e-459e-acd7-d76f014fd801",
      "550e8400-e29b-41d4-a716-446655440000"
    ],
    "totalRequested": 2,
    "totalFound": 0
  }
}
```

未找到部分資料不代表整批失敗；應以 `missingIds` 呈現已刪除或不存在的項目。請求格式、認證或權限錯誤則依一般 API 錯誤處理，不應當成空結果。

## 快取與實作依據

`com.example.BackendArchitectureLab.Util.BatchCacheHelper` 先讀取細粒度快取，再將未命中 ID 批次查庫並回填；支援 `com.example.BackendArchitectureLab.Config.BatchCacheOperations` 的快取可使用批次讀寫。前端不必先逐筆查詢，也不可因查無部分 ID 而無限重試。

本文件描述批次載入，不代表所有 Vo 都含完整授權資訊。登入與權限校驗的完整權限載入路徑，應與一般使用者列表／詳情回應區分，不以列表中的權限陣列推斷使用者無任何權限。
