-- ============================================================
-- V4.12 (PG): 部门补强 - leader_user_id
-- ============================================================

ALTER TABLE department
    ADD COLUMN leader_user_id BIGINT NULL,
    ADD CONSTRAINT fk_dept_leader
        FOREIGN KEY (leader_user_id) REFERENCES app_user(id) ON DELETE SET NULL;

CREATE INDEX idx_dept_leader ON department(leader_user_id);

COMMENT ON COLUMN department.leader_user_id IS '部门负责人 user_id (离职/调岗后自动 SET NULL)';