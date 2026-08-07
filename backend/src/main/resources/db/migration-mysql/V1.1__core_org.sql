-- ============================================================
-- V1.1 核心: 组织架构 + 用户 + 角色(MySQL 8 方言)
-- ============================================================

-- 部门表(支持树形)
CREATE TABLE department (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(64)  NOT NULL,
    code            VARCHAR(32)  NOT NULL UNIQUE,
    parent_id       BIGINT       NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_dept_parent FOREIGN KEY (parent_id) REFERENCES department(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表(树形)';
CREATE INDEX idx_dept_deleted ON department(deleted);

-- 角色表(固定5个角色,Phase2再做权限引擎)
CREATE TABLE role (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL UNIQUE,    -- PM / DEPT_LEAD / PMO_ADMIN / EXEC / VIEWER
    name            VARCHAR(64)  NOT NULL,
    description     VARCHAR(256) NULL,
    built_in        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表(内置5种角色)';

-- 用户表
CREATE TABLE app_user (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password_hash   VARCHAR(256) NOT NULL,         -- BCrypt
    full_name       VARCHAR(64)  NOT NULL,
    email           VARCHAR(128) NULL UNIQUE,
    phone           VARCHAR(32)  NULL,
    department_id   BIGINT       NULL,
    primary_role_id BIGINT       NOT NULL,
    job_title       VARCHAR(64)  NULL,                   -- 岗位: 项目经理/开发/测试
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at   DATETIME(3)  NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_user_dept FOREIGN KEY (department_id) REFERENCES department(id) ON DELETE SET NULL,
    CONSTRAINT fk_user_role FOREIGN KEY (primary_role_id) REFERENCES role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
CREATE INDEX idx_user_deleted ON app_user(deleted);

-- 用户-角色多对多(预留扩展)
CREATE TABLE user_role (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    role_id         BIGINT       NOT NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_role (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联表';
