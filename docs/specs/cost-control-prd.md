---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: 财务精细化成本管控 PRD(5 大模块 / 验收标准 / 技术架构 / 风险)
---

# 成本控制 PRD(Cost Control PRD)

> 单一事实来源:财务精细化成本管控系统的产品需求(F1-F5 五大模块)。
> 对应来源:[`legacy/PRD-cost-control.md`](legacy/PRD-cost-control.md)

---

## 1. 项目目标

### 1.1 业务目标

1. **看得见**: 从工时数据自动算清"每个项目/阶段/部门花多少钱"
2. **控得住**: 偏差超 10% 自动预警,财务对账 3 单闭环
3. **能决策**: 高层 4 指标黄金区一屏看全,移动端可达

### 1.2 非业务目标

- **合规**: 满足金融行业 6 个月审计日志硬性要求
- **可扩展**: 支持后续��入 用友/金蝶/Oracle EBS

---

## 2. 用户角色

| 角色 | 场景 | 关键诉求 |
|---|---|---|
| **CFO/董事会** | 月度例会前,拿手机看 | 4 指标 + 异常红绿灯 |
| **财务总监** | 每月 5 号对账 | 合同/发票/付款 3 单匹配 |
| **PMO 负责人** | 资源规划 | 多项目组合成本 + 冲突预警 |
| **PM** | 周会 | 我负责项目实时预算偏差 |
| **部门负责人** | 季度复盘 | 部门人均成本 + 产能 |

---

## 3. P0 五大模块(16 周)

### F1. 工时→成本引擎(Week 1-2)

- `hourly_rate_v4` 表(人 × 月度 × 角色)
- 财务上传 `cost_rates.csv` 月度调档
- 后端 SQL: `工时 × 时薪 = 当月人力成本`
- API: `GET /api/cost/user/{userId}?month=`

### F2. 多维成本核算(Week 3-5)

- 视图: `v_project_cost` / `v_phase_cost` / `v_dept_cost`
- 报表: 项目 × 月 预算/实际/偏差
- 报表: 7 阶段 预算占比 vs 实际占比
- 报表: 部门 人均产能/人均成本
- API: `GET /api/cost/dimension?type=...&from=...&to=...`

### F3. 财务对账 + 合同闭环(Week 6-9)

- 4 新表: `contract` / `invoice` / `payment` / `cost_item`
- 3 单匹配引擎(合同/发票/付款)
- 项目维度自动入账
- 审计追溯链: 任何 1 元 → 合同/发票/审批人/任务
- API: `GET /api/finance/contracts` + `POST /api/finance/match`

### F4. 成本预警 + 偏差分析(Week 10-13)

- 规则引擎: 6 条规则(详见 [`cost-engine.md`](cost-engine.md))
- EAC/ETC/VAC 挣值分析
- 通知: 消息中心 + 邮件 + 钉钉
- 看板: 红/黄/绿三态
- API: `GET /api/cost/forecast/{projectId}`

### F5. 高层驾驶舱(Week 14-16)

- 4 大核心指标(黄金区)
  1. 本季总投入/预算占比
  2. 偏差 ±10% 异常项目数
  3. 7 阶段成本结构(饼图)
  4. 重点项目里程碑红绿灯
- 响应式移动端
- 项目组合视图(PMO 视角)
- 数字孪生雏形
- API: `GET /api/dashboard/exec`

---

## 4. 验收标准

| 模块 | 验证项 |
|---|---|
| F1 | 上传 `cost_rates.csv` → 财务输入张三 6 月 40h → 返回 ¥24,000 |
| F2 | 选项目 P-001 / 6 月 → 看到 7 阶段预算占比 vs 实际占比 |
| F3 | 上传合同 + 3 张发票 + 2 笔付款 → 系统自动 3 单匹配 + 入账 |
| F4 | P-001 实际/预算 > 90% → 触发钉钉 + 邮件 + 看板红 |
| F5 | 4 指标 < 3s 加载,移动端 < 2s |

---

## 5. 技术架构

### 5.1 复用

- `timesheet` / `timesheet_entry` (F1/F2)
- `milestone` + `milestone_phase` (F2/F4)
- `project` + `user` (F1/F2)
- `Workload.vue` / `MilestoneAnalysis.vue` (前端集成)

### 5.2 新增

- 4 张表: `hourly_rate_v4` / `contract` / `invoice` / `payment`
- 1 个服务: `cost-engine`(定时跑规则)
- 1 个端口: `finance-api`(财务对账)
- 1 个前端: `CostDashboard.vue`(F1-F5 集成)

---

## 6. 风险与依赖

| 风险 | 缓解 |
|---|---|
| 财务主数据采集慢 | 财务 BA 提前 2 周介入 |
| 用友接口未确认 | 走 webhook 增量,失败重试 3 次 |
| 高层变更需求 | 黄金区 4 指标先定,其他迭代 |
| 数据量大 SQL 慢 | 提前建 `idx_...` 复合索引 |

---

## 7. 不在范围(Out of Scope)

- 银企直连(P3 后续)
- 预算编制工作流(P3 后续)
- 多币种汇率(V4.1)
- 数字孪生仿真引擎(V5.0)
