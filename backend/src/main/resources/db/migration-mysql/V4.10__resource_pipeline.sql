-- P6 资源管道: 人员技能 + 调度事件
-- 用于资源管理协同大盘

CREATE TABLE resource_skill (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    skill_code  VARCHAR(32) NOT NULL COMMENT 'JAVA / REACT / AWS / PM ...',
    skill_level TINYINT NOT NULL DEFAULT 3 COMMENT '1=初学 2=初级 3=熟练 4=高级 5=专家',
    certified   TINYINT(1) NOT NULL DEFAULT 0,
    cert_date   DATE,
    years_exp   DECIMAL(4,1),
    remark      VARCHAR(256),
    deleted     TINYINT(1) NOT NULL DEFAULT 0,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_rs_user (user_id),
    INDEX idx_rs_skill (skill_code),
    INDEX idx_rs_level (skill_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='P6 人员技能标签';

CREATE TABLE resource_pipeline_event (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    project_id      BIGINT NOT NULL,
    from_status     VARCHAR(16),
    to_status       VARCHAR(16) NOT NULL,
    allocation_pct  DECIMAL(5,2) NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE,
    decided_by      BIGINT NOT NULL,
    reason          VARCHAR(256),
    deleted         TINYINT(1) NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_rpe_user (user_id),
    INDEX idx_rpe_project (project_id),
    INDEX idx_rpe_dates (start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='P6 资源管道事件';
