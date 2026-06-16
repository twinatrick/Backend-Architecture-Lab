-- ============================================================================
-- 壓力測試使用者技能關聯資料 (~150,000 筆)
-- 每位使用者綁定 3 項技能（Round-Robin 分配）
-- ============================================================================

BEGIN;

WITH user_ordered AS (
  SELECT id, ROW_NUMBER() OVER (ORDER BY id) - 1 AS rn
  FROM "user"
  WHERE email LIKE 'employee%'
),
skill_ordered AS (
  SELECT id, ROW_NUMBER() OVER (ORDER BY id) - 1 AS sn
  FROM skill
),
user_skills AS (
  SELECT uo.id AS user_id, so.id AS skill_id
  FROM user_ordered uo
  JOIN skill_ordered so ON so.sn IN (
    (uo.rn * 3) % 100,
    (uo.rn * 3 + 1) % 100,
    (uo.rn * 3 + 2) % 100
  )
)
INSERT INTO user_skill (id, user_id, skill_id, skill_level_id, created_by, updated_by, created_time, updated_time)
SELECT
  gen_random_uuid(),
  us.user_id,
  us.skill_id,
  (SELECT sl.id FROM skill_level sl WHERE sl.skill_id = us.skill_id ORDER BY sl.level_value LIMIT 1),
  'stress-test', 'stress-test', NOW(), NOW()
FROM user_skills us
ON CONFLICT (user_id, skill_id) DO NOTHING;

COMMIT;
