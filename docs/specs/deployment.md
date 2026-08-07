---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: CI/CD 与部署(5 jobs + Docker 多阶段镜像 + 关键环境变量)
---

# CI/CD 与部署(Deployment)

> 单一事实来源:GitHub Actions 5 jobs、Docker 多阶段镜像、compose 服务、关键环境变量。
> 对应来源:[`legacy/pmo-pms-mvp-design.md` §10](legacy/pmo-pms-mvp-design.md) + [`.github/workflows/maven-test.yml`](../../.github/workflows/maven-test.yml)

---

## 1. GitHub Actions — 5 jobs

```
┌─ backend-test   ─┐
│                  ├─► backend-build ─► integration-smoke ─┐
└─ frontend-build ─┘                                       │
                                                           ├─► ci-status
                       ┌─ docs-lint (新) ──────────────────┘
                       │
                       └─ (与 4 个构建 job 并列,不互阻)
```

| Job | 触发 | 做什么 | 依赖 |
|---|---|---|---|
| `backend-test` | push / PR | JDK 21 + Maven + 78+ JUnit + 抓 openapi.json artifact | - |
| `backend-build` | push / PR | Maven `package -DskipTests` | backend-test |
| `frontend-build` | push / PR | Node 20 + pnpm + `vue-tsc` + Vite build | - |
| `integration-smoke` | push / PR | H2 profile 起 jar + `/actuator/health` + 登录端点探测 | backend-test + backend-build |
| **`docs-lint`** | push / PR | `bash scripts/docs-lint.sh docs`(front-matter / STATUS-WBS 双轨 / 死链接 / CHANGELOG [Unreleased] / decisions 编号) | - |

> Dependabot 每周一自动检查 Maven / npm / GitHub Actions 依赖。

---

## 2. Docker 镜像

| 服务 | 基础镜像 | 多阶段 | 终态 |
|---|---|---|---|
| **backend** | `eclipse-temurin:21-jdk-alpine` → `21-jre-alpine` | 2 阶段 | JDK 构建 → JRE 运行,瘦身 50% |
| **frontend** | `node:20-alpine` → `nginx:1.27-alpine` | 2 阶段 | node 构建 → nginx 静态 |
| **mysql** | `mysql:8.0` | - | utf8mb4 + Asia/Shanghai 时区 |

后端用 `adduser project-govern` 切非 root 运行;健康检查 `wget /api/actuator/health`。

---

## 3. compose 服务

| 服务 | 端口 | 健康检查 | 启动顺序 |
|---|---|---|---|
| mysql | 3306 | `mysqladmin ping` | 1 |
| backend | 8088 | `/api/actuator/health` | 2 (等 mysql healthy) |
| frontend | 8080 | `/healthz` (nginx 返 200) | 3 (等 backend healthy) |

- `docker-compose.override.yml` 给开发用:把 backend 切到 `target: build` + 挂载本地源码 + spring-boot-devtools
- 生产建议把 mysql 换成外部托管(RDS / PolarDB),`docker-compose.yml` 只留 backend + frontend

---

## 4. 关键环境变量

| 变量 | 默认 | 生产必须改 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | mysql 容器内网 | 指向 RDS |
| `SPRING_DATASOURCE_PASSWORD` | `project_govern_dev_2025` | secret manager |
| `PROJECT_GOVERN_SECURITY_JWT_SECRET` | yaml 默认(项目名 dev key) | `openssl rand -base64 48` ≥ 32 字节 |
| `PMO_CORS_ALLOWED_ORIGINS` | `http://localhost,8080,5173` | 真实域名 |
| `JAVA_TOOL_OPTIONS` | `-Xms256m -Xmx512m` | 按机器调 |

---

## 5. 一键起

```bash
docker compose up -d        # 后台起 3 个服务
docker compose logs -f      # 跟日志
docker compose down         # 停(保留数据卷)
docker compose down -v      # 停 + 删数据卷
```

访问:
- 前端: <http://localhost:8080>
- Swagger: <http://localhost:8088/api/swagger-ui.html>
- 默认账号: `admin / pmo123`
