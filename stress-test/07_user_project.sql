-- ============================================================================
-- 壓力測試使用者專案關聯 (~200,000 筆)
-- 每位使用者參與 4 個專案（Round-Robin）+ ProjectSkill + UserProjectSkill
-- 採用確定性員工 UUID (00000000-0000-0000-0002-xxxxxxxxxxxx)，無需跨庫查詢 "user"
-- ============================================================================

BEGIN;

-- ============================================================================
-- 使用者專案關聯 (200,000 筆)
-- ============================================================================
WITH user_ordered AS (
  SELECT
    ('00000000-0000-0000-0002-' || LPAD(n::text, 12, '0'))::uuid AS id,
    (n - 1) AS rn
  FROM generate_series(1, 50000) AS n
),
project_ordered AS (
  SELECT id, ROW_NUMBER() OVER (ORDER BY id) - 1 AS pn
  FROM project
)
INSERT INTO user_project (id, user_id, project_id, created_by, updated_by, created_time, updated_time)
SELECT
  gen_random_uuid(),
  uo.id,
  po.id,
  '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', NOW(), NOW()
FROM user_ordered uo
JOIN project_ordered po ON po.pn IN (
  (uo.rn * 4) % 50000,
  (uo.rn * 4 + 1) % 50000,
  (uo.rn * 4 + 2) % 50000,
  (uo.rn * 4 + 3) % 50000
)
ON CONFLICT (user_id, project_id) DO NOTHING;

-- 為 Admin ('00000000-0000-0000-0000-000000000001') 綁定前 5 個專案以供個人端點壓測
INSERT INTO user_project (id, user_id, project_id, created_by, updated_by, created_time, updated_time)
SELECT
  gen_random_uuid(),
  '00000000-0000-0000-0000-000000000001'::uuid,
  p.id,
  '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', NOW(), NOW()
FROM (SELECT id FROM project ORDER BY id LIMIT 5) p
ON CONFLICT (user_id, project_id) DO NOTHING;

-- ============================================================================
-- 專案技能關聯 (ProjectSkill, ~33,000 筆)
-- ============================================================================
WITH project_ordered AS (
  SELECT id, ROW_NUMBER() OVER (ORDER BY id) - 1 AS pn FROM project
),
skill_ordered AS (
  SELECT id, ROW_NUMBER() OVER (ORDER BY id) - 1 AS sn FROM skill
)
INSERT INTO project_skill (id, project_id, skill_id, skill_level_id, created_by, updated_by, created_time, updated_time)
SELECT
  gen_random_uuid(),
  po.id,
  so.id,
  (SELECT sl.id FROM skill_level sl WHERE sl.skill_id = so.id ORDER BY sl.level_value LIMIT 1),
  '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', NOW(), NOW()
FROM project_ordered po
JOIN skill_ordered so ON so.sn = po.pn % 100
WHERE po.pn % 3 != 0
ON CONFLICT (project_id, skill_id) DO NOTHING;

-- ============================================================================
-- 使用者專案技能關聯 (UserProjectSkill)
-- ============================================================================
INSERT INTO user_project_skill (id, user_id, project_id, skill_id, skill_level_id, created_by, updated_by, created_time, updated_time)
SELECT
  gen_random_uuid(),
  up.user_id,
  up.project_id,
  ps.skill_id,
  ps.skill_level_id,
  '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', NOW(), NOW()
FROM user_project up
JOIN project_skill ps ON ps.project_id = up.project_id
WHERE ('x' || substr(md5(up.user_id::text || up.project_id::text), 1, 4))::bit(16)::int < 19660
ON CONFLICT (user_id, project_id, skill_id) DO NOTHING;

COMMIT;
