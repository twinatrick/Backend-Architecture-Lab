-- ============================================================================
-- 壓力測試感測器資料 (200,000 筆)
-- 模擬 20 個站點 × 10,000 筆時間序列資料，確定性 UUID 支援冪等重跑
-- ============================================================================

BEGIN;

INSERT INTO aquark_data (
  id, station_id, csq, trans_time, rain_d, moisture, temperature,
  echo, water_speed_aquark, is_peak,
  v1, v2, v3, v4, v5, v6, v7,
  created_by, updated_by, created_time, updated_time
)
SELECT
  ('00000000-0000-0000-0008-' || LPAD(n::text, 12, '0'))::uuid,
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
  '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', NOW(), NOW()
FROM generate_series(1, 200000) AS n
ON CONFLICT (id) DO NOTHING;

INSERT INTO alert_check_limit (id, table_name, column_name, limit_value, created_by, updated_by, created_time, updated_time) VALUES
('00000000-0000-0000-0000-000000000401', 'aquark_data', 'rain_d_bench_1', 10, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', NOW(), NOW()),
('00000000-0000-0000-0000-000000000402', 'aquark_data', 'moisture_bench_2', 10, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', NOW(), NOW()),
('00000000-0000-0000-0000-000000000403', 'aquark_data', 'temperature_bench_3', 10, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

COMMIT;
