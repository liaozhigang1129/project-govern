-- ============================================================
-- V2.8 用户管理增强 (PG 版)
-- 1) app_user 加字段:登录失败计数/锁定/最后登录 IP/密码变更时间/强制改密
-- 2) 新增 user_role_assignments 多对多表
--    (表名避免与老的 P1.5 时期 user_role 表冲突 — 那张已废弃但被 JPA validate 看到)
-- 3) 初始化:把现有 primary_role_id 同步进 user_role_assignments
-- ============================================================

-- ① 字段扩展 (与 AppUser 实体字段严格一致)
ALTER TABLE app_user
  ADD COLUMN IF NOT EXISTS login_fail_count      INT          NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS locked_until          TIMESTAMPTZ  NULL,
  ADD COLUMN IF NOT EXISTS last_login_ip         VARCHAR(64)  NULL,
  ADD COLUMN IF NOT EXISTS password_changed_at   TIMESTAMPTZ  NULL,
  ADD COLUMN IF NOT EXISTS must_change_password  BOOLEAN      NOT NULL DEFAULT false;

-- ② 多角色关联表 (user_role_assignments)
CREATE TABLE IF NOT EXISTS user_role_assignments (
    user_id    BIGINT      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_id    BIGINT      NOT NULL REFERENCES role(id)     ON DELETE CASCADE,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    granted_by BIGINT      REFERENCES app_user(id),
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX IF NOT EXISTS idx_ura_user ON user_role_assignments(user_id);
CREATE INDEX IF NOT EXISTS idx_ura_role ON user_role_assignments(role_id);

-- ③ 初始化:把现有 primary_role 灌进 user_role_assignments
INSERT INTO user_role_assignments (user_id, role_id, granted_at, granted_by)
SELECT id, primary_role_id, NOW(), 1
FROM app_user
WHERE primary_role_id IS NOT NULL
ON CONFLICT (user_id, role_id) DO NOTHING;
