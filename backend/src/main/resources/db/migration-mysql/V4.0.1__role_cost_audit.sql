-- V4.0.1 补齐 role_cost_default 审计列 (AuditableEntity 实体要求)
-- 项目历史上 V4.0 没为这个字典表加审计列
ALTER TABLE role_cost_default
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
