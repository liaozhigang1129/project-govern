---
status: draft
created: 2026-08-11
updated: 2026-08-11
summary: API 契约 - 分页/错误码/时间格式/鉴权
related: docs/specs/api-contract.md
---

# API 契约 (Developer Quick Reference)

> 完整规范见 [docs/specs/api-contract.md](../specs/api-contract.md), 本文档是开发者速查。

## 1. 通用约定

### 1.1 分页

```http
GET /api/projects?page=0&size=20&sort=createdAt,desc
```

响应:
```json
{
  "code": 200,
  "data": {
    "content": [...],
    "page": 0,
    "size": 20,
    "totalElements": 142,
    "totalPages": 8
  }
}
```

### 1.2 错误码

| Code | 含义 | HTTP |
|------|------|------|
| 200 | 成功 | 200 |
| 400 | 参数错误 / 业务校验失败 | 400 |
| 401 | 未登录 / token 失效 | 401 |
| 403 | 无权限 (RBAC) | 403 |
| 404 | 资源不存在 | 404 |
| 409 | 业务冲突 (如重复提交) | 409 |
| 500 | 系统异常 | 500 |

### 1.3 时间格式

- **DB 存储**: `TIMESTAMPTZ` (UTC)
- **API 出参**: ISO-8601 (`2026-08-11T10:30:00Z`)
- **API 入参**: ISO-8601 / `2026-08-11` (LocalDate)
- **序列化**: Jackson + `jackson-datatype-jsr310`

### 1.4 鉴权

所有 `/api/**` (除 `/auth/login`, `/auth/refresh`) 都需要:
```
Authorization: Bearer <jwt>
```

JWT Claims: `sub` (username), `uid`, `roles` (array), `exp`.

## 2. RBAC

22 个端点用 `@RequireRoles` 限制:

| 角色 | 权限 |
|------|------|
| `PM` | 项目内里程碑/工时 读写 |
| `DEPT_LEAD` | 本部门立项审批 |
| `PMO_ADMIN` | 立项复核 + 治理配置 |
| `EXEC` | 立项终审 |
| `VIEWER` | 只读 |

## 3. 跨域

`CorsConfig` 默认放行 `http://localhost:5173` (Vite dev), 生产由 Nginx 处理。

## 4. Swagger

`http://localhost:8080/api/swagger-ui.html` — 在线测试所有端点。
OpenAPI 3 JSON: `/v3/api-docs`