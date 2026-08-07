---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: 架构设计(系统结构 + 设计理由),不写接口字段级契约
---

# 架构设计(DESIGN)

> 本文档回答"系统长什么样、为什么这么架构",不回答"接口字段长啥样"(那是 [`specs/`](specs/))。
>
> 凡是实现代码时要对照的字段、接口、行为契约一律写在 `specs/`,本文只做结构与理由叙述,通过链接引用。

---

## 1. 架构总览

```
                ┌────────────────────────────────────┐
                │   Browser (Vue 3 SPA)               │
                │   http://localhost:8080            │
                └────────────────┬───────────────────┘
                                 │ /api/*
                ┌────────────────▼───────────────────┐
                │   Nginx 1.27 (容器内)               │
                │   反代 /api → backend:8088          │
                └────────────────┬───────────────────┘
                                 │
                ┌────────────────▼───────────────────┐
                │  Spring Boot 3.3 (Java 21)         │
                │  http://localhost:8088/api         │
                │  ├─ Spring Security + JWT (HS512)  │
                │  ├─ @RequireRoles (RBAC)           │
                │  ├─ springdoc-openapi 2.6          │
                │  ├─ AuditLog 切面 (异步写)          │
                │  └─ JPA / Hibernate 6.x            │
                │  └─ Flyway 10.x (PG+MySQL 双轨)    │
                └────────────────┬───────────────────┘
                                 │ JDBC
                ┌────────────────▼───────────────────┐
                │  MySQL 8.0 (生产)                   │
                │  PostgreSQL 16 (CI/测试)            │
                │  H2 PG-mode (单元测试 in-memory)    │
                └────────────────────────────────────┘

外部依赖:
  IM 通道 ──→ 企业微信 / 钉钉 / 飞书 (Webhook, MVP 无 OAuth)
  邮件    ──→ MailService → mailpit (本地) / SMTP (生产)
  ML 模型 ──→ models/milestone_lgbm_*.pkl (预置, 不在线训练)
```

**3 个进程 / 1 条命令:**

```bash
docker compose up -d   # mysql + backend + frontend 一起拉起
```

---

## 2. 分层与模块

### 2.1 后端模块结构(19 模块)

```
com.company.pmo/
├── common/                  ← 通用基础(横切关注点)
│   ├── api/                 ← 统一响应 / 错误码 / 业务异常
│   ├── audit/               ← 切面 + 异步 + 查询 API
│   ├── exception/           ← 业务异常体系
│   └── security/            ← JWT + 过滤器 + RBAC
│
├── tools/                   ← 工具类(日期、字典、导入导出)
│
├── admin/                   ← 系统管理(预留扩展位)
│
└── module/                  ← 业务模块
    ├── org/                 ← 用户 / 部门 / 角色 (V2.8/V2.9)
    ├── dict/                ← 业务字典 (项目类型 / 状态 / 健康度 / 阶段)
    ├── project/             ← 项目主数据 (V2.x)
    ├── initiation/          ← 立项 + 3 级审批 (V3.0)
    ├── milestone/           ← 里程碑 + 加权进度 (V3.1)
    ├── timesheet/           ← 工时 + 审批流 (V2.11/P2)
    ├── workload/            ← 人员负载矩阵 + Gantt (V2.12)
    ├── risk/                ← 风险矩阵 + 历史快照 (V2.6/V2.7)
    ├── wbs/                 ← WBS 任务拆解 + EVM (P3)
    ├── cost/                ← 成本引擎 (V4.0)
    ├── finance/             ← 财务 3-way match (V4.2)
    ├── alert/               ← 预警数据层 + 控制器 (V4.3/V4.4)
    ├── healthadvisor/       ← 健康度自动建议
    ├── dashboard/           ← KPI 聚合
    ├── notification/        ← 通知中心 + IM 多通道 (P1.5/P2-A)
    ├── dingtalk/            ← 钉钉同步基础 (V2.13)
    ├── workflow/            ← 流程引擎(预留)
    └── member/              ← 项目成员 (V2.3)
```

**模块依赖方向**(严禁反向):
```
common ← tools ← 所有业务模块
        ↑
        └─ admin (横切)
```

业务模块之间**不互相依赖**,跨模块数据通过 Service 调用或事件总线(`NotificationListener` 等)。

### 2.2 前端模块结构(Vue 3 + Pinia)

```
frontend/src/
├── api/                     ← 17 个 API 客户端(client / users / ...)
├── views/                   ← 18 个视图(Login / Dashboard / ...)
├── components/              ← 13+ 复用组件(GanttView / WbsTreeView / ...)
├── stores/                  ← Pinia(auth / project / notification)
├── router/                  ← 路由 + meta.roles 门控
├── utils/                   ← axios / 错误处理
└── styles/                  ← 主题 + Design Token
```

**视图组织原则**:
- 每个领域模块对应 1-N 个视图(如 `initiation/` 下含 `List.vue` / `Detail.vue` / `ApprovalDrawer.vue`)
- 视图不超过 800 行;超出则拆组件
- 路由 `meta.roles` 与后端 `@RequireRoles` 双重门控(前端防误入,后端是真权限)

---

## 3. 关键设计决策(WHY)

### 3.1 为什么用 Spring Boot 3.3 + Java 21

详见 ADR [001](decisions/001-spring-boot-vue-baseline.md)。核心三点:
- Spring Boot 2.7 EOL、安全补丁缺位
- Java 21 虚拟线程稳定,长事务(成本引擎月度快照)排队改善
- Vue 2 EOL,生态全面 Vue 3

### 3.2 为什么 MySQL + PostgreSQL 双轨

详见 ADR [002](decisions/002-mysql-pg-dual-track.md)。核心三点:
- H2 方言与 MySQL 不一致,生产端偶发 syntax error 难在测试期发现
- PG 严格标准 SQL,语法兼容性更早暴露
- Flyway 区分目录(`migration-pg/` 与 `migration-mysql/`)一份写两套

### 3.3 为什么用 JWT 双 token + HttpOnly cookie

**问题**:JWT 单 token 泄漏后无法主动失效;纯 localStorage 易被 XSS 偷。

**方案**:
- **Access Token**(2h):放 HttpOnly cookie + Authorization header,**前端 JS 不可读**,XSS 偷不到
- **Refresh Token**(30d):放 HttpOnly cookie,仅 `/api/auth/refresh` 端点使用
- **黑名单**(`revoked_token` 表):登出 / 改密时主动失效,即使 token 未到期

详见 `specs/legacy/zhiyu-mvp-design.md` §安全设计。

### 3.4 为什么审计日志走切面 + 异步写

**问题**:业务事务里同步写 AuditLog 会拖慢主链路;失败还可能回滚业务。

**方案**:
- `@AuditLog` 注解 + AOP 切面拦截 Controller 层(只记录用户操作)
- 异步写(`@Async` + 独立线程池),失败仅 warn log,不影响主事务
- 13 个写方法覆盖 6 模块(AUTH / INITIATION / MILESTONE / PROJECT / HEALTH_ADVISOR / NOTIFICATION)
- 查询 API(`GET /audit-logs`)RBAC 限 PMO_ADMIN,VIEWER 403,未登录 401

详见 `drafts/扩展文档/P1.5-收尾/P1.5-收尾一页纸.md` 与 `reviews/2026-06-06-p1.5-d-auditlog-fe-summary-pmo-hex.md`。

### 3.5 为什么通知中心走"通道无关"扇出

**问题**:立项 / 工时 / 财务 / 预警事件多通道(邮件 + 3 IM)扇出,直接耦合到每个 Service 会导致扇出逻辑散落。

**方案**:
- 事件源(InitiationService / TimesheetService 等)统一发 `ApplicationEvent`
- `NotificationDispatcher` 监听事件,按用户-IM-绑定表路由到具体通道
- MailService(老) + 各 IM 适配器(新)各自独立,**任一通道失败不影响其他**
- 总开关 / 通道独立开关 / 勿扰时段均配置化

详见 `drafts/扩展文档/P2-A-IM通知/P2-A-IM通知.md` §3。

### 3.6 为什么 IM 平台不做 OAuth(本期)

**权衡**:IM 平台 OAuth(企业微信 / 钉钉 / 飞书)接入工作量 + 加密回调服务都不小;M6 范围里先用"自建应用 + 群机器人 Webhook"实现,无 OAuth。

**代价**:
- 不能"一键审批"卡片按钮(需要回调服务);
- 用户自助绑定 UI 不做,只能管理员用 REST API 绑定;
- 误发风险由"灰度开关 + 通道独立开关"隔离。

**何时重审**:M6 门禁前开一次 spike,工作量评估后决定是否升级 OAuth。详见 [STATUS.md §4 R-002](STATUS.md)。

### 3.7 为什么 Gantt 自动范围用"今天 + 里程碑锚定"

**问题**:全公司 Gantt 跨项目多,项目时间窗与里程碑时间窗错位时,里程碑菱形会被坐标轴裁掉。

**方案**(P2.B + P2.C 演进):
1. **第一版**:仅用 project 的 planStart/End + actualStart/End,里程碑被裁
2. **P2.B 兜底**:加 `anchorOnToday` 分支,荒废项目用 today±3 月
3. **P2.C 锚定**:把 milestone 的 planDate/actualDate 也纳入 autoFrom/autoTo 计算,温和错位也覆盖

详见 `reviews/2026-06-09-p2-c-gantt-axis-fix-pmo-hex.md` 与 `reviews/2026-06-09-p2-b-workload-views-fix-pmo-hex.md`。

### 3.8 为什么 EVM 指标在 WBS 模块内做,不在 Dashboard 单独搞

**权衡**:EVM(BAC/PV/EV/AC/CPI/SPI)需要 WBS 进度 + 工时成本数据,放在 Dashboard 会跨模块拉数据;放在 WBS 模块内,EVM 是 WBS 视图的派生数据。

**接口契约**:见 `drafts/扩展文档/P3-WBS-EVM-Risk-PRD/PR-5-API与非功能.md`。

### 3.9 为什么 CostItem 走"实时计算 + 月度快照"

**权衡**:
- **实时计算**:每次查询都从 Timesheet × RoleRate 重算,P95 < 500ms,数据最新
- **月度快照**:每月 1 号跑一次落 `cost_snapshot` 表,做历史趋势 / 财务对账

两条链路并存,实时链路供 Dashboard / CostUserMonth 视图,快照链路供 Finance 3-way match。详见 `specs/legacy/zhiyu-cost-engine.md`。

### 3.10 为什么测试用三档(H2 / PG / MySQL)

| 层级 | 数据库 | 用途 | 速度 |
|------|--------|------|------|
| **单元测试** | H2 PG-mode | 单测快跑,无外部依赖 | < 1s/类 |
| **集成测试** | PostgreSQL | 严格 SQL,语法兼容性早暴露 | ~5s/类 |
| **CI smoke** | MySQL | 生产一致(视图 / 触发器) | ~30s 全套 |

详见 ADR [002](decisions/002-mysql-pg-dual-track.md) 与 `docs/testing/postman/README.md`。

---

## 4. 横切关注点

### 4.1 鉴权与 RBAC

```
请求 → JwtAuthFilter → 解析 token → SecurityContext
                                    ↓
                          @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN')")
                                    ↓
                          Controller 方法
                                    ↓
                          @RequireRoles("PMO_ADMIN") ← 业务语义层(更细粒度)
```

- **第一层**(`@PreAuthorize`):框架级,粗粒度角色匹配
- **第二层**(`@RequireRoles`):业务语义层,22 个端点细粒度

### 4.2 异常体系

```
BusinessException (基类)
├── ResourceNotFoundException       → 404
├── ValidationException              → 400 + field errors
├── PermissionDeniedException        → 403
├── StateConflictException           → 409 (状态机冲突)
└── ExternalServiceException         → 502 (IM / 邮件服务失败)

@ExceptionHandler 统一返回:
{ "code": 40001, "message": "项目编码已存在", "data": null, "timestamp": ... }
```

### 4.3 日志与监控

- **结构化日志**:JSON 格式,字段 `traceId / userId / module / action / costMs`
- **审计日志**:独立表 `operation_log`,由 `@AuditLog` 切面异步写
- **指标**(M5):`ops.metrics` / `ops.timeline` 派生指标(P50 / P95 / 触发→started 延迟分布)

### 4.4 部署拓扑

```
docker compose up -d
├── mysql       (3306, 健康检查 mysqladmin ping)
├── backend     (8088, 健康检查 /api/actuator/health)
└── frontend    (8080, nginx + SPA, 健康检查 /healthz)

生产建议:
- MySQL 替换为外部托管(RDS / PolarDB)
- frontend 加 CDN
- backend 加 HPA(2~8 副本)
- JWT secret 放 secrets manager
```

详见 `docs/runbooks/A5-上线计划/` 与 [README §十 部署](../README.md)。

---

## 5. 数据模型(高层视图)

完整字段定义见 `specs/legacy/` 与 `drafts/扩展文档/A1-数据字典/`。此处只列**域**与**关键关系**:

```
┌─ org 域 ────────────────┐
│ users / departments /    │
│ positions / user_positions│
│ user_roles / roles       │
└──────────┬───────────────┘
           │ user_id
           ▼
┌─ project 域 ────────────┐         ┌─ initiation 域 ──────────┐
│ projects / milestones /  │◄────────│ initiations / records    │
│ project_members /        │ 1:N     │ (审批状态机)              │
│ baselines                │         └──────────────────────────┘
└──────────┬───────────────┘
           │ project_id
           ├──────────────────┐
           ▼                  ▼
┌─ wbs 域 ───────────┐  ┌─ timesheet 域 ─────────────┐
│ wbs_nodes /         │  │ timesheet_entries /        │
│ assignments /       │  │ timesheet_approvals        │
│ dependencies        │  └──────────┬─────────────────┘
└──────────┬─────────┘             │
           │                       ▼
           │              ┌─ cost 域 ─────────────────┐
           │              │ cost_items /               │
           │              │ cost_snapshots /           │
           │              │ role_rates                 │
           │              └──────────┬─────────────────┘
           │                         │
           ▼                         ▼
┌─ finance 域 ──────────────────────────────────────────┐
│ contracts / invoices / payments (3-way match)         │
└───────────────────────────────────────────────────────┘

┌─ 风险 / 预警 ────────────┐  ┌─ 通知 ──────────────────┐
│ risks / risk_history     │  │ notifications /          │
│ alerts / alert_rules     │  │ user_im_bindings /       │
│                          │  │ user_im_quiet_hours      │
└──────────────────────────┘  └─────────────────────────┘
```

域之间通过外键约束,跨域查询通过视图(`v_active_user` / `v_user_weekly_load` / `v_cost_monthly` 等)聚合,视图健康检查由 `scripts/db-views-healthcheck.sh` 在 CI 中跑。

---

## 6. 接口契约

**不写**字段级契约(那是 `specs/`),只列**契约存放位置**:
- **OpenAPI 3.0 存档**:`specs/openapi/openapi.json`(33 paths / 37 schemas / 10 tags)
- **swagger UI 实时**:启动后端访问 `http://localhost:8088/api/swagger-ui.html`
- **Postman 集合**:`testing/postman/zhiyu-pms.postman_collection.json`(29 请求 / 8 文件夹)
- **Shell smoke**:`testing/postman/smoke.sh`(16 端点,可进 CI)

修改接口必须同步改 `specs/openapi/openapi.json`(随 PR 提交)。

---

## 7. 安全模型

| 维度 | 措施 |
|------|------|
| **传输** | HTTPS(生产,Nginx / Cloudflare);开发 HTTP |
| **认证** | JWT HS512,双 token(2h access + 30d refresh)+ HttpOnly cookie |
| **鉴权** | `@PreAuthorize`(框架层)+ `@RequireRoles`(业务层)22 端点 |
| **审计** | `@AuditLog` 切面 + 异步写 + 4 模块 13 写方法 |
| **黑名单** | `revoked_token` 表,登出/改密主动失效 |
| **CORS** | 白名单配置,默认 deny |
| **输入校验** | Bean Validation(`@Valid`)+ 业务层校验双层 |
| **SQL 注入** | JPA 参数化 + 视图聚合,无字符串拼接 SQL |
| **密钥** | JWT secret 放 secrets manager,不入 git |

---

## 8. 可观测性

| 维度 | 实现 |
|------|------|
| **结构化日志** | JSON,字段 `traceId / userId / module / action / costMs` |
| **审计日志** | `operation_log` 表 + Web 查询 API(PMO_ADMIN only) |
| **健康检查** | `/api/actuator/health`(Liveness + Readiness) |
| **指标**(M5) | `ops.metrics` / `ops.timeline` 派生查询,`sift metrics` / `timeline` CLI |
| **告警**(M5) | 6 类规则:成本 / 进度 / 质量 / 风险 / 资源 / 合规 |

---

## 9. 演进路线

| 阶段 | 范围 | 状态 |
|------|------|------|
| v2.x | 项目主数据 + 立项 | ✅ done |
| v3.0 | 立项全流程 5 子模块 | ✅ done |
| v3.1 | 里程碑七阶段字典 | ✅ done |
| v4.0 | 成本引擎 | 🟡 active 85% |
| v4.2 | 财务 3-way match | 🟡 active 85% |
| v4.3-V4.4 | 预警 | 🟡 active 60% |
| P2-A | IM 多通道 | 🟡 active 70% |
| v5 | AI / 移动 / 治理 | ⏸ draft 0% |

详见 [STATUS.md §2](STATUS.md) 与 [WBS.md](WBS.md)。

---

## 10. 文档对应关系

- **任务结构** → [WBS.md](WBS.md)
- **当前快照** → [STATUS.md](STATUS.md)
- **接口契约** → [specs/](specs/)
- **测试策略** → [testing/](testing/)
- **架构决策** → [decisions/](decisions/)
- **变更历史** → [CHANGELOG.md](CHANGELOG.md)
- **历史规格沉淀** → `specs/legacy/`(老 `docs/*.md` 归档)
