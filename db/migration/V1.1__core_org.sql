-- ============================================================
-- V1.1 核心: 组织架构 + 用户 + 角色
-- ============================================================

-- 部门表(支持树形)
CREATE TABLE department (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(64)  NOT NULL,
    code            VARCHAR(32)  UNIQUE NOT NULL,
    parent_id       BIGINT       REFERENCES department(id) ON DELETE SET NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_dept_parent ON department(parent_id);
CREATE INDEX idx_dept_deleted ON department(deleted);
COMMENT ON TABLE department IS '部门表(树形)';

-- 角色表(固定5个角色,Phase2再做权限引擎)
CREATE TABLE role (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(32)  UNIQUE NOT NULL,    -- PM / DEPT_LEAD / PMO_ADMIN / EXEC / VIEWER
    name            VARCHAR(64)  NOT NULL,
    description     VARCHAR(256),
    built_in        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE role IS '角色表(内置5种角色)';

-- 用户表
CREATE TABLE app_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64)  UNIQUE NOT NULL,
    password_hash   VARCHAR(256) NOT NULL,         -- BCrypt
    full_name       VARCHAR(64)  NOT NULL,
    email           VARCHAR(128) UNIQUE,
    phone           VARCHAR(32),
    department_id   BIGINT       REFERENCES department(id) ON DELETE SET NULL,
    primary_role_id BIGINT       NOT NULL REFERENCES role(id),
    job_title       VARCHAR(64),                   -- 岗位: 项目经理/开发/测试
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_user_dept ON app_user(department_id);
CREATE INDEX idx_user_role ON app_user(primary_role_id);
CREATE INDEX idx_user_deleted ON app_user(deleted);
COMMENT ON TABLE app_user IS '用户表';

-- 用户-角色多对多(预留扩展)
CREATE TABLE user_role (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_id         BIGINT       NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, role_id)
);
CREATE INDEX idx_user_role_user ON user_role(user_id);
COMMENT ON TABLE user_role IS '用户-角色关联表';
