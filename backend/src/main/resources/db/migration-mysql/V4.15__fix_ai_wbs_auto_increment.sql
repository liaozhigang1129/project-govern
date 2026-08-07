-- ============================================================
-- V4.15 修复:补 AUTO_INCREMENT (Flyway 历史 migration 漏了)
--   bug: V3.0__initiation_full_workflow.sql + V2.5__wbs.sql 创建 approval_record /
--        initiation_ai_wbs_draft / milestone / wbs_task / wbs_assignment / budget_line
--        时漏了 AUTO_INCREMENT,导致 INSERT 时报 "Field 'id' doesn't have a default value"
--   fix: 显式 ALTER ... MODIFY id BIGINT NOT NULL AUTO_INCREMENT
-- ============================================================

ALTER TABLE approval_record          MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE initiation_ai_wbs_draft  MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE milestone                MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE wbs_task                 MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE wbs_assignment           MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE budget_line              MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
