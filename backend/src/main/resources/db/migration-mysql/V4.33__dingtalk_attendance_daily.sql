-- ============================================================
-- V4.33 钉钉考勤: 改为"一天一条"聚合表
-- 业务规则 (来自产品, 阶段 0 决策):
--   0.1 唯一粒度: (dingtalk_userid, work_date)
--   0.2 老表 dingtalk_attendance 保留只读, 给前端"详情/历史原始打卡"用 (新表 raw_record_ids 回链)
-- 业务必备字段: 上班时间、下班时间、打卡地、是否补卡、是否异常 (迟到/早退/...)
-- 尽量填: 当天所属项目 (从 timesheet_entry 关联)
-- 每次同步按 (userid, work_date) upsert, 严格去重
-- 数据源: 钉钉 /attendance/listRecord 原始多条记录, 聚合 OnDuty(最早) + OffDuty(最晚)
-- ============================================================

-- 1) 新表: 一人一天一条
CREATE TABLE IF NOT EXISTS dingtalk_attendance_daily (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    -- 聚合维度
    userid              VARCHAR(64)   NOT NULL                            COMMENT '钉钉 userid',
    work_date           DATE          NOT NULL                            COMMENT '工作日 (本地时区)',
    -- 上班 (兼容 check_type ∈ {OnDuty, Before})
    on_duty_plan        DATETIME(6)   NULL                                COMMENT '计划上班打卡时间',
    on_duty_actual      DATETIME(6)   NULL                                COMMENT '实际上班打卡时间 (取最早一次)',
    on_duty_result      VARCHAR(16)   NOT NULL DEFAULT ''                 COMMENT 'Normal/Tardy/Early/SeriousTardy/NotSigned',
    on_duty_source      VARCHAR(16)   NOT NULL DEFAULT ''                 COMMENT 'USER/SYSTEM/BT/FACE/MAP',
    on_duty_location    VARCHAR(256)  NOT NULL DEFAULT ''                 COMMENT '上班打卡地址 (新数据从钉钉 locationResult/location 填; 老数据回填空)',
    on_duty_location_method VARCHAR(16) NOT NULL DEFAULT ''               COMMENT '定位方式: MAP/BEACON/...',
    on_duty_location_result VARCHAR(16) NOT NULL DEFAULT ''               COMMENT 'Normal/Outside/Invalid',
    -- 下班 (兼容 check_type ∈ {OffDuty, After})
    off_duty_plan       DATETIME(6)   NULL                                COMMENT '计划下班打卡时间',
    off_duty_actual     DATETIME(6)   NULL                                COMMENT '实际下班打卡时间 (取最晚一次)',
    off_duty_result     VARCHAR(16)   NOT NULL DEFAULT ''                 COMMENT 'Normal/Early/SeriousEarly/NotSigned',
    off_duty_source     VARCHAR(16)   NOT NULL DEFAULT ''                 COMMENT 'USER/SYSTEM/BT/FACE/MAP',
    off_duty_location   VARCHAR(256)  NOT NULL DEFAULT ''                 COMMENT '下班打卡地址',
    off_duty_location_method VARCHAR(16) NOT NULL DEFAULT '',
    off_duty_location_result VARCHAR(16) NOT NULL DEFAULT '',
    -- 汇总标记
    check_count         INT           NOT NULL DEFAULT 0                  COMMENT '当天原始打卡次数 (OnDuty+OffDuty 合计, 审计用)',
    is_makeup           TINYINT(1)    NOT NULL DEFAULT 0                  COMMENT '1=当天有补卡 (老表无 sourceType, 仅新同步数据可填)',
    is_abnormal         TINYINT(1)    NOT NULL DEFAULT 0                  COMMENT '1=任一打卡 timeResult ∈ {Tardy,Early,SeriousTardy,NotSigned,...}',
    abnormal_types      VARCHAR(128)  NOT NULL DEFAULT ''                 COMMENT '异常类型汇总 "迟到;早退" 留痕',
    -- 项目 (从 timesheet_entry JOIN, 在 service 聚合时填; 老数据回填留空)
    project_ids         VARCHAR(128)  NOT NULL DEFAULT ''                 COMMENT '当天填写的项目 ID, 多个用 , 分隔',
    project_names       VARCHAR(512)  NOT NULL DEFAULT ''                 COMMENT '当天填写的项目名, 多个用 , 分隔',
    -- 关联 PMO
    pmo_user_id         BIGINT        NULL                                COMMENT 'app_user.id',
    user_name           VARCHAR(64)   NULL,
    department_id       BIGINT        NULL,
    -- 同步 / 审计
    raw_record_ids      TEXT          NULL                                COMMENT '当天原始打卡 record_id 数组 JSON: ["biz1","biz2"], 用于详情反查老表',
    dingtalk_updated_at DATETIME(6)   NULL                                COMMENT '钉钉侧该用户当天最新更新时间 (取 MAX)',
    synced_at           DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- 通用
    deleted             TINYINT(1)    NOT NULL DEFAULT 0,
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    -- 业务唯一性
    UNIQUE KEY uq_daily_user_date (userid, work_date),
    INDEX idx_dad_userid    (userid),
    INDEX idx_dad_work_date (work_date),
    INDEX idx_dad_pmo_user  (pmo_user_id),
    INDEX idx_dad_dept      (department_id),
    INDEX idx_dad_is_abnormal (is_abnormal),
    INDEX idx_dad_is_makeup   (is_makeup),
    INDEX idx_dad_deleted   (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='钉钉考勤每日聚合 (一人一天一条) V4.33';

-- 2) 迁移老数据 (dingtalk_attendance) → 新表
--    老表一条=单次打卡, 新表一天一条. 取 OnDuty 最早 + OffDuty 最晚
--    check_type 兼容: OnDuty/OffDuty (老钉钉) 和 Before/After (新钉钉)
--    老表没有 location 文本/坐标/sourceType/makeup 标记, 这些字段回填空或 0
INSERT INTO dingtalk_attendance_daily (
    userid, work_date,
    on_duty_plan, on_duty_actual, on_duty_result, on_duty_source,
    on_duty_location_method, on_duty_location_result,
    off_duty_plan, off_duty_actual, off_duty_result, off_duty_source,
    off_duty_location_method, off_duty_location_result,
    check_count, is_abnormal, abnormal_types,
    pmo_user_id, user_name, department_id,
    raw_record_ids, dingtalk_updated_at
)
SELECT
    userid, work_date,
    -- 上班: 最早一次 OnDuty/Before
    -- 实测 source 取值: MAP/ATM/WIFI/OTHER (没有 USER/SYSTEM/BT/FACE), 已知空字符串
    MIN(CASE WHEN check_type IN ('OnDuty','Before') THEN plan_time   END)   AS on_duty_plan,
    MIN(CASE WHEN check_type IN ('OnDuty','Before') THEN actual_time END)   AS on_duty_actual,
    COALESCE(SUBSTRING_INDEX(
        GROUP_CONCAT(CASE WHEN check_type IN ('OnDuty','Before') THEN time_result END
                          ORDER BY actual_time ASC SEPARATOR ','), ',', 1), '') AS on_duty_result,
    COALESCE(SUBSTRING_INDEX(
        GROUP_CONCAT(CASE WHEN check_type IN ('OnDuty','Before') THEN source    END
                          ORDER BY actual_time ASC SEPARATOR ','), ',', 1), '') AS on_duty_source,
    COALESCE(SUBSTRING_INDEX(
        GROUP_CONCAT(CASE WHEN check_type IN ('OnDuty','Before') THEN location_method END
                          ORDER BY actual_time ASC SEPARATOR ','), ',', 1), '') AS on_duty_location_method,
    COALESCE(SUBSTRING_INDEX(
        GROUP_CONCAT(CASE WHEN check_type IN ('OnDuty','Before') THEN location_result END
                          ORDER BY actual_time ASC SEPARATOR ','), ',', 1), '') AS on_duty_location_result,
    -- 下班: 最晚一次 OffDuty/After
    MAX(CASE WHEN check_type IN ('OffDuty','After') THEN plan_time   END)   AS off_duty_plan,
    MAX(CASE WHEN check_type IN ('OffDuty','After') THEN actual_time END)   AS off_duty_actual,
    COALESCE(SUBSTRING_INDEX(
        GROUP_CONCAT(CASE WHEN check_type IN ('OffDuty','After') THEN time_result END
                          ORDER BY actual_time DESC SEPARATOR ','), ',', 1), '') AS off_duty_result,
    COALESCE(SUBSTRING_INDEX(
        GROUP_CONCAT(CASE WHEN check_type IN ('OffDuty','After') THEN source    END
                          ORDER BY actual_time DESC SEPARATOR ','), ',', 1), '') AS off_duty_source,
    COALESCE(SUBSTRING_INDEX(
        GROUP_CONCAT(CASE WHEN check_type IN ('OffDuty','After') THEN location_method END
                          ORDER BY actual_time DESC SEPARATOR ','), ',', 1), '') AS off_duty_location_method,
    COALESCE(SUBSTRING_INDEX(
        GROUP_CONCAT(CASE WHEN check_type IN ('OffDuty','After') THEN location_result END
                          ORDER BY actual_time DESC SEPARATOR ','), ',', 1), '') AS off_duty_location_result,
    -- 异常 / 计数
    -- 实测 time_result 取值: Normal / Late / Early / '' (空)
    COUNT(*)                                                                AS check_count,
    IF(SUM(CASE WHEN time_result IN ('Late','Early','SeriousLate','SeriousEarly','NotSigned') THEN 1 ELSE 0 END) > 0, 1, 0) AS is_abnormal,
    COALESCE(GROUP_CONCAT(DISTINCT CASE WHEN time_result IN ('Late','Early','SeriousLate','SeriousEarly','NotSigned') THEN time_result END
                              SEPARATOR ';'), '')                            AS abnormal_types,
    -- 关联 PMO
    MAX(user_id)       AS pmo_user_id,
    MAX(user_name)     AS user_name,
    MAX(department_id) AS department_id,
    -- raw_record_ids: 改用 JSON_ARRAYAGG (MySQL 8.0) 更稳, 不需要转义
    CAST(CONCAT('[', GROUP_CONCAT(DISTINCT JSON_QUOTE(record_id) SEPARATOR ','), ']') AS JSON) AS raw_record_ids,
    MAX(dingtalk_updated_at) AS dingtalk_updated_at
FROM dingtalk_attendance
WHERE deleted = 0
GROUP BY userid, work_date
ON DUPLICATE KEY UPDATE
    on_duty_actual   = VALUES(on_duty_actual),
    off_duty_actual  = VALUES(off_duty_actual),
    check_count      = VALUES(check_count),
    is_abnormal      = VALUES(is_abnormal),
    abnormal_types   = VALUES(abnormal_types),
    raw_record_ids   = VALUES(raw_record_ids),
    dingtalk_updated_at = VALUES(dingtalk_updated_at),
    updated_at       = CURRENT_TIMESTAMP(6);

-- 3) ⚠️ 老表 dingtalk_attendance 保留 (阶段 0.2 决策: 只读, 给前端详情查原始打卡)
--    不 DROP, 不改结构
--    应用层保证: 同步时只写新表, 不再 INSERT/UPDATE 老表
