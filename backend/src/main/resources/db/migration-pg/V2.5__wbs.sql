-- ============================================================
-- V2.5 WBS 分解 + 项目计划 + EVM
-- A.1.1 建表(5 张新表)
-- 框架参考: PMBOK 7 (100% 原则) + DCMA 14-point + ANSI/EIA-748 (EVM)
-- ============================================================

-- ============================================================
-- ① wbs_task —— WBS 任务(树形,100% 原则)
-- ============================================================
CREATE TABLE wbs_task (
    id                BIGSERIAL PRIMARY KEY,
    project_id        BIGINT       NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    parent_id         BIGINT       REFERENCES wbs_task(id) ON DELETE CASCADE,
    -- 自引用,根=NULL。ON DELETE CASCADE 防止孤儿

    wbs_code          VARCHAR(32)  NOT NULL,   -- 项目内编号 1, 1.1, 1.1.1 ...
    name              VARCHAR(256) NOT NULL,
    task_type         VARCHAR(16)  NOT NULL,
    -- SUMMARY(汇总) / EXECUTION(执行) / MILESTONE(里程碑) / DELIVERABLE(交付物)

    status            VARCHAR(16)  NOT NULL DEFAULT 'NOT_STARTED',
    -- NOT_STARTED / IN_PROGRESS / COMPLETED / BLOCKED / CANCELLED

    owner_user_id     BIGINT       REFERENCES app_user(id),
    plan_start_date   DATE,
    plan_end_date     DATE,
    actual_start_date DATE,
    actual_end_date   DATE,

    plan_hours        NUMERIC(10,2) NOT NULL DEFAULT 0,   -- 计划工时(人时)
    actual_hours      NUMERIC(10,2) NOT NULL DEFAULT 0,   -- 已耗工时(从工时表汇总)
    progress_pct      INT          NOT NULL DEFAULT 0,   -- 0-100
    weight            INT          NOT NULL DEFAULT 1,   -- 1-10,加权用

    is_critical       BOOLEAN      NOT NULL DEFAULT FALSE, -- 是否关键路径(CPM)
    is_milestone      BOOLEAN      NOT NULL DEFAULT FALSE, -- 是否里程碑节点
    milestone_id      BIGINT       REFERENCES milestone(id), -- 软关联现有 milestone

    predecessor_ids   BIGINT[]     NOT NULL DEFAULT '{}',  -- 前置任务 ID 列表(CPM)

    deliverable       TEXT,
    remark            TEXT,

    created_by        BIGINT       REFERENCES app_user(id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_wbs_project_code  UNIQUE (project_id, wbs_code),
    CONSTRAINT ck_wbs_progress      CHECK (progress_pct BETWEEN 0 AND 100),
    CONSTRAINT ck_wbs_weight        CHECK (weight        BETWEEN 1 AND 10),
    CONSTRAINT ck_wbs_plan_hours    CHECK (plan_hours   >= 0),
    CONSTRAINT ck_wbs_actual_hours  CHECK (actual_hours >= 0),
    CONSTRAINT ck_wbs_task_type     CHECK (task_type IN ('SUMMARY','EXECUTION','MILESTONE','DELIVERABLE')),
    CONSTRAINT ck_wbs_status        CHECK (status IN ('NOT_STARTED','IN_PROGRESS','COMPLETED','BLOCKED','CANCELLED'))
);
COMMENT ON TABLE wbs_task IS 'WBS 任务(树形结构,100% 原则:叶子节点100%归到父节点)';

-- wbs_task 字段 COMMENT(22 个,严格对齐 V2.2 规范)
COMMENT ON COLUMN wbs_task.id                IS '任务 ID(主键)';
COMMENT ON COLUMN wbs_task.project_id        IS '所属项目 ID(project.id)';
COMMENT ON COLUMN wbs_task.parent_id         IS '父任务 ID(自引用,根=NULL)';
COMMENT ON COLUMN wbs_task.wbs_code          IS 'WBS 编码(项目内唯一,1/1.1/1.1.1 等)';
COMMENT ON COLUMN wbs_task.name              IS '任务名称';
COMMENT ON COLUMN wbs_task.task_type         IS '任务类型:SUMMARY/EXECUTION/MILESTONE/DELIVERABLE';
COMMENT ON COLUMN wbs_task.status            IS '状态:NOT_STARTED/IN_PROGRESS/COMPLETED/BLOCKED/CANCELLED';
COMMENT ON COLUMN wbs_task.owner_user_id     IS '负责人 ID(app_user.id)';
COMMENT ON COLUMN wbs_task.plan_start_date   IS '计划开始日期';
COMMENT ON COLUMN wbs_task.plan_end_date     IS '计划结束日期';
COMMENT ON COLUMN wbs_task.actual_start_date IS '实际开始日期';
COMMENT ON COLUMN wbs_task.actual_end_date   IS '实际结束日期';
COMMENT ON COLUMN wbs_task.plan_hours        IS '计划工时(人时,从 wbs_assignment.planned_hours 汇总)';
COMMENT ON COLUMN wbs_task.actual_hours      IS '已耗工时(人时,从已审批 timesheet_entry.hours 汇总)';
COMMENT ON COLUMN wbs_task.progress_pct      IS '完成进度 0-100(手动维护 + 工时回算)';
COMMENT ON COLUMN wbs_task.weight            IS '权重 1-10(用于父任务加权进度计算)';
COMMENT ON COLUMN wbs_task.is_critical       IS '是否关键路径(CPM 自动计算)';
COMMENT ON COLUMN wbs_task.is_milestone      IS '是否里程碑节点(WBS 中的里程碑类型)';
COMMENT ON COLUMN wbs_task.milestone_id      IS '关联的现有 milestone ID(双向软关联)';
COMMENT ON COLUMN wbs_task.predecessor_ids   IS '前置任务 ID 列表(数组,CPM 用)';
COMMENT ON COLUMN wbs_task.deliverable       IS '交付物说明';
COMMENT ON COLUMN wbs_task.remark            IS '备注';
COMMENT ON COLUMN wbs_task.created_by        IS '创建人 ID';
COMMENT ON COLUMN wbs_task.created_at        IS '创建时间';
COMMENT ON COLUMN wbs_task.updated_at        IS '更新时间(BEFORE UPDATE 触发器自动维护)';
COMMENT ON COLUMN wbs_task.deleted           IS '软删标记';

-- 索引
CREATE INDEX idx_wbs_project      ON wbs_task(project_id);
CREATE INDEX idx_wbs_parent       ON wbs_task(parent_id);
CREATE INDEX idx_wbs_status       ON wbs_task(status);
CREATE INDEX idx_wbs_owner        ON wbs_task(owner_user_id);
CREATE INDEX idx_wbs_milestone    ON wbs_task(milestone_id);
-- 关键路径部分索引(只索引 TRUE 行,空间最小)
CREATE INDEX idx_wbs_critical     ON wbs_task(project_id) WHERE is_critical;
-- 叶子节点(用于工时归集/EVM)
CREATE INDEX idx_wbs_leaf         ON wbs_task(project_id) WHERE parent_id IS NOT NULL AND deleted = FALSE;
-- 软删过滤
CREATE INDEX idx_wbs_deleted      ON wbs_task(deleted);


-- ============================================================
-- ② budget_line —— 预算分项
-- ============================================================
CREATE TABLE budget_line (
    id               BIGSERIAL PRIMARY KEY,
    project_id       BIGINT       NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    wbs_task_id      BIGINT       REFERENCES wbs_task(id) ON DELETE SET NULL,
    -- 可空:非任务级预算(如"项目预留金/差旅"不挂任务)

    category         VARCHAR(32)  NOT NULL,
    -- LABOR / PURCHASE / TRAVEL / CONTINGENCY / OTHER
    name             VARCHAR(128) NOT NULL,

    planned_amount   NUMERIC(14,2) NOT NULL DEFAULT 0,    -- BAC(完工预算分项)
    committed_amount NUMERIC(14,2) NOT NULL DEFAULT 0,    -- 已承诺(合同/PO)
    actual_amount    NUMERIC(14,2) NOT NULL DEFAULT 0,    -- 已实际发生

    currency         VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    note             TEXT,

    created_by       BIGINT       REFERENCES app_user(id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT ck_budget_category CHECK (category IN ('LABOR','PURCHASE','TRAVEL','CONTINGENCY','OTHER')),
    CONSTRAINT ck_budget_planned  CHECK (planned_amount   >= 0),
    CONSTRAINT ck_budget_committed CHECK (committed_amount >= 0),
    CONSTRAINT ck_budget_actual   CHECK (actual_amount    >= 0)
);
COMMENT ON TABLE budget_line IS '预算分项(BAC/承诺/实际)';
COMMENT ON COLUMN budget_line.planned_amount   IS '计划金额(完工预算 BAC 的一部分)';
COMMENT ON COLUMN budget_line.committed_amount IS '已承诺金额(合同/PO)';
COMMENT ON COLUMN budget_line.actual_amount    IS '已实际发生金额';

CREATE INDEX idx_budget_project  ON budget_line(project_id);
CREATE INDEX idx_budget_wbs       ON budget_line(wbs_task_id);
CREATE INDEX idx_budget_category  ON budget_line(category);
CREATE INDEX idx_budget_deleted   ON budget_line(deleted);


-- ============================================================
-- ③ budget_snapshot —— 预算快照(EVM 历史)
-- ============================================================
CREATE TABLE budget_snapshot (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT       NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    snapshot_date   DATE         NOT NULL,
    version         INT          NOT NULL,    -- 项目内自增(1, 2, 3 ...)
    reason          VARCHAR(256),              -- 例:"Q2 ETC 重算","范围变更 CR-2025-03"

    -- EVM 5 大核心值
    bac             NUMERIC(14,2) NOT NULL,    -- Budget at Completion(完工预算)
    pv              NUMERIC(14,2) NOT NULL DEFAULT 0, -- Planned Value
    ev              NUMERIC(14,2) NOT NULL DEFAULT 0, -- Earned Value
    ac              NUMERIC(14,2) NOT NULL DEFAULT 0, -- Actual Cost

    -- 派生指标
    cpi             NUMERIC(6,3)  NOT NULL DEFAULT 1,  -- Cost Performance Index
    spi             NUMERIC(6,3)  NOT NULL DEFAULT 1,  -- Schedule Performance Index
    eac             NUMERIC(14,2) NOT NULL,            -- Estimate At Completion
    etc             NUMERIC(14,2) NOT NULL,            -- Estimate To Complete
    vac             NUMERIC(14,2) NOT NULL,            -- Variance At Completion

    created_by      BIGINT       REFERENCES app_user(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_snapshot_project_version UNIQUE (project_id, version),
    CONSTRAINT ck_snapshot_bac CHECK (bac >= 0),
    CONSTRAINT ck_snapshot_cpi CHECK (cpi > 0),
    CONSTRAINT ck_snapshot_spi CHECK (spi > 0)
);
COMMENT ON TABLE  budget_snapshot  IS '预算快照(EVM 历史,version 项目内自增)';
COMMENT ON COLUMN budget_snapshot.bac IS 'Budget At Completion 完工预算';
COMMENT ON COLUMN budget_snapshot.pv  IS 'Planned Value 计划值';
COMMENT ON COLUMN budget_snapshot.ev  IS 'Earned Value 挣值';
COMMENT ON COLUMN budget_snapshot.ac  IS 'Actual Cost 实际成本';
COMMENT ON COLUMN budget_snapshot.cpi IS 'Cost Performance Index 成本绩效指数 EV/AC';
COMMENT ON COLUMN budget_snapshot.spi IS 'Schedule Performance Index 进度绩效指数 EV/PV';
COMMENT ON COLUMN budget_snapshot.eac IS 'Estimate At Completion 完工估算 BAC/CPI';
COMMENT ON COLUMN budget_snapshot.etc IS 'Estimate To Complete 完工尚需估算 EAC-AC';
COMMENT ON COLUMN budget_snapshot.vac IS 'Variance At Completion 完工偏差 BAC-EAC';

CREATE INDEX idx_snapshot_project  ON budget_snapshot(project_id);
CREATE INDEX idx_snapshot_date     ON budget_snapshot(snapshot_date);


-- ============================================================
-- ④ wbs_assignment —— 资源分配(WBS 任务 × 人员)
-- ============================================================
CREATE TABLE wbs_assignment (
    id              BIGSERIAL PRIMARY KEY,
    wbs_task_id     BIGINT       NOT NULL REFERENCES wbs_task(id) ON DELETE CASCADE,
    user_id         BIGINT       NOT NULL REFERENCES app_user(id),
    role            VARCHAR(64),                       -- DEV/QA/ARCH/PM/UI/OPS

    planned_hours   NUMERIC(10,2) NOT NULL DEFAULT 0,   -- 该人该任务计划工时
    actual_hours    NUMERIC(10,2) NOT NULL DEFAULT 0,   -- 从工时表汇总

    start_date      DATE,
    end_date        DATE,
    note            TEXT,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_assignment_task_user UNIQUE (wbs_task_id, user_id),
    CONSTRAINT ck_assign_planned CHECK (planned_hours >= 0),
    CONSTRAINT ck_assign_actual  CHECK (actual_hours  >= 0)
);
COMMENT ON TABLE wbs_assignment IS 'WBS 任务资源分配(DCMA 14-point:任务必有责任人)';
COMMENT ON COLUMN wbs_assignment.planned_hours IS '该人该任务计划工时(人时)';
COMMENT ON COLUMN wbs_assignment.actual_hours  IS '已实际发生工时(从已审批工时表汇总)';

CREATE INDEX idx_assign_task    ON wbs_assignment(wbs_task_id);
CREATE INDEX idx_assign_user    ON wbs_assignment(user_id);
CREATE INDEX idx_assign_deleted ON wbs_assignment(deleted);


-- ============================================================
-- ⑤ hourly_rate —— 角色费率字典(EVM 人工成本计算用)
-- ============================================================
CREATE TABLE hourly_rate (
    id              BIGSERIAL PRIMARY KEY,
    role            VARCHAR(64)  UNIQUE NOT NULL,   -- DEV/QA/ARCH/PM/UI/OPS
    rate            NUMERIC(10,2) NOT NULL,         -- 元/小时
    effective_date  DATE         NOT NULL,          -- 生效日期(支持历史调价)
    note            VARCHAR(256),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_rate_positive CHECK (rate > 0)
);
COMMENT ON TABLE  hourly_rate      IS '角色费率(元/小时,EVM 算 AC 用)';
COMMENT ON COLUMN hourly_rate.role IS '角色编码:DEV/QA/ARCH/PM/UI/OPS';
COMMENT ON COLUMN hourly_rate.rate IS '每小时费率(元)';


-- ============================================================
-- A.1.2 扩展 timesheet_entry —— 工时下挂 WBS 任务
-- 兼容策略: 可空,旧数据保留,新填报推荐选 WBS(前端提示)
-- ============================================================
ALTER TABLE timesheet_entry
    ADD COLUMN wbs_task_id BIGINT REFERENCES wbs_task(id) ON DELETE SET NULL;
-- ON DELETE SET NULL: WBS 任务被硬删时,工时记录保留(归到项目级即可)

-- 索引: EVM 按 WBS 任务汇总工时
CREATE INDEX idx_tsent_wbs ON timesheet_entry(wbs_task_id)
    WHERE wbs_task_id IS NOT NULL AND deleted = FALSE;

-- 联合唯一约束扩展: 新填报若选了 wbs_task,加上 wbs_task_id 作去重维度
-- PG 不支持直接改 UNIQUE 约束,采用 drop + add
ALTER TABLE timesheet_entry DROP CONSTRAINT uq_tsent_dedup;
ALTER TABLE timesheet_entry
    ADD CONSTRAINT uq_tsent_dedup
    UNIQUE (timesheet_id, work_date, project_id, milestone_id, wbs_task_id);
-- 兼容 NULL: PG 的 UNIQUE 允许多个 NULL 值,旧数据(无 WBS)和新数据(有 WBS)可共存
-- milestone_id NULL + wbs_task_id NULL 仍是旧的去重粒度
-- 若日后想强约束,加 EXCLUDE 约束(GIST btree_gist 扩展,暂不用以免引入新依赖)

COMMENT ON COLUMN timesheet_entry.wbs_task_id IS 'WBS 任务 ID(可空,旧数据保留),新填报应优先选 WBS 任务而非仅到项目级';

-- 负载视图扩展: 增加 WBS 维度
DROP VIEW IF EXISTS v_user_weekly_load;
CREATE OR REPLACE VIEW v_user_weekly_load AS
SELECT  u.id                              AS user_id,
        u.full_name,
        u.department_id,
        ts.week_start,
        ts.week_end,
        ts.status                         AS timesheet_status,
        COALESCE(SUM(te.hours), 0)        AS total_hours,
        COUNT(DISTINCT te.project_id)     AS project_count,
        COUNT(DISTINCT te.wbs_task_id)    AS wbs_task_count,    -- V2.5 新增: 涉及 WBS 任务数
        COUNT(*)                          AS entry_count
FROM app_user u
LEFT JOIN timesheet_week ts  ON ts.user_id = u.id AND ts.deleted = FALSE
LEFT JOIN timesheet_entry te ON te.timesheet_id = ts.id
WHERE u.deleted = FALSE
GROUP BY u.id, u.full_name, u.department_id, ts.week_start, ts.week_end, ts.status;
COMMENT ON VIEW v_user_weekly_load IS '用户周负载(V2.5 扩展:增加 wbs_task_count)';


-- ============================================================
-- A.1.3 扩展 project 表 —— EVM 快照冗余字段
-- 目的: 仪表盘免 GROUP BY 聚合,加快查询
-- 数据源: 每次 EvmSnapshotJob 跑完同步写入
-- 6 个字段:
--   bac          完工预算(Budget at Completion)
--   evm_cpi      成本绩效指数
--   evm_spi      进度绩效指数
--   evm_eac      完工估算
--   evm_etc      完工尚需估算
--   evm_vac      完工偏差
-- ============================================================
ALTER TABLE project
    ADD COLUMN bac            NUMERIC(14,2),               -- 完工预算(从 budget_line 汇总)
    ADD COLUMN evm_cpi        NUMERIC(6,3),                -- 最新 CPI(EV/AC)
    ADD COLUMN evm_spi        NUMERIC(6,3),                -- 最新 SPI(EV/PV)
    ADD COLUMN evm_eac        NUMERIC(14,2),               -- 最新 EAC(BAC/CPI)
    ADD COLUMN evm_etc        NUMERIC(14,2),               -- 最新 ETC(EAC-AC)
    ADD COLUMN evm_vac        NUMERIC(14,2),               -- 最新 VAC(BAC-EAC)
    ADD COLUMN evm_updated_at TIMESTAMPTZ,                 -- 上次 EVM 计算时间
    ADD COLUMN baseline_version INT         NOT NULL DEFAULT 0, -- 当前冻结的 baseline 版本号
    ADD COLUMN baseline_frozen_at TIMESTAMPTZ,             -- 当前 baseline 冻结时间
    ADD COLUMN baseline_frozen_by BIGINT REFERENCES app_user(id); -- 当前 baseline 冻结人

-- 校验: EVM 指标都为正数(零成本时用 1 占位,见 fn_compute_evm)
-- 注: cpi/spi 不加 NOT NULL,允许新项目未跑 EVM 时为 NULL
ALTER TABLE project
    ADD CONSTRAINT ck_project_bac_nonneg  CHECK (bac IS NULL OR bac >= 0),
    ADD CONSTRAINT ck_project_cpi_pos     CHECK (evm_cpi IS NULL OR evm_cpi > 0),
    ADD CONSTRAINT ck_project_spi_pos     CHECK (evm_spi IS NULL OR evm_spi > 0),
    ADD CONSTRAINT ck_project_baseline_v  CHECK (baseline_version >= 0);

-- 索引: 仪表盘按 SPI/CPI 排序(快查"所有异常项目")
-- 只在有 EVM 数据的项目上建索引(节省空间)
CREATE INDEX idx_project_evm_health
    ON project (evm_cpi, evm_spi)
    WHERE evm_updated_at IS NOT NULL AND deleted = FALSE;
-- 用于 PMO 视图"所有 EAC > BAC 1.1 倍的项目"快查
-- 注: 部分索引 WHERE 不能含子查询,改为简单条件
-- 应用层或视图 v_project_evm_overrun 过滤 status_id='ACTIVE'
CREATE INDEX idx_project_evm_overrun
    ON project (evm_eac)
    WHERE evm_eac IS NOT NULL AND deleted = FALSE;

-- 字段注释
COMMENT ON COLUMN project.bac               IS '完工预算 Budget at Completion(从 budget_line.planned_amount 汇总)';
COMMENT ON COLUMN project.evm_cpi           IS '成本绩效指数 Cost Performance Index = EV/AC';
COMMENT ON COLUMN project.evm_spi           IS '进度绩效指数 Schedule Performance Index = EV/PV';
COMMENT ON COLUMN project.evm_eac           IS '完工估算 Estimate At Completion = BAC/CPI';
COMMENT ON COLUMN project.evm_etc           IS '完工尚需估算 Estimate To Complete = EAC-AC';
COMMENT ON COLUMN project.evm_vac           IS '完工偏差 Variance At Completion = BAC-EAC';
COMMENT ON COLUMN project.evm_updated_at    IS '上次 EVM 计算时间(EvmSnapshotJob 写入)';
COMMENT ON COLUMN project.baseline_version  IS '当前冻结的 baseline 版本号(0=未冻结,1=v1,2=v2...)';
COMMENT ON COLUMN project.baseline_frozen_at IS '当前 baseline 冻结时间';
COMMENT ON COLUMN project.baseline_frozen_by IS '当前 baseline 冻结人(app_user.id)';

-- EAC 超阈值的项目快查视图(PMO 仪表盘用)
-- BAC IS NULL 的项目(未录入预算)排除
-- EAC > BAC * 1.1 触发预警
CREATE OR REPLACE VIEW v_project_evm_overrun AS
SELECT  p.id                                      AS project_id,
        p.code,
        p.name,
        p.bac,
        p.evm_eac,
        p.evm_vac,
        p.evm_cpi,
        p.evm_spi,
        CASE WHEN p.bac > 0
             THEN ROUND(p.evm_eac / p.bac, 3)
             ELSE NULL
        END                                       AS overrun_ratio,
        CASE WHEN p.evm_cpi >= 1.0 AND p.evm_spi >= 1.0 THEN 'GREEN'
             WHEN p.evm_cpi >= 0.9 AND p.evm_spi >= 0.9 THEN 'YELLOW'
             ELSE 'RED'
        END                                       AS suggested_health
FROM project p
WHERE p.bac IS NOT NULL
  AND p.evm_eac IS NOT NULL
  AND p.deleted = FALSE
  AND p.status_id IN (SELECT id FROM project_status WHERE code = 'ACTIVE');
COMMENT ON VIEW v_project_evm_overrun IS 'EAC 超阈值项目(EAC/BAC > 1.1 预警,PMO 仪表盘用)';


-- ============================================================
-- A.1.5 触发器 —— updated_at 自动维护
-- 复用 V1.0 已建好的 pmo.fn_set_updated_at() 函数
-- 给 4 张新表(wbs_task / budget_line / budget_snapshot / wbs_assignment)
-- 注: hourly_rate 也加(虽然 6 行种子数据为主,但允许用户维护调价历史)
-- ============================================================

CREATE TRIGGER trg_wbs_task_updated_at
    BEFORE UPDATE ON wbs_task
    FOR EACH ROW EXECUTE FUNCTION pmo.fn_set_updated_at();
COMMENT ON TRIGGER trg_wbs_task_updated_at ON wbs_task
    IS 'wbs_task.updated_at 自动维护';

CREATE TRIGGER trg_budget_line_updated_at
    BEFORE UPDATE ON budget_line
    FOR EACH ROW EXECUTE FUNCTION pmo.fn_set_updated_at();
COMMENT ON TRIGGER trg_budget_line_updated_at ON budget_line
    IS 'budget_line.updated_at 自动维护';

-- budget_snapshot 不允许 UPDATE(version 不可变,只 INSERT)
-- 加一个保护: 禁止 UPDATE
CREATE OR REPLACE FUNCTION pmo.fn_snapshot_immutable()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'budget_snapshot 是不可变历史表,禁止 UPDATE/DELETE (id=%, version=%)',
        OLD.id, OLD.version
        USING ERRCODE = 'P0001';
END;
$$ LANGUAGE plpgsql;
COMMENT ON FUNCTION pmo.fn_snapshot_immutable() IS 'budget_snapshot 不可变保护(只允许 INSERT)';

CREATE TRIGGER trg_budget_snapshot_no_update
    BEFORE UPDATE ON budget_snapshot
    FOR EACH ROW EXECUTE FUNCTION pmo.fn_snapshot_immutable();
COMMENT ON TRIGGER trg_budget_snapshot_no_update ON budget_snapshot
    IS '禁止 UPDATE budget_snapshot(EVM 历史只追加)';

CREATE TRIGGER trg_budget_snapshot_no_delete
    BEFORE DELETE ON budget_snapshot
    FOR EACH ROW EXECUTE FUNCTION pmo.fn_snapshot_immutable();
COMMENT ON TRIGGER trg_budget_snapshot_no_delete ON budget_snapshot
    IS '禁止 DELETE budget_snapshot(EVM 历史只追加)';

CREATE TRIGGER trg_wbs_assignment_updated_at
    BEFORE UPDATE ON wbs_assignment
    FOR EACH ROW EXECUTE FUNCTION pmo.fn_set_updated_at();
COMMENT ON TRIGGER trg_wbs_assignment_updated_at ON wbs_assignment
    IS 'wbs_assignment.updated_at 自动维护';

CREATE TRIGGER trg_hourly_rate_updated_at
    BEFORE UPDATE ON hourly_rate
    FOR EACH ROW EXECUTE FUNCTION pmo.fn_set_updated_at();
COMMENT ON TRIGGER trg_hourly_rate_updated_at ON hourly_rate
    IS 'hourly_rate.updated_at 自动维护(支持调价历史维护)';


-- ============================================================
-- A.1.8 EVM 计算函数
-- pmo.fn_compute_evm(p_project_id BIGINT) 返回 EVM 9 大指标
-- Java 端 EvmSnapshotJob 调用:
--   SELECT * FROM pmo.fn_compute_evm(?)
-- ============================================================

-- 删除旧版本(若有)
DROP FUNCTION IF EXISTS pmo.fn_compute_evm(BIGINT);

CREATE OR REPLACE FUNCTION pmo.fn_compute_evm(p_project_id BIGINT)
RETURNS TABLE(
    bac NUMERIC,   -- Budget at Completion 完工预算
    pv  NUMERIC,   -- Planned Value 计划值
    ev  NUMERIC,   -- Earned Value 挣值
    ac  NUMERIC,   -- Actual Cost 实际成本
    cpi NUMERIC,   -- Cost Performance Index
    spi NUMERIC,   -- Schedule Performance Index
    eac NUMERIC,   -- Estimate At Completion
    etc NUMERIC,   -- Estimate To Complete
    vac NUMERIC    -- Variance At Completion
)
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_bac         NUMERIC := 0;
    v_pv          NUMERIC := 0;
    v_ev          NUMERIC := 0;
    v_ac          NUMERIC := 0;
    v_cpi         NUMERIC;
    v_spi         NUMERIC;
    v_eac         NUMERIC;
    v_etc         NUMERIC;
    v_vac         NUMERIC;

    v_plan_start  DATE;
    v_plan_end    DATE;
    v_today       DATE := CURRENT_DATE;
    v_time_pct    NUMERIC := 0;        -- 时间进度 0..1
    v_weight_pct  NUMERIC := 0;        -- 加权进度 0..1
BEGIN
    -- 1) BAC: sum(budget_line.planned_amount),项目级聚合
    SELECT COALESCE(SUM(planned_amount), 0)
      INTO v_bac
      FROM budget_line
     WHERE project_id = p_project_id
       AND NOT deleted;

    -- 2) 时间进度 → PV
    --    公式: PV = BAC × min(1, max(0, (today - plan_start) / (plan_end - plan_start)))
    SELECT plan_start_date, plan_end_date
      INTO v_plan_start, v_plan_end
      FROM project
     WHERE id = p_project_id;

    IF v_plan_start IS NOT NULL
       AND v_plan_end   IS NOT NULL
       AND v_plan_end > v_plan_start
    THEN
        v_time_pct := LEAST(1.0, GREATEST(0.0,
            (v_today - v_plan_start)::NUMERIC
            / (v_plan_end - v_plan_start)::NUMERIC
        ));
    END IF;
    v_pv := ROUND(v_bac * v_time_pct, 2);

    -- 3) 加权进度 → EV
    --    公式: weight_pct = sum(weight × progress_pct) / sum(weight) / 100
    --    EV = BAC × weight_pct
    SELECT COALESCE(
        SUM(weight * progress_pct)::NUMERIC
        / NULLIF(SUM(weight), 0)
        / 100.0,
        0
    )
      INTO v_weight_pct
      FROM wbs_task
     WHERE project_id = p_project_id
       AND NOT deleted;
    v_ev := ROUND(v_bac * v_weight_pct, 2);

    -- 4) AC = 人工实际成本 + 非人工 budget_line.actual
    --    人工 = sum(timesheet_entry.hours × hourly_rate.rate)
    --    关联 app_user.job_title → hourly_rate.role
    SELECT COALESCE(SUM(te.hours * COALESCE(hr.rate, 1)), 0)
      INTO v_ac
      FROM timesheet_entry te
      JOIN timesheet_week tw ON tw.id = te.timesheet_id
                            AND tw.deleted = FALSE
                            AND tw.status = 'APPROVED'
      JOIN wbs_task wt       ON wt.id = te.wbs_task_id
                            AND wt.project_id = p_project_id
      JOIN app_user u        ON u.id = tw.user_id
      LEFT JOIN hourly_rate hr ON hr.role = u.job_title
     WHERE te.deleted = FALSE
       AND te.wbs_task_id IS NOT NULL;

    v_ac := v_ac + COALESCE((
        SELECT SUM(actual_amount) FROM budget_line
         WHERE project_id = p_project_id AND NOT deleted
    ), 0);

    v_ac := ROUND(v_ac, 2);

    -- 5) 派生指标
    -- CPI = EV / AC   (AC=0 时 CPI 设为 1,表示"零成本无参考")
    -- SPI = EV / PV   (PV=0 时 SPI 设为 1)
    v_cpi := CASE WHEN v_ac = 0 THEN 1 ELSE ROUND(v_ev / v_ac, 3) END;
    v_spi := CASE WHEN v_pv = 0 THEN 1 ELSE ROUND(v_ev / v_pv, 3) END;

    -- EAC = BAC / CPI  (经典公式: 假设未来按当前 CPI 继续)
    --     AC=0 或 EV=0 时 EAC = BAC
    v_eac := CASE
                WHEN v_ac = 0 OR v_ev = 0 THEN v_bac
                ELSE ROUND(v_bac * v_ac / v_ev, 2)
             END;
    -- ETC = EAC - AC
    v_etc := ROUND(v_eac - v_ac, 2);
    -- VAC = BAC - EAC
    v_vac := ROUND(v_bac - v_eac, 2);

    RETURN QUERY SELECT v_bac, v_pv, v_ev, v_ac, v_cpi, v_spi, v_eac, v_etc, v_vac;
END;
$$;
COMMENT ON FUNCTION pmo.fn_compute_evm(BIGINT) IS
    'EVM 计算: 输入 project_id,返回 BAC/PV/EV/AC/CPI/SPI/EAC/ETC/VAC 九大指标';


-- 辅助函数: 给定 CPI/SPI,自动判定健康度(GREEN/YELLOW/RED)
DROP FUNCTION IF EXISTS pmo.fn_evm_health(NUMERIC, NUMERIC);

CREATE OR REPLACE FUNCTION pmo.fn_evm_health(p_cpi NUMERIC, p_spi NUMERIC)
RETURNS VARCHAR(16)
LANGUAGE plpgsql
IMMUTABLE
AS $$
BEGIN
    -- 判定规则: 与 v_project_evm_overrun.suggested_health 一致
    -- CPI>=1.0 AND SPI>=1.0 → GREEN
    -- CPI>=0.9 AND SPI>=0.9 → YELLOW
    -- 其他 → RED
    IF p_cpi IS NULL OR p_spi IS NULL THEN
        RETURN 'GREEN';   -- 无 EVM 数据时按正常
    END IF;
    IF p_cpi >= 1.0 AND p_spi >= 1.0 THEN
        RETURN 'GREEN';
    ELSIF p_cpi >= 0.9 AND p_spi >= 0.9 THEN
        RETURN 'YELLOW';
    ELSE
        RETURN 'RED';
    END IF;
END;
$$;
COMMENT ON FUNCTION pmo.fn_evm_health(NUMERIC, NUMERIC) IS
    'EVM 健康度判定: 输入 CPI/SPI,返回 GREEN/YELLOW/RED';


-- 辅助函数: 一键把 EVM 快照写入 budget_snapshot
-- 供 EvmSnapshotJob 调用,业务侧不需要直接算指标
DROP FUNCTION IF EXISTS pmo.fn_snapshot_evm(BIGINT, VARCHAR, BIGINT);

CREATE OR REPLACE FUNCTION pmo.fn_snapshot_evm(
    p_project_id BIGINT,
    p_reason     VARCHAR,
    p_created_by BIGINT DEFAULT NULL
)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    v_next_version INT;
    v_bac NUMERIC; v_pv NUMERIC; v_ev NUMERIC; v_ac NUMERIC;
    v_cpi NUMERIC; v_spi NUMERIC; v_eac NUMERIC; v_etc NUMERIC; v_vac NUMERIC;
    v_new_id BIGINT;
BEGIN
    -- 1) 算下一版 version
    SELECT COALESCE(MAX(version), 0) + 1
      INTO v_next_version
      FROM budget_snapshot
     WHERE project_id = p_project_id;

    -- 2) 调 fn_compute_evm
    SELECT bac, pv, ev, ac, cpi, spi, eac, etc, vac
      INTO v_bac, v_pv, v_ev, v_ac, v_cpi, v_spi, v_eac, v_etc, v_vac
      FROM pmo.fn_compute_evm(p_project_id);

    -- 3) 插 budget_snapshot
    INSERT INTO budget_snapshot (
        project_id, snapshot_date, version, reason,
        bac, pv, ev, ac, cpi, spi, eac, etc, vac, created_by
    ) VALUES (
        p_project_id, CURRENT_DATE, v_next_version, p_reason,
        v_bac, v_pv, v_ev, v_ac, v_cpi, v_spi, v_eac, v_etc, v_vac, p_created_by
    )
    RETURNING id INTO v_new_id;

    -- 4) 同步冗余字段到 project(供仪表盘快查)
    UPDATE project
       SET bac         = v_bac,
           evm_cpi     = v_cpi,
           evm_spi     = v_spi,
           evm_eac     = v_eac,
           evm_etc     = v_etc,
           evm_vac     = v_vac,
           evm_updated_at = NOW()
     WHERE id = p_project_id;

    RETURN v_new_id;
END;
$$;
COMMENT ON FUNCTION pmo.fn_snapshot_evm(BIGINT, VARCHAR, BIGINT) IS
    '一键 EVM 快照: 算指标 + 写 budget_snapshot + 同步 project 表';
-- 注: budget_snapshot 触发器禁止 UPDATE/DELETE,但 INSERT 不受影响


-- ============================================================
-- A.1.9 WBS 树查询递归视图
-- v_wbs_tree: 一次性返回项目全树 + 深度 + 路径编码
-- 前端 Element Plus el-tree 直接用
-- ============================================================

DROP VIEW IF EXISTS v_wbs_tree;

CREATE OR REPLACE VIEW v_wbs_tree AS
WITH RECURSIVE wbs_rec AS (
    -- 锚点: 根任务(parent_id IS NULL)
    SELECT  t.id,
            t.project_id,
            t.parent_id,
            t.wbs_code,
            t.name,
            t.task_type,
            t.status,
            t.owner_user_id,
            t.plan_start_date,
            t.plan_end_date,
            t.actual_start_date,
            t.actual_end_date,
            t.plan_hours,
            t.actual_hours,
            t.progress_pct,
            t.weight,
            t.is_critical,
            t.is_milestone,
            t.milestone_id,
            t.predecessor_ids,
            t.deliverable,
            t.remark,
            t.created_at,
            t.updated_at,
            0                       AS depth,                          -- 根 = 0
            ARRAY[t.wbs_code]::TEXT[] AS path,                          -- 路径编码数组
            LPAD('0', 3, '0') || t.wbs_code
                || CASE WHEN t.parent_id IS NULL THEN '' ELSE '' END
                                    AS sort_key                        -- 排序用
    FROM wbs_task t
    WHERE t.parent_id IS NULL
      AND NOT t.deleted
    UNION ALL
    -- 递归: 子任务
    SELECT  c.id,
            c.project_id,
            c.parent_id,
            c.wbs_code,
            c.name,
            c.task_type,
            c.status,
            c.owner_user_id,
            c.plan_start_date,
            c.plan_end_date,
            c.actual_start_date,
            c.actual_end_date,
            c.plan_hours,
            c.actual_hours,
            c.progress_pct,
            c.weight,
            c.is_critical,
            c.is_milestone,
            c.milestone_id,
            c.predecessor_ids,
            c.deliverable,
            c.remark,
            c.created_at,
            c.updated_at,
            p.depth + 1                                                    AS depth,
            p.path || c.wbs_code                                            AS path,
            p.sort_key || '.' || LPAD('0', 3, '0') || c.wbs_code           AS sort_key
    FROM wbs_task c
    JOIN wbs_rec p ON c.parent_id = p.id
    WHERE NOT c.deleted
)
SELECT * FROM wbs_rec;
COMMENT ON VIEW v_wbs_tree IS 'WBS 任务树(递归 CTE,深度/路径编码/排序键,供 el-tree 直接使用)';


-- 视图: 项目级 WBS 进度汇总(给仪表盘用)
DROP VIEW IF EXISTS v_wbs_progress_summary;

CREATE OR REPLACE VIEW v_wbs_progress_summary AS
SELECT  project_id,
        COUNT(*)                                      AS task_count,
        COUNT(*) FILTER (WHERE status = 'COMPLETED')  AS completed_count,
        COUNT(*) FILTER (WHERE status = 'IN_PROGRESS')AS in_progress_count,
        COUNT(*) FILTER (WHERE status = 'BLOCKED')    AS blocked_count,
        COUNT(*) FILTER (WHERE status = 'NOT_STARTED')AS not_started_count,
        COUNT(*) FILTER (WHERE is_critical)           AS critical_count,
        COUNT(*) FILTER (WHERE is_milestone)          AS milestone_count,
        COALESCE(SUM(weight * progress_pct)::NUMERIC
                 / NULLIF(SUM(weight), 0), 0)         AS weighted_progress_pct,
        COALESCE(SUM(plan_hours),   0)                AS total_plan_hours,
        COALESCE(SUM(actual_hours), 0)                AS total_actual_hours,
        CASE WHEN SUM(plan_hours) > 0
             THEN ROUND(SUM(actual_hours)::NUMERIC
                        / SUM(plan_hours)::NUMERIC * 100, 1)
             ELSE 0
        END                                           AS hours_burn_pct
FROM wbs_task
WHERE NOT deleted
GROUP BY project_id;
COMMENT ON VIEW v_wbs_progress_summary IS '项目级 WBS 进度汇总(任务数/加权进度/工时燃尽比)';


-- 视图: 资源分配汇总(PM 看"我团队下个月有多少投入")
DROP VIEW IF EXISTS v_assignment_summary;

CREATE OR REPLACE VIEW v_assignment_summary AS
SELECT  a.user_id,
        u.full_name,
        a.wbs_task_id,
        wt.wbs_code,
        wt.name                                        AS task_name,
        wt.project_id,
        p.code                                         AS project_code,
        p.name                                         AS project_name,
        a.role,
        a.planned_hours,
        a.actual_hours,
        a.start_date,
        a.end_date
FROM wbs_assignment a
JOIN app_user u        ON u.id = a.user_id
JOIN wbs_task wt       ON wt.id = a.wbs_task_id
JOIN project p         ON p.id = wt.project_id
WHERE NOT a.deleted
  AND NOT wt.deleted
  AND NOT p.deleted;
COMMENT ON VIEW v_assignment_summary IS '资源分配汇总(用户/任务/项目/工时)';
