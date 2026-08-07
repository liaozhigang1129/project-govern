-- V4.17 立项基础信息补全 (MySQL 版)
-- 业务诉求与字段说明同 V4.17 pg 版

-- 1) 立项表加列
ALTER TABLE project_initiation
    ADD COLUMN pm_user_id               BIGINT NULL                          COMMENT '项目经理'                       AFTER applicant_id,
    ADD COLUMN project_type_code        VARCHAR(32) NULL                     COMMENT '项目类型 code (DELIVERY/SELF_RD/INNER_PRODUCT/RD)' AFTER department_id,
    ADD COLUMN project_level_code       VARCHAR(32) NULL                     COMMENT '项目级别 code (S/A/B/C)' AFTER project_type_code,
    ADD COLUMN expected_gross_margin_pct DECIMAL(5,2) NULL                   COMMENT '预估毛利率 %, 0~100' AFTER budget_estimate,
    ADD COLUMN planned_launch_date      DATE NULL                            COMMENT '计划上线时间' AFTER planned_end;

-- 2) 注释
ALTER TABLE project_initiation
    MODIFY COLUMN planned_start DATE NULL COMMENT '入场时间 (kickoff)',
    MODIFY COLUMN planned_end   DATE NULL COMMENT '项目结束时间';

-- 3) FK / 索引
CREATE INDEX idx_initiation_pm            ON project_initiation(pm_user_id);
CREATE INDEX idx_initiation_project_level ON project_initiation(project_level_code);

-- 4) 项目表同步加列
ALTER TABLE project
    ADD COLUMN project_level_code        VARCHAR(32) NULL                     COMMENT '项目级别 code (S/A/B/C)' AFTER type_id,
    ADD COLUMN expected_gross_margin_pct DECIMAL(5,2) NULL                   COMMENT '预估毛利率 %, 0~100' AFTER budget_estimate,
    ADD COLUMN planned_launch_date       DATE NULL                            COMMENT '计划上线时间' AFTER plan_end_date;

-- 5) 注释
ALTER TABLE project
    MODIFY COLUMN pm_user_id      BIGINT NULL COMMENT '项目经理 (PM)',
    MODIFY COLUMN plan_start_date DATE NULL COMMENT '入场时间 (kickoff)',
    MODIFY COLUMN plan_end_date   DATE NULL COMMENT '项目结束时间';

-- 6) 新增项目级别字典
CREATE TABLE IF NOT EXISTS project_level (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    code        VARCHAR(32) NOT NULL,
    name        VARCHAR(64) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    description VARCHAR(256) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_level_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目级别字典 (S/A/B/C)';

INSERT INTO project_level(code, name, sort_order, description) VALUES
    ('S', 'S 级 - 战略级', 10, '公司战略级 / 千万级以上 / 跨 BU 协同项目'),
    ('A', 'A 级 - 重点级', 20, 'BU 重点项目 / 百万级以上'),
    ('B', 'B 级 - 标准级', 30, '常规交付项目'),
    ('C', 'C 级 - 轻量级', 40, '小项目 / 内部工具 / PoC')
ON DUPLICATE KEY UPDATE name = VALUES(name), sort_order = VALUES(sort_order), description = VALUES(description);
