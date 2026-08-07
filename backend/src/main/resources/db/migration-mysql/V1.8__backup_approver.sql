-- P1.5 收尾 — 立项审批 P0 风险: Backup Approver
-- 1) app_user.backup_user_id: 主审批人缺席时 fallback
-- 2) approval_record.on_behalf_of_user_id: 代审原审批人(审计)

-- 用 SET @cnt + IF 模式保证幂等
SET @cnt = (SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema=DATABASE() AND table_name='app_user' AND column_name='backup_user_id');
SET @sql = IF(@cnt=0, 'ALTER TABLE app_user ADD COLUMN backup_user_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @cnt = (SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema=DATABASE() AND table_name='approval_record' AND column_name='on_behalf_of_user_id');
SET @sql = IF(@cnt=0, 'ALTER TABLE approval_record ADD COLUMN on_behalf_of_user_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 为 backup_user_id 建索引(加速 fallback 查找)
SET @cnt = (SELECT COUNT(*) FROM information_schema.statistics
            WHERE table_schema=DATABASE() AND table_name='app_user' AND index_name='idx_backup_user');
SET @sql = IF(@cnt=0, 'CREATE INDEX idx_backup_user ON app_user(backup_user_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
