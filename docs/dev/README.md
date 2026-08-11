---
status: draft
created: 2026-08-11
updated: 2026-08-11
summary: 开发者手册 - 本地环境/启动/调试/扩展/CI 全流程
audience: backend / frontend / devops
---

# 开发者手册 (Dev Guide)

> 本文档面向**项目治理系统 project-govern** 的开发者(后端 / 前端 / 运维)。
>
> 用户手册见 [docs/guides/README.md](../guides/README.md), 架构与设计见 [docs/specs/](../specs/)。

## 1. 本地开发环境

### 1.1 必备工具

| 工具 | 版本 | 用途 |
|------|------|------|
| JDK | 21 (LTS) | 后端编译 + 运行 |
| Node | 20.x | 前端构建 |
| pnpm | 9.x | 前端包管理 (强制) |
| Maven | 3.9.x | 后端构建 |
| Docker Desktop | latest | 一键拉 MySQL/PG/Redis |
| IntelliJ IDEA | 2024.3+ | 后端 IDE |
| VS Code | latest | 前端 IDE |

### 1.2 快速启动 (Docker Compose)

```bash
# 1. 拉起所有依赖 (MySQL 8 + PG 16 + Redis 7)
make dev-up

# 2. 后端启动 (另起终端)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 3. 前端启动 (另起终端)
cd frontend
pnpm install
pnpm dev
```

访问:
- 前端: http://localhost:5173
- 后端: http://localhost:8080/api
- Swagger: http://localhost:8080/api/swagger-ui.html
- 默认账号: `admin / pmo123`

## 2. 后端开发

### 2.1 工程结构

```
backend/src/main/java/com/hex/projectgovern/
├── ZhiyuApplication.java       # Spring Boot 启动类
├── common/                      # 通用 (config/security/exception)
│   ├── config/ClockConfig.java  # 系统 Clock 注入 (测试可替换)
│   ├── exception/                # BusinessException + GlobalHandler
│   └── security/                 # JWT + RBAC + RevokedToken
├── module/                       # 业务模块 (按业务域分)
│   ├── project/                  # 项目主数据
│   ├── milestone/                # 里程碑
│   ├── initiation/               # 立项
│   ├── approval/                 # 通用审批引擎 (WP-M7-03~07)
│   ├── timesheet/                # 工时
│   ├── finance/                  # 财务
│   ├── alert/                    # 预警
│   ├── notification/             # 通知中心
│   ├── org/                      # 组织 (用户/部门/角色)
│   └── ...
└── tools/                        # 工具 (导入导出/AI 集成)
```

### 2.2 测试约定

- **单元测试**: `backend/src/test/java/.../service/*Test.java` (Spring + JUnit 5)
- **集成测试**: `*IntegrationTest.java` (SpringBootTest + H2 + @SpyBean)
- **契约测试**: `*ContractTest.java` (MockMvc + JsonPath)
- **共享数据**: `common/testsupport/ContractTestDataInitializer.java`
- **测试 Profile**: `@ActiveProfiles("test")`, 走 H2 in-memory
- **Clock 注入**: 业务时间敏感逻辑统一从 `Clock` Bean 拿, 测试用 `@TestConfiguration` 覆盖

### 2.3 Flyway 迁移

- MySQL: `backend/src/main/resources/db/migration-mysql/V*.sql`
- PG: `backend/src/main/resources/db/migration-pg/V*.sql`
- 命名: `V{版本}__{描述}.sql` (双下划线)
- 幂等: 字典/seed 数据用 `INSERT IGNORE` (MySQL) / `ON CONFLICT (code) DO NOTHING` (PG)
- 详见 [docs/dev/database.md](database.md)

### 2.4 调试技巧

- **远程调试**: `mvn spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"`
- **日志**:
  - SQL 调试: `application-dev.yml` 设 `logging.level.org.hibernate.SQL=DEBUG`
  - 业务日志: `log.info("...initiationId={} ...", id)` (SLF4J 模板)
- **断点**: IDEA 在 `RestController` / `Service` 方法入口打断点即可
- **H2 控制台**: `http://localhost:8080/api/h2-console` (dev profile)

### 2.5 新增业务模块 (模板)

按以下 6 步新增模块:

1. **Entity** (`module/<name>/Xxx.java`) — JPA + Lombok + 软删 (`@SQLDelete`)
2. **Repository** (`module/<name>/XxxRepository.java`) — `JpaRepository<Xxx, Long>`
3. **DTO** (`module/<name>/dto/XxxDtos.java`) — `record` + `@Valid`
4. **Service** (`module/<name>/XxxService.java`) — `@Service @RequiredArgsConstructor @Transactional`
5. **Controller** (`module/<name>/XxxController.java`) — `@RestController @RequestMapping`
6. **Test** — 单测 (`XxxServiceTest`) + 集成测试 (`XxxIntegrationTest`)

参考 [docs/dev/extending.md](extending.md) 详细模板。

## 3. 前端开发

### 3.1 技术栈

- **框架**: Vue 3 (Composition API + `<script setup>`)
- **构建**: Vite 5 + TypeScript 5
- **UI**: Element Plus + ECharts + Pinia + Vue Router
- **代码质量**: ESLint (vue + @typescript-eslint) + Prettier

### 3.2 目录结构

```
frontend/src/
├── api/                  # API 客户端 (axios + 类型)
├── components/           # 可复用组件
│   ├── wbs/              # WBS 子组件
│   ├── admin/            # 管理后台子组件
│   └── ...
├── views/                # 页面 (路由级)
├── router/               # 路由
├── stores/               # Pinia stores
├── types/                # 全局类型
└── utils/                # 工具函数
```

### 3.3 常用命令

```bash
pnpm install              # 安装依赖
pnpm dev                  # 启动开发服务器 (HMR)
pnpm build                # 生产构建
pnpm lint                 # ESLint 检查
pnpm format               # Prettier 格式化
pnpm test                 # 单元测试 (Vitest)
```

## 4. CI/CD

### 4.1 GitHub Actions

`.github/workflows/` 下 4 个流水线:

| Workflow | 触发 | 用途 |
|----------|------|------|
| `maven-test.yml` | PR/push to main | 后端测试 (H2) + Jacoco |
| `pg-test.yml` | PR/push to main | 后端测试 (PG 16) 验证迁移 |
| `frontend-lint.yml` | PR/push to main | 前端 lint + build |
| `release.yml` | tag v*.*.* | 发布 Docker 镜像 + 文档站 |

### 4.2 PR 提交流程

1. 拉分支: `git switch -c feat/<wp-id>-<short-desc>`
2. 提交: `git commit -m "feat(<scope>): <description>"`
3. 推送: `git push origin feat/...`
4. 开 PR: 关联 issue, 等 CI 全绿, 1 reviewer 通过
5. 合并: **Squash and merge** (主分支保持线性)

## 5. 部署

详见 [docs/dev/deployment.md](deployment.md):
- Docker Compose (单节点小团队)
- Kubernetes + Helm (生产环境)
- Nginx 反代 + HTTPS 配置

## 6. 文档导航

- 用户手册: [docs/guides/](../guides/)
- 设计 spec: [docs/specs/](../specs/)
- API 契约: [docs/specs/api-contract.md](../specs/api-contract.md) + [docs/dev/api-contract.md](api-contract.md)
- 部署指南: [docs/dev/deployment.md](deployment.md)
- 扩展指南: [docs/dev/extending.md](extending.md)
- 数据库迁移: [docs/dev/database.md](database.md)