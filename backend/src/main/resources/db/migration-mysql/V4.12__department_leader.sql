-- ============================================================
-- V4.12 (MySQL): 部门补强
--   1. department 加 leader_user_id (部门负责人)
--   2. 不强制 NOT NULL — 历史部门无负责人时允许 NULL
--   3. ON DELETE SET NULL — 负责人离职/调岗后,部门仍保留
-- ============================================================

ALTER TABLE department
    ADD COLUMN leader_user_id BIGINT NULL AFTER enabled,
    ADD CONSTRAINT fk_dept_leader
        FOREIGN KEY (leader_user_id) REFERENCES app_user(id) ON DELETE SET NULL;

CREATE INDEX idx_dept_leader ON department(leader_user_id);

-- 备注
ALTER TABLE department MODIFY COLUMN leader_user_id BIGINT
    NULL COMMENT '部门负责人 user_id(离职/调岗后自动 SET NULL)';