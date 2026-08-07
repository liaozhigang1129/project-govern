---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: 安全设计(JWT + RBAC + 白名单 + CORS + 密码策略)
---

# 安全设计(Security)

> 单一事实来源:JWT 双 token、RBAC、CORS、密码策略、审计日志。
> 对应来源:[`legacy/pmo-pms-mvp-design.md` §7](legacy/pmo-pms-mvp-design.md)

---

## 1. JWT 双 Token(HS512)

```
Header  : { alg: "HS512", typ: "JWT" }
Payload : { sub: <username>, iat: <now>, exp: <iat + 2h) }
Signature: HMACSHA512(header.payload, secret)
```

| 类型 | 有效期 | 存放 | 用途 |
|---|---|---|---|
| **Access Token** | 2h | HttpOnly cookie + Authorization header | API 调用;前端 JS 不可读,XSS 偷不到 |
| **Refresh Token** | 30d | HttpOnly cookie,仅 `/api/auth/refresh` 端点使用 | 换新 access + 轮换 refresh |

- **secret**:来自 `application.yml` 的 `pmo.security.jwt.secret`(dev 默认值,生产**必须**用 `PROJECT_GOVERN_SECURITY_JWT_SECRET` 环境变量覆盖)
- **算法**:`Keys.hmacShaKeyFor(secret.getBytes(UTF_8))` 至少 32 字节,生产 64 字节
- **传输**:Cookie 自动带,axios 拦截器从 `Authorization` header 也读
- **黑名���**:`revoked_token` 表存已主动失效的 access token,登出 / 改密时写入;查询时 `tokenBlacklist.contains(token)` → 401

---

## 2. RBAC(基于角色字符串)

`AppUserDetailsService` 加载用户时把 `primaryRole.code` 包成 `ROLE_<code>`:

```java
.authorities(List.of(new SimpleGrantedAuthority("ROLE_" + u.getPrimaryRole().getCode())))
```

**双层门控**:

- **第一层**(`@PreAuthorize`):框架级,粗粒度角色匹配
- **第二层**(`@RequireRoles`):业务语义层,22 个端点细粒度

`SecurityConfig` 默认 `anyRequest().authenticated()`。

---

## 3. 公开白名单

```java
.requestMatchers("/auth/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health").permitAll()
```

`/actuator/health` 是 GitHub Actions `integration-smoke` job 必查的端点(原 SecurityConfig 默认锁了全部 actuator 路径,导致健康检查 500,已修)。

---

## 4. CORS

`CorsConfig` 配置:

```java
cfg.setAllowedOriginPatterns(List.of("*"));        // dev 用
cfg.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
cfg.setAllowedHeaders(List.of("*"));
cfg.setAllowCredentials(true);
```

**生产收紧**:把 `setAllowedOriginPatterns("*")` 改成环境变量 `PMO_CORS_ALLOWED_ORIGINS` 注入的允许域名列表。

---

## 5. 密码

- **算法**:BCrypt strength 10(`BCryptPasswordEncoder`)
- **存储**:`app_user.password_hash` 256 长度
- **Seed**:演示账号统一 `pmo123`(仅 dev),**生产前必须改 + 加"首次登录强制改密"**

---

## 6. 审计日志

详见 [`common.audit` 模块](data-model.md#4-软删除统一约定)。

- `@AuditLog` 注解 + AOP 切面拦截 Controller 层
- 异步写(`@Async` + 独立线程池),失败仅 warn log,不影响主事务
- 13 个写方法覆盖 6 模块(AUTH / INITIATION / MILESTONE / PROJECT / HEALTH_ADVISOR / NOTIFICATION)
- 查询 API(`GET /audit-logs`)RBAC 限 PMO_ADMIN,VIEWER 403,未登录 401
