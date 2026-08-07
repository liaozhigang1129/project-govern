# A2 OpenAPI 3.0 规范 Part8 — 系统管理与认证授权

> 本 Part 覆盖 A2.11 系统管理域（租户、SSO、字典、扩展能力）。

---

## A2.11 系统管理与认证授权

### A2.11.1 租户与组织

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/tenants` | 租户列表（仅平台） |
| POST | `/tenants` | 新建租户 |
| GET | `/tenants/{id}` | 详情 |
| PATCH | `/tenants/{id}` | 更新 |
| POST | `/tenants/{id}/disable` | 停用 |
| GET | `/tenants/{id}/quotas` | 配额 |
| PATCH | `/tenants/{id}/quotas` | 调整配额 |
| GET | `/tenants/{id}/usage` | 用量统计 |

```yaml
Tenant:
  type: object
  properties:
    id: { type: string }
    code: { type: string }
    name: { type: string }
    status: { type: string, enum: [ACTIVE, SUSPENDED, ARCHIVED] }
    plan: { type: string, enum: [FREE, STANDARD, ENTERPRISE, CUSTOM] }
    locale: { type: string }
    timezone: { type: string }
    region: { type: string, enum: [CN, APAC, EU, US, GLOBAL] }
    customDomain: { type: string, nullable: true }
    createdAt: { type: string, format: date-time }

TenantQuotas:
  type: object
  properties:
    tenantId: { type: string }
    maxUsers: { type: integer }
    maxProjects: { type: integer }
    maxStorageGb: { type: integer }
    maxApiCallsPerMin: { type: integer }
    features: { type: array, items: { type: string } }
```

### A2.11.2 认证（Auth）

| 方法 | 路径 |
| --- | --- |
| POST | `/auth/login` | 密码登录
| POST | `/auth/login-mfa` | MFA 验证（第二步）
| POST | `/auth/logout` | 注销
| POST | `/auth/refresh` | 刷新 Token
| POST | `/auth/sso/callback` | SSO 回调
| POST | `/auth/sso/initiate?system=saml|oidc` | SSO 发起
| POST | `/auth/password/change` | 修改密码
| POST | `/auth/password/reset` | 重置密码（需邮件链接）
| POST | `/auth/mfa/enable` | 启用 MFA
| POST | `/auth/mfa/disable` | 关闭 MFA
| POST | `/auth/mfa/verify` | 校验 MFA Token
| GET | `/auth/sessions` | 当前活跃会话
| DELETE | `/auth/sessions/{id}` | 注销指定会话
| GET | `/auth/me` | 当前用户信息

```yaml
LoginRequest:
  type: object
  required: [loginName, password]
  properties:
    loginName: { type: string }
    password: { type: string, format: password }
    tenantCode: { type: string, description: "多租户场景必填" }
    captcha: { type: string }
    deviceFingerprint: { type: string }

LoginResponse:
  type: object
  properties:
    accessToken: { type: string }
    refreshToken: { type: string }
    tokenType: { type: string, example: "Bearer" }
    expiresIn: { type: integer, example: 3600 }
    mfaRequired: { type: boolean }
    mfaToken: { type: string, nullable: true }
    user: { $ref: '#/components/schemas/UserSummary' }
    permissions: { type: array, items: { type: string } }
```

### A2.11.3 SSO 配置

| 方法 | 路径 |
| --- | --- |
| GET | `/sso/providers` | 接入的 IdP 列表 |
| POST | `/sso/providers` | 新建 IdP |
| GET | `/sso/providers/{id}` | 详情 |
| PATCH | `/sso/providers/{id}` | 更新 |
| DELETE | `/sso/providers/{id}` | 删除 |
| POST | `/sso/providers/{id}/test` | 测试连通性 |
| POST | `/sso/providers/{id}/sync` | 触发 LDAP 同步 |

```yaml
SsoProvider:
  type: object
  properties:
    id: { type: string }
    name: { type: string }
    type: { type: string, enum: [SAML, OIDC, LDAP, OAUTH2] }
    config: { type: object, description: "IdP 配置（证书已脱敏）" }
    mapping: { type: object, description: "属性映射" }
    autoProvision: { type: boolean }
    defaultRoleId: { type: string, nullable: true }
    enabled: { type: boolean }
    lastSyncAt: { type: string, format: date-time, nullable: true }
```

#### SAML 流程

```yaml
# 1. 浏览器 → /auth/sso/initiate?system=saml&providerId=sp-001
# 2. 返回 302 → IdP SSO URL（带 SAMLRequest）
# 3. IdP 鉴权后回调 /auth/sso/callback 带 SAMLResponse
POST /auth/sso/callback
Request: { SAMLResponse: "base64...", RelayState: "..." }
Response 302:
  Set-Cookie: access_token=...; refresh_token=...
  Location: /sso-landing
```

#### LDAP 同步

```yaml
POST /sso/providers/sp-001/sync
Request:
  mode: FULL | INCREMENTAL
  batchSize: 500
Response 202:
  { "taskId": "task-001", "status": "RUNNING" }
```

### A2.11.4 角色与权限

| 方法 | 路径 |
| --- | --- |
| GET | `/roles` |
| POST | `/roles` |
| GET | `/roles/{id}` |
| PATCH | `/roles/{id}` |
| DELETE | `/roles/{id}` |
| POST | `/roles/{id}/clone` |
| GET | `/roles/{id}/permissions` |
| PATCH | `/roles/{id}/permissions` |
| GET | `/permissions` | 权限点字典
| GET | `/users/{id}/roles` | 用户的角色
| POST | `/users/{id}/roles` | 授予
| DELETE | `/users/{id}/roles/{roleId}` | 撤销

```yaml
Role:
  type: object
  properties:
    id: { type: string }
    code: { type: string }
    name: { type: string }
    category: { type: string, enum: [SYSTEM, PMO, PROJECT, FUNCTION] }
    permissions: { type: array, items: { $ref: '#/components/schemas/Permission' } }
    isBuiltIn: { type: boolean }
    status: { type: string, enum: [ENABLED, DISABLED] }

Permission:
  type: object
  properties:
    code: { type: string, example: "project.create" }
    name: { type: string }
    resource: { type: string, example: "project" }
    action: { type: string, example: "create" }
    scope: { type: string, enum: [GLOBAL, ORG, PROJECT, SELF] }
    conditions: { type: object, nullable: true }
```

#### 权限点命名

格式：`{resource}.{action}`，如：

```
project.create
project.read
project.update
project.delete
project.member.assign
workitem.create
workitem.transition
workitem.bulk_update
risk.escalate
risk.close
change.approve
budget.read
budget.approve
budget.adjust
timeentry.submit
timeentry.approve
document.publish
workflow.approve
audit.read
report.export
```

### A2.11.5 字典

| 方法 | 路径 |
| --- | --- |
| GET | `/dictionaries` |
| POST | `/dictionaries` |
| GET | `/dictionaries/{code}` |
| PATCH | `/dictionaries/{code}` |
| DELETE | `/dictionaries/{code}` |
| GET | `/dictionaries/{code}/items` |
| POST | `/dictionaries/{code}/items` |
| PATCH | `/dictionaries/{code}/items/{itemId}` |
| DELETE | `/dictionaries/{code}/items/{itemId}` |
| POST | `/dictionaries/{code}/reorder` |

### A2.11.6 模板

| 方法 | 路径 |
| --- | --- |
| GET | `/project-templates` |
| POST | `/project-templates` |
| GET | `/project-templates/{id}` |
| PATCH | `/project-templates/{id}` |
| DELETE | `/project-templates/{id}` |
| POST | `/project-templates/{id}/clone` |
| POST | `/projects/from-template` | 从模板建项目

```yaml
ProjectTemplate:
  type: object
  properties:
    id: { type: string }
    name: { type: string }
    category: { type: string }
    industry: { type: string, nullable: true }
    wbsTemplate: { type: object }
    milestoneTemplate: { type: object }
    riskTemplate: { type: object }
    budgetTemplate: { type: object }
    documentTemplateIds: { type: array, items: { type: string } }
    roleTemplate: { type: object }
    workflowCodes: { type: array, items: { type: string } }
    estimatedDurationDays: { type: integer, nullable: true }
    version: { type: string }
    isActive: { type: boolean }
```

### A2.11.7 自定义对象 / 字段

| 方法 | 路径 |
| --- | --- |
| GET | `/custom-field-defs` |
| POST | `/custom-field-defs` |
| PATCH | `/custom-field-defs/{id}` |
| DELETE | `/custom-field-defs/{id}` |
| GET | `/custom-field-defs/{targetType}` | 按对象类型列出
| GET | `/entities/{type}/{id}/custom-fields` | 取对象自定义值
| PUT | `/entities/{type}/{id}/custom-fields` | 一次性更新

### A2.11.8 OpenAPI 元端点

| 方法 | 路径 |
| --- | --- |
| GET | `/openapi.json` | 规范导出 |
| GET | `/openapi.yaml` | YAML 格式 |
| GET | `/api/health` | 健康检查 |
| GET | `/api/version` | 版本 |
| GET | `/api/status` | 组件状态 |
| GET | `/api/metrics` | Prometheus 指标（内部） |

### A2.11.9 业务规则

- **租户隔离**：所有 API 强校验 `tenantId`，跨租户访问返回 403；
- **Token 寿命**：access 1h，refresh 30d，refresh 滚动续期；
- **MFA 强制**：管理员/PMO 角色首次登录必须启用 MFA；
- **会话并发**：同账号最多 5 个活跃会话，新登录踢出最旧；
- **密码策略**：≥ 10 字符，含大小写+数字+特殊，90 天强制更换（管理员可调）；
- **登录失败**：5 次锁定 30 分钟；
- **SSO 自动开通**：`autoProvision=true` 时，未存在的用户按映射自动创建，分配 `defaultRoleId`；
- **角色继承**：用户继承其角色的所有权限点，权限点求并集；冲突以"更严格"为准（如审计员不能被赋予写权限）；
- **权限缓存**：用户权限变更后 60s 内全局失效；
- **字典缓存**：字典项 TTL 10min，写操作即时失效；
- **配额超限**：返回 429 + 错误码 `PMS_QUOTA_EXCEEDED`。

---

**Part8 完成。剩余：**
- **Part9-INDEX**：A2 全集索引 + 错误码全量清单
- **A3-UI Part1+2**
- **A4-迁移 Part1+2**
- **A5-上线 Part1+2**
- **INDEX 总索引**

按计划继续下一步。是否继续 Part9（API 索引 + 错误码全量）？还是切换到 A3-UI Part1？
