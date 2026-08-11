-- ============================================================
-- V6.2 timesheet_week.approval_instance_id 关联 (PostgreSQL 版)
-- 与 MySQL 版 V6.2__timesheet_approval_link.sql 同构
-- ============================================================

ALTER TABLE timesheet_week ADD COLUMN IF NOT EXISTS approval_instance_id BIGINT NULL;
CREATE INDEX IF NOT EXISTS idx_timesheet_approval_instance ON timesheet_week(approval_instance_id);