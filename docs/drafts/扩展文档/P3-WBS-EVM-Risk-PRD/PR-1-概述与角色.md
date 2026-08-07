# PR-1: P3 模块概述与角色权限

> **版本**: v0.1 (草稿)
> **作者**: PMO 研发组
> **评审**: @PM @架构师 @后端 @前端 @QA
> **更新**: 2025-06-10
> **状态**: ⏳ 评审中

---

## 1. 背景与目标

### 1.1 业务痛点

PMO 在管 30+ 项目时, 当前存在三大问题:

| # | 痛点 | 现状 | 后果 |
|:---:|---|---|---|
| 1 | **项目黑盒** | PM 用 Excel 维护 WBS, PMO 月底收数 | 进度失真, 失控才发现 |
| 2 | **完工不知花多少** | 工时/预算散落 4-5 张表 | 完工超支 20%-50% 常见 |
| 3 | **风险凭感觉** | 风险在脑子里, 没记录没跟踪 | 救火文化, 救完就忘 |

### 1.2 目标

P3 模块聚焦 **"看得清 + 算得准 + 控得住"**:

- **看得清**: WBS 树 + 甘特 + 网络图, 项目结构/时间/依赖一眼看穿
- **算得准**: EVM 6 公式 (CV/SV/CPI/SPI/EAC/ETC/VAC), 项目健康度量化
- **控得住**: 风险矩阵 5×5 + 应对行动 + 变更历史, 风险有迹可循

### 1.3 不做什么 (Out of Scope)

| 不做 | 原因 |
|---|---|
| 工时审批 / 工资核算 | 已有 P1.6 / P2 模块 |
| IM 通知 / 消息推送 | 已有 P2-A 模块 |
| 计费 / 发票 | 财务模块, 后续 P4+ |
| 项目立项审批 | 已有 P1.2 / P1.3 模块 |
| 文档协作 / Wiki | 知识库, P4+ 再说 |

---

## 2. 范围 (In Scope)

### 2.1 功能清单 (8 大模块)

| # | 模块 | 主要能力 | 优先级 |
|:---:|---|---|:---:|
| 1 | WBS 任务树 | CRUD/拖拽/重排/树组装 | P0 |
| 2 | 资源分配矩阵 | 5 角色 × N 任务, 工时汇总 | P0 |
| 3 | EVM 趋势卡片 | 9 KPI + 5 折线 + 健康度灯 | P0 |
| 4 | EVM 快照引擎 | 手动触发 + 定时 Job + 里程碑联动 | P0 |
| 5 | 甘特图 (任务级) | 复用 GanttView, 任务适配 | P1 |
| 6 | 网络图 + 关键路径 | ECharts GraphChart, CPM 算法 | P1 |
| 7 | 风险 CRUD + 矩阵 | 5×5 概率×影响, 应对行动 | P0 |
| 8 | 风险变更历史 | append-only, 软删记 history | P0 |

### 2.2 交付物

| 类型 | 数量 | 详情 |
|---|---|---|
| 后端 Java 类 | 26 | 7 实体 + 1 record + 4 service + 2 controller + 12 dto |
| 后端 endpoint | 31 | 20 WBS + 11 Risk |
| 前端组件 | 13 | 5 视图 + 5 弹窗 + 3 卡片/列表 |
| 前端路由 | 5 | /wbs /wbs/:id /risks /risks/:id /gantt |
| 数据库表 | 5 | wbs_task / wbs_assignment / budget_snapshot / risk / risk_response / risk_history |
| SQL 函数 | 1 | pmo.fn_snapshot_evm (PG) |
| 单元测试 | 100+ | JUnit 5 + Mockito + AssertJ |

---

## 3. 用户故事

### 3.1 角色 5 类

| 角色 | 简称 | 典型用户 | 关注点 |
|---|---|---|---|
| **PMO_ADMIN** | 治理 | PMO 总监 | 跨项目仪表盘/审计 |
| **PM** | 项目经理 | 项目经理 | 自己项目的 WBS/进度/EVM/风险 |
| **EXEC** | 高层 | VP / CTO | 跨项目健康度, 不操作 |
| **DOER** | 执行 | 开发/测试工程师 | 自己的任务/工时 |
| **OBSERVER** | 旁观 | 客户/审计 | 只读, 不写 |

### 3.2 用户故事 (8 条, 代表性)

```
US-1 [PM] 作为 PM, 我要在 WBS 树里拖拽任务到不同父节点下, 让团队结构跟项目同步
US-2 [PM] 作为 PM, 我要看 EVM 卡片, 3 秒内判断项目是健康/亚健康/病危
US-3 [PM] 作为 PM, 我要手动触发 EVM 快照, 周一开会前录入最新数据
US-4 [PM] 作为 PM, 我要在资源矩阵里给"1.1 需求"分配"张三负责/李四执行"
US-5 [PM] 作为 PM, 我要看网络图, 一眼看出哪条任务链决定项目生死
US-6 [PM] 作为 PM, 我要在 5×5 矩阵里登记一个新风险 "需求变更频繁", 概率 4 影响 5
US-7 [PMO_ADMIN] 作为 PMO 总监, 我要看所有项目的风险热力图, 找出"红色区"项目
US-8 [DOER] 作为开发, 我要打开 WBS 任务详情, 看自己的任务和负责人
```

### 3.3 角色权限矩阵 (30 个 endpoint × 5 角色)

| 模块 | endpoint | PMO_ADMIN | PM | EXEC | DOER | OBSERVER |
|---|---|:---:|:---:|:---:|:---:|:---:|
| **WBS 任务** | GET /wbs/tasks/by-project/{id} | ✅ | ✅ | ✅ | ✅ | ✅ |
| | POST /wbs/tasks | ✅ | ✅ (自己项目) | ❌ | ❌ | ❌ |
| | DELETE /wbs/tasks/{id} | ✅ | ✅ (自己项目) | ❌ | ❌ | ❌ |
| | POST /wbs/tasks/{id}/move | ✅ | ✅ | ❌ | ❌ | ❌ |
| | POST /wbs/projects/{id}/auto-reorder | ✅ | ✅ | ❌ | ❌ | ❌ |
| **WBS 分配** | GET /wbs/assignments/* | ✅ | ✅ | ✅ | ✅ (限自己) | ✅ |
| | POST /wbs/assignments | ✅ | ✅ | ❌ | ❌ | ❌ |
| | DELETE /wbs/assignments/{id} | ✅ | ✅ | ❌ | ❌ | ❌ |
| **EVM 快照** | GET /wbs/snapshots/* | ✅ | ✅ | ✅ | ❌ | ✅ |
| | POST /wbs/snapshots/{id}/trigger | ✅ | ✅ | ❌ | ❌ | ❌ |
| **WBS 可视化** | GET /wbs/gantt/by-project/{id} | ✅ | ✅ | ✅ | ✅ | ✅ |
| | GET /wbs/network/by-project/{id} | ✅ | ✅ | ✅ | ✅ | ✅ |
| **风险** | GET /risks/by-project/{id} | ✅ | ✅ | ✅ | ✅ | ✅ |
| | POST /risks | ✅ | ✅ | ❌ | ❌ | ❌ |
| | DELETE /risks/{id} | ✅ | ✅ | ❌ | ❌ | ❌ |
| | GET /risks/{id}/responses | ✅ | ✅ | ✅ | ✅ | ✅ |
| | POST /risks/{id}/responses | ✅ | ✅ | ❌ | ❌ | ❌ |
| | GET /risks/{id}/history | ✅ | ✅ | ✅ | ❌ | ✅ |
| | GET /risks/health/by-project/{id} | ✅ | ✅ | ✅ | ❌ | ✅ |
| | GET /risks/matrix/by-project/{id} | ✅ | ✅ | ✅ | ❌ | ✅ |

**默认**: 未登录 401, 已登录但无权限 403.

---

## 4. 术语表 (EVM + PMBOK)

| 术语 | 全称 | 含义 | 健康阈值 |
|---|---|---|---|
| **WBS** | Work Breakdown Structure | 工作分解结构, 树形任务清单 | — |
| **EVM** | Earned Value Management | 挣值管理, 用钱量化项目 | — |
| **BAC** | Budget At Completion | 完工预算 | — |
| **PV** | Planned Value | 计划值 (到今天该完成多少) | — |
| **EV** | Earned Value | 挣值 (实际干完多少) | — |
| **AC** | Actual Cost | 实际成本 (花了多少钱) | — |
| **CV** | Cost Variance | 成本偏差 = EV - AC | ≥ 0 |
| **SV** | Schedule Variance | 进度偏差 = EV - PV | ≥ 0 |
| **CPI** | Cost Performance Index | 成本绩效 = EV / AC | ≥ 1.0 |
| **SPI** | Schedule Performance Index | 进度绩效 = EV / PV | ≥ 1.0 |
| **EAC** | Estimate At Completion | 完工估算 = BAC / CPI | ≤ BAC × 1.1 |
| **ETC** | Estimate To Complete | 完工尚需 = EAC - AC | — |
| **VAC** | Variance At Completion | 完工偏差 = BAC - EAC | ≥ 0 |
| **CPM** | Critical Path Method | 关键路径法, 找最长任务链 | — |
| **ES/EF/LS/LF** | Earliest/Latest Start/Finish | CPM 4 时刻 | — |
| **Slack** | Float | 总浮动 = LS - ES, = 0 即关键 | — |
| **Risk Score** | 风险分 = prob × impact | 5×5 矩阵分值, 1-25 | < 10 低, ≥ 15 高 |
| **Predecessor** | 紧前任务 | 必须先完成的任务 | — |
| **Milestone** | 里程碑 | 关键时间点, 工期为 0 | — |
| **Weight** | 权重 | 任务在父级汇总中的占比, 1-10 | — |

---

## 5. 技术栈

### 5.1 后端

| 维度 | 选型 | 版本 | 理由 |
|---|---|---|---|
| 语言 | Java | 21 (LTS) | record / sealed / pattern matching |
| 框架 | Spring Boot | 3.3.x | Jakarta EE 10, 自动配置 |
| 持久化 | Spring Data JPA + Hibernate | 6.5.x | 实体映射, 写 JPQL |
| 数据库 (生产) | PostgreSQL | 16 | 触发器/函数/JSONB 全支持 |
| 数据库 (测试) | H2 | 2.2.x | 内存跑, 零依赖 |
| 迁移 | Flyway | 10.x | V2.5__wbs.sql 等 |
| 安全 | Spring Security + JWT | 6.x | @RequireRoles 注解 |
| API 文档 | springdoc-openapi | 2.6.x | Swagger UI |
| 工具 | Lombok + MapStruct | — | 减样板 |
| 测试 | JUnit 5 + Mockito + AssertJ | 5.10 / 5.12 | 单测覆盖 |

### 5.2 前端

| 维度 | 选型 | 版本 | 理由 |
|---|---|---|---|
| 框架 | Vue | 3.5.x | Composition API + script setup |
| UI 库 | Element Plus | 2.14.x | el-tree / el-table / el-dialog |
| 图表 | ECharts | 6.1.x | GraphChart / LineChart |
| Vue 绑定 | vue-echarts | 8.0.x | 响应式 option |
| 路由 | Vue Router | 4.x | 5 条新路由 |
| 状态 | Pinia | 2.x | auth/project store |
| HTTP | Axios | 1.x | 拦截器 + 统一错误 |
| 构建 | Vite | 5.x | 极速 HMR |
| 类型 | TypeScript | 5.4.x | 严格模式 |
| 测试 | (规划中) Vitest | 1.x | 组件测, 当前 0 用例 |

### 5.3 部署

- **开发**: Docker Compose (PostgreSQL + Redis)
- **CI**: GitHub Actions (mvn test → pnpm build)
- **生产**: K8s (待规划)

---

## 6. 非功能目标

### 6.1 性能

| 指标 | 目标 | 实测 (P3.5) |
|---|---|---|
| WBS 树 (100 任务) 加载 | < 200ms | 180ms ✅ |
| WBS 树 (500 任务) 加载 | < 500ms | 420ms ✅ |
| EVM 趋势 (30 天) 加载 | < 500ms | 320ms ✅ |
| 甘特图 (50 任务) 渲染 | < 800ms | 650ms ✅ |
| 网络图 (30 节点) 渲染 | < 1s | 780ms ✅ |
| 资源矩阵 (30 任务 × 10 人) | < 300ms | 240ms ✅ |
| 风险矩阵 (50 风险) | < 300ms | 180ms ✅ |

### 6.2 兼容性

- **浏览器**: Chrome 90+, Edge 90+, Safari 15+
- **数据库**: PG 14+ (生产), H2 2.2+ (测试, MySQL 留扩展)
- **屏幕**: ≥ 1280×720 (WBS 树/资源矩阵需宽屏)
- **语言**: 中文 (zh-CN), 预留 i18n 框架

### 6.3 安全性

- **认证**: JWT Bearer Token, 24h 过期
- **授权**: @RequireRoles.Read / .Operate 注解, 5 角色
- **审计**: @AuditLog 注解, 写操作自动入库
- **SQL 注入**: JPA 参数化, 无字符串拼接
- **XSS**: Vue 自动转义, 后端白名单校验
- **CSRF**: 前后端分离, 不依赖 cookie

### 6.4 可用性

- **错误提示**: 中文友好提示 + 错误码 (如 WBS-001)
- **空态**: 全部列表/卡片都有 el-empty 引导
- **加载**: 全部异步操作 v-loading
- **键盘**: Tab 顺序合理, Enter 提交
- **无障碍**: 颜色对比度 ≥ 4.5:1 (WCAG AA)

### 6.5 可维护性

- **代码规范**: Google Java Style + Vue Style Guide
- **注释率**: 公共类 ≥ 30%, 工具类 ≥ 50%
- **测试覆盖率**: 核心 service ≥ 80%, controller ≥ 60%
- **文档同步**: OpenAPI 自动生成, 本 PRD 5 篇同步更新

---

## 7. 验收标准 (本篇 PR-1 维度)

| # | 验收项 | 验证方式 |
|:---:|---|---|
| 1 | 5 角色定义清晰, 互不重叠 | 本文档 §3.1 |
| 2 | 30 个 endpoint 权限分配无歧义 | 本文档 §3.3 矩阵 |
| 3 | 术语表 20 个词, 与 PMBOK 第 6/7 版一致 | 本文档 §4 |
| 4 | 技术栈选型有理由, 不裸用 | 本文档 §5 |
| 5 | 性能目标 7 项, 与 P3.5 实测对得上 | 本文档 §6.1 |

---

## 8. 风险与待定

| # | 风险/待定 | 当前假设 | 需 PM 拍板 |
|:---:|---|---|:---:|
| 1 | 多项目并行的 EVM 趋势是分图还是合图? | 分图 (按 projectId 切) | 待定 |
| 2 | 资源矩阵是否需要批量导入? | 暂不做, v2 | 待定 |
| 3 | 风险应对行动是否需联动任务? | 暂不联动, 仅备注 | 待定 |
| 4 | EVM 快照保留期? | 永久 (append-only) | 已定 |
| 5 | WBS 编码允许 1.1.1.1.1 几层深? | 建议 ≤ 5 层, 不限 | 待定 |
| 6 | 前端是否需要 Vitest 单测? | 当前 0 用例, P4+ 补 | 待定 |

---

## 9. 关联文档

- 立项 PRD: `zhiyu-requirements/03-项目管理/03-项目管理模块.md`
- 数据字典: `zhiyu-requirements/A1-数据字典/`
- API 规范: `zhiyu-requirements/A2-API规范/`
- 后续: PR-2 (数据模型) / PR-3 (核心功能) / PR-4 (可视化) / PR-5 (API)
