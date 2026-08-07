# OpenAPI 契约

## 生成方式

后端使用 `springdoc-openapi-starter-webmvc-ui` 自动从 Spring MVC 注解生成 OpenAPI 3.0.1 文档。

- 实时访问: <http://localhost:8088/api/swagger-ui.html>
- JSON 契约: <http://localhost:8088/api/v3/api-docs>
- 仓库存档: [`openapi.json`](./openapi.json) (snapshot)

## 模块清单 (10 tags / 33 operations / 37 schemas)

| Tag | 数量 | 接口 |
|---|---|---|
| **Auth** | 4 | POST /auth/login · POST /auth/refresh · POST /auth/logout · GET /auth/me |
| **AuditLog** | 2 | GET /audit-logs · GET /audit-logs/{id} |
| **Dashboard** | 4 | /dashboard/{kpis,status-distribution,health-distribution,active-projects} |
| **Departments** | 1 | GET /departments |
| **Dictionaries** | 6 | /dict/{project-types,project-statuses,health-levels,initiation-statuses,milestone-statuses,approval-steps} |
| **HealthAdvisor** | 2 | GET /health-advisor/suggest/{id} · POST /health-advisor/apply-all |
| **Initiations** | 5 | GET/POST /initiations, GET /initiations/{id}, POST /initiations/{id}/decide, GET /initiations/{id}/records |
| **Milestones** | 5 | POST /milestones, GET /milestones/by-project/{pid}, PUT /milestones/{id}/status, DELETE /milestones/{id}, GET /milestones/progress/{pid} |
| **Projects** | 5 | GET/POST /projects, GET/PUT/DELETE /projects/{id} |
| **Users** | 1 | GET /users |

## 通用约定

```json
// 成功响应
{ "code": 0, "message": "ok", "data": <T>, "timestamp": 1700000000000 }

// 业务异常
{ "code": 400, "message": "Project code exists: P-2025-001", "data": null, "timestamp": ... }
```

- `code=0` 表示成功,其他为业务异常
- 所有写接口需 `Authorization: Bearer <token>` (除 `/auth/login`)

## 重新生成

```bash
# 启动后端
java -jar backend/target/zhiyu-pms-backend.jar

# 抓取
TOKEN=$(curl -s -X POST http://localhost:8088/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"pmo123"}' | jq -r .data.token)

curl -s "http://localhost:8088/api/v3/api-docs" \
  -H "Authorization: Bearer $TOKEN" > docs/openapi/openapi.json
```

## 前端集成

Vue 3 + Axios 推荐做法:

```ts
// src/api/client.ts
import axios from 'axios'
import openapi from '@/api/openapi.json' // 拷本仓库的 openapi.json

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE ?? 'http://localhost:8088/api',
})

api.interceptors.request.use((cfg) => {
  const tok = localStorage.getItem('token')
  if (tok) cfg.headers.Authorization = `Bearer ${tok}`
  return cfg
})
```

也可以用 `openapi-typescript` 从 `openapi.json` 自动生成类型:

```bash
npx openapi-typescript docs/openapi/openapi.json -o src/api/schema.d.ts
```
