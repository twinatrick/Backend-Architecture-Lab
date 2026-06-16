-- ============================================================================
-- 壓力測試使用者資料 (50,000 筆)
-- 密碼 BCrypt hash ({bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy)
-- 對應明文: password
-- 注意: Spring Security 5+ DelegatingPasswordEncoder 需要 {bcrypt} 前綴
-- ============================================================================

BEGIN;

-- ============================================================================
-- 1. 系統管理員帳號（固定 UUID 方便測試）
-- ============================================================================
INSERT INTO "user" (id, name, email, password, phone, disabled, created_by, updated_by, created_time, updated_time)
VALUES
('00000000-0000-0000-0000-000000000001', 'Admin', 'admin@tsmc.com', '{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '0910000000', false, 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000002', 'HR_Manager', 'hr@tsmc.com', '{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '0910000001', false, 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000003', 'Engineer_Lead', 'engineer@tsmc.com', '{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '0910000002', false, 'stress-test', 'stress-test', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 2. 大量員工資料 (50,000 筆)
-- ============================================================================
INSERT INTO "user" (id, name, email, password, phone, disabled, created_by, updated_by, created_time, updated_time)
SELECT
  gen_random_uuid(),
  'Employee_' || LPAD(n::text, 6, '0'),
  'employee' || LPAD(n::text, 6, '0') || '@tsmc.com',
  '{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
  '09' || LPAD((10000000 + n)::text, 8, '0'),
  CASE WHEN n % 100 = 0 THEN true ELSE false END,
  'stress-test', 'stress-test', NOW(), NOW()
FROM generate_series(1, 50000) AS n
ON CONFLICT (email) DO NOTHING;

-- ============================================================================
-- 3. 角色關聯（UserRole）
-- 管理員 → 系統管理員
-- HR → HR
-- 每 50 人第 1 人 → 部門主管，每 20 人 → 工程師，其餘 → 一般員工
-- ============================================================================
INSERT INTO user_role (id, user_id, role_id, created_by, updated_by, created_time, updated_time)
VALUES
('00000000-0000-0000-0000-100000000001', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-100000000002', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-100000000003', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000004', 'stress-test', 'stress-test', NOW(), NOW())
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO user_role (id, user_id, role_id, created_by, updated_by, created_time, updated_time)
SELECT
  gen_random_uuid(),
  u.id,
  CASE
    WHEN (SUBSTRING(u.email FROM 'employee0*(\d+)')::int) % 50 = 1 THEN '00000000-0000-0000-0000-000000000003'::uuid
    WHEN (SUBSTRING(u.email FROM 'employee0*(\d+)')::int) % 20 = 0 THEN '00000000-0000-0000-0000-000000000004'::uuid
    ELSE '00000000-0000-0000-0000-000000000005'::uuid
  END,
  'stress-test', 'stress-test', NOW(), NOW()
FROM "user" u
WHERE u.email LIKE 'employee%'
  AND u.email NOT IN ('employee000001@tsmc.com', 'employee000051@tsmc.com')
ON CONFLICT (user_id, role_id) DO NOTHING;

COMMIT;
