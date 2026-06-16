-- ============================================================================
-- 壓力測試角色功能權限綁定
-- 此腳本應在 ALL 微服務啟動完成後執行
-- （InitAndCheckService 會建立 Function，服務啟動後才會有 Function 資料）
-- 使用動態 JOIN 找出正確的 Function UUID，不依賴固定 ID
-- ============================================================================

BEGIN;

-- Step 1: 先清理已在 DB 中的錯誤中文 Function（若之前的腳本已執行過）
DELETE FROM role_function WHERE function_id IN (
  SELECT id FROM function WHERE id::text LIKE '00000000-0000-0000-0000-000000000%'
);
DELETE FROM function WHERE id::text LIKE '00000000-0000-0000-0000-000000000%';

-- Step 2: 動態綁定所有 leaf functions 到 系統管理員 角色
-- 葉節點 Function.name 為 View, Edit, EditAll, PersonalEdit, Scrape
-- 因 role_function 無 (role_id, function_id) 唯一約束，使用 NOT EXISTS 防重複
INSERT INTO role_function (id, role_id, function_id)
SELECT gen_random_uuid(), r.id, f.id
FROM role r
CROSS JOIN function f
WHERE r.name = '系統管理員'
  AND f.parent IS NOT NULL
  AND f.parent != ''
  AND f.name IN ('View', 'Edit', 'EditAll', 'PersonalEdit', 'Scrape')
  AND NOT EXISTS (
    SELECT 1 FROM role_function rf
    WHERE rf.role_id = r.id AND rf.function_id = f.id
  );

COMMIT;
