---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: 系统架构总览(部署拓扑 + 模块分层 + 关键约定)
---

# 架构总览(Architecture)

> 单一事实来源:系统部署拓扑、进程内模块分层、关键工程约定。
> 对应来源:[`legacy/pmo-pms-mvp-design.md` §1 §2 §3](legacy/pmo-pms-mvp-design.md)
> 模块清单见 [`data-model.md`](data-model.md),API 契约见 [`api-contract.md`](api-contract.md),安全见 [`security.md`](security.md)。

---

## 1. 部署拓扑

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

详见 [`deployment.md`](deployment.md) §Docker Compose。

---

## 2. 进程内模块分层

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
- 业务模块之间**不互相依赖**,跨模块数据通过 Service 调用或事件总线

---

## 3. 技术栈与版本

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
| | H2 (PG mode) | 单测 in-memory |
| **测试** | JUnit 5 | 5.10 |
| | AssertJ | 3.25 |
| | Cypress | 13 (E2E) |
| | Newman | CLI Postman runner |
| **部署** | Docker Compose | v2 |
| | GitHub Actions | 5 jobs(backend-test / backend-build / frontend-build / integration-smoke / **docs-lint**) |
| | Nginx | 1.27 |

> **依赖升级决策**:见 [decisions/002-mysql-pg-dual-track.md](../decisions/002-mysql-pg-dual-track.md) 与 [decisions/001-spring-boot-vue-baseline.md](../decisions/001-spring-boot-vue-baseline.md)。
