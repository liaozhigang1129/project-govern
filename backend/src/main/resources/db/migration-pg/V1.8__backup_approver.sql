-- P1.5 收尾 — 立项审批 P0 风险: Backup Approver (PG 版)
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS backup_user_id BIGINT NULL;
ALTER TABLE approval_record ADD COLUMN IF NOT EXISTS on_behalf_of_user_id BIGINT NULL;
CREATE INDEX IF NOT EXISTS idx_backup_user ON app_user(backup_user_id);
