-- ============================================================================
-- 壓力測試使用者職缺收藏資料 (50,000 筆)
-- 遍覽使用者隨機收藏職缺
-- ============================================================================

BEGIN;

INSERT INTO user_job_link (id, user_id, job_posting_id, user_notes, gemini_feedback, created_by, updated_by, created_time, updated_time)
SELECT
  gen_random_uuid(),
  u.id,
  jp.id,
  'Stress test note for job #' || (ROW_NUMBER() OVER ()) || ' - ' || md5(random()::text),
  'AI analysis placeholder',
  'stress-test', 'stress-test', NOW(), NOW()
FROM (
  SELECT id FROM "user"
  WHERE email LIKE 'employee%'
    AND random() < 0.3
  LIMIT 30000
) u
CROSS JOIN LATERAL (
  SELECT id FROM job_posting
  ORDER BY random()
  LIMIT (floor(random() * 3)::int + 1)
) jp
ON CONFLICT (user_id, job_posting_id) DO NOTHING;

COMMIT;
