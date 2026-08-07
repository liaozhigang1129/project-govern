# project-govern

> 治理视角下的项目全生命周期管理:**立项审批 → 项目执行 → 里程碑跟踪 → 健康度监控 → 成本 / 工时 / 风险 / 通知**。

📄 **核心文档**(详细分层见 [`docs/README.md`](docs/README.md)):

- 产品需求 → [`docs/PRD.md`](docs/PRD.md)
- 架构设计 → [`docs/DESIGN.md`](docs/DESIGN.md)
- 工作分解 → [`docs/WBS.md`](docs/WBS.md)
- 项目状态 → [`docs/STATUS.md`](docs/STATUS.md)
- 版本演进 → [`docs/CHANGELOG.md`](docs/CHANGELOG.md)
- 架构决策 → [`docs/decisions/`](docs/decisions/)

---

## 目录

- [一、架构总览](#一架构总览)
- [二、技术栈](#二技术栈)
- [三、目录结构](#三目录结构)
- [四、核心领域模型](#四核心领域模型)
- [五、API 契约](#五api-契约)
- [六、快速开始](#六快速开始)
- [七、测试](#七测试)
- [八、Docker Compose 一键起](#八docker-compose-一键起)
- [九、CI / CD](#九ci--cd)
- [十、部署](#十部署)
- [十一、文档规范](#十一文档规范)
- [十二、License](#十二license)

---

## 一、架构总览

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
                            │  MySQL 8.0 / PostgreSQL 16    │
                            │  port 3306 / 5432             │
                            └────────────────────────────────┘
```

**3 个进程 / 1 条命令:**

```bash
docker compose up -d     # mysql + backend + frontend 一起拉起
```

---

## 二、技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| **后端** | Spring Boot | 3.3 |
| | Java | 21 (LTS) |
| | Spring Security + JWT | jjwt 0.12 (HS512) |
| | JPA / Hibernate | 6.x |
| | Flyway | 10.x |
| | springdoc-openapi | 2.6 |
| **前端** | Vue | 3.5 |
| | Element Plus | 2.8 |
| | Pinia | 2.2 |
| | Vite | 5.4 |
| | ECharts | 5.5 |
| | Axios | 1.7 |
| **数据库** | MySQL | 8.0 (生产) |
| | PostgreSQL | 16 (CI/测试) |
| | H2 (PG mode) | 测试 in-memory |
| **测试** | JUnit 5 | 5.10 |
| | AssertJ | 3.25 |
| | Cypress | 13 (E2E) |
| | Newman | CLI Postman runner |
| **部署** | Docker Compose | v2 |
| | GitHub Actions | 4 jobs |
| | Nginx | 1.27 |

---

## 三、目录结构

```
project-govern/
├── backend/                            # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/java/com/company/pmo/  # 19 个业务模块
├── frontend/                           # Vue 3 前端
├── db/migration/                       # Flyway SQL 脚本
├── deploy/docker/                      # Dockerfile
├── scripts/                            # 运维 / 种子数据 / docs-lint
├── cypress/                            # E2E
├── models/                             # 预置 ML 模型(.pkl)
│
├── docs/                               # 文档(STATUS/WBS 双轨)
│   ├── README.md                       # 文档地图与约定
│   ├── PRD.md                          # 产品需求
│   ├── DESIGN.md                       # 架构设计
│   ├── WBS.md                          # 工作分解(只列任务)
│   ├── STATUS.md                       # 全局状态(快照)
│   ├── CHANGELOG.md                    # 版本演进(只追加)
│   ├── decisions/                      # ADR(只追加)
│   ├── specs/                          # 模块规格 + 接口契约
│   ├── plans/                          # 实现计划
│   ├── testing/                        # 测试策略
│   ├── analysis/                       # 分析过程(选型/对比)
│   ├── drafts/                         # 历史草稿
│   ├── reviews/                        # 评审存档
│   ├── guides/                         # 用户文档
│   ├── runbooks/                       # 运维手册
│   └── dev/                            # 开发者文档
│
├── docker-compose.yml
├── Makefile                            # make test/build/run/docs-lint
├── AGENTS.md                           # 代理导航(只放指针)
└── README.md                           # 本文件
```

---

## 四、核心领域模型

### 4.1 立项三级审批(状态机)

```
    提交立项
       │
       ▼
  ┌─────────┐
  │ PENDING │ ◄──────────────────────┐
  │ DEPT_LEAD│                        │
  └────┬────┘                        │
       │ 通过                         │ 驳回
       ▼                              │
  ┌──────────┐                        │
  │ PENDING  │                        │
  │  PMO     │ ──── 驳回 ─────────────┤
  └────┬─────┘                        │
       │ 通过                         │
       ▼                              │
  ┌──────────┐                        │
  │ PENDING  │                        │
  │  EXEC    │ ──── 驳回 ─────────────┤
  └────┬─────┘                        │
       │ 通过                         │
       ▼                              │
  ┌──────────┐                        │
  │ APPROVED │                        │
  └──────────┘                        │
                                      │
                            ┌─────────┴────────┐
                            │ REJECTED (终态)   │
                            └──────────────────┘
```

### 4.2 项目加权进度

```
项目进度 = Σ(里程碑权重 × 状态完成度) / Σ(权重)
权重来自 milestone.weight, 状态完成度: PENDING=0, IN_PROGRESS=0.5, DONE=1
```

### 4.3 健康度(5 级字典)

`EXCELLENT / GOOD / NORMAL / WARNING / CRITICAL` — 字典表统一管理

---

## 五、API 契约

**OpenAPI 3.0.1** — 33 paths / 37 schemas / 10 tags / JWT (HS512) security

- 源文件: [`docs/specs/openapi/openapi.json`](docs/specs/openapi/openapi.json)
- Swagger UI: 启动后端后访问 <http://localhost:8088/api/swagger-ui.html>
- Postman 一键导入: [`docs/testing/postman/zhiyu.postman_collection.json`](docs/testing/postman/zhiyu.postman_collection.json)

### 5.1 业务响应约定

```json
// 成功
{ "code": 0, "data": { ... } }

// 失败
{ "code": 40001, "message": "项目编码已存在" }
```

### 5.2 鉴权

```
POST /api/auth/login   { "username":"admin", "password":"pmo123" }
→  { "code":0, "data":{ "token":"eyJhbGc...", "user":{...} } }

后续所有请求:
Authorization: Bearer <token>
```

---

## 六、快速开始

### 6.1 准备条件

- Docker + Docker Compose v2
- 或 Java 21 + Node 20 + MySQL 8(本地开发)

### 6.2 一键启动(推荐)

```bash
git clone <repo> && cd project-govern
docker compose up -d
# 等待 ~60s (MySQL + Flyway + Spring Boot 启动)
docker compose ps
curl http://localhost:8088/api/dashboard/kpis
```

访问:

- 前端: <http://localhost:8080>
- Swagger: <http://localhost:8088/api/swagger-ui.html>
- 默认账号: `admin / pmo123`

### 6.3 本地开发(源码模式)

```bash
# 1. 起 MySQL
docker compose up -d mysql

# 2. 后端
cd backend
./mvnw spring-boot:run          # http://localhost:8088/api

# 3. 前端
cd frontend
pnpm install
pnpm dev                        # http://localhost:5173
```

或用 dev override(后端代码改完自动重启):

```bash
docker compose -f docker-compose.yml -f docker-compose.override.yml up
```

---

## 七、测试

### 7.1 后端(JUnit 5,78 个测试)

```bash
cd backend && ./mvnw test
```

### 7.2 端到端 API 烟雾测试(16 个核心调用)

```bash
bash docs/testing/postman/smoke.sh
```

### 7.3 E2E(Cypress,4 suite)

```bash
pnpm e2e
```

### 7.4 Postman GUI

导入 [`docs/testing/postman/zhiyu.postman_collection.json`](docs/testing/postman/zhiyu.postman_collection.json) + 同目录 `*environment.json`,用 **Collection Runner** 跑完 29 个请求。

### 7.5 文档规范检查

```bash
make docs-lint
```

校验 docs/ 目录的 front-matter、STATUS/WBS 双轨边界、相对链接、CHANGELOG `[Unreleased]`、decisions 编号顺序。

---

## 八、Docker Compose 一键起

```bash
docker compose up -d        # 后台起 3 个服务
docker compose logs -f      # 跟日志
docker compose down         # 停(保留数据卷)
docker compose down -v      # 停 + 删数据卷
```

服务清单:

| 服务 | 端口 | 健康检查 |
|---|---|---|
| mysql | 3306 | `mysqladmin ping` |
| backend | 8088 | `/api/actuator/health` |
| frontend | 8080 | `/healthz` |

生产建议:把 MySQL 换成外部托管(RDS / PolarDB / 自管),`docker-compose.yml` 里只留 `backend` + `frontend`。

---

## 九、CI / CD

`.github/workflows/ci.yml` — 4 个并行/串行 jobs:

```
┌─ backend-test   ─┐
│                  ├─► api-smoke  (需后端测试通过)
└─ frontend-build ─┘
              │
              └─► docker-build  (独立校验 compose 文件)
```

| Job | 触发 | 做什么 |
|---|---|---|
| `backend-test` | push / PR | JDK 21 + Maven + 78 个 JUnit,导出 `openapi.json` artifact |
| `frontend-build` | push / PR | Node 20 + pnpm + `vue-tsc` + Vite build |
| `api-smoke` | push / PR(需 backend-test 过) | 起服务跑 `smoke.sh` 16 个调用 |
| `docker-build` | push / PR | `docker compose config --quiet` 语法校验 |

Dependabot 每周一自动检查 Maven / npm / GitHub Actions 依赖。

---

## 十、部署

### 10.1 单机部署(本仓库默认)

```bash
docker compose up -d
```

### 10.2 生产拆分(数据库托管)

```bash
# 1. 把 docker-compose.yml 里的 mysql 服务删掉
# 2. 修改 backend 环境变量指向 RDS
SPRING_DATASOURCE_URL=jdbc:mysql://rds.example.com:3306/project_govern
SPRING_DATASOURCE_USERNAME=pmo_app
SPRING_DATASOURCE_PASSWORD=<from-secret-manager>

# 3. 改 JWT 密钥(必做!)
PMO_SECURITY_JWT_SECRET=$(openssl rand -base64 48)

# 4. 改 CORS 白名单
PMO_CORS_ALLOWED_ORIGINS=https://pmo.your-domain.com

# 5. 起 backend + frontend
docker compose up -d
```

### 10.3 反向代理(Nginx / Cloudflare)

把 `https://pmo.your-domain.com/api` 反代到后端 `8088`,`https://pmo.your-domain.com/` 反代到前端 `8080`。**一定要把 JWT secret 放 secrets manager,不要进 git。**

---

## 十一、文档规范

本仓库采用 **Sift 风格文档规范 + STATUS/WBS 双轨**(见 ADR [003](docs/decisions/003-docs-status-wbs-split.md)):

- **`docs/README.md`** — 文档地图与命名约定
- **`docs/STATUS.md`** — 全��项目计划执行情况(里程碑进度 / WIP / 风险 / 决策快照 / 门禁),每次里程碑评审后更新
- **`docs/WBS.md`** — 工作分解(只列任务结构),**不带 status 字段**
- **`docs/decisions/`** — ADR 架构决策,只追加不修改
- **`docs/specs/`** — 模块规格 / 接口契约,随代码同步
- **`docs/reviews/`** — 评审存档,只读
- **`docs/drafts/`** — 历史草稿,只读

代理默认只在 `status: active | draft` 的文档中工作;详细规则见 [AGENTS.md](AGENTS.md) 与 [docs/README.md §上下文预算](docs/README.md)。

---

## 十二、License

[MIT](LICENSE)
