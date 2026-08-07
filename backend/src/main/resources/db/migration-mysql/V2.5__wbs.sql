-- ============================================================
-- V2.5 WBS 分解 + 项目计划 + EVM (MySQL 版)
-- 字段/约束/索引 跟 PG 版 (V2.5__wbs.sql) 对齐
-- 差异:
--   - BIGSERIAL → BIGINT NOT NULL AUTO_INCREMENT
--   - TIMESTAMPTZ → DATETIME(6)
--   - NUMERIC → DECIMAL
--   - BOOLEAN → TINYINT(1)
--   - DATE / TEXT / VARCHAR 同义
--   - bigint[] (PG 数组) → JSON (MySQL 用 JSON 列存 long 数组)
--   - PG 触发器 + 函数 → 删 (JPA @PreUpdate / EvmSnapshotJob Java 端维护)
--   - PG 视图/函数 → 改用 Java 端实现 (WbsService.java 已有等价方法)
-- ============================================================

-- ============================================================
-- ① wbs_task —— WBS 任务(树形,100% 原则)
-- ============================================================
CREATE TABLE IF NOT EXISTS wbs_task (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id        BIGINT       NOT NULL,
    parent_id         BIGINT       NULL,
    -- 自引用,根=NULL. 应用层维护树, 不加 FK CASCADE (防 MySQL 自引用 FK 的坑)

    wbs_code          VARCHAR(32)  NOT NULL,
    name              VARCHAR(256) NOT NULL,
    task_type         VARCHAR(16)  NOT NULL,
    -- SUMMARY(汇总) / EXECUTION(执行) / MILESTONE(里程碑) / DELIVERABLE(交付物)

    status            VARCHAR(16)  NOT NULL DEFAULT 'NOT_STARTED',
    -- NOT_STARTED / IN_PROGRESS / COMPLETED / BLOCKED / CANCELLED

    owner_user_id     BIGINT       NULL,
    plan_start_date   DATE         NULL,
    plan_end_date     DATE         NULL,
    actual_start_date DATE         NULL,
    actual_end_date   DATE         NULL,

    plan_hours        DECIMAL(10,2) NOT NULL DEFAULT 0,
    actual_hours      DECIMAL(10,2) NOT NULL DEFAULT 0,
    progress_pct      INT          NOT NULL DEFAULT 0,
    weight            INT          NOT NULL DEFAULT 1,

    is_critical       TINYINT(1)   NOT NULL DEFAULT 0,
    is_milestone      TINYINT(1)   NOT NULL DEFAULT 0,
    milestone_id      BIGINT       NULL,

    predecessor_ids   JSON         NOT NULL,    -- PG 是 bigint[], MySQL 用 JSON 存 long 数组
    -- Java 端用 @JdbcTypeCode(SqlTypes.ARRAY) + Long[] 映射, MySQL 走 JSON 类型
    -- 例: [1, 5, 12] (空 = [])

    deliverable       TEXT         NULL,
    remark            TEXT         NULL,

    created_by        BIGINT       NULL,
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted           TINYINT(1)   NOT NULL DEFAULT 0,

    UNIQUE KEY uk_wbs_project_code (project_id, wbs_code),
    KEY idx_wbs_project   (project_id),
    KEY idx_wbs_parent    (parent_id),
    KEY idx_wbs_status    (status),
    KEY idx_wbs_owner     (owner_user_id),
    KEY idx_wbs_milestone (milestone_id),
    KEY idx_wbs_deleted   (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WBS 任务(树形结构,100% 原则:叶子节点100%归到父节点)';


-- ============================================================
-- ② budget_line —— 预算分项
-- ============================================================
CREATE TABLE IF NOT EXISTS budget_line (
    id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id       BIGINT       NOT NULL,
    wbs_task_id      BIGINT       NULL,
    -- 可空:非任务级预算(如"项目预留金/差旅"不挂任务)

    category         VARCHAR(32)  NOT NULL,
    -- LABOR / PURCHASE / TRAVEL / CONTINGENCY / OTHER
    name             VARCHAR(128) NOT NULL,

    planned_amount   DECIMAL(14,2) NOT NULL DEFAULT 0,
    committed_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
    actual_amount    DECIMAL(14,2) NOT NULL DEFAULT 0,

    currency         VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    note             TEXT         NULL,

    created_by       BIGINT       NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,

    KEY idx_budget_project  (project_id),
    KEY idx_budget_wbs       (wbs_task_id),
    KEY idx_budget_category  (category),
    KEY idx_budget_deleted   (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算分项(BAC/承诺/实际)';


-- ============================================================
-- ③ budget_snapshot —— 预算快照(EVM 历史)
-- ============================================================
CREATE TABLE IF NOT EXISTS budget_snapshot (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    snapshot_date   DATE         NOT NULL,
    version         INT          NOT NULL,    -- 项目内自增
    reason          VARCHAR(256) NULL,

    bac             DECIMAL(14,2) NOT NULL,
    pv              DECIMAL(14,2) NOT NULL DEFAULT 0,
    ev              DECIMAL(14,2) NOT NULL DEFAULT 0,
    ac              DECIMAL(14,2) NOT NULL DEFAULT 0,

    cpi             DECIMAL(6,3)  NOT NULL DEFAULT 1,
    spi             DECIMAL(6,3)  NOT NULL DEFAULT 1,
    eac             DECIMAL(14,2) NOT NULL,
    etc             DECIMAL(14,2) NOT NULL,
    vac             DECIMAL(14,2) NOT NULL,

    created_by      BIGINT       NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    UNIQUE KEY uq_snapshot_project_version (project_id, version),
    KEY idx_snapshot_project  (project_id),
    KEY idx_snapshot_date     (snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算快照(EVM 历史,version 项目内自增)';


-- ============================================================
-- ④ wbs_assignment —— 资源分配
-- ============================================================
CREATE TABLE IF NOT EXISTS wbs_assignment (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    wbs_task_id     BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    role            VARCHAR(64)  NULL,

    planned_hours   DECIMAL(10,2) NOT NULL DEFAULT 0,
    actual_hours    DECIMAL(10,2) NOT NULL DEFAULT 0,

    start_date      DATE         NULL,
    end_date        DATE         NULL,
    note            TEXT         NULL,

    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,

    UNIQUE KEY uq_assignment_task_user (wbs_task_id, user_id),
    KEY idx_assign_task    (wbs_task_id),
    KEY idx_assign_user    (user_id),
    KEY idx_assign_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WBS 任务资源分配(DCMA 14-point:任务必有责任人)';


-- ============================================================
-- ⑤ hourly_rate —— 角色费率字典
-- ============================================================
CREATE TABLE IF NOT EXISTS hourly_rate (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    role            VARCHAR(64)  UNIQUE NOT NULL,
    rate            DECIMAL(10,2) NOT NULL,
    effective_date  DATE         NOT NULL,
    note            VARCHAR(256) NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色费率(元/小时,EVM 算 AC 用)';


-- ============================================================
-- A.1.2 扩展 timesheet_entry —— 工时下挂 WBS 任务
-- 兼容策略: 可空,旧数据保留,新填报推荐选 WBS
-- ============================================================
ALTER TABLE timesheet_entry
    ADD COLUMN wbs_task_id BIGINT NULL AFTER milestone_id;

-- 索引: EVM 按 WBS 任务汇总工时
CREATE INDEX idx_tsent_wbs ON timesheet_entry(wbs_task_id);

-- 联合唯一约束扩展: 新填报若选了 wbs_task,加上 wbs_task_id 作去重维度
-- MySQL 8 InnoDB: 字段被 FK 引用时, 含该字段的索引不可单独 drop, 需先建替代索引
-- (FK 检查的是"该字段是否有任意索引覆盖", 加新普通索引即可)
-- 一次性 ALTER 内部按顺序执行:
--   1) 添加替代普通索引  2) drop 旧 UNIQUE  3) add 新 UNIQUE  4) drop 替代索引
-- 索引最终名沿用 uq_tsent_dedup (新列组成, 与旧列组成不同, 但应用代码不依赖索引名)
ALTER TABLE timesheet_entry
    ADD INDEX idx_tsent_project_tmp (project_id),
    DROP INDEX uq_tsent_dedup,
    ADD UNIQUE KEY uq_tsent_dedup (timesheet_id, work_date, project_id, milestone_id, wbs_task_id),
    DROP INDEX idx_tsent_project_tmp;
-- 兼容 NULL: MySQL 的 UNIQUE 允许多个 NULL 值, 旧数据(无 WBS)和新数据(有 WBS)可共存
-- 若日后想强约束, 需要在应用层校验


-- ============================================================
-- A.1.3 扩展 project 表 —— EVM 快照冗余字段
-- 6 + 4 字段:
--   EVM 6 件套: bac / evm_cpi / evm_spi / evm_eac / evm_etc / evm_vac / evm_updated_at
--   baseline 3 件套: baseline_version / baseline_frozen_at / baseline_frozen_by
-- ============================================================
ALTER TABLE project
    ADD COLUMN bac               DECIMAL(14,2)  NULL COMMENT '完工预算 BAC',
    ADD COLUMN evm_cpi           DECIMAL(6,3)   NULL COMMENT 'CPI 成本绩效指数 EV/AC',
    ADD COLUMN evm_spi           DECIMAL(6,3)   NULL COMMENT 'SPI 进度绩效指数 EV/PV',
    ADD COLUMN evm_eac           DECIMAL(14,2)  NULL COMMENT 'EAC 完工估算 BAC/CPI',
    ADD COLUMN evm_etc           DECIMAL(14,2)  NULL COMMENT 'ETC 完工尚需估算 EAC-AC',
    ADD COLUMN evm_vac           DECIMAL(14,2)  NULL COMMENT 'VAC 完工偏差 BAC-EAC',
    ADD COLUMN evm_updated_at    DATETIME(6)    NULL COMMENT '上次 EVM 计算时间',
    ADD COLUMN baseline_version  INT            NOT NULL DEFAULT 0 COMMENT '当前冻结 baseline 版本号',
    ADD COLUMN baseline_frozen_at DATETIME(6)   NULL COMMENT '当前 baseline 冻结时间',
    ADD COLUMN baseline_frozen_by BIGINT        NULL COMMENT '当前 baseline 冻结人';

-- 索引: 仪表盘按 SPI/CPI 排序(快查"所有异常项目")
CREATE INDEX idx_project_evm_health ON project (evm_cpi, evm_spi);
CREATE INDEX idx_project_evm_overrun ON project (evm_eac);
-- 注: MySQL 不支持 PG 的部分索引 WHERE 子句, 用普通索引代替
-- 应用层或 EvmSnapshotJob 过滤 status='ACTIVE'


-- ============================================================
-- 备注: V2.5 PG 版的以下 MySQL 不需要 (Java 端已有等价实现)
--   - pmo.fn_compute_evm(BIGINT)         → WbsService.networkByProject / ganttByProject 等
--   - pmo.fn_snapshot_evm(...)          → EvmSnapshotJob.java
--   - pmo.fn_evm_health(cpi, spi)       → ProjectService 内的 getSuggestedHealth()
--   - pmo.fn_snapshot_immutable()       → JPA Entity 自身不加 @PreUpdate 即可
--   - v_wbs_tree                        → WbsService.listTreeByProject() (流式组装)
--   - v_wbs_progress_summary            → WbsService.progressSummary()
--   - v_assignment_summary              → WbsService.listAssignmentsByProject()
--   - v_user_weekly_load (扩展)         → WorkloadService 已有
--   - v_project_evm_overrun             → ProjectService 内的 listEvmOverrun()
-- ============================================================
