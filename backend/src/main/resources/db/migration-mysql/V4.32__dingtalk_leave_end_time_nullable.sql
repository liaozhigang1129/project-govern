-- ============================================================
-- V4.32 钉钉请休假 end_time 改为可空 (MySQL 版, 与 PG V4.32 对齐)
-- 原因: 部分审批(加班/补卡)无明确结束时间,旧 NOT NULL 会导致整批 leave 全部
--       rollback, 同步一直 FAILED. 改为可空后业务展示用 - 表示。
-- ============================================================
ALTER TABLE dingtalk_leave MODIFY COLUMN end_time DATETIME(6) NULL;
