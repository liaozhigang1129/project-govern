-- ============================================================
-- V4.18 (PG): 补充部门表缺失的字段 (dingtalk_parent_id)
-- ============================================================

ALTER TABLE department 
    ADD COLUMN IF NOT EXISTS dingtalk_parent_id BIGINT,
    ADD COLUMN IF NOT EXISTS tree_path VARCHAR(512),
    ADD COLUMN IF NOT EXISTS tree_level SMALLINT DEFAULT 0;

-- 索引
CREATE INDEX IF NOT EXISTS idx_dept_dingtalk_parent ON department(dingtalk_parent_id);
CREATE INDEX IF NOT EXISTS idx_dept_tree_path ON department(tree_path);
CREATE INDEX IF NOT EXISTS idx_dept_tree_level ON department(tree_level);
