-- ============================================================
-- V2.3 项目组成员 (project_member) — PostgreSQL 版
-- ============================================================
--
-- 业务说明:
--  - 每个项目可有多个成员,每个成员分配一个项目角色
--  - 角色字典化:member_role 表(code 全大写,便于跨语言/序列化)
--  - 成员可以是系统中已存在的 app_user,也可以只填姓名(外部专家/客户方)
--    -> 字段 user_id 可空(只填姓名);is_external 标识
--  - 时间段:join_date / leave_date,默认 join=项目计划开始,leave=项目计划结束
--  - 一致性:同一项目下 同一用户 不能存在两条未结束记录
--    (靠 idx_pm_dedup + 应用层校验)
--  - 软删:与已有表风格一致(SoftDeletableEntity)
--  - 触发器:pmo.fn_set_updated_at

-- 1) 成员角色字典
CREATE TABLE member_role (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(32) UNIQUE NOT NULL,     -- PM / ASSISTANT / DEV / QA ...
    name            VARCHAR(64) NOT NULL,            -- 项目经理
    description     VARCHAR(256),
    sort_order      INT NOT NULL DEFAULT 0,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_mr_deleted ON member_role(deleted);
COMMENT ON TABLE  member_role IS '项目成员角色字典';
COMMENT ON COLUMN member_role.code IS '角色编码(全大写,PM/ASSISTANT/DEV/QA/BA/CFG/ARCH)';
COMMENT ON COLUMN member_role.name IS '角色显示名(项目经理/项目助理/开发工程师...)';

-- 初始化 7 个标准项目角色(按 P2-项目管理 需求:项目经理/助理/开发/测试/BA/配置/架构)
INSERT INTO member_role (code, name, description, sort_order) VALUES
  ('PM',       '项目经理',     '负责项目整体计划/协调/风险/汇报', 10),
  ('ASSISTANT','项目助理',     '协助项目经理处理项目日常事务(周报、会议、文档)', 20),
  ('ARCH',     '架构师',       '负责系统架构设计、技术选型、关键技术攻关', 30),
  ('BA',       '需求分析师',   '负责需求收集、分析、原型、验收', 40),
  ('DEV',      '开发工程师',   '负责编码、单元测试、技术实现', 50),
  ('QA',       '测试工程师',   '负责测试用例、缺陷跟踪、验收测试', 60),
  ('CFG',      '配置管理员',   '负责环境/版本/配置/发布管理', 70)
ON CONFLICT (code) DO NOTHING;
COMMENT ON COLUMN member_role.sort_order IS '前端下拉排序(数字越小越靠前)';

-- 2) 项目组成员
CREATE TABLE project_member (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    role_id         BIGINT NOT NULL REFERENCES member_role(id),
    user_id         BIGINT REFERENCES app_user(id),       -- 可空:外部成员
    member_name     VARCHAR(64) NOT NULL,                 -- 冗余:内部 user 取 fullName,外部人员手填
    is_external     BOOLEAN NOT NULL DEFAULT FALSE,      -- TRUE=外部人员
    join_date       DATE NOT NULL,                        -- 参与开始时间
    leave_date      DATE,                                  -- 参与结束时间(可空=仍在项目中)
    allocation_pct  INT NOT NULL DEFAULT 100,            -- 投入比例 0-100(为后续工时/资源模块预留)
    remark          VARCHAR(256),                          -- 备注
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_pm_project ON project_member(project_id);
CREATE INDEX idx_pm_role    ON project_member(role_id);
CREATE INDEX idx_pm_user    ON project_member(user_id);
CREATE INDEX idx_pm_deleted ON project_member(deleted);
COMMENT ON TABLE  project_member IS '项目组成员(一个项目可有多人,每人一个角色)';
COMMENT ON COLUMN project_member.id IS '主键';
COMMENT ON COLUMN project_member.project_id IS '所属项目 ID';
COMMENT ON COLUMN project_member.role_id IS '项目角色 ID(字典:member_role)';
COMMENT ON COLUMN project_member.user_id IS '系统用户 ID(可空:外部人员只填姓名)';
COMMENT ON COLUMN project_member.member_name IS '成员姓名(冗余字段,内部 user 取 fullName,外部人员手填)';
COMMENT ON COLUMN project_member.is_external IS '是否外部人员(FALSE=本系统用户 TRUE=客户/外包)';
COMMENT ON COLUMN project_member.join_date IS '项目参与开始日期';
COMMENT ON COLUMN project_member.leave_date IS '项目参与结束日期(可空=仍在项目中)';
COMMENT ON COLUMN project_member.allocation_pct IS '投入比例 0-100(为后续资源/工时模块预留)';
COMMENT ON COLUMN project_member.remark IS '备注';

-- 3) updated_at 触发器
DROP TRIGGER IF EXISTS trg_mr_updated_at ON member_role;
CREATE TRIGGER trg_mr_updated_at BEFORE UPDATE ON member_role
    FOR EACH ROW EXECUTE FUNCTION pmo.fn_set_updated_at();

DROP TRIGGER IF EXISTS trg_pm_updated_at ON project_member;
CREATE TRIGGER trg_pm_updated_at BEFORE UPDATE ON project_member
    FOR EACH ROW EXECUTE FUNCTION pmo.fn_set_updated_at();

-- 4) 业务约束: leave_date >= join_date(应用层 + DB 层 check 双保险)
ALTER TABLE project_member
    ADD CONSTRAINT chk_pm_date_range
    CHECK (leave_date IS NULL OR leave_date >= join_date);

ALTER TABLE project_member
    ADD CONSTRAINT chk_pm_allocation
    CHECK (allocation_pct BETWEEN 0 AND 100);
