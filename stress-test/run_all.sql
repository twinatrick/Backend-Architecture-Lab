-- ============================================================================
-- 壓力測試資料總執行腳本（跨微服務獨立資料庫）
-- 使用方法：psql -U postgres -f run_all.sql
-- ============================================================================

-- 注意：舊資料不會被清空，腳本使用 ON CONFLICT 與確定性 UUID 確保冪等重跑
-- 若希望重新產生資料，可使用自動化腳本帶 -ResetSeedData 參數執行

\timing on

\echo '===== [1/4] 切換至 iam_service 載入 IAM 壓測資料 ====='
\c iam_service
\i run_iam.sql

\echo '===== [2/4] 切換至 competency_service 載入 Competency 壓測資料 ====='
\c competency_service
\i run_competency.sql

\echo '===== [3/4] 切換至 job_service 載入 Job 壓測資料 ====='
\c job_service
\i run_job.sql

\echo '===== [4/4] 切換至 alert_service 載入 Alert 壓測資料 ====='
\c alert_service
\i run_alert.sql

\echo '===== [附屬] 確保 external_api_service 結構與最新 Entity 一致 ====='
\c external_api_service
ALTER TABLE IF EXISTS bot_config ADD COLUMN IF NOT EXISTS version bigint DEFAULT 0;
ALTER TABLE IF EXISTS discord_gf_session DROP COLUMN IF EXISTS conversation_history;
ALTER TABLE IF EXISTS line_gf_session DROP COLUMN IF EXISTS conversation_history;
CREATE TABLE IF NOT EXISTS discord_gf_message (
    id uuid NOT NULL PRIMARY KEY,
    created_by varchar(255),
    updated_by varchar(255),
    created_time timestamp NOT NULL,
    updated_time timestamp,
    session_id uuid NOT NULL REFERENCES discord_gf_session(id) ON DELETE CASCADE,
    role varchar(32) NOT NULL,
    content text NOT NULL,
    sender_name varchar(255)
);
CREATE TABLE IF NOT EXISTS line_gf_message (
    id uuid NOT NULL PRIMARY KEY,
    created_by varchar(255),
    updated_by varchar(255),
    created_time timestamp NOT NULL,
    updated_time timestamp,
    session_id uuid NOT NULL REFERENCES line_gf_session(id) ON DELETE CASCADE,
    role varchar(32) NOT NULL,
    content text NOT NULL,
    sender_name varchar(255)
);
INSERT INTO bot_config (id, version, platform, config_key, config_value, description, created_by, updated_by, created_time, updated_time)
VALUES ('00000000-0000-0000-0000-000000000601', 0, 'DISCORD', 'STRESS_TEST_KEY', 'true', '壓測基準配置', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

\echo '===== 驗證四大微服務資料庫筆數 ====='
\i verify_counts.sql

\echo '===== 全部執行完成！ ====='

\timing off
