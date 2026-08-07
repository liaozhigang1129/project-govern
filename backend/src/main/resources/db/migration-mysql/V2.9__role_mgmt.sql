-- ============================================================
-- V2.9 角色管理增强 (MySQL 版)
-- 与 PG 版同构
-- ============================================================

-- 字段扩展 (用存储过程判存在, 避免 1060)
SET @db = DATABASE();

SET @stmt = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'role' AND COLUMN_NAME = 'enabled') = 0,
  'ALTER TABLE role ADD COLUMN enabled TINYINT(1) NOT NULL DEFAULT 1',
  'SELECT 1'
)); PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'role' AND COLUMN_NAME = 'sort_order') = 0,
  'ALTER TABLE role ADD COLUMN sort_order INT NOT NULL DEFAULT 100',
  'SELECT 1'
)); PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'role' AND COLUMN_NAME = 'updated_at') = 0,
  'ALTER TABLE role ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)',
  'SELECT 1'
)); PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

UPDATE role SET sort_order = 10  WHERE code = 'PM';
UPDATE role SET sort_order = 20  WHERE code = 'DEPT_LEAD';
UPDATE role SET sort_order = 30  WHERE code = 'PMO_ADMIN';
UPDATE role SET sort_order = 40  WHERE code = 'EXEC';
UPDATE role SET sort_order = 50  WHERE code = 'VIEWER';

CREATE INDEX idx_role_enabled ON role(enabled);
CREATE INDEX idx_role_sort    ON role(sort_order);
