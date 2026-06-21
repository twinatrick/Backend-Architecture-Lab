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

-- Foreign keys
ALTER TABLE company_website ADD CONSTRAINT IF NOT EXISTS fk_company_website_company FOREIGN KEY (company_id) REFERENCES company;
ALTER TABLE job_posting ADD CONSTRAINT IF NOT EXISTS fk_job_posting_company FOREIGN KEY (company_id) REFERENCES company;
ALTER TABLE project_skill ADD CONSTRAINT IF NOT EXISTS fk_project_skill_project FOREIGN KEY (project_id) REFERENCES project;
ALTER TABLE project_skill ADD CONSTRAINT IF NOT EXISTS fk_project_skill_skill FOREIGN KEY (skill_id) REFERENCES skill;
ALTER TABLE project_skill ADD CONSTRAINT IF NOT EXISTS fk_project_skill_skill_level FOREIGN KEY (skill_level_id) REFERENCES skill_level;
ALTER TABLE role_function ADD CONSTRAINT IF NOT EXISTS fk_role_function_function FOREIGN KEY (function_id) REFERENCES function;
ALTER TABLE role_function ADD CONSTRAINT IF NOT EXISTS fk_role_function_role FOREIGN KEY (role_id) REFERENCES role;
ALTER TABLE skill_level ADD CONSTRAINT IF NOT EXISTS fk_skill_level_skill FOREIGN KEY (skill_id) REFERENCES skill;
ALTER TABLE user_job_link ADD CONSTRAINT IF NOT EXISTS fk_user_job_link_job FOREIGN KEY (job_posting_id) REFERENCES job_posting;
ALTER TABLE user_job_link ADD CONSTRAINT IF NOT EXISTS fk_user_job_link_user FOREIGN KEY (user_id) REFERENCES "user";
ALTER TABLE user_project ADD CONSTRAINT IF NOT EXISTS fk_user_project_project FOREIGN KEY (project_id) REFERENCES project;
ALTER TABLE user_project ADD CONSTRAINT IF NOT EXISTS fk_user_project_user FOREIGN KEY (user_id) REFERENCES "user";
ALTER TABLE user_project_skill ADD CONSTRAINT IF NOT EXISTS fk_ups_project FOREIGN KEY (project_id) REFERENCES project;
ALTER TABLE user_project_skill ADD CONSTRAINT IF NOT EXISTS fk_ups_skill FOREIGN KEY (skill_id) REFERENCES skill;
ALTER TABLE user_project_skill ADD CONSTRAINT IF NOT EXISTS fk_ups_skill_level FOREIGN KEY (skill_level_id) REFERENCES skill_level;
ALTER TABLE user_project_skill ADD CONSTRAINT IF NOT EXISTS fk_ups_user FOREIGN KEY (user_id) REFERENCES "user";
ALTER TABLE user_role ADD CONSTRAINT IF NOT EXISTS fk_user_role_role FOREIGN KEY (role_id) REFERENCES role;
ALTER TABLE user_role ADD CONSTRAINT IF NOT EXISTS fk_user_role_user FOREIGN KEY (user_id) REFERENCES "user";
ALTER TABLE user_skill ADD CONSTRAINT IF NOT EXISTS fk_user_skill_skill FOREIGN KEY (skill_id) REFERENCES skill;
ALTER TABLE user_skill ADD CONSTRAINT IF NOT EXISTS fk_user_skill_skill_level FOREIGN KEY (skill_level_id) REFERENCES skill_level;
ALTER TABLE user_skill ADD CONSTRAINT IF NOT EXISTS fk_user_skill_user FOREIGN KEY (user_id) REFERENCES "user";
