-- ============================================================================
-- 壓力測試感測器資料 (200,000 筆)
-- 模擬 20 個站點 × 10,000 筆時間序列資料
-- ============================================================================

BEGIN;

-- ============================================================================
-- 感測器站點資料 (station_id 從 ST01 ~ ST20)
-- 每個站點產生 10,000 筆紀錄，時間從 2024-01-01 開始
-- ============================================================================
INSERT INTO aquark_data (
  id, station_id, csq, trans_time, rain_d, moisture, temperature,
  echo, water_speed_aquark, is_peak,
  v1, v2, v3, v4, v5, v6, v7,
  created_by, updated_by, created_time, updated_time
)
SELECT
  gen_random_uuid(),
  'ST' || LPAD(((n - 1) / 10000 + 1)::text, 2, '0'),
  CASE floor(random() * 5)::int
    WHEN 0 THEN '20' WHEN 1 THEN '25' WHEN 2 THEN '30' WHEN 3 THEN '15' ELSE '10'
  END,
  TIMESTAMP '2024-01-01 00:00:00' + interval '1 hour' * ((n - 1) % 10000) + interval '1 day' * ((n - 1) / 10000),
  round((random() * 100)::numeric, 2),
  round((60 + random() * 40)::numeric, 2),
  round((20 + random() * 15)::numeric, 2),
  round((random() * 10)::numeric, 2),
  round((random() * 3)::numeric, 2),
  random() > 0.95,
  round((random() * 12)::numeric, 3),
  round((random() * 12)::numeric, 3),
  round((random() * 12)::numeric, 3),
  round((random() * 12)::numeric, 3),
  round((random() * 12)::numeric, 3),
  round((random() * 12)::numeric, 3),
  round((random() * 12)::numeric, 3),
  'stress-test', 'stress-test', NOW(), NOW()
FROM generate_series(1, 200000) AS n;

COMMIT;
