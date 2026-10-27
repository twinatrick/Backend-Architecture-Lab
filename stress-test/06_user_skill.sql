-- ============================================================================
-- 壓力測試使用者技能關聯資料 (~150,000 筆)
-- 每位使用者綁定 3 項技能（Round-Robin 分配）
-- 採用確定性員工 UUID (00000000-0000-0000-0002-xxxxxxxxxxxx)，無需跨庫查詢 "user"
-- ============================================================================

BEGIN;

WITH user_ordered AS (
  SELECT
    ('00000000-0000-0000-0002-' || LPAD(n::text, 12, '0'))::uuid AS id,
    (n - 1) AS rn
  FROM generate_series(1, 50000) AS n
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
  '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', NOW(), NOW()
FROM user_skills us
ON CONFLICT (user_id, skill_id) DO NOTHING;

-- 為 Admin ('00000000-0000-0000-0000-000000000001') 綁定前 10 項技能以供個人端點壓測
INSERT INTO user_skill (id, user_id, skill_id, skill_level_id, created_by, updated_by, created_time, updated_time)
SELECT
  gen_random_uuid(),
  '00000000-0000-0000-0000-000000000001'::uuid,
  s.id,
  (SELECT sl.id FROM skill_level sl WHERE sl.skill_id = s.id ORDER BY sl.level_value LIMIT 1),
  '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', NOW(), NOW()
FROM (SELECT id FROM skill ORDER BY id LIMIT 10) s
ON CONFLICT (user_id, skill_id) DO NOTHING;

COMMIT;
