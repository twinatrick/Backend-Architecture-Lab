-- ============================================================================
-- 各微服務獨立資料庫資料量驗證
-- 使用方法：psql -U postgres -f verify_counts.sql
-- ============================================================================

-- ----- IAM Service (iam_service) -----
\c iam_service
\echo '=== IAM Service (iam_service) ==='
SELECT 'role' AS tbl, COUNT(*) AS cnt FROM role
UNION ALL SELECT 'user', COUNT(*) FROM "user"
UNION ALL SELECT 'user_role', COUNT(*) FROM user_role
UNION ALL SELECT 'function', COUNT(*) FROM function
UNION ALL SELECT 'role_function', COUNT(*) FROM role_function;

-- ----- Competency Service (competency_service) -----
\c competency_service
\echo '=== Competency Service (competency_service) ==='
SELECT 'skill' AS tbl, COUNT(*) AS cnt FROM skill
UNION ALL SELECT 'skill_level', COUNT(*) FROM skill_level
UNION ALL SELECT 'project', COUNT(*) FROM project
UNION ALL SELECT 'project_skill', COUNT(*) FROM project_skill
UNION ALL SELECT 'user_skill', COUNT(*) FROM user_skill
UNION ALL SELECT 'user_project', COUNT(*) FROM user_project
UNION ALL SELECT 'user_project_skill', COUNT(*) FROM user_project_skill;

-- ----- Job Service (job_service) -----
\c job_service
\echo '=== Job Service (job_service) ==='
SELECT 'company' AS tbl, COUNT(*) AS cnt FROM company
UNION ALL SELECT 'company_website', COUNT(*) FROM company_website
UNION ALL SELECT 'job_posting', COUNT(*) FROM job_posting
UNION ALL SELECT 'user_job_link', COUNT(*) FROM user_job_link;

-- ----- Alert Service (alert_service) -----
\c alert_service
\echo '=== Alert Service (alert_service) ==='
SELECT 'aquark_data' AS tbl, COUNT(*) AS cnt FROM aquark_data
UNION ALL SELECT 'alert_check_limit', COUNT(*) FROM alert_check_limit;
