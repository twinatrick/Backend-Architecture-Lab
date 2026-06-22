-- ============================================================================
-- Competency Service 壓力測試資料
-- 資料庫：competency_service
-- 使用方法：psql -U postgres -d competency_service -f run_competency.sql
-- ============================================================================

\echo '===== Competency: 技能與等級資料 ====='
\i 02b_comp_base_data.sql

\echo '===== Competency: 專案資料 (50,000 筆) ====='
\i 04_projects.sql

\echo '===== Competency: 使用者技能關聯 (~150,000 筆) ====='
\i 06_user_skill.sql

\echo '===== Competency: 使用者專案關聯 (~200,000 筆) ====='
\i 07_user_project.sql

\echo '===== Competency 資料載入完成！ ====='
