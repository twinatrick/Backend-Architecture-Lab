-- ============================================================================
-- 壓力測試公司與職缺資料
-- 12 家公司 + 1,000 筆職缺
-- ============================================================================

BEGIN;

-- ============================================================================
-- 1. 公司資料 (12 家)
-- ============================================================================
INSERT INTO company (id, name, description, last_scraped_at, created_by, updated_by, created_time, updated_time)
VALUES
('00000000-0000-0000-0000-000000000301', '台積電 (TSMC)', '全球最大半導體晶圓代工廠', NULL, 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000302', '聯發科 (MediaTek)', '全球頂尖 IC 設計公司', NULL, 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000303', '聯電 (UMC)', '知名半導體晶圓代工廠', NULL, 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000304', '日月光 (ASE)', '全球最大半導體封測廠', NULL, 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000305', '鴻海 (Foxconn)', '全球最大電子製造服務商', NULL, 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000306', '華碩 (ASUS)', '台灣電腦硬體與電競領導品牌', NULL, 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000307', '台達電 (Delta)', '全球電源管理與自動化解決方案領導者', NULL, 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000308', '廣達 (Quanta)', '全球最大筆記型電腦代工廠', NULL, 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000309', '中華電信 (CHT)', '台灣最大電信服務公司', NULL, 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000310', '緯創 (Wistron)', '全球 ICT 產品代工領導廠商', NULL, 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000311', '友達 (AUO)', '台灣面板顯示器大廠', NULL, 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000312', 'Google 台灣', '全球軟體與雲端服務領導者', NULL, 'stress-test', 'stress-test', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 2. 公司網站
-- ============================================================================
INSERT INTO company_website (id, company_id, url, created_by, updated_by, created_time, updated_time)
VALUES
('00000000-0000-0000-0000-000000000401', '00000000-0000-0000-0000-000000000301', 'https://www.tsmc.com', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000402', '00000000-0000-0000-0000-000000000302', 'https://www.mediatek.com', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000403', '00000000-0000-0000-0000-000000000303', 'https://www.umc.com', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000404', '00000000-0000-0000-0000-000000000304', 'https://www.aseglobal.com', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000405', '00000000-0000-0000-0000-000000000305', 'https://www.honhai.com', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000406', '00000000-0000-0000-0000-000000000306', 'https://www.asus.com', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000407', '00000000-0000-0000-0000-000000000307', 'https://www.deltaww.com', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000408', '00000000-0000-0000-0000-000000000308', 'https://www.quantatw.com', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000409', '00000000-0000-0000-0000-000000000309', 'https://www.cht.com.tw', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000410', '00000000-0000-0000-0000-000000000310', 'https://www.wistron.com', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000411', '00000000-0000-0000-0000-000000000311', 'https://www.auo.com', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000412', '00000000-0000-0000-0000-000000000312', 'https://www.google.com', 'stress-test', 'stress-test', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 3. 職缺資料 (1,000 筆)
-- 各公司輪換產生，確保每家公司約 80-85 筆職缺
-- ============================================================================
INSERT INTO job_posting (id, company_id, title, url, description, requirements, responsibilities, salary_range, posted_date, created_by, updated_by, created_time, updated_time)
SELECT
  gen_random_uuid(),
  CASE (n % 12)
    WHEN 0 THEN '00000000-0000-0000-0000-000000000301'::uuid
    WHEN 1 THEN '00000000-0000-0000-0000-000000000302'::uuid
    WHEN 2 THEN '00000000-0000-0000-0000-000000000303'::uuid
    WHEN 3 THEN '00000000-0000-0000-0000-000000000304'::uuid
    WHEN 4 THEN '00000000-0000-0000-0000-000000000305'::uuid
    WHEN 5 THEN '00000000-0000-0000-0000-000000000306'::uuid
    WHEN 6 THEN '00000000-0000-0000-0000-000000000307'::uuid
    WHEN 7 THEN '00000000-0000-0000-0000-000000000308'::uuid
    WHEN 8 THEN '00000000-0000-0000-0000-000000000309'::uuid
    WHEN 9 THEN '00000000-0000-0000-0000-000000000310'::uuid
    WHEN 10 THEN '00000000-0000-0000-0000-000000000311'::uuid
    WHEN 11 THEN '00000000-0000-0000-0000-000000000312'::uuid
  END,
  CASE (n % 20)
    WHEN 0 THEN '資深軟體工程師'
    WHEN 1 THEN '後端工程師'
    WHEN 2 THEN '前端工程師'
    WHEN 3 THEN '演算法工程師'
    WHEN 4 THEN '資料科學家'
    WHEN 5 THEN '半導體製程工程師'
    WHEN 6 THEN 'IC設計工程師'
    WHEN 7 THEN '產品經理'
    WHEN 8 THEN '專案經理'
    WHEN 9 THEN 'DevOps工程師'
    WHEN 10 THEN '韌體工程師'
    WHEN 11 THEN '硬體工程師'
    WHEN 12 THEN '系統架構師'
    WHEN 13 THEN 'QA測試工程師'
    WHEN 14 THEN 'AI工程師'
    WHEN 15 THEN '資訊安全分析師'
    WHEN 16 THEN '資料庫管理師'
    WHEN 17 THEN '技術主管'
    WHEN 18 THEN '技術支援工程師'
    WHEN 19 THEN '業務工程師'
  END || ' (StressTest #' || n || ')',
  'https://stress-test.example.com/job/' || n,
  '壓力測試職缺 #' || n || ' - ' || md5(random()::text),
  '必要條件：' || CASE (n % 5)
    WHEN 0 THEN '3年以上相關工作經驗，大學以上資訊相關科系畢業'
    WHEN 1 THEN '5年以上相關工作經驗，碩士以上畢業'
    WHEN 2 THEN '2年以上相關工作經驗，熟悉敏捷開發'
    WHEN 3 THEN '具備團隊合作與溝通能力，無經驗可'
    WHEN 4 THEN '10年以上專業領域經驗，具備管理經驗'
  END,
  CASE (n % 6)
    WHEN 0 THEN '1. 系統設計與開發\n2. 技術文件撰寫\n3. Code Review'
    WHEN 1 THEN '1. 需求分析與規劃\n2. 系統架構設計\n3. 效能調校'
    WHEN 2 THEN '1. 產品開發與維護\n2. 自動化測試\n3. 技術研究'
    WHEN 3 THEN '1. 專案時程管控\n2. 跨部門溝通\n3. 風險管理'
    WHEN 4 THEN '1. 資料分析建模\n2. 報表產出\n3. 決策支援'
    WHEN 5 THEN '1. 系統維運管理\n2. 問題排除\n3. 效能監控'
  END,
  CASE (n % 5)
    WHEN 0 THEN 'NT$50,000 - NT$80,000/月'
    WHEN 1 THEN 'NT$80,000 - NT$120,000/月'
    WHEN 2 THEN 'NT$120,000 - NT$180,000/月'
    WHEN 3 THEN 'NT$180,000 - NT$250,000/月'
    WHEN 4 THEN '薪資面議（優於市場行情）'
  END,
  CURRENT_DATE - (random() * 365)::int,
  'stress-test', 'stress-test', NOW(), NOW()
FROM generate_series(1, 1000) AS n;

COMMIT;
