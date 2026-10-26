-- ============================================================================
-- 壓力測試使用者職缺收藏資料 (50,000 筆)
-- 採用確定性員工 UUID (00000000-0000-0000-0002-xxxxxxxxxxxx)，無需跨庫查詢 "user"
-- ============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_job_link_user_job ON user_job_link (user_id, job_posting_id);

BEGIN;

WITH user_subset AS (
  SELECT
    ('00000000-0000-0000-0002-' || LPAD(n::text, 12, '0'))::uuid AS user_id,
    n
  FROM generate_series(1, 25000) AS n
),
job_ordered AS (
  SELECT id, ROW_NUMBER() OVER (ORDER BY id) - 1 AS jn
  FROM job_posting
)
INSERT INTO user_job_link (id, user_id, job_posting_id, user_notes, gemini_feedback, created_by, updated_by, created_time, updated_time)
SELECT
  gen_random_uuid(),
  u.user_id,
  jp.id,
  'Stress test note for job #' || u.n || ' - ' || md5(u.n::text || jp.id::text),
  'AI analysis placeholder',
  '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', NOW(), NOW()
FROM user_subset u
JOIN job_ordered jp ON jp.jn IN (
  (u.n * 2) % 1000,
  (u.n * 2 + 1) % 1000
)
ON CONFLICT (user_id, job_posting_id) DO NOTHING;

COMMIT;
