-- ============================================================================
-- 各資料庫資料量驗證
-- 使用方法：依序連線各資料庫執行對應區塊
-- ============================================================================

-- ----- IAM Service (iam_service) -----
\echo '=== IAM Service ==='
SELECT 'role' AS tbl, COUNT(*) AS cnt FROM role;
SELECT '"user"' AS tbl, COUNT(*) AS cnt FROM "user";
SELECT 'user_role' AS tbl, COUNT(*) AS cnt FROM user_role;
SELECT 'function' AS tbl, COUNT(*) AS cnt FROM function;
SELECT 'role_function' AS tbl, COUNT(*) AS cnt FROM role_function;

-- ----- Competency Service (competency_service) -----
\echo '=== Competency Service ==='
SELECT 'skill' AS tbl, COUNT(*) AS cnt FROM skill;
SELECT 'skill_level' AS tbl, COUNT(*) AS cnt FROM skill_level;
SELECT 'project' AS tbl, COUNT(*) AS cnt FROM project;
SELECT 'project_skill' AS tbl, COUNT(*) AS cnt FROM project_skill;
SELECT 'user_skill' AS tbl, COUNT(*) AS cnt FROM user_skill;
SELECT 'user_project' AS tbl, COUNT(*) AS cnt FROM user_project;
SELECT 'user_project_skill' AS tbl, COUNT(*) AS cnt FROM user_project_skill;

-- ----- Job Service (job_service) -----
\echo '=== Job Service ==='
SELECT 'company' AS tbl, COUNT(*) AS cnt FROM company;
SELECT 'company_website' AS tbl, COUNT(*) AS cnt FROM company_website;
SELECT 'job_posting' AS tbl, COUNT(*) AS cnt FROM job_posting;
SELECT 'user_job_link' AS tbl, COUNT(*) AS cnt FROM user_job_link;

-- ----- Alert Service (alert_service) -----
\echo '=== Alert Service ==='
SELECT 'aquark_data' AS tbl, COUNT(*) AS cnt FROM aquark_data;
