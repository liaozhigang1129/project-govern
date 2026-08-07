-- ============================================================
-- V2.9 角色管理增强 (PG 版)
-- 1) role 表加 enabled / sort_order / updated_at
-- 2) 把现有 5 个内置角色加上 sort_order
-- ============================================================

ALTER TABLE role
  ADD COLUMN IF NOT EXISTS enabled    BOOLEAN     NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS sort_order INT         NOT NULL DEFAULT 100,
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 内置角色排序 (PM 排最前, VIEWER 排最后)
UPDATE role SET sort_order = 10  WHERE code = 'PM';
UPDATE role SET sort_order = 20  WHERE code = 'DEPT_LEAD';
UPDATE role SET sort_order = 30  WHERE code = 'PMO_ADMIN';
UPDATE role SET sort_order = 40  WHERE code = 'EXEC';
UPDATE role SET sort_order = 50  WHERE code = 'VIEWER';

CREATE INDEX IF NOT EXISTS idx_role_enabled ON role(enabled) WHERE enabled = true;
CREATE INDEX IF NOT EXISTS idx_role_sort    ON role(sort_order);
