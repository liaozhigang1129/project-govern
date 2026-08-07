# PMO 项目管理系统 — 立项提案 (Proposal)

> 文件: `pmo-pms-proposal.md` · 版本: v0.1.0 · 状态: ✅ 已批准落地
> 配套: [`pmo-pms-mvp-design.md`](./pmo-pms-mvp-design.md) · [`../README.md`](../README.md)

---

## 1. 一句话立项理由

公司当前缺一个**治理视角**的项目管理系统,PM / 部门负责人 / PMO / 分管副总各方都靠 Excel + 微信群协同,信息不对称、状态不同步、健康度无人盯、审批无留痕。**知驭 ZhiYu(MVP)** 是填补这个空白的最小可用系统。

---

## 2. 业务背景与痛点

| 角色 | 当前痛点 | 业务影响 |
|---|---|---|
| **项目经理 (PM)** | 项目进度靠脑记 / Excel,里程碑没结构化跟踪 | 高管看不到真实进度,频繁被"进展怎么样?"打断 |
| **部门负责人 (DEPT_LEAD)** | 立项靠走邮件 / 拉群,审批无留痕 | 翻历史困难,出过"项目到底批没批"的扯皮 |
| **PMO 管理员 (PMO_ADMIN)** | 健康度(正常/关注/严重)靠个人主观判断,无统一数据 | 风险项目到最后一刻才暴露,救火成本极高 |
| **分管副总 (EXEC)** | 没有 Dashboard,Excel 月报滞后一周 | 决策慢、看不到治理趋势 |
| **只读访客 (VIEWER)** | 没渠道,只能私下问 | 审计、跨部门协作效率低 |

**核心矛盾**:业务复杂度在涨(项目类型多、跨部门多、预算大),但**管理手段停留在 2010 年代的 Excel**,治理盲区越来越大。

---

## 3. 目标与非目标

### 3.1 目标 (Phase 1 / MVP)

1. **立项审批在线化**:提交 → 部门 → PMO → 副总 三级流转,全部留痕可追溯
2. **项目主数据统一**:一张表管全公司的项目,类型/状态/健康度字典化
3. **里程碑 + 加权进度**:替代"PM 自己拍脑袋报 60%"
4. **Dashboard 一页看全**:4 项 KPI + 状态/健康度分布 + 项目卡片
5. **鉴权最小化**:JWT 登录,5 个内置角色,RBAC 就位
6. **契约先行**:OpenAPI 3.0 + Postman 29 用例 + 端到端 smoke 16 调用

### 3.2 非目标 (Phase 2+ 再做)

- ❌ 预算/工时/资源的精细化管控(目前只录 `budget_estimate` / `plan_workdays`)
- ❌ 工时填报、人员负载、甘特图
- ❌ 自定义审批流 / 条件分支(目前固定 3 级)
- ❌ 消息中心 / WebSocket 推送(目前靠轮询 + 邮件外发)
- ❌ 多租户 / SaaS 化(目前单公司)
- ❌ 移动端 / 小程序(目前 PC Web only)

---

## 4. 干系人与角色

| 角色 | 编码 | 人数(MVP) | 核心职责 |
|---|---|---|---|
| 项目经理 | `PM` | ~20 | 创建/维护自己项目的里程碑、报进度 |
| 部门负责人 | `DEPT_LEAD` | 4 | 审批本部门提交的立项 |
| PMO 管理员 | `PMO_ADMIN` | 1-2 | 立项复核、治理指标配置 |
| 分管副总 | `EXEC` | 1-3 | 最终审批 |
| 只读访客 | `VIEWER` | 不限 | 看 Dashboard / 报表,无写权限 |

> 角色在 `V1.4__seed_data.sql` 里 seed,5 条数据。**Phase 2 才做"用户-角色多对多"**(`user_role` 表已预留)。

---

## 5. 核心业务流程

### 5.1 立项三级审批

```
申请人提交立项(IR-2025-001)
    ↓
[DEPT_LEAD] 部门初审 — 通过/驳回/补材料
    ↓ 通过
[PMO_ADMIN] PMO 治理复核 — 通过/驳回/补材料
    ↓ 通过
[EXEC] 分管副总终审 — 通过/驳回
    ↓ 通过
  ✓ 自动建项目(P-AUTO-XXX)并置为 ACTIVE
```

**状态机编码**(`initiation_status` 字典):

| code | name | sort_order | 终态? |
|---|---|---|---|
| DRAFT | 草稿 | 0 | 否 |
| PENDING | 审批中 | 1 | 否 |
| DEPT_APPROVED | 部门通过 | 2 | 否 |
| PMO_APPROVED | PMO 通过 | 3 | 否 |
| EXEC_APPROVED | 已批准 | 4 | **是** |
| REJECTED | 已驳回 | 5 | **是** |
| SUPPLEMENT | 需补充 | 6 | 否 |

### 5.2 项目生命周期

```
草稿(DRAFT)
  ↓ 立项批准
待立项(PENDING)            ←—— 实际上是审批流后段
  ↓ 启动
执行中(ACTIVE)              ←—— 里程碑维护、健康度评估
  ↓ 暂停
已暂停(SUSPENDED)
  ↓ 恢复 → ACTIVE
  ↓ 结项
已结项(CLOSED)   [终态]
  ↘ 任意阶段被驳回 → REJECTED  [终态]
```

### 5.3 加权进度计算

```
项目进度% = Σ(里程碑权重 × 状态完成度) / Σ(权重) × 100

状态完成度:
  PENDING      = 0
  IN_PROGRESS  = 0.5
  COMPLETED    = 1
  DELAYED      = 0  ← 故意为 0,延期不算"做了"
```

**实现位置**:`MilestoneRepository.computeWeightedProgressPct()`,**JPQL 聚合一次往返**,`NULLIF + COALESCE` 兜底空集/0 权重。

---

## 6. 关键数据字典

| 表 | 字段含义 | 行数 (seed) |
|---|---|---|
| `project_type` | 项目类型(交付/自研/内部/研发) | 4 |
| `project_status` | 项目状态(6 态) | 6 |
| `health_level` | 健康度(绿/黄/红 + 颜色 hex) | 3 |
| `initiation_status` | 立项状态(7 态) | 7 |
| `milestone_status` | 里程碑状态(4 态) | 4 |
| `approval_step` | 审批步骤(固定 3 级) | 3 |
| `role` | 角色(5 内置) | 5 |
| `department` | 部门(树形) | 4 |
| `app_user` | 用户(密码 BCrypt) | 6 |

所有字典表**只读 / 增量由 SQL seed 维护**,不在应用层做 CRUD 接口(避免业务乱改)。如需新增字典值,Q&A 4 写明:插行即可,前端 `/api/dict/*` 自动返回。

---

## 7. 成功指标 (Phase 1)

| 指标 | 目标值 | 验收方式 |
|---|---|---|
| 立项审批平均周期 | ≤ 5 个工作日 | 统计 `closed_at - submitted_at` |
| 项目数据完整率(类型/PM/计划起止齐备) | ≥ 95% | Dashboard 加 SQL 校验 |
| 活跃项目里程碑覆盖 | 100% | 任意 ACTIVE 项目 ≥ 2 个里程碑 |
| 健康度更新及时性 | 严重(RED)项目 7 天内有评估 | Dashboard KPI + 人工抽检 |
| 月报耗时 | 从 1 周缩到 1 天 | Dashboard 一键截图 |
| 用户活跃度 | 月活 ≥ 80% 干系人 | 登录日志统计(Phase 2) |

---

## 8. 风险与对策

| 风险 | 等级 | 对策 |
|---|---|---|
| **审批流程变来变去** | 高 | MVP 写死 3 级;Phase 2 引入流程引擎(Camunda/Flowable)前不开放配置 |
| **Excel 飞书/钉钉导入历史数据** | 中 | 不在 MVP 做。Phase 1 接受双轨;给 PMO_ADMIN 一个后台 SQL 脚本工具 |
| **健康度评估主观** | 中 | 用"延期天数 + 里程碑完成率"自动建议健康度(Phase 1.5) |
| **JWT secret 泄露** | 中 | 默认 secret 仅供 dev;生产强制 `PMO_SECURITY_JWT_SECRET` 环境变量(README §10.2) |
| **前端/后端契约漂移** | 中 | OpenAPI 是 single source of truth;`docs/openapi/openapi.json` 由 CI 自动抓取并失败时报警 |
| **MySQL/PostgreSQL 方言差异** | 低 | Flyway 脚本用 PG 方言;MySQL 靠 `docker-compose.yml` 默认配置 + 测试用 H2(模拟 PG 模式) |

---

## 9. 范围 / 计划

| 阶段 | 时间 | 产出 | 当前状态 |
|---|---|---|---|
| **P0 - 调研 / 提案** | 2025-Q2 | 本文档 | ✅ |
| **P1 - MVP 后端 + 最小前端** | 2025-Q3 | Spring Boot 3.3 + Vue 3 + 28 测试 + Postman | ✅ |
| **P1.1 - 契约修复** | 2025-Q3 末 | DTO 化 ProjectController + 字典子接口 + 36 测试 | ✅ |
| **P1.2 - 部署 / CI** | 2025-Q4 | Docker Compose + GitHub Actions 4 jobs | ✅ |
| **P1.3 - E2E 测试** | 2025-Q4 | Node 18 零依赖 E2E(3 suite 18 case) + Cypress 选装 | ✅ |
| **P1.4 - 立项审批流闭环** | 2026-Q1 | 立项提交→三级审批→补料重提→详情抽屉/timeline + E2E 30 case(原 18 + 立项 12) | ✅ |
| **P1.5 - 体验增强** | 2026-Q2 | Flyway 双方言 + 健康度自动建议 + CORS 收紧;JWT + 邮件 + Audit | ✅ |
| **P2 - 工时 / 资源 / 通知** | 2026-Q3 | (规划中) | ⏳ |
| **P3 - 多租户 / SaaS** | 2026-Q2+ | (规划中) | ⏳ |

---

## 10. 预算与人力

| 角色 | 投入 | 备注 |
|---|---|---|
| 后端 | 1 人 × 12 周 (P1) | Spring Boot / JPA / Flyway |
| 前端 | 0.5 人 × 8 周 (P1) | Vue 3 / Element Plus / ECharts |
| PMO 业务方 | 0.2 人持续 | 需求澄清 + UAT |
| 运维 | 0.1 人 × 2 周 | Docker / CI |

**基础设施**:MySQL 8(自有)/ PostgreSQL 16(测试)·Docker Compose ·GitHub Actions 免费额度内。

---

## 11. 决策记录 (ADR 摘要)

| 决定 | 选项 | 选 | 理由 |
|---|---|---|---|
| 后端语言 | Java / Go / Node | **Java 17/21** | 团队熟悉、Spring 全家桶、JPA 省 DAO |
| 前端框架 | Vue / React | **Vue 3.5** | 中文社区、上手快、Element Plus 组件齐 |
| ORM | JPA / MyBatis | **JPA + Flyway** | 字典化表多,JPQL 聚合优雅 |
| DB | MySQL / PostgreSQL | **MySQL 8 生产 + PG 16 测试** | 公司已用 MySQL,生产稳定 |
| 鉴权 | Session / JWT | **JWT (HS512)** | 前后端分离,无状态,易扩 |
| 部署 | K8s / Compose | **Compose v2 (单机 MVP)** | 用户量小,K8s 是过度工程 |
| 测试 | JUnit + Postman + Cypress | **三层都做** | 单元防回归 / 契约防漂移 / E2E 防集成崩 |

---

## 12. 验收

✅ **MVP 已交付**:
- 后端 78 个 JUnit 测试全绿(`mvn test`,P1.4 49 + P1.5 健康度 7 + JWT 18 + Audit 4)
- 16 个 smoke 调用全 2xx(`./docs/api-testing/smoke.sh`)
- 33 个 OpenAPI path(P1.4 25 + JWT 5 + 邮件 1 + Audit 2)
- 30 个 E2E case 全绿(`pnpm e2e`,含立项审批流 12 case)
- 3 个容器一键拉起(`docker compose up -d`)
- CI 4 jobs 全部通过(`....[truncated]
- Dashboard 4 KPI + 2 饼图 + 项目卡片均可视化
- 立项审批三级流(部门 → PMO → 副总)在线化,留痕可追溯;支持补料重提
- **健康度自动建议**:延期天数 + 里程碑完成率 → GREEN/YELLOW/RED(`GET /health-advisor/suggest/{id}` + `POST /apply-all?apply=true`)

**注 P1.4 → P1.5**:
- P1.4 累计 49 单测 / 30 E2E
- P1.5 健康度建议模块 +7 单测 → 56
- P1.5-c JWT +18 单测 → 74
- P1.5-d Audit +4 单测 → **78**
- Flyway 双方言(CI 仍用 PG profile,生产 docker compose 用 mysql profile)

📌 **后续**:接入审批邮件收件箱(IMAP)自动收审批回执 / 移动端 PWA / SSO。
