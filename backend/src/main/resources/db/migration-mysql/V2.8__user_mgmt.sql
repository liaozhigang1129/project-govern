-- ============================================================
-- V2.8 用户管理增强 (MySQL 版)
-- 与 PG 版 V2.8__user_mgmt.sql 同构, 差异:
--   - TIMESTAMPTZ → DATETIME(6)
--   - BOOLEAN → TINYINT(1)
--   - 去掉 IF NOT EXISTS (MySQL 8 ADD COLUMN IF NOT EXISTS 8.0.29+ 支持, 这里用更稳的写法)
-- ============================================================

-- ① 字段扩展 (用存储过程判存在, 避免 1060 Duplicate column)
SET @db = DATABASE();

SET @stmt = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'app_user' AND COLUMN_NAME = 'login_fail_count') = 0,
  'ALTER TABLE app_user ADD COLUMN login_fail_count INT NOT NULL DEFAULT 0',
  'SELECT 1'
)); PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'app_user' AND COLUMN_NAME = 'locked_until') = 0,
  'ALTER TABLE app_user ADD COLUMN locked_until DATETIME(6) NULL',
  'SELECT 1'
)); PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'app_user' AND COLUMN_NAME = 'password_changed_at') = 0,
  'ALTER TABLE app_user ADD COLUMN password_changed_at DATETIME(6) NULL',
  'SELECT 1'
)); PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'app_user' AND COLUMN_NAME = 'must_change_password') = 0,
  'ALTER TABLE app_user ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0',
  'SELECT 1'
)); PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'app_user' AND COLUMN_NAME = 'last_login_ip') = 0,
  'ALTER TABLE app_user ADD COLUMN last_login_ip VARCHAR(45) NULL',
  'SELECT 1'
)); PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ② 多角色关联表
-- V1.1 已建老 user_role (id, user_id, role_id, created_at), 与 V2.8 期望结构不兼容
-- 这里 drop + create, 丢失老数据. 后续 init step 会从 app_user.primary_role 重新灌入
DROP TABLE IF EXISTS user_role;
CREATE TABLE user_role (
    user_id    BIGINT      NOT NULL,
    role_id    BIGINT      NOT NULL,
    granted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    granted_by BIGINT      NULL,
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_role_user (user_id),
    KEY idx_user_role_role (role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role(id)     ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色多对多';

-- ③ 初始化:把现有 primary_role 灌进 user_role
INSERT IGNORE INTO user_role (user_id, role_id, granted_at, granted_by)
SELECT id, primary_role_id, NOW(6), 1
FROM app_user
WHERE primary_role_id IS NOT NULL;
