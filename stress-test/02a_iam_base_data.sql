-- ============================================================================
-- IAM 基礎資料：角色 (Role)
-- 擷取自 02_base_data.sql 的 IAM 部分
-- ============================================================================

BEGIN;

INSERT INTO role (id, name, description, created_by, updated_by, created_time, updated_time)
VALUES
('00000000-0000-0000-0000-000000000001', '系統管理員', '擁有所有系統權限', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000002', 'HR', '人力資源管理權限', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000003', '部門主管', '各部門主管權限', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000004', '工程師', '工程部門一般員工', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000005', '一般員工', '一般員工權限', 'stress-test', 'stress-test', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

COMMIT;
