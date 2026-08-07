-- ============================================================
-- V1.2 项目主表 + 字典
-- ============================================================

-- 项目类型字典
CREATE TABLE project_type (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(32) UNIQUE NOT NULL,    -- DELIVERY/SELF_RD/INNER_PRODUCT/RD
    name            VARCHAR(64) NOT NULL,
    description     VARCHAR(256)
);
COMMENT ON TABLE project_type IS '项目类型字典';

-- 项目状态字典
CREATE TABLE project_status (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(32) UNIQUE NOT NULL,    -- DRAFT/PENDING/ACTIVE/SUSPENDED/CLOSED/REJECTED
    name            VARCHAR(64) NOT NULL,
    is_terminal     BOOLEAN NOT NULL DEFAULT FALSE  -- 终态: CLOSED/REJECTED
);
COMMENT ON TABLE project_status IS '项目状态字典';

-- 健康度字典
CREATE TABLE health_level (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(16) UNIQUE NOT NULL,    -- GREEN/YELLOW/RED
    name            VARCHAR(32) NOT NULL,
    color_hex       VARCHAR(8)
);
COMMENT ON TABLE health_level IS '健康度字典';

-- 项目主表
CREATE TABLE project (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(32) UNIQUE NOT NULL,    -- 业务编号 P-2025-001
    name            VARCHAR(128) NOT NULL,
    type_id         BIGINT NOT NULL REFERENCES project_type(id),
    status_id       BIGINT NOT NULL REFERENCES project_status(id),
    health_id       BIGINT REFERENCES health_level(id),
    customer        VARCHAR(128),                   -- 客户名称(交付类必填)
    department_id   BIGINT REFERENCES department(id),
    pm_user_id      BIGINT REFERENCES app_user(id), -- 项目经理
    sponsor_user_id BIGINT REFERENCES app_user(id), -- 项目发起人
    description     TEXT,
    background      TEXT,                           -- 项目背景
    goals           TEXT,                           -- 项目目标
    scope           TEXT,                           -- 项目范围
    plan_start_date DATE,
    plan_end_date   DATE,
    actual_start_date DATE,
    actual_end_date DATE,
    plan_workdays   INT,                           -- 预计人天
    progress_pct    INT NOT NULL DEFAULT 0 CHECK (progress_pct BETWEEN 0 AND 100),
    budget_estimate NUMERIC(14,2),                 -- 预计预算(元)
    created_by      BIGINT REFERENCES app_user(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_project_status ON project(status_id);
CREATE INDEX idx_project_pm ON project(pm_user_id);
CREATE INDEX idx_project_dept ON project(department_id);
CREATE INDEX idx_project_health ON project(health_id);
CREATE INDEX idx_project_deleted ON project(deleted);
CREATE INDEX idx_project_plan_end ON project(plan_end_date);
COMMENT ON TABLE project IS '项目主表';
