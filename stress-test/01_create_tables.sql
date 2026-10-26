-- ============================================================================
-- 建立所有資料表（模擬 Hibernate ddl-auto=update 的 DDL）
-- ⚠️ 僅供參考：Hibernate ddl-auto=update 已自動處理表格建立
--    若需手動建立，請在對應資料庫中執行此腳本
-- ============================================================================

CREATE TABLE IF NOT EXISTS alert_check_limit (
    limit_value float(53),
    created_time timestamp(6),
    updated_time timestamp(6),
    id uuid NOT NULL,
    column_name varchar(255),
    created_by varchar(255),
    table_name varchar(255),
    updated_by varchar(255),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS aquark_data (
    echo float(24),
    is_peak boolean,
    moisture float(24),
    rain_d float(24),
    temperature float(24),
    v1 float(24),
    v2 float(24),
    v3 float(24),
    v4 float(24),
    v5 float(24),
    v6 float(24),
    v7 float(24),
    water_speed_aquark float(24),
    created_time timestamp(6),
    trans_time timestamp(6),
    updated_time timestamp(6),
    id uuid NOT NULL,
    created_by varchar(255),
    csq varchar(255),
    station_id varchar(255),
    updated_by varchar(255),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS company (
    last_scraped_at date,
    created_time timestamp(6),
    updated_time timestamp(6),
    id uuid NOT NULL,
    created_by varchar(255),
    description TEXT,
    name varchar(255) NOT NULL,
    updated_by varchar(255),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS company_website (
    created_time timestamp(6),
    updated_time timestamp(6),
    company_id uuid NOT NULL,
    id uuid NOT NULL,
    created_by varchar(255),
    updated_by varchar(255),
    url varchar(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS function (
    type integer,
    created_time timestamp(6),
    updated_time timestamp(6),
    id uuid NOT NULL,
    created_by varchar(255),
    name varchar(255),
    parent varchar(255),
    sort varchar(255),
    updated_by varchar(255),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS job_posting (
    posted_date date,
    created_time timestamp(6),
    updated_time timestamp(6),
    company_id uuid NOT NULL,
    id uuid NOT NULL,
    created_by varchar(255),
    description TEXT,
    gemini_analysis TEXT,
    requirements TEXT,
    responsibilities TEXT,
    salary_range varchar(255),
    title varchar(255) NOT NULL,
    updated_by varchar(255),
    url varchar(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS project (
    version bigint DEFAULT 0,
    created_time timestamp(6),
    updated_time timestamp(6),
    id uuid NOT NULL,
    created_by varchar(255),
    description varchar(255),
    name varchar(255),
    updated_by varchar(255),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS project_skill (
    created_time timestamp(6),
    updated_time timestamp(6),
    id uuid NOT NULL,
    project_id uuid NOT NULL,
    skill_id uuid NOT NULL,
    skill_level_id uuid NOT NULL,
    created_by varchar(255),
    updated_by varchar(255),
    PRIMARY KEY (id),
    UNIQUE (project_id, skill_id)
);

CREATE TABLE IF NOT EXISTS role (
    id uuid NOT NULL,
    created_time timestamp(6),
    updated_time timestamp(6),
    created_by varchar(255),
    description varchar(255),
    name varchar(255),
    updated_by varchar(255),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS role_function (
    created_time timestamp(6),
    updated_time timestamp(6),
    function_id uuid,
    id uuid NOT NULL,
    role_id uuid,
    created_by varchar(255),
    updated_by varchar(255),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS skill (
    created_time timestamp(6),
    updated_time timestamp(6),
    id uuid NOT NULL,
    created_by varchar(255),
    description varchar(255),
    name varchar(255),
    updated_by varchar(255),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS skill_level (
    level_value integer NOT NULL,
    created_time timestamp(6),
    updated_time timestamp(6),
    id uuid NOT NULL,
    skill_id uuid NOT NULL,
    created_by varchar(255),
    description varchar(255),
    title varchar(255) NOT NULL,
    updated_by varchar(255),
    PRIMARY KEY (id),
    UNIQUE (skill_id, level_value)
);

CREATE TABLE IF NOT EXISTS "user" (
    version bigint DEFAULT 0,
    disabled boolean,
    created_time timestamp(6),
    updated_time timestamp(6),
    id uuid NOT NULL,
    created_by varchar(255),
    email varchar(255) NOT NULL,
    name varchar(255),
    password varchar(255),
    phone varchar(255),
    updated_by varchar(255),
    PRIMARY KEY (id)
);

ALTER TABLE "user" ADD COLUMN IF NOT EXISTS version bigint DEFAULT 0;
ALTER TABLE project ADD COLUMN IF NOT EXISTS version bigint DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_email ON "user" (email);

CREATE TABLE IF NOT EXISTS user_job_link (
    created_time timestamp(6),
    updated_time timestamp(6),
    id uuid NOT NULL,
    job_posting_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_by varchar(255),
    gemini_feedback TEXT,
    updated_by varchar(255),
    user_notes TEXT,
    PRIMARY KEY (id),
    UNIQUE (user_id, job_posting_id)
);

CREATE TABLE IF NOT EXISTS user_project (
    created_time timestamp(6),
    updated_time timestamp(6),
    id uuid NOT NULL,
    project_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_by varchar(255),
    updated_by varchar(255),
    PRIMARY KEY (id),
    UNIQUE (user_id, project_id)
);

CREATE TABLE IF NOT EXISTS user_project_skill (
    created_time timestamp(6),
    updated_time timestamp(6),
    id uuid NOT NULL,
    project_id uuid NOT NULL,
    skill_id uuid NOT NULL,
    skill_level_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_by varchar(255),
    updated_by varchar(255),
    PRIMARY KEY (id),
    UNIQUE (user_id, project_id, skill_id)
);

CREATE TABLE IF NOT EXISTS user_role (
    created_time timestamp(6),
    updated_time timestamp(6),
    id uuid NOT NULL,
    role_id uuid,
    user_id uuid,
    created_by varchar(255),
    updated_by varchar(255),
    PRIMARY KEY (id),
    UNIQUE (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS user_skill (
    created_time timestamp(6),
    updated_time timestamp(6),
    id uuid NOT NULL,
    skill_id uuid NOT NULL,
    skill_level_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_by varchar(255),
    updated_by varchar(255),
    PRIMARY KEY (id),
    UNIQUE (user_id, skill_id)
);

CREATE TABLE IF NOT EXISTS compensation_outbox_event (
    id uuid NOT NULL,
    created_by varchar(255),
    updated_by varchar(255),
    created_time timestamp(6),
    updated_time timestamp(6),
    transaction_id uuid NOT NULL,
    event_id uuid NOT NULL,
    action varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    payload text NOT NULL,
    delivery_status varchar(20) NOT NULL,
    processing_at timestamp(6),
    lease_until timestamp(6),
    attempt_count integer NOT NULL,
    next_attempt_at timestamp(6),
    sent_at timestamp(6),
    owner_id varchar(64),
    fencing_version bigint,
    error_message varchar(1024),
    last_alerted_at timestamp(6),
    PRIMARY KEY (id),
    UNIQUE (event_id)
);

CREATE TABLE IF NOT EXISTS compensation_event_log (
    id uuid NOT NULL,
    created_by varchar(255),
    updated_by varchar(255),
    created_time timestamp(6),
    updated_time timestamp(6),
    event_id uuid NOT NULL,
    transaction_id uuid NOT NULL,
    status varchar(32) NOT NULL,
    attempt_count integer NOT NULL,
    last_error varchar(1024),
    received_at timestamp(6) NOT NULL,
    processing_at timestamp(6),
    lease_until timestamp(6),
    processed_at timestamp(6),
    failed_at timestamp(6),
    next_attempt_at timestamp(6),
    last_alerted_at timestamp(6),
    owner_id varchar(64) NOT NULL,
    fencing_version bigint,
    payload text,
    PRIMARY KEY (id),
    UNIQUE (event_id)
);

CREATE TABLE IF NOT EXISTS compensation_restore_log (
    event_id uuid NOT NULL,
    project_id uuid NOT NULL,
    processed_at timestamp(6) NOT NULL,
    status varchar(32) NOT NULL,
    version bigint,
    last_error varchar(1024),
    owner_id varchar(64) NOT NULL,
    fencing_version bigint,
    lease_until timestamp(6),
    before_state text,
    PRIMARY KEY (event_id)
);

CREATE TABLE IF NOT EXISTS external_sync_command (
    id uuid NOT NULL,
    created_by varchar(255),
    updated_by varchar(255),
    created_time timestamp(6),
    updated_time timestamp(6),
    transaction_id uuid NOT NULL,
    project_id uuid NOT NULL,
    delivery_status varchar(20) NOT NULL,
    payload text NOT NULL,
    processing_at timestamp(6),
    lease_until timestamp(6),
    attempt_count integer NOT NULL,
    next_attempt_at timestamp(6),
    sent_at timestamp(6),
    owner_id varchar(64),
    fencing_version bigint,
    error_message varchar(1024),
    PRIMARY KEY (id)
);

-- 將舊版 Hibernate @Lob 自動建出的 oid 欄位轉為 text 以符合 Liquibase 與現行 Entity 契約
ALTER TABLE compensation_outbox_event ALTER COLUMN payload TYPE text USING payload::text;
ALTER TABLE compensation_event_log ALTER COLUMN payload TYPE text USING payload::text;
ALTER TABLE external_sync_command ALTER COLUMN payload TYPE text USING payload::text;

-- Foreign keys (僅保留同微服務資料庫內部的實體關聯，跨服務 user_id 採邏輯關聯)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_company_website_company') THEN
        ALTER TABLE company_website ADD CONSTRAINT fk_company_website_company FOREIGN KEY (company_id) REFERENCES company;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_job_posting_company') THEN
        ALTER TABLE job_posting ADD CONSTRAINT fk_job_posting_company FOREIGN KEY (company_id) REFERENCES company;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_project_skill_project') THEN
        ALTER TABLE project_skill ADD CONSTRAINT fk_project_skill_project FOREIGN KEY (project_id) REFERENCES project;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_project_skill_skill') THEN
        ALTER TABLE project_skill ADD CONSTRAINT fk_project_skill_skill FOREIGN KEY (skill_id) REFERENCES skill;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_project_skill_skill_level') THEN
        ALTER TABLE project_skill ADD CONSTRAINT fk_project_skill_skill_level FOREIGN KEY (skill_level_id) REFERENCES skill_level;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_role_function_function') THEN
        ALTER TABLE role_function ADD CONSTRAINT fk_role_function_function FOREIGN KEY (function_id) REFERENCES function;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_role_function_role') THEN
        ALTER TABLE role_function ADD CONSTRAINT fk_role_function_role FOREIGN KEY (role_id) REFERENCES role;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_skill_level_skill') THEN
        ALTER TABLE skill_level ADD CONSTRAINT fk_skill_level_skill FOREIGN KEY (skill_id) REFERENCES skill;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_user_job_link_job') THEN
        ALTER TABLE user_job_link ADD CONSTRAINT fk_user_job_link_job FOREIGN KEY (job_posting_id) REFERENCES job_posting;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_user_project_project') THEN
        ALTER TABLE user_project ADD CONSTRAINT fk_user_project_project FOREIGN KEY (project_id) REFERENCES project;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ups_project') THEN
        ALTER TABLE user_project_skill ADD CONSTRAINT fk_ups_project FOREIGN KEY (project_id) REFERENCES project;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ups_skill') THEN
        ALTER TABLE user_project_skill ADD CONSTRAINT fk_ups_skill FOREIGN KEY (skill_id) REFERENCES skill;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ups_skill_level') THEN
        ALTER TABLE user_project_skill ADD CONSTRAINT fk_ups_skill_level FOREIGN KEY (skill_level_id) REFERENCES skill_level;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_user_role_role') THEN
        ALTER TABLE user_role ADD CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_user_role_user') THEN
        ALTER TABLE user_role ADD CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES "user";
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_user_skill_skill') THEN
        ALTER TABLE user_skill ADD CONSTRAINT fk_user_skill_skill FOREIGN KEY (skill_id) REFERENCES skill;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_user_skill_skill_level') THEN
        ALTER TABLE user_skill ADD CONSTRAINT fk_user_skill_skill_level FOREIGN KEY (skill_level_id) REFERENCES skill_level;
    END IF;
END $$;

-- Indexes aligned with Liquibase baseline changelogs
CREATE INDEX IF NOT EXISTS idx_function_parent ON function (parent);
CREATE INDEX IF NOT EXISTS idx_function_type ON function (type);
CREATE INDEX IF NOT EXISTS idx_user_role_user_id ON user_role (user_id);
CREATE INDEX IF NOT EXISTS idx_user_role_role_id ON user_role (role_id);
CREATE INDEX IF NOT EXISTS idx_role_function_role_id ON role_function (role_id);
CREATE INDEX IF NOT EXISTS idx_role_function_function_id ON role_function (function_id);

CREATE INDEX IF NOT EXISTS idx_skill_level_skill_id ON skill_level (skill_id);
CREATE INDEX IF NOT EXISTS idx_user_project_user_id ON user_project (user_id);
CREATE INDEX IF NOT EXISTS idx_user_project_project_id ON user_project (project_id);
CREATE INDEX IF NOT EXISTS idx_user_skill_user_id ON user_skill (user_id);
CREATE INDEX IF NOT EXISTS idx_user_skill_skill_id ON user_skill (skill_id);
CREATE INDEX IF NOT EXISTS idx_user_skill_level_id ON user_skill (skill_level_id);
CREATE INDEX IF NOT EXISTS idx_project_skill_project_id ON project_skill (project_id);
CREATE INDEX IF NOT EXISTS idx_project_skill_skill_id ON project_skill (skill_id);
CREATE INDEX IF NOT EXISTS idx_project_skill_level_id ON project_skill (skill_level_id);
CREATE INDEX IF NOT EXISTS idx_ups_user_id ON user_project_skill (user_id);
CREATE INDEX IF NOT EXISTS idx_ups_project_id ON user_project_skill (project_id);
CREATE INDEX IF NOT EXISTS idx_ups_skill_id ON user_project_skill (skill_id);
CREATE INDEX IF NOT EXISTS idx_ups_level_id ON user_project_skill (skill_level_id);

CREATE INDEX IF NOT EXISTS idx_company_website_company_id ON company_website (company_id);
CREATE INDEX IF NOT EXISTS idx_job_posting_company_id ON job_posting (company_id);
CREATE INDEX IF NOT EXISTS idx_user_job_link_job_posting_id ON user_job_link (job_posting_id);

-- Performance optimization indexes (aligned with Liquibase 0002/0003 changelogs)
CREATE INDEX IF NOT EXISTS idx_user_created_time_id ON "user" (created_time DESC, id ASC);
CREATE INDEX IF NOT EXISTS idx_role_created_time_id ON role (created_time DESC, id ASC);
CREATE INDEX IF NOT EXISTS idx_project_created_time_id ON project (created_time DESC, id ASC);
CREATE INDEX IF NOT EXISTS idx_skill_created_time_id ON skill (created_time DESC, id ASC);
CREATE INDEX IF NOT EXISTS idx_skill_level_created_time_id ON skill_level (created_time DESC, id ASC);
CREATE INDEX IF NOT EXISTS idx_user_job_link_created_time_id ON user_job_link (created_time DESC, id ASC);
CREATE INDEX IF NOT EXISTS idx_job_posting_created_time_id ON job_posting (created_time DESC, id ASC);
CREATE INDEX IF NOT EXISTS idx_company_created_time_id ON company (created_time DESC, id ASC);
CREATE INDEX IF NOT EXISTS idx_aquark_data_trans_time ON aquark_data (trans_time DESC, id ASC);
CREATE INDEX IF NOT EXISTS idx_aquark_data_station_trans_time ON aquark_data (station_id, trans_time DESC);
CREATE INDEX IF NOT EXISTS idx_aquark_data_created_time ON aquark_data (created_time DESC, id ASC);
CREATE INDEX IF NOT EXISTS idx_alert_check_limit_created_time ON alert_check_limit (created_time DESC, id ASC);


