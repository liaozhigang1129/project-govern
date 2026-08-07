-- ============================================================
-- V4.15 修复:补 AUTO_INCREMENT (Flyway 历史 migration 漏了)
--   PG 版:approval_record / initiation_ai_wbs_draft / milestone / wbs_task /
--         wbs_assignment / budget_line 缺 SERIAL/BIGSERIAL
--   同样会触发 "id doesn't have a default value" 错
--   fix: BIGINT → BIGSERIAL (隐式 NOT NULL + 自增)
-- ============================================================

ALTER TABLE approval_record          ALTER COLUMN id SET DATA TYPE BIGSERIAL;
ALTER TABLE initiation_ai_wbs_draft  ALTER COLUMN id SET DATA TYPE BIGSERIAL;
ALTER TABLE milestone                ALTER COLUMN id SET DATA TYPE BIGSERIAL;
ALTER TABLE wbs_task                 ALTER COLUMN id SET DATA TYPE BIGSERIAL;
ALTER TABLE wbs_assignment           ALTER COLUMN id SET DATA TYPE BIGSERIAL;
ALTER TABLE budget_line              ALTER COLUMN id SET DATA TYPE BIGSERIAL;
