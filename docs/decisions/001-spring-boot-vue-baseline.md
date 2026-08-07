# 001 · v4+ 基线采用 Spring Boot 3.3 + Java 21 + Vue 3.5

- 状态:**accepted**
- 日期:2026-08-07(回溯立项于 v4.0.0 release 2026-06-13)
- 决定人:PMO + 架构组

## 背景

老仓库 v2.x 用的是 Spring Boot 2.7 + Java 17 + Vue 2.7,2025 年下半年起遇到三个痛点:
- Spring Boot 2.7 已停止 OSS 支持,安全补丁缺位;
- Java 17 的虚拟线程未稳定,长事务场景(成本引擎月度快照)排队明显;
- Vue 2.7 EOL 已宣布(2023-12-31),前端生态全面转向 Vue 3。

## 决定

v4+ 全栈基线升级为:
- **后端**:Spring Boot 3.3 + Java 21 (LTS) + JPA/Hibernate 6.x + Flyway 10.x + springdoc-openapi 2.6
- **前端**:Vue 3.5 + Element Plus 2.8 + Pinia 2.2 + Vite 5.4 + ECharts 5.5 + Axios 1.7
- **数据库**:MySQL 8.0(生产)+ PostgreSQL 16(CI/测试)+ H2 PG mode(测试 in-memory)
- **鉴权**:Spring Security + jjwt 0.12 (HS512),双 token 体系(2h access + 30d refresh + HttpOnly cookie)
- **CI**:GitHub Actions 4 jobs(backend-test / frontend-build / api-smoke / docker-build)+ Dependabot 周扫
- **部署**:Docker Compose v2 + Nginx 1.27 反代

## 不采用的方案

- **方案 B:保留 Java 17,只升 Spring Boot 3.x**
  缺点:吃不满虚拟线程,成本引擎月度快照场景无法改善;且 Spring Boot 3.x 最低要求 Java 17,但官方推荐 Java 21。
- **方案 C:迁到 Node.js/Go**
  缺点:重写成本高;现有 78 个 JUnit 测试与 Flyway migration 全部作废;团队技能不匹配。
- **方案 D:保留 Vue 2 + 升级 Element UI**
  缺点:Vue 2 EOL 后 Element UI 也停更,前端技术债继续累积。

## 影响

- v3.x → v4.x 是一次 breaking 升级,JDK 21 + Jakarta EE 9 namespace 变更需全仓搜索替换 `javax.*` → `jakarta.*`。
- 前端 `package.json` `engines.node` 锁到 `>=20`。
- 详见 [docs/drafts/扩展文档/P1.5-收尾/P1.5-收尾一页纸.md](../drafts/扩展文档/P1.5-收尾/P1.5-收尾一页纸.md) §3 与 [README.md §二 技术栈](../../README.md)。

## 验收

- ✅ 2026-06-13 v4.0.0 release(`c45df49`)在基线上发布,19 后端模块 / 35 Controller / 78 JUnit / 33 paths 全绿
- ✅ CI 4 jobs 全绿
- ✅ Docker Compose 一键起 + Nginx 反代 /api 路径工作正常
