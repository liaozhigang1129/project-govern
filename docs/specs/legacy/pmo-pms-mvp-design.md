# PMO 项目管理系统 — MVP 技术设计 (Design)

> 文件: `pmo-pms-mvp-design.md` · 版本: v0.1.0 · 状态: ✅ MVP 已落地
> 配套: [`pmo-pms-proposal.md`](./pmo-pms-proposal.md) · [`../README.md`](../README.md)

---

## 0. 文档目的

把"PMO PMS MVP"从代码反推成**一份给新同学 / 接手人的技术地图**:

- 项目结构、模块划分、关键约定
- 数据模型、API 契约、关键算法
- 为什么这么设计(踩过的坑)
- 怎么跑、怎么测、怎么改

如果你要**新加一个功能**,先看 §2 模块地图,再去 §5 找对应的 DTO 模式。
如果你要**排查一个 bug**,先看 §11 已知坑点 / ADR 摘要。

---

## 1. 架构总览

### 1.1 部署拓扑

```
                            ┌──────────────────────────────┐
                            │   Browser (Vue 3 SPA)         │
                            │   http://localhost:8080        │
                            └──────────┬───────────────────┘
                                       │ /api/*
                            ┌──────────▼───────────────────┐
                            │   Nginx 80 (容器内)            │
                            │   反代 /api → backend:8088    │
                            └──────────┬───────────────────┘
                                       │
                            ┌──────────▼───────────────────┐
                            │  Spring Boot 3.3 (Java 21)   │
                            │  http://localhost:8088/api    │
                            │  ├─ JWT 鉴权 (HS512)          │
                            │  ├─ springdoc-openapi         │
                            │  └─ JPA / Flyway              │
                            └──────────┬───────────────────┘
                                       │ JDBC
                            ┌──────────▼───────────────────┐
                            │  MySQL 8.0 (生产)             │
                            │  PostgreSQL 16 (CI/测试)      │
                            │  H2-PG-mode (单测 in-memory) │
                            └────────────────────────────────┘
```

**3 个进程 / 1 条命令**:

```bash
docker compose up -d     # mysql + backend + frontend
```

### 1.2 进程内模块分层

```
┌─────────────────────────────────────────────────┐
│  Controller  (HTTP 边界、参数校验、DTO 转换)     │
│      ↓                                          │
│  Service     (业务规则、状态机、事务边界)        │
│      ↓                                          │
│  Repository  (Spring Data JPA, 名字查询)        │
│      ↓                                          │
│  Entity      (JPA 映射,SoftDeletableEntity 基类)│
└─────────────────────────────────────────────────┘
↑
|  common/ 横切关注点:security / exception / api / config / entity
```

**关键约定**:
- Controller **不直接返回 Entity**,统一用 DTO(`ProjectDetailResponse` / `ProjectCardDto`)
- Service 是事务边界,`@Transactional(readOnly = true)` 给查询
- Repository 命名即查询(`findByCodeAndDeletedFalse`...),复杂聚合才写 `@Query`

---

## 2. 模块地图

### 2.1 目录结构

```
pmo-pms/
├── backend/                            # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/java/com/company/pmo/
│       ├── common/                     # 横切关注点
│       │   ├── api/                    # 统一响应 ApiResponse
│       │   ├── audit/                  # (预留) 审计
│       │   ├── config/                 # Cors / JpaAuditing / OpenAPI
│       │   ├── entity/                 # AuditableEntity / SoftDeletableEntity
│       │   ├── exception/              # BusinessException + GlobalExceptionHandler
│       │   └── security/               # JWT (Service / Filter / UserDetails / SecurityConfig)
│       └── module/
│           ├── org/                    # 用户 / 部门 / 角色 + 认证
│           ├── dict/                   # 6 个业务字典 + 6 个查询接口
│           ├── project/                # 项目主数据 CRUD + DTO
│           ├── initiation/             # 立项 + 3 级审批
│           ├── milestone/              # 里程碑 + 加权进度
│           └── dashboard/              # KPI 统计 + DTO
│
├── frontend/                           # Vue 3 前端
│   ├── package.json                    # pnpm
│   ├── vite.config.ts                  # /api 代理 → :8088
│   └── src/
│       ├── views/                      # Login / Dashboard / Projects / Initiations
│       ├── stores/                     # Pinia (auth)
│       ├── api/                        # Axios 封装 + 类型
│       ├── components/                 # (后续)
│       └── router/                     # Vue Router (history 模式)
│
├── docs/
│   ├── openapi/                        # OpenAPI 3.0 契约 (33 paths / 37 schemas / 10 tags)
│   ├── api-testing/                    # Postman (29) + smoke.sh (16)
│   ├── pmo-pms-proposal.md             # ← 立项提案
│   └── pmo-pms-mvp-design.md           # ← 本文档
│
├── deploy/docker/                      # Docker 构建文件
│   ├── backend/Dockerfile              # 多阶段: JDK21 构建 → JRE21 运行
│   └── frontend/                       # 多阶段: node 构建 → nginx 静态
│
├── .github/workflows/ci.yml            # 4 jobs
├── docker-compose.yml                  # 3 服务
└── README.md
```

### 2.2 模块依赖图

```
              ┌─────────────┐
              │  dashboard  │  ← 只读,聚合 4 个模块
              └──────┬──────┘
                     │ 读
       ┌─────────────┼─────────────┐
       │             │             │
       ▼             ▼             ▼
   ┌────────┐   ┌────────┐   ┌──────────┐
   │ project│   │initiate│   │milestone │
   └───┬────┘   └───┬────┘   └────┬─────┘
       │            │             │
       └────────────┴─────────────┘
                    │
              ┌─────▼─────┐
              │   org     │  ← 引用 (用户/部门)
              │   dict    │  ← 引用 (类型/状态/健康度/...)
              └───────────┘
```

---

## 3. 技术栈与版本

| 层 | 技术 | 版本 | 备注 |
|---|---|---|---|
| **后端语言** | Java | 21 (LTS) | Dockerfile 用 21;`pom.xml` 标 17,**实际编译按 Docker / 21 走** |
| **后端框架** | Spring Boot | 3.3.4 | spring-boot-starter-parent |
| **持久化** | JPA / Hibernate | 6.x | `ddl-auto: validate`,由 Flyway 控 schema |
| **迁移** | Flyway | 10.x | `V1.0` ~ `V1.4` 共 5 个脚本 |
| **校验** | spring-boot-starter-validation | 3.3 | `@Valid` + JSR-303 |
| **安全** | Spring Security | 6.x | 无状态,JWT filter 放 UsernamePasswordAuthenticationFilter 之前 |
| **JWT** | jjwt | 0.12.6 | HS512,`Keys.hmacShaKeyFor(secret)` |
| **API 文档** | springdoc-openapi | 2.6.0 | 启动时从 `@Operation` 生成 OpenAPI 3.0.1 |
| **工具** | Lombok | 1.18.38 | JDK 21 兼容 |
| **单测** | JUnit 5 + AssertJ | 5.10 / 3.25 | `@DataJpaTest` + H2 (PG mode) |
| **契约测试** | Postman / Newman | - | 仓库存档 `docs/api-testing/` |
| **E2E** | Node 18+ 原生 fetch | - | **零依赖**,4 suite / 30 case(原 18 + 立项 12) |
| **E2E(选装)** | Cypress | 13 | 留给开发者本地 GUI |
| **DB(生产)** | MySQL | 8.0 | utf8mb4 / Asia/Shanghai |
| **DB(测试)** | PostgreSQL | 16-alpine | CI service container |
| **DB(单测)** | H2 | - | `MODE=PostgreSQL` 模拟 |
| **前端框架** | Vue | 3.5 | `<script setup>` + Composition API |
| **UI 库** | Element Plus | 2.14 | 按需自动注册 (`unplugin-vue-components`) |
| **状态** | Pinia | 3.0 | auth store |
| **路由** | Vue Router | 4.x | history 模式 |
| **构建** | Vite | 5.4 | `/api` proxy → `http://localhost:8088` |
| **图表** | ECharts + vue-echarts | 5.5 / 8 | 按需注册 7 个组件 (echarts 6.x 强制) |
| **HTTP** | Axios | 1.7 | 拦截器自动 `Bearer` + 业务码 0 判断 |
| **包管理** | pnpm | 9 | `pnpm-lock.yaml` 锁版本 |
| **容器** | Docker Compose | v2 | 3 服务,共享 `pmo-net` |
| **反向代理** | Nginx | 1.27-alpine | 前端容器内,反代 `/api` → backend:8088 |
| **CI** | GitHub Actions | - | 4 jobs 并行/串行 |

---

## 4. 数据模型

### 4.1 ER 概览

```
┌─────────────┐         ┌──────────────┐         ┌──────────────┐
│ department  │1───────*│  app_user    │*───────1│    role      │
│  (树形)      │         │              │         │              │
└─────────────┘         └──────┬───────┘         └──────────────┘
                               │
                               │ 1
                               │
                               │ *
                    ┌──────────▼─────────┐         ┌──────────────┐
                    │  project           │*────────1│ project_type │
                    │  (SoftDeletable)   │         └──────────────┘
                    │                    │*────────1│ project_status│
                    │                    │?────────1│ health_level │
                    └────┬───────────────┘         └──────────────┘
                         │ 1
                         │
                         │ *
                    ┌────▼─────────┐              ┌──────────────┐
                    │  milestone   │*────────────1│milestone_stat│
                    └──────────────┘              └──────────────┘

                    ┌──────────────────┐         ┌──────────────┐
                    │ project_initiation│*───────1│initiation_sta│
                    │  (立项申请)       │         └──────────────┘
                    └────┬─────────────┘
                         │ 1
                         │ *
                    ┌────▼─────────────┐         ┌──────────────┐
                    │ approval_record  │*───────1│ approval_step│
                    │  (审批流水)       │         └──────────────┘
                    └──────────────────┘
```

### 4.2 Flyway 5 个迁移脚本

| 版本 | 文件 | 内容 |
|---|---|---|
| V1.0 | `V1.0__init_extensions.sql` | `pgcrypto` 扩展 + `pmo` schema + 通用 `fn_set_updated_at()` 触发器函数 |
| V1.1 | `V1.1__core_org.sql` | `department` (树形) / `role` (内置 5 种) / `app_user` (BCrypt) / `user_role` (预留多对多) |
| V1.2 | `V1.2__project_main.sql` | `project_type` / `project_status` / `health_level` 字典 + `project` 主表 |
| V1.3 | `V1.3__initiation_milestone.sql` | `initiation_status` / `approval_step` 字典 + `project_initiation` / `approval_record` / `milestone_status` / `milestone` + `operation_log` + 3 个 updated_at triggers |
| V1.4 | `V1.4__seed_data.sql` | seed 全部字典值 + 5 角色 + 4 部门 + 6 用户(密码统一 `pmo123`) |

> **MySQL 兼容**:仓库 SQL 写的是 PG 方言(`BIGSERIAL` / `TIMESTAMPTZ`)。生产 MySQL 8 由 **应用启动时 + 容器 entrypoint** 把 SQL 翻成 MySQL 方言(若不走 PG)。当前 docker-compose 默认 MySQL,**测试环境是 PG**,这条路径是 P1.5 的 TODO(详见 §11)。

### 4.3 软删除统一约定

所有业务表继承 `SoftDeletableEntity`,带 `deleted BOOLEAN DEFAULT FALSE`:

- 查询:**默认**走 `findByXxxAndDeletedFalse` / `@Query("... AND m.deleted = false")`
- 删除:Service 调 `softDelete(id)`,set `deleted = true`,**不进 SQL DELETE**
- 字典表不软删(只 seed,不业务写入)
- `OperationLog` 不软删(审计永不丢)

### 4.4 审计 / 时间戳

`AuditableEntity` 基类带 `@CreatedDate` / `@LastModifiedDate`,由 `JpaAuditingConfig` 启用:
- 生产环境:走 `@EntityListeners(AuditingEntityListener.class)`,**自动写**
- 单测 (`@DataJpaTest`):审计监听器**默认不启用**,`createdAt` 可能为 null → DashboardService 已做 null 过滤

---

## 5. API 契约设计

### 5.1 统一响应

所有接口都返回 `ApiResponse<T>`:

```json
// 成功
{ "code": 0, "message": "ok", "data": <T>, "timestamp": 1700000000000 }

// 业务异常
{ "code": 400, "message": "Project code exists: P-2025-001", "data": null, "timestamp": 1700000000001 }
```

定义在 `common/api/ApiResponse.java`,**前端 axios 拦截器**(`api/client.ts`)拿到 `code !== 0` 直接 reject,业务层 `await` 即拿到 `data`,无需再 `.data.data`。

### 5.2 OpenAPI / 仓库存档

- **源**:`springdoc-openapi-starter-webmvc-ui` 从 `@Operation` / `@Tag` 自动生成
- **实时**:后端启动后 <http://localhost:8088/api/swagger-ui.html>
- **JSON 契约**:`docs/openapi/openapi.json`(snapshot)
- **重新生成**:CI `backend-test` job 跑完测试后 `curl /v3/api-docs -o openapi.json`

> **契约是 single source of truth**。前端类型从 openapi.json 抄,改接口必须先改后端 → 重新生成 → 前端跟改。

### 5.3 10 个 tag / 33 paths 一览

| Tag | Paths | 说明 |
|---|---|---|
| **Auth** | POST `/auth/login`, POST `/auth/refresh`, POST `/auth/logout`, GET `/auth/me` | JWT 登录 + 双 token 刷新 + 登出黑名单 |
| **AuditLog** | GET `/audit-logs`, GET `/audit-logs/{id}` | 审计日志查询(PMO_ADMIN 限读) |
| **Dashboard** | `/dashboard/{kpis,status-distribution,health-distribution,active-projects}` | 4 KPI + 2 分布 + 项目卡片 |
| **Departments** | GET `/departments` | 部门列表(按 sortOrder) |
| **Dictionaries** | `/dict/{project-types,project-statuses,health-levels,initiation-statuses,milestone-statuses,approval-steps}` | 6 个只读字典 |
| **HealthAdvisor** | GET `/health-advisor/suggest/{id}`, POST `/health-advisor/apply-all` | 健康度自动建议(单项目 / 全量跑批) |
| **Initiations** | GET/POST `/initiations`, GET `/initiations/{id}`, POST `/initiations/{id}/decide`, GET `/initiations/{id}/records` | 立项 + 3 级审批 |
| **Milestones** | POST `/milestones`, GET `/milestones/by-project/{pid}`, PUT `/milestones/{id}/status`, DELETE `/milestones/{id}`, GET `/milestones/progress/{pid}` | 里程碑 + 加权进度 |
| **Projects** | GET/POST `/projects`, GET/PUT/DELETE `/projects/{id}` | 项目主数据 CRUD |
| **Users** | GET `/users` | 用户列表(EAGER 加载 role) |

### 5.4 DTO 模式(重要!)

**Controller 不直接返 Entity**。所有涉及 LAZY 关联的接口,都引入了 DTO。

| 接口 | 返回类型 | 备注 |
|---|---|---|
| GET `/projects` | `List<ProjectCardDto>` | 列表只读场景,内嵌 DictRef(type/status/health) |
| GET `/projects/{id}` | `ProjectDetailResponse` | 详情,内嵌 DictRef(带 health colorHex) |
| GET `/projects/{id}` 旧版 | `Project` entity | **保留兼容,带 LAZY 风险** |
| POST `/projects` | `ProjectDetailResponse` | 接收 `ProjectCreateRequest`(typeCode/statusCode/healthCode 用 **code 字符串**) |
| PUT `/projects/{id}` | `ProjectDetailResponse` | 接收 `ProjectUpdateRequest`(局部,code 不可改) |
| GET `/dashboard/active-projects` | `List<ProjectCardDto>` | 跟 `/projects` 同 shape,前端代码可复用 |

**为什么要 code 字符串而不是 id**:
1. 防越权:客户端不能传 `{type:{id:1}}` 偷偷指到别的字典条目
2. 字典值稳定:`code` 不会因为重建字典而漂移
3. API 友好:前端表单直接 `el-select` 绑 `code`,无需先查 id

`DictRef` 形状:

```ts
{ id: 1, code: "DELIVERY", name: "客户交付" }       // ProjectType/Status
{ id: 1, code: "GREEN",   name: "正常", colorHex: "#67C23A" }  // HealthLevel 多 colorHex
```

### 5.5 字典子接口:按 code 查

```java
public interface ProjectTypeRepository extends JpaRepository<ProjectType, Long> {
    Optional<ProjectType> findByCode(String code);
    boolean existsByCode(String code);
}
```

由 ProjectDtoContractTest (8 用例) 覆盖契约:传 `"GHOST_TYPE"` 应 400 `"Unknown typeCode: GHOST_TYPE"`。

### 5.6 错误码体系

| code | 含义 | HTTP | 触发场景 |
|---|---|---|---|
| 0 | 成功 | 200 | - |
| 400 | 业务校验失败 | 400 | `BusinessException` 默认 / `@Valid` 失败 |
| 401 | 未认证 | 401 | SecurityFilter 拒绝 / JWT 失效 |
| 404 | 资源不存在 | 200 (body) | `BusinessException(404, "...")` |
| 500 | 未捕获异常 | 500 | `Exception.class` catch-all |

`GlobalExceptionHandler` 统一翻译成 `ApiResponse.fail(code, message)`,前端只需看 body.code。

---

## 6. 关键算法与状态机

### 6.1 立项 3 级审批状态机

`InitiationService.decide(...)` 内部:

```java
private static final List<String> APPROVAL_FLOW = List.of("DEPT_LEAD", "PMO_ADMIN", "EXEC");

switch (d.decision()) {
  case "REJECTED" -> {
    i.setStatus(REJECTED);
    i.setCurrentStep(null);
    i.setClosedAt(now);
  }
  case "SUPPLEMENT" -> {
    i.setStatus(SUPPLEMENT);  // 留在当前步骤,等申请人补料
  }
  case "APPROVED" -> {
    if (idx + 1 >= APPROVAL_FLOW.size()) {
      i.setStatus(EXEC_APPROVED);
      i.setClosedAt(now);
      createProjectFromInitiation(i);   // ← 关键副作用
    } else {
      i.setCurrentStep(nextStep);
      i.setStatus(stepStatusMap.get(nextStep));
    }
  }
}
```

**关键点**:
- `currentStep` 是 **String**(DEPT_LEAD/PMO_ADMIN/EXEC),不是 stepId,便于跨库迁移
- 状态 / 步骤**双重字段**:`status_id` 是字典(给前端展示),`current_step` 是步骤(给后端流转)
- 3 级全过 → `createProjectFromInitiation` 自动建项目,`projectId` 回写到 initiation
- SUPPLEMENT 不前进,等申请人重新 `submit` 触发再次流转

### 6.2 加权进度算法

`MilestoneRepository.computeWeightedProgressPct(projectId)` — **JPQL 一次往返**:

```jpql
SELECT COALESCE(
  ROUND(
    100.0 * SUM(CASE WHEN s.code = 'COMPLETED' THEN m.weight ELSE 0 END) /
    NULLIF(SUM(m.weight), 0)
  ), 0)
FROM Milestone m JOIN m.status s
WHERE m.projectId = :projectId AND m.deleted = false
```

**为什么不用 Java 算**:
1. **LAZY 问题**:在 Java 端遍历 `milestones` 会触发 status 的 lazy load,Service 间调用 + 事务关闭后会 500
2. **N+1**:每个 milestone 都要查 status
3. **不一致**:Service 自调用 `this.computeXxx()` 绕过 Spring 代理,`@Transactional` 不生效

**边界**:
- 空集 / 0 权重 → `NULLIF` 兜底 → `COALESCE` 返回 0
- 所有 weight 都是 0 → 同上
- `ROUND(..., 0)` 整数百分比,前端不再处理小数

### 6.3 Dashboard 4 项 KPI

| KPI | 计算 |
|---|---|
| `activeCount` | `status.code = "ACTIVE"` 的项目数 |
| `newInitiationsThisMonth` | `initiation.createdAt` 在本年月的数量 |
| `closedThisMonth` | `status.code = "CLOSED"` 且 `actualEndDate` 在本月的数量 |
| `overdueProjects` | `status.code = "ACTIVE"` 且 `planEndDate < today` 的数量 |

**性能**:MVP 量级(几十到几百项目)直接 in-memory stream 过滤;**项目数 > 5K 时**应改成 native SQL + GROUP BY(已留 TODO)。

### 6.4 Dashboard 容错

`Dashboard.vue` 改用 `Promise.allSettled` — **4 个 API 任何一个失败,其他 3 个的图仍然画**。这是从 efa911b 修的,之前 `Promise.all` 任一失败会全空。

---

## 7. 安全设计

### 7.1 JWT (HS512)

```
Header  : { alg: "HS512", typ: "JWT" }
Payload : { sub: <username>, iat: <now>, exp: <iat + 24h> }
Signature: HMACSHA512(header.payload, secret)
```

- **secret**:来自 `pmo.security.jwt.secret` (yaml 默认值,生产**必须**用 `PMO_SECURITY_JWT_SECRET` 环境变量覆盖)
- **过期**:默认 24h(`pmo.security.jwt.expiration-hours`),Phase 2 缩到 2-4h + refresh token
- **算法**:`Keys.hmacShaKeyFor(secret.getBytes(UTF_8))` 至少 32 字节,生产 64 字节
- **传输**:`Authorization: Bearer <token>`,axios 拦截器自动注入

### 7.2 RBAC(基于角色字符串)

`AppUserDetailsService` 加载用户时把 `primaryRole.code` 包成 `ROLE_<code>`:

```java
.authorities(List.of(new SimpleGrantedAuthority("ROLE_" + u.getPrimaryRole().getCode())))
```

- `SecurityConfig` 默认 `anyRequest().authenticated()`
- 业务接口**目前没在方法上加 `@PreAuthorize`**(MVP 简化),靠前端路由守卫 + role 判断挡 UI
- 写接口的细粒度校验是 Phase 2 TODO(§12)

### 7.3 公开白名单

```java
.requestMatchers("/auth/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health").permitAll()
```

`/actuator/health` 是被 §11 修复中暴露的(原来 SecurityConfig 默认锁了全部 actuator 路径,健康检查 500)。

### 7.4 CORS

`CorsConfig` 允许 `*` 源模式 + 凭证:

```java
cfg.setAllowedOriginPatterns(List.of("*"));
cfg.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
cfg.setAllowedHeaders(List.of("*"));
cfg.setAllowCredentials(true);
```

**生产收紧**:把 `setAllowedOriginPatterns("*")` 改成环境变量注入的允许域名(README §10.2)。

### 7.5 密码

- **算法**:BCrypt strength 10(`BCryptPasswordEncoder`)
- **存储**:`app_user.password_hash` 256 长度
- **Seed**:6 个演示账号统一 `pmo123`,**生产前必须改 + 加"首次登录强制改密"**(Phase 2)

---

## 8. 前端架构

### 8.1 目录结构

```
frontend/src/
├── main.ts                 # createApp + Pinia + Router + ElementPlus(zh-cn)
├── App.vue                 # 整体布局:侧边栏 + 顶栏 + <RouterView>
├── router/index.ts         # 4 个路由 + 守卫(无 token 跳 /login)
├── stores/auth.ts          # Pinia: token / user, 持久化到 localStorage
├── api/client.ts           # Axios 实例 + 拦截器 + TS 类型
├── views/
│   ├── Login.vue           # 登录页(默认 admin/pmo123)
│   ├── Dashboard.vue       # 4 KPI + 2 ECharts 饼图 + 项目卡片
│   ├── Projects.vue        # 项目列表(增删改)
│   └── Initiations.vue     # 立项申请 + 3 级审批
├── components/             # (预留)
├── styles/main.scss
└── assets/
```

### 8.2 路由 + 守卫

```ts
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!auth.token && to.path !== '/login') return { path: '/login' }
  if (auth.token && to.path === '/login')  return { path: '/' }
})
```

- history 模式(nginx 用 `try_files $uri $uri/ /index.html` 兜底)
- 4 个核心路由:`/login` · `/`(Dashboard) · `/projects` · `/initiations`
- **细粒度 role 控制**还没做(菜单是写死的,Phase 2 引入动态菜单)

### 8.3 Pinia auth store

```ts
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const user  = ref<UserInfo | null>(null)

  async function login(username, password) {
    const res = await api.post<LoginResponse>('/auth/login', { username, password })
    token.value = res.token
    user.value  = res.user
    localStorage.setItem('token', res.token)
    localStorage.setItem('user', JSON.stringify(res.user))
  }
  function logout() { token.value = null; user.value = null; localStorage.clear() }
  function restore() { const raw = localStorage.getItem('user'); if (raw) user.value = JSON.parse(raw) }
  return { token, user, login, logout, restore }
})
```

- **Token 持久化**:`localStorage`(MVP 简化),生产前应改 `httpOnly cookie`(Phase 2,XSS 风险)
- `App.vue` 的 `onMounted` 调 `restore()` 防止刷新掉登录态

### 8.4 Axios 拦截器

```ts
api.interceptors.response.use(
  (r) => {
    const body = r.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) return Promise.reject(new Error(body.message))
      return body.data     // ← 业务层 await 拿到的是 data,不用再 .data.data
    }
    return body
  },
  (err) => {
    if (err.response?.status === 401) { localStorage.removeItem('token'); window.location.href = '/login' }
    return Promise.reject(new Error(err.response?.data?.message ?? err.message))
  }
)
```

请求拦截器自动 `Bearer <token>`,**业务代码零感知**。

### 8.5 ECharts 6.x 按需注册

```ts
// main.ts
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'

use([CanvasRenderer, PieChart, BarChart, LineChart,
     TitleComponent, TooltipComponent, LegendComponent, GridComponent])
```

- vue-echarts 8.x + echarts 6.x **强制要求显式注册**,否则 canvas 不渲染
- Dashboard 用 PieChart × 2(状态分布 + 健康度分布)
- 这次回归在 efa911b 修,以后再加新图只需 `use([...])` 多注册一个组件

### 8.6 Element Plus 按需

`vite.config.ts` 用 `unplugin-auto-import` + `unplugin-vue-components`,`ElButton` / `ElMenu` / `ElCard` 等直接 `<el-button>` 写,不用 import。

### 8.7 Vite dev proxy

```ts
server: {
  port: 5173,
  proxy: { '/api': { target: 'http://localhost:8088', changeOrigin: true } }
}
```

**前端开发**:`/api/auth/login` 走 Vite proxy → 后端 8088,**没有跨域问题**。
**生产**:走 nginx 反代(同 /api 路径),无需改前端代码。

---

## 9. 测试策略

### 9.1 3 层金字塔

```
        ╱  ╲
       ╱ E2E╲           Cypress / Node 18 零依赖
      ╱──────╲          慢,贵,模拟真实用户
     ╱ 契约测试 ╲        Postman collection(29) + smoke.sh(16)
    ╱────────────╲       中速,验 API 契约
   ╱   单元/集成    ╲     JUnit 5 + H2 (PG mode)
  ╱──────────────────╲   快速,验业务规则
```

| 层 | 工具 | 数量 | 跑在哪 |
|---|---|---|---|
| **单元 / 集成** | JUnit 5 + AssertJ + `@DataJpaTest` (H2) | 74 | `mvn test` · CI `backend-test` |
| **契约** | Postman + Newman + 自写 smoke.sh | 29 + 16 | CI `api-smoke` · 开发者本地 |
| **E2E** | Node 18 原生 `fetch`(零依赖) | 30 (4 suite) | CI 不跑(可加)· 开发者本地 |
| **E2E(选装)** | Cypress 13 | 4 case | 开发者本地 GUI,**不进 CI**(避免冗余) |

### 9.2 7 个单测文件

| 测试 | 数量 | 验证点 |
|---|---|---|
| `ProjectServiceTest` | 6 | CRUD + 重名拒绝 + 软删 + 必填校验 |
| `ProjectDtoContractTest` | 8 | DTO 契约:`typeCode` / `healthCode` / 校验失败消息 |
| `InitiationServiceTest` | 13 | 3 级审批状态机 4 决策 × 3 起始状态 + 补料重提 |
| `InitiationControllerTest` | 8 | 立项接口契约:submit / decide / resubmit / records |
| `HealthAdvisorServiceTest` | 7 | GREEN / YELLOW(超期)/ RED(超期)/ RED(落后)/ 终态跳过 / 跑批回写 |
| `MilestoneRepositoryProgressTest` | 7 | 加权进度:全 0 / 部分完成 / 全完成 / JOIN FETCH 防 LAZY |
| `DashboardServiceTest` | 7 | 4 KPI 计数 + null 过滤 |

**总:74 用例,跑时 ~ 14 秒**(H2 in-memory)

### 9.3 跑测试

```bash
# 后端
cd backend && mvn test          # 74/74 ✅

# 契约
./docs/api-testing/smoke.sh    # 16 个核心调用 ✅
# 或 newman 跑 Postman
newman run pmo-pms.postman_collection.json -e pmo-pms.postman_environment.json

# E2E(需前后端都起着)
cd frontend && pnpm e2e         # 4 suite 30 case ✅
```

### 9.4 测试约定

- **不 mock 太多**:`@DataJpaTest` 直接用 H2 跑真 SQL,验证 JPQL
- **不 mock 安全**:`@SpringBootTest` + `@WithMockUser`,**真实过一遍过滤器链**
- **测试数据**:`@BeforeEach` 现场 seed 字典,不用 SQL fixture
- **断言**:AssertJ 链式 `assertThat(x).isEqualTo(y)`,不写 JUnit 老的 `assertEquals`

---

## 10. CI / CD 与部署

### 10.1 GitHub Actions — 4 jobs

```
┌─ backend-test   ─┐
│                  ├─► api-smoke  (需后端测试通过)
└─ frontend-build ─┘
              │
              └─► docker-build  (独立校验 compose 文件)
```

| Job | 触发 | 做什么 | 依赖 |
|---|---|---|---|
| `backend-test` | push / PR | JDK 21 + Maven + 74 JUnit + 抓 openapi.json artifact | - |
| `frontend-build` | push / PR | Node 20 + pnpm + `vue-tsc` + Vite build | - |
| `api-smoke` | push / PR | MySQL service + jar + smoke.sh 16 调用 | backend-test |
| `docker-build` | push / PR | `docker compose config --quiet` 语法校验 | - |

> Dependabot 每周一自动检查 Maven / npm / GitHub Actions 依赖。

### 10.2 Docker 镜像

| 服务 | 基础镜像 | 多阶段 | 终态 |
|---|---|---|---|
| **backend** | `eclipse-temurin:21-jdk-alpine` → `21-jre-alpine` | 2 阶段 | JDK 构建 → JRE 运行,瘦身 50% |
| **frontend** | `node:20-alpine` → `nginx:1.27-alpine` | 2 阶段 | node 构建 → nginx 静态 |
| **mysql** | `mysql:8.0` | - | utf8mb4 + Asia/Shanghai 时区 |

后端用 `adduser pmo` 切非 root 运行;健康检查 `wget /api/actuator/health`。

### 10.3 compose 服务

| 服务 | 端口 | 健康检查 | 启动顺序 |
|---|---|---|---|
| mysql | 3306 | `mysqladmin ping` | 1 |
| backend | 8088 | `/api/actuator/health` | 2 (等 mysql healthy) |
| frontend | 8080 | `/healthz` (nginx 返 200) | 3 (等 backend healthy) |

- `docker-compose.override.yml` 给开发用:把 backend 切到 `target: build` + 挂载本地源码 + spring-boot-devtools
- 生产建议把 mysql 换成外部托管(RDS / PolarDB),`docker-compose.yml` 只留 backend + frontend

### 10.4 关键环境变量

| 变量 | 默认 | 生产必须改 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | mysql 容器内网 | 指向 RDS |
| `SPRING_DATASOURCE_PASSWORD` | `pmo_pms_dev_2025` | secret manager |
| `PMO_SECURITY_JWT_SECRET` | yaml 默认 | `openssl rand -base64 48` ≥ 32 字节 |
| `PMO_CORS_ALLOWED_ORIGINS` | `http://localhost,8080,5173` | 真实域名 |
| `JAVA_TOOL_OPTIONS` | `-Xms256m -Xmx512m` | 按机器调 |

---

## 11. 已知坑点 / 踩过的雷

> 这些是已经修过 / 已经埋了防护的,但**接手人**必须知道为什么这么写。

### 11.1 LAZY 反序列化 500(最经典的坑)

**症状**:GET `/projects/{id}` 偶发 500,堆栈 `LazyInitializationException: no Session`

**根因**:`Project` entity 有 `@ManyToOne(fetch = LAZY)` 关联 `ProjectType` / `ProjectStatus` / `HealthLevel`。Service 层 `getDetail()` 返回 entity 之后,**事务关闭**,Jackson 在 Controller 序列化时再访问 `project.getType().getName()` → Hibernate 找不到 Session。

**修法**:
- ✅ Controller **不再直接返 entity**,改返 `ProjectDetailResponse`(DTO 形态)
- ✅ DTO 在 Service 事务**内部**完成映射(强制初始化 LAZY: `if (p.getType() != null) p.getType().getName();`)
- ✅ Repository 列表查询用 `JOIN FETCH`(如 `findAllActiveWithStatus`)防 controller 端 LAZY

**教训**:**Open Session in View = false**(`spring.jpa.open-in-view: false`),逼着你在 Service / DTO 层显式处理关联。

### 11.2 POST /projects 用 Entity 接 body

**症状**:POST `{ "code": "P-1", "type": { "id": 1 }, ... }` 报 400,或**诡异落库**(type_id 是脏值)

**根因**:`@RequestBody Project` 让 Jackson 把 `type: {id: 1}` 反序列化成 Hibernate 的 `ProjectType` proxy,带 `id=1` 但 `code=null` `name=null`,Service 持久化时,游离态 entity 行为不可预期。

**修法**:**全部走 DTO**。`ProjectCreateRequest` 接 `typeCode: "DELIVERY"`(字符串),Service 内部 `typeRepo.findByCode(...).orElseThrow()` 拿到完整 entity 再装配。

**教训**:写接口**永远不要**直接 `@RequestBody` 接收 JPA entity。DTO 是边界。

### 11.3 健康度字段 PUT 静默不生效

**症状**:前端 PUT `{name: "x", health: {id: 3}}` 期望把 health 改成 RED,实际没变。

**根因**:`ProjectUpdateRequest`(旧版)是 entity,`health: {id: 3}` 反序列化时不带 `code/name`,被吞掉。

**修法**:`ProjectUpdateRequest`(新版)用 `healthCode: "RED"` 字符串,Service 显式 `healthRepo.findByCode(...).orElseThrow()`。

### 11.4 Lombok 1.18.30 + JDK 24 NoSuchFieldError

**症状**:本地装 JDK 24 跑 `mvn compile` 报 `NoSuchFieldError: jdk.internal.misc.Unsafe`。

**根因**:Lombok 1.18.30 的字节码魔改不识别 JDK 24 的 Unsafe 偏移。

**修法**:Lombok 升 `1.18.38`(已用)+ `maven-compiler-plugin` 显式参数避开新字节码。

### 11.5 /actuator/health 锁死 500

**症状**:`docker-compose` 健康检查 30s 一直失败,但应用其实正常。

**根因**:Spring Security 6 默认锁所有 `/actuator/**`,health endpoint 走到过滤器链被 401 → 5xx。

**修法**:`SecurityConfig` 白名单加 `/actuator/health`。

### 11.6 ECharts 6.x 不显式注册 → canvas 不出

**症状**:升级 echarts 后 Dashboard 两张饼图空白,F12 没报错。

**根因**:echarts 6.x 改了 tree-shaking 策略,vue-echarts 8.x **强制**手动 `use([...])` 注册。

**修法**:`main.ts` 显式注册 `CanvasRenderer / PieChart / BarChart / LineChart / Title / Tooltip / Legend / GridComponent`。

### 11.7 Dashboard 任一 API 失败 → 全空白

**症状**:`/dashboard/health-distribution` 偶尔 500,整个 Dashboard 一片白。

**根因**:`Promise.all` 任一 reject 立刻抛,其他 3 个 await 中断,模板不渲染。

**修法**:改 `Promise.allSettled` + 每个 case 单独 `if (k.status === 'fulfilled')` 赋值。

### 11.8 MySQL / PostgreSQL 方言差异

**症状**:本地 PG 跑得动,生产 MySQL 启动 `BIGSERIAL` 报错。

**根因**:Flyway 脚本写的是 PG 方言(`BIGSERIAL` / `TIMESTAMPTZ` / `JSONB`)。

**当前状态**:
- CI:PG 16-alpine,跑通
- 生产:docker-compose 用 MySQL,**当前通过应用层用 `SPRING_JPA_HIBERNATE_DDL_AUTO: validate` + dialect 切换兼容**(因为 schema 简单,差异常常被 `validate` 抓住)
- **P1.5 TODO**:把 Flyway 脚本重写为两套方言,或统一用 H2-compatible 写法

### 11.9 Soft delete + unique 约束冲突

**症状**:删一个 `code=P-1` 的项目后,再新建 `code=P-1` 报"编码已存在"。

**根因**:`code UNIQUE` 约束**不区分** `deleted=true` 行。

**修法(MVP 不修)**:
- 当前:用 `code + deleted` 复合唯一约束(但 Flyway 改起来麻烦)
- 计划:加 `deleted_at` 字段,unique 约束改 `code + deleted_at IS NULL`(PG 部分索引),MySQL 走函数索引

### 11.10 InitiationService 状态流转边界

**症状**:SUPPLEMENT 后,申请人重新 submit,审批人发现"流程没前进"。

**根因**:`submit()` 当前**只是 `save()`**,没把 status 从 SUPPLEMENT 改回 PENDING 也没重置 `currentStep`。

**P1.4 修法**:
- ✅ 新增 `POST /initiations/{id}/resubmit` 接口,Service 内显式 `status = PENDING`、`currentStep = currentStep`、清空 `decidedBy/decidedAt/comment`,写一条 `INITIATION_RESUBMIT` 流水
- ✅ 前端 Initiations 详情抽屉加 timeline,展示从 SUBMIT → DEPT → PMO → EXEC → SUPPLEMENT → RESUBMIT 的完整 7 态轨迹
- ✅ 申请人角色在 SUPPLEMENT 状态下看得到「重新提交」按钮,其他角色看不到

---

## 12. 未来路线图

### 12.0 P1.4 — 立项审批流闭环(已交付)

| 项 | 价值 | 改动点 |
|---|---|---|
| **3 级审批接口** | 在线化 | `POST /initiations/{id}/decide` + 状态机 DEPT_APPROVE → PMO_APPROVE → EXEC_APPROVE |
| **补料重提接口** | 状态机闭环 | `POST /initiations/{id}/resubmit`,SUPPLEMENT → PENDING 并清空上一审批痕迹 |
| **详情抽屉 / Timeline** | 留痕可追溯 | 前端抽屉展示 7 态轨迹,操作记录按时间倒序 |
| **E2E 30 case** | 回归防护 | 原 18 case + 立项 12 case(覆盖提交/审批/驳回/补料重提) |
| **后端 +13 测试** | 单测补强 | 立项域 Controller + Service + 状态机分支覆盖 |

### 12.1 P1.5 — 体验增强(部分已交付)

| 项 | 价值 | 改动点 | 状态 |
|---|---|---|---|
| **健康度自动建议** | 减少主观 | `HealthAdvisor` 纯函数 + `HealthAdvisorService` 跑批 + `GET /health-advisor/suggest/{id}` + `POST /health-advisor/apply-all` | ✅ P1.5-a |
| **审批邮件通知** | 不依赖人盯 | Spring Mail + 模板 + 申请人/审批人/抄送 PMO | ✅ P1.5-b |
| **JWT 短过期 + refresh** | 减少盗用风险 | 2h access + 30d refresh + httpOnly cookie + 黑名单(jti 拉黑 + 03:30 自动清理) | ✅ P1.5-c |
| **Flyway 双方言** | 解决 MySQL/PG 不一致 | 拆 `flyway-pg` / `flyway-mysql` 目录 + maven profile | ✅ T1(已合并) |
| **CORS 收紧** | 防 CSRF | 接受 env 注入允许域名(`PMO_CORS_ALLOWED_ORIGINS` 已生效) | ✅ |
| **Audit 写入 + 查询** | 合规追溯 | `@Aspect` 切 13 个 Controller 写方法 → `operation_log`(8KB 截断,失败重试 1 次);`@PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN')")` 查询接口;@JdbcTypeCode(SqlTypes.JSON) 适配 PG jsonb / MySQL json | ✅ P1.5-d |

**P1.5-a 健康度建议算法**(HealthAdvisor):

- 输入: `Project`(plan_start/end)+ `List<Milestone>`(权重 + status)
- 期望完成率 = `min(已过天数 / 总工期, 1) * 100`
- 加权完成率 = `SUM(weight WHERE status=COMPLETED) / SUM(weight) * 100`
- 规则:
  1. 终态项目(CLOSED/REJECTED/DRAFT/PENDING)→ 跳过
  2. 超期 ≥ 30 天 **或** 加权完成率 < 期望完成率 × 50% → **RED**
  3. 超期 1-29 天 **或** 加权完成率 < 期望完成率 × 80% → **YELLOW**
  4. 其它 → **GREEN**
- 接口:
  - `GET /api/health-advisor/suggest/{id}` — 单项目 dry-run
  - `POST /api/health-advisor/apply-all?apply=true|false` — 全量跑批(默认 dry-run,需 `apply=true` 才写回)
- 定时任务:`@Scheduled cron="0 0 2 * * *"` 每日 02:00,默认关闭(`pmo.health-advisor.job-enabled=true` 才生效)
- 单测:`HealthAdvisorServiceTest` 7 case 覆盖 GREEN / YELLOW(超期)/ RED(超期)/ RED(落后)/ CLOSED 跳过 / PENDING 跳过 / 跑批回写

**P1.5-d Audit 模块**:

- 实体:`OperationLog`(88 行,字段严格按 V1.3 表结构,`@JdbcTypeCode(SqlTypes.JSON)` 兼容 PG jsonb / MySQL json)
- 注解:`@AuditLog(module=..., action=..., extractResourceId=true|false)`
  - `module`:资源类型(共 6 个:INITIATION / MILESTONE / PROJECT / HEALTH_ADVISOR / AUTH)
  - `action`:动作(CREATE / UPDATE / UPDATE_STATUS / DELETE / APPROVE / RESUBMIT / RUN_BATCH / LOGIN / REFRESH / LOGOUT)
  - `extractResourceId`:默认 true(从 path 变量 `{id}` 自动提取);参数路径或非 ID 时置 false
- 切面:`OperationLogAspect` @Around
  - 捕获:userId(`SecurityUtils.currentUserId()` 反查)/ resourceId / IP(`RequestContextHolder`)/ 请求体 / 响应 / 异常 / duration ms
  - 序列化:JavaTimeModule 装好 LocalDate / LocalDateTime;ApiResponse 反射读 code/message/data
  - 截断:payload > 8KB → 截掉后半段,补 `...TRUNCATED`
  - 异步:`@Async("auditExecutor")` 8 线程池,不阻塞主业务
  - 失败:try/catch 重试 1 次,仍失败 WARN,不影响主流程
- 接入 13 个写方法:
  - `AuthController` × 3(LOGIN / REFRESH / LOGOUT)
  - `InitiationController` × 3(CREATE / APPROVE / RESUBMIT)
  - `MilestoneController` × 3(CREATE / UPDATE_STATUS / DELETE)
  - `ProjectController` × 3(CREATE / UPDATE / DELETE)
  - `HealthAdvisorController` × 1(RUN_BATCH)
- 查询 API(`AuditLogController`,`@PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN')")`):
  - `GET /api/audit-logs?resourceType=&userId=&action=&start=&end=&page=&size=`
    - 默认窗口 7 天,默认 size=20,max=100
    - 排序:createdAt DESC
  - `GET /api/audit-logs/{id}` 详情(完整 payload 字符串)
- 全局异常处理(`GlobalExceptionHandler`):
  - `AccessDeniedException` / `AuthorizationDeniedException` → 403 `forbidden: insufficient role`
  - `AuthenticationException` → 401 `unauthenticated`
  - 不再走到 generic 500
- 索引(由 V1.3 DDL 保证):`idx_oplog_user` / `idx_oplog_resource` / `idx_oplog_created`
- FK:`fk_oplog_user → app_user.id`(默认 RESTRICT)
- 单测:`AuditLogControllerTest` 4 case(PMO_ADMIN 列表 + VIEWER 403 + 未登录 401 + size 截 100)
- 真实 smoke(MySQL):写 7 次不同模块,operation_log +7 条,1 条 FAILURE(业务异常也落表)

**目录树增量**:

```
backend/src/main/java/com/company/pmo/common/audit/
  ├── AuditLog.java                    # 注解 (45 行)
  ├── OperationLog.java                # 实体 (88 行)
  ├── OperationLogRepository.java      # Repository (65 行)
  ├── OperationLogAspect.java          # 切面 + 异步 + 重试 (311 行)
  ├── AsyncConfig.java                 # auditExecutor 8 线程池 (43 行)
  ├── AuditLogController.java          # 查询 API (113 行)
  └── SecurityUtils.java               # 当前 userId 反查 (63 行)
```

**关键设计决策**:

1. **`@JdbcTypeCode(SqlTypes.JSON)` 替代 `columnDefinition="json"`** — 自动适配 PG `jsonb` / MySQL `json`,启动 JPA validate 不挂
2. **principal 保持 username(token subject 也保持)** — 改 userId 后 H2 contract test 失败,反查解决
3. **切面自调用** — `@Async` 必须走代理 → 把 `asyncPersist` 抽成 public 方法(同 bean 内调用 @Async 不生效)
4. **payload 8KB 截断** — 8KB 是 MySQL `json` 列的实际限制(虽然 server 端可能更大,但 DDL 是 8KB)
5. **FAILURE 也落表** — `result: FAILURE` + `error: <msg>` 字段,异常被 try/catch 后业务异常不会绕过
6. **6 模块枚举**:`INITIATION` / `MILESTONE` / `PROJECT` / `HEALTH_ADVISOR` / `AUTH`(USER/DEPT/DICT 暂未接入,P2 阶段)
7. **DICT/USER/DEPT 写操作不审计** — 字典数据基本不动,审计价值低,等 P2 工时 + RBAC 一起上

### 12.2 P2 — 工时 + 资源(2 个月)

| 模块 | 能力 |
|---|---|
| **工时填报** | 周维度,PM 录入,自动算剩余工时 |
| **人员负载** | 按部门/角色拉报表,看人均饱和度 |
| **项目预算** | `budget_estimate` → `budget_actual`,允许分项拆分 |
| **甘特图** | 前端 dhtmlx-gantt 或 vue-ganttastic,从 milestone 渲染 |
| **通知中心** | WebSocket + 站内信,聚合所有审批 / 风险 |
| **数据导出** | Excel / PDF 月报,模板化 |
| **细粒度 RBAC** | `@PreAuthorize("hasRole('EXEC')")` 标到方法 |

### 12.3 P3 — 多租户 + SaaS(季度级)

- **多租户隔离**:`tenant_id` 列 + 行级安全(Row-Level Security)
- **SSO**:对接公司 LDAP / OAuth2 (OIDC)
- **自定义审批流**:可视化流程设计器(Camunda Modeler)
- **移动端**:Flutter 或 PWA,主流程审批 / 看 Dashboard
- **AI 助手**:风险预警 + 智能问答(RAG 喂历史项目)

### 12.4 长期技术债(可独立排期)

- [ ] InitiationService 流程引擎化
- [ ] Dashboard 大数据量改 native SQL
- [ ] 前端组件库沉淀(自建 `pmo-ui` 包)
- [ ] 端到端契约测试自动化(OpenAPI → Postman → E2E)
- [ ] 接入 Sentry / OpenTelemetry 可观测性
- [ ] 数据库审计 / 慢查询监控

---

## 13. 文档集

| 文件 | 用途 | 受众 |
|---|---|---|
| [`pmo-pms-proposal.md`](./pmo-pms-proposal.md) | 立项提案(为什么做) | 高管 / PMO 业务方 |
| `pmo-pms-mvp-design.md` (本文) | 技术设计(怎么做的) | 开发 / 接手人 |
| [`../README.md`](../README.md) | 使用手册(怎么跑 / 怎么测) | 所有干系人 |
| [`./openapi/README.md`](./openapi/README.md) | API 契约 (8 tags / 25 paths) | 前端 / 集成方 |
| [`./api-testing/README.md`](./api-testing/README.md) | Postman / smoke 跑法 | 测试 / 运维 |
| [`../deploy/`](../deploy/) | Dockerfile 注释 | 运维 |
