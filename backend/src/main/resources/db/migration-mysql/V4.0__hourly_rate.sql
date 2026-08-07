-- ============================================================
-- V4.0: 财务精细化成本管控 - 工时→成本引擎 (MySQL 版)
-- 设计:与 PG 版 (V4.0__hourly_rate.sql) 同构
--   1) app_user 加 default_hourly_rate NUMERIC(10,2) 兜底  (幂等)
--   2) hourly_rate 已存在 → 升级 (添加 user_id/end_month/created_by/remark,
--                                 把 role→role_code, effective_date→effective_month)
--   3) role_cost_default 新增 (6 角色档字典)
-- ============================================================

-- ① app_user 加 default_hourly_rate 兜底 (幂等)
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'app_user'
    AND COLUMN_NAME  = 'default_hourly_rate'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE app_user ADD COLUMN default_hourly_rate NUMERIC(10,2) NOT NULL DEFAULT 0',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ② 升级 hourly_rate 表 (添加人/结束月/创建人/备注, 改名兼容)
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='hourly_rate' AND COLUMN_NAME='user_id');
SET @sql := IF(@col = 0,
  'ALTER TABLE hourly_rate ADD COLUMN user_id BIGINT NULL',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='hourly_rate' AND COLUMN_NAME='end_month');
SET @sql := IF(@col = 0,
  'ALTER TABLE hourly_rate ADD COLUMN end_month DATE NULL',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='hourly_rate' AND COLUMN_NAME='created_by');
SET @sql := IF(@col = 0,
  'ALTER TABLE hourly_rate ADD COLUMN created_by BIGINT NULL',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='hourly_rate' AND COLUMN_NAME='remark');
SET @sql := IF(@col = 0,
  'ALTER TABLE hourly_rate ADD COLUMN remark VARCHAR(256) NULL',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- effective_date → effective_month (兼容老数据)
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='hourly_rate' AND COLUMN_NAME='effective_date');
SET @col2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='hourly_rate' AND COLUMN_NAME='effective_month');
SET @sql := IF(@col = 1 AND @col2 = 0,
  'ALTER TABLE hourly_rate CHANGE COLUMN effective_date effective_month DATE NOT NULL',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- role → role_code
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='hourly_rate' AND COLUMN_NAME='role');
SET @col2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='hourly_rate' AND COLUMN_NAME='role_code');
SET @sql := IF(@col = 1 AND @col2 = 0,
  'ALTER TABLE hourly_rate CHANGE COLUMN role role_code VARCHAR(32) NOT NULL',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ③ role_code 长度调整 (旧 VARCHAR(64) → VARCHAR(32))
ALTER TABLE hourly_rate MODIFY COLUMN role_code VARCHAR(32) NOT NULL;

-- ④ role_cost_default (6 角色档字典)
CREATE TABLE IF NOT EXISTS role_cost_default (
    code        VARCHAR(32)  NOT NULL PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    rate        NUMERIC(10,2) NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO role_cost_default (code, name, rate, sort_order) VALUES
    ('ARCH',   '架构师',  800.00, 1),
    ('DEV',    '开发',    600.00, 2),
    ('TEST',   '测试',    500.00, 3),
    ('PM',     'PM',      700.00, 4),
    ('BA',     'BA',      600.00, 5),
    ('OPS',    '运维',    550.00, 6)
ON DUPLICATE KEY UPDATE
    name       = VALUES(name),
    rate       = VALUES(rate),
    sort_order = VALUES(sort_order);

-- ④.5 数据兼容: hourly_rate 老 role_code (AR/FR/SR 等) 改为 PM (role_cost_default 必有)
-- 防止 FK 1452 错误 (add constraint 时 MySQL 校验现有数据)
UPDATE hourly_rate hr
LEFT JOIN role_cost_default rc ON hr.role_code = rc.code
SET hr.role_code = 'PM'
WHERE rc.code IS NULL;

-- ⑤ hourly_rate.role_code 外键 (如未建)
SET @fk_exists := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME        = 'hourly_rate'
    AND CONSTRAINT_NAME   = 'hourly_rate_role_code_fkey'
);
SET @sql := IF(@fk_exists = 0,
  'ALTER TABLE hourly_rate ADD CONSTRAINT hourly_rate_role_code_fkey FOREIGN KEY (role_code) REFERENCES role_cost_default(code)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ⑥ hourly_rate.user_id 外键 (如未建)
SET @fk_exists := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME        = 'hourly_rate'
    AND CONSTRAINT_NAME   = 'hourly_rate_user_id_fkey'
);
SET @sql := IF(@fk_exists = 0,
  'ALTER TABLE hourly_rate ADD CONSTRAINT hourly_rate_user_id_fkey FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ⑦ hourly_rate.created_by 外键 (如未建)
SET @fk_exists := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME        = 'hourly_rate'
    AND CONSTRAINT_NAME   = 'hourly_rate_created_by_fkey'
);
SET @sql := IF(@fk_exists = 0,
  'ALTER TABLE hourly_rate ADD CONSTRAINT hourly_rate_created_by_fkey FOREIGN KEY (created_by) REFERENCES app_user(id)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ⑧ 索引
CREATE INDEX idx_hourly_rate_user_month ON hourly_rate(user_id, effective_month);
CREATE INDEX idx_hourly_rate_role_month ON hourly_rate(role_code, effective_month);