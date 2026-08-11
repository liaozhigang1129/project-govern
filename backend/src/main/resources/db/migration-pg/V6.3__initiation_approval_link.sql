-- ============================================================
-- V6.3 project_initiation.approval_instance_id 关联 (PG 版)
-- ============================================================

ALTER TABLE project_initiation ADD COLUMN IF NOT EXISTS approval_instance_id BIGINT NULL;
CREATE INDEX IF NOT EXISTS idx_initiation_approval_instance ON project_initiation(approval_instance_id);