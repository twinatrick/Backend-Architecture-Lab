-- ============================================================================
-- 壓力測試專案資料 (50,000 筆)
-- 10 大類別輪換產生
-- ============================================================================

BEGIN;

INSERT INTO project (id, name, description, created_by, updated_by, created_time, updated_time)
SELECT
  gen_random_uuid(),
  CASE (n % 10)
    WHEN 0 THEN '先進製程_' || ((n / 10) + 1)::text || '_期專案'
    WHEN 1 THEN '晶圓廠營運_' || ((n / 10) + 1)::text
    WHEN 2 THEN '研發創新_' || ((n / 10) + 1)::text
    WHEN 3 THEN '封裝測試_' || ((n / 10) + 1)::text
    WHEN 4 THEN '品質管理_' || ((n / 10) + 1)::text
    WHEN 5 THEN '資訊系統_' || ((n / 10) + 1)::text
    WHEN 6 THEN 'AI應用_' || ((n / 10) + 1)::text
    WHEN 7 THEN '全球業務_' || ((n / 10) + 1)::text
    WHEN 8 THEN '人力資源_' || ((n / 10) + 1)::text
    WHEN 9 THEN '供應鏈_' || ((n / 10) + 1)::text
  END,
  'Stress test project #' || n || ' - [' ||
  CASE (n % 10)
    WHEN 0 THEN '先進製程整合開發'
    WHEN 1 THEN '晶圓廠營運最佳化'
    WHEN 2 THEN '前瞻研發計畫'
    WHEN 3 THEN '先進封裝技術開發'
    WHEN 4 THEN '全面品質提升'
    WHEN 5 THEN '數位轉型專案'
    WHEN 6 THEN 'AI/ML導入計畫'
    WHEN 7 THEN '客戶關係管理'
    WHEN 8 THEN '人才發展計畫'
    WHEN 9 THEN '全球供應鏈優化'
  END || '] 詳細描述: ' || md5(random()::text),
  'stress-test', 'stress-test', NOW(), NOW()
FROM generate_series(1, 50000) AS n;

COMMIT;
