-- ============================================================
-- V2.3 项目组成员 (project_member) — MySQL 版
-- ============================================================
-- 与 PG 版保持同步

-- 1) 成员角色字典
CREATE TABLE member_role (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(32) NOT NULL UNIQUE,
    name            VARCHAR(64) NOT NULL,
    description     VARCHAR(256),
    sort_order      INT NOT NULL DEFAULT 0,
    enabled         TINYINT(1) NOT NULL DEFAULT 1,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='项目成员角色字典';
CREATE INDEX idx_mr_deleted ON member_role(deleted);

-- 初始化 7 个标准项目角色
INSERT INTO member_role (code, name, description, sort_order) VALUES
  ('PM',       '项目经理',     '负责项目整体计划/协调/风险/汇报', 10),
  ('ASSISTANT','项目助理',     '协助项目经理处理项目日常事务(周报、会议、文档)', 20),
  ('ARCH',     '架构师',       '负责系统架构设计、技术选型、关键技术攻关', 30),
  ('BA',       '需求分析师',   '负责需求收集、分析、原型、验收', 40),
  ('DEV',      '开发工程师',   '负责编码、单元测试、技术实现', 50),
  ('QA',       '测试工程师',   '负责测试用例、缺陷跟踪、验收测试', 60),
  ('CFG',      '配置管理员',   '负责环境/版本/配置/发布管理', 70);

-- 2) 项目组成员
CREATE TABLE project_member (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT NOT NULL,
    role_id         BIGINT NOT NULL,
    user_id         BIGINT,
    member_name     VARCHAR(64) NOT NULL,
    is_external     TINYINT(1) NOT NULL DEFAULT 0,
    join_date       DATE NOT NULL,
    leave_date      DATE,
    allocation_pct  INT NOT NULL DEFAULT 100,
    remark          VARCHAR(256),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT fk_pm_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_role    FOREIGN KEY (role_id)    REFERENCES member_role(id),
    CONSTRAINT fk_pm_user    FOREIGN KEY (user_id)    REFERENCES app_user(id),
    CONSTRAINT chk_pm_date_range    CHECK (leave_date IS NULL OR leave_date >= join_date),
    CONSTRAINT chk_pm_allocation    CHECK (allocation_pct BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='项目组成员(一个项目可有多人,每人一个角色)';
CREATE INDEX idx_pm_project ON project_member(project_id);
CREATE INDEX idx_pm_role    ON project_member(role_id);
CREATE INDEX idx_pm_user    ON project_member(user_id);
CREATE INDEX idx_pm_deleted ON project_member(deleted);
