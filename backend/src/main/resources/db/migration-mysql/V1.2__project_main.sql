-- ============================================================
-- V1.2 项目主表 + 字典(MySQL 8 方言)
-- ============================================================

-- 项目类型字典
CREATE TABLE project_type (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL UNIQUE,    -- DELIVERY/SELF_RD/INNER_PRODUCT/RD
    name            VARCHAR(64)  NOT NULL,
    description     VARCHAR(256) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目类型字典';

-- 项目状态字典
CREATE TABLE project_status (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL UNIQUE,    -- DRAFT/PENDING/ACTIVE/SUSPENDED/CLOSED/REJECTED
    name            VARCHAR(64)  NOT NULL,
    is_terminal     BOOLEAN      NOT NULL DEFAULT FALSE  -- 终态: CLOSED/REJECTED
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目状态字典';

-- 健康度字典
CREATE TABLE health_level (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(16)  NOT NULL UNIQUE,    -- GREEN/YELLOW/RED
    name            VARCHAR(32)  NOT NULL,
    color_hex       VARCHAR(8)   NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='健康度字典';

-- 项目主表
CREATE TABLE project (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL UNIQUE,    -- 业务编号 P-2025-001
    name            VARCHAR(128) NOT NULL,
    type_id         BIGINT       NOT NULL,
    status_id       BIGINT       NOT NULL,
    health_id       BIGINT       NULL,
    customer        VARCHAR(128) NULL,                   -- 客户名称(交付类必填)
    department_id   BIGINT       NULL,
    pm_user_id      BIGINT       NULL, -- 项目经理
    sponsor_user_id BIGINT       NULL, -- 项目发起人
    description     TEXT         NULL,
    background      TEXT         NULL,                           -- 项目背景
    goals           TEXT         NULL,                           -- 项目目标
    scope           TEXT         NULL,                           -- 项目范围
    plan_start_date DATE         NULL,
    plan_end_date   DATE         NULL,
    actual_start_date DATE       NULL,
    actual_end_date DATE         NULL,
    plan_workdays   INT          NULL,                           -- 预计人天
    progress_pct    INT          NOT NULL DEFAULT 0,
    budget_estimate DECIMAL(14,2) NULL,                 -- 预计预算(元)
    created_by      BIGINT       NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_project_type FOREIGN KEY (type_id) REFERENCES project_type(id),
    CONSTRAINT fk_project_status FOREIGN KEY (status_id) REFERENCES project_status(id),
    CONSTRAINT fk_project_health FOREIGN KEY (health_id) REFERENCES health_level(id),
    CONSTRAINT fk_project_dept FOREIGN KEY (department_id) REFERENCES department(id),
    CONSTRAINT fk_project_pm FOREIGN KEY (pm_user_id) REFERENCES app_user(id),
    CONSTRAINT fk_project_sponsor FOREIGN KEY (sponsor_user_id) REFERENCES app_user(id),
    CONSTRAINT fk_project_creator FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT chk_project_progress CHECK (progress_pct BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目主表';
CREATE INDEX idx_project_status ON project(status_id);
CREATE INDEX idx_project_pm ON project(pm_user_id);
CREATE INDEX idx_project_dept ON project(department_id);
CREATE INDEX idx_project_health ON project(health_id);
CREATE INDEX idx_project_deleted ON project(deleted);
CREATE INDEX idx_project_plan_end ON project(plan_end_date);
