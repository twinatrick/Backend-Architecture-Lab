-- ==============================================================================
-- 線上資料庫審計欄位修復腳本：將 created_by 與 updated_by 就地刷新為最高管理者 Admin UUID
-- 目標 Admin UUID: '00000000-0000-0000-0000-000000000001'
-- ==============================================================================

-- 1. IAM 服務資料庫 (iam_service)
\c iam_service;
UPDATE "user" SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE role SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE function SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE user_role SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE role_function SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';

-- 2. Competency 服務資料庫 (competency_service)
\c competency_service;
UPDATE project SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE skill SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE skill_level SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE user_project SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE user_skill SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE project_skill SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE user_project_skill SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';

-- 3. Job 服務資料庫 (job_service)
\c job_service;
UPDATE company SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE job_posting SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE user_job_link SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE company_website SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';

-- 4. Alert 服務資料庫 (alert_service)
\c alert_service;
UPDATE alert_check_limit SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE aquark_data SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';

-- 5. External API 服務資料庫 (external_api_service)
\c external_api_service;
UPDATE bot_config SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE user_voice_upload SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE voice_translation SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE discord_subscription SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE discord_gf_session SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE line_gf_session SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
UPDATE line_chat_session SET created_by = '00000000-0000-0000-0000-000000000001', updated_by = '00000000-0000-0000-0000-000000000001' WHERE created_by IS NULL OR created_by = 'stress-test' OR created_by = 'admin';
