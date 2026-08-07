---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: API 契约(响应约定 + OpenAPI 工具链 + DTO 模式 + 错误码)
---

# API 契约(API Contract)

> 单一事实来源:统一响应约定、OpenAPI 工具链、DTO 模式、错误码体系。
> 对应来源:[`legacy/pmo-pms-mvp-design.md` §5](legacy/pmo-pms-mvp-design.md)
> 当前 33 paths / 37 schemas / 10 tags 完整 OpenAPI 存档见 [`openapi/openapi.json`](openapi/openapi.json)。
> Postman 集合(29 请求)见 [`../testing/postman/`](../testing/postman/)。

---

## 1. 统一响应

所有接口都返回 `ApiResponse<T>`:

```json
// 成功
{ "code": 0, "message": "ok", "data": <T>, "timestamp": 1700000000000 }

// 业务异常
{ "code": 400, "message": "Project code exists: P-2025-001", "data": null, "timestamp": 1700000000001 }
```

定义在 `common/api/ApiResponse.java`,**前端 axios 拦截器**(`api/client.ts`)拿到 `code !== 0` 直接 reject,业务层 `await` 即拿到 `data`,无需再 `.data.data`。

---

## 2. OpenAPI / 仓库存档

- **源**:`springdoc-openapi-starter-webmvc-ui` 从 `@Operation` / `@Tag` 自动生成
- **实时**:后端启动后 <http://localhost:8088/api/swagger-ui.html>
- **JSON 契约**:`docs/specs/openapi/openapi.json`(snapshot)
- **重新生成**:CI `backend-test` job 跑完测试后 `curl /v3/api-docs -o openapi.json`

> **契约是 single source of truth**。前端类型从 openapi.json 抄,改接口必须先改后端 → 重新生成 → 前端跟改。

---

## 3. DTO 模式(重要!)

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

---

## 4. 字典子接口:按 code 查

```java
public interface ProjectTypeRepository extends JpaRepository<ProjectType, Long> {
    Optional<ProjectType> findByCode(String code);
    boolean existsByCode(String code);
}
```

由 `ProjectDtoContractTest` (8 用例) 覆盖契约:传 `"GHOST_TYPE"` 应 400 `"Unknown typeCode: GHOST_TYPE"`。

---

## 5. 错误码体系

| code | 含义 | HTTP | 触发场景 |
|---|---|---|---|
| 0 | 成功 | 200 | - |
| 400 | 业务校验失败 | 400 | `BusinessException` 默认 / `@Valid` 失败 |
| 401 | 未认证 | 401 | SecurityFilter 拒绝 / JWT 失效 |
| 404 | 资源不存在 | 200 (body) | `BusinessException(404, "...")` |
| 500 | 未捕获异常 | 500 | `Exception.class` catch-all |

`GlobalExceptionHandler` 统一翻译成 `ApiResponse.fail(code, message)`,前端只需看 body.code。

---

## 6. 鉴权与双 Token

详见 [`security.md` §JWT](security.md)。要点:

- 所有写接口需 `Authorization: Bearer <token>`(除 `/auth/login`)
- 双 token:2h access + 30d refresh,refresh 放 HttpOnly cookie
- 黑名单:登出 / 改密时主动失效,即使 token 未到期
