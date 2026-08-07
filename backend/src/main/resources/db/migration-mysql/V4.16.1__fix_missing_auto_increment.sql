-- ============================================================
-- V4.17 修复: 补 AUTO_INCREMENT (ColumnCommentMigrator 历史漏掉)
--
-- 背景 (与 V4.15 同根):
--   ColumnCommentMigrator 拼 ALTER TABLE ... MODIFY COLUMN 时, 用
--   `CONCAT(COLUMN_TYPE, ' NOT NULL', ' DEFAULT ...', EXTRA)`, 但
--   当 EXTRA 为空时, `def` 末尾无空格分隔, 后续 `extraClause` 拼上去会
--   形成 "NOT NULLauto_increment" 缺空格, MySQL 解析失败 → 跳过 →
--   AUTO_INCREMENT 永远没补上。
--
-- 触发条件:
--   1) 表已建好但 id 列 *当时* 没 AUTO_INCREMENT (e.g. V1.7 直接
--      IMPORT 出来的, 或 init 容器 pg 拷过来时缺 EXTRA)
--   2) 后续代码 `INSERT` 时报 "Field 'id' doesn't have a default value"
--   3) 异步通知路径 (NotifWriteService) 失败被标 non-fatal, HTTP 仍 200,
--      但 SSE 推送会缺事件, 用户无感知
--
-- 修复:
--   1) 本 migration 显式补 AUTO_INCREMENT (V4.15 风格)
--   2) ColumnCommentMigrator 同步: 拼 def 时保证末尾空格, 并把 auto_increment
--      / DEFAULT_GENERATED 关键字加进去
--
-- 范围: 6 张业务表 (ACT_/FLW_ 是 flowable changelog 不需, milestone_phase 是
--       字典表 id 固定 1-7 也不需)
-- ============================================================

ALTER TABLE notification          MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE revoked_token         MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE timesheet_entry       MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE user_im_binding       MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE user_im_quiet_hours   MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
