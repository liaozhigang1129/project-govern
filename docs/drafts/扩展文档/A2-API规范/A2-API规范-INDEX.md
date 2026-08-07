# A2 OpenAPI 3.0 规范 Part9 — 总索引 + 错误码全量 + 附录

> 本 Part 为 A2 系列收尾文档。

---

## A2.12 A2-API 规范全 Part 索引

| Part | 文件名 | 内容 |
| --- | --- | --- |
| 0 | A2-API规范-INDEX（本文） | 索引 + 错误码全量 |
| 1 | A2-API规范-Part1-项目任务资源域.md | 顶层约定 / 项目 / 任务 / 资源 |
| 2 | A2-API规范-Part2-成本工时采购域.md | 预算 / 工时 / 报销 / 采购 / EVM |
| 3 | A2-API规范-Part3-风险变更缺陷评审审计域.md | 风险 / 问题 / 变更 / 缺陷 / 评审 / 审计 |
| 4 | A2-API规范-Part4-文档交付物知识域.md | 文档 / 交付物 / 知识库 |
| 5 | A2-API规范-Part5-流程引擎与通知域.md | 工作流 / 通知 / 公告 / 订阅 |
| 6 | A2-API规范-Part6-报表仪表盘统计分析.md | 仪表盘 / 数据集 / 报表 |
| 7 | A2-API规范-Part7-Webhook事件契约.md | 集成 / Webhook / 事件清单 |
| 8 | A2-API规范-Part8-系统管理与认证授权.md | 租户 / SSO / 角色 / 字典 / 模板 |

## A2.13 端点数量统计

| 域 | 端点数（估） |
| --- | --- |
| A2.1 项目 | 28 |
| A2.2 工作项 | 26 |
| A2.3 资源 | 24 |
| A2.4 成本工时 | 35 |
| A2.5 风险/变更/缺陷/评审/审计 | 42 |
| A2.7 文档/交付物/知识 | 28 |
| A2.8 流程/通知 | 30 |
| A2.9 报表/仪表盘 | 16 |
| A2.10 Webhook | 12 |
| A2.11 系统/认证 | 38 |
| **合计** | **~280** |

## A2.14 错误码全量清单（PMS_ 前缀）

> 命名规范：`PMS_{DOMAIN}_{REASON}`，全大写，下划线分隔。
> 客户端可通过 `code` 字段做 i18n 映射或自动重试。

### A2.14.1 通用（COMMON）

| 错误码 | HTTP | 含义 | 可重试 |
| --- | --- | --- | --- |
| PMS_INTERNAL_ERROR | 500 | 服务内部错误 | ✅ |
| PMS_SERVICE_UNAVAILABLE | 503 | 依赖不可用 | ✅ |
| PMS_TIMEOUT | 504 | 超时 | ✅ |
| PMS_VALIDATION_FAILED | 400 | 参数校验失败 | ❌ |
| PMS_UNAUTHENTICATED | 401 | 未认证 | ❌ |
| PMS_TOKEN_EXPIRED | 401 | Token 过期 | ✅ |
| PMS_FORBIDDEN | 403 | 越权 | ❌ |
| PMS_RATE_LIMITED | 429 | 限流 | ✅ |
| PMS_QUOTA_EXCEEDED | 429 | 配额超限 | ❌ |
| PMS_IDEMPOTENT_REPLAY | 200 | 幂等重放（用上次的响应） | — |
| PMS_MAINTENANCE | 503 | 维护中 | ✅ |

### A2.14.2 资源（RESOURCE）

| 错误码 | HTTP | 含义 |
| --- | --- | --- |
| PMS_RESOURCE_NOT_FOUND | 404 | 资源不存在 |
| PMS_VERSION_CONFLICT | 409 | 乐观锁冲突（If-Match 失败） |
| PMS_DUPLICATE_KEY | 409 | 唯一键冲突 |
| PMS_REFERENCED | 409 | 资源被引用，不可删 |
| PMS_LOCKED | 423 | 资源被锁定 |

### A2.14.3 业务规则（BIZ）

| 错误码 | HTTP | 含义 |
| --- | --- | --- |
| PMS_BUSINESS_RULE_VIOLATED | 422 | 业务规则违反 |
| PMS_STATE_INVALID | 422 | 状态机非法流转 |
| PMS_PERMISSION_DENIED_FIELD | 403 | 字段级权限不足 |
| PMS_DATA_SCOPE_DENIED | 403 | 数据域越权 |
| PMS_BUDGET_EXCEEDED | 422 | 预算超支（硬控制） |
| PMS_BUDGET_SOFT_WARN | 200 | 预算超支（软控制，仅警告） |
| PMS_WORK_HOURS_EXCEEDED | 422 | 工时超日上限 |
| PMS_TIME_LOCKED | 423 | 工时周期已锁 |
| PMS_RESOURCE_OVERLOAD | 422 | 资源过载 |
| PMS_RESOURCE_CONFLICT | 409 | 资源时间冲突 |
| PMS_RISK_ESCALATION_REQUIRED | 422 | 必须先升级 |
| PMS_CHANGE_IMPACT_REQUIRED | 422 | 缺少影响分析 |
| PMS_GATE_NOT_PASSED | 422 | 阶段门未通过 |
| PMS_DOC_READ_CONFIRM_REQUIRED | 422 | 文档需强回执 |
| PMS_DOC_LOCKED | 423 | 文档已发布，不可改 |
| PMS_KB_DUPLICATE | 409 | 知识库条目重复 |
| PMS_DELIVERABLE_NOT_READY | 422 | 交付物未达交付条件 |
| PMS_SIGNATURE_REQUIRED | 422 | 需电子签 |

### A2.14.4 流程（WF）

| 错误码 | HTTP | 含义 |
| --- | --- | --- |
| PMS_WF_NOT_FOUND | 404 | 流程定义不存在 |
| PMS_WF_ALREADY_RUNNING | 409 | 同一 businessKey 已有运行实例 |
| PMS_WF_TASK_NOT_ASSIGNED | 403 | 当前用户非审批人 |
| PMS_WF_TASK_EXPIRED | 422 | 任务已超时/已处理 |
| PMS_WF_FORM_INVALID | 422 | 表单校验失败 |
| PMS_WF_WITHDRAW_FORBIDDEN | 403 | 不可撤回（已过环节） |
| PMS_WF_TERMINATE_FORBIDDEN | 403 | 不可终止 |

### A2.14.5 集成（INT）

| 错误码 | HTTP | 含义 |
| --- | --- | --- |
| PMS_INT_CONNECT_FAILED | 502 | 外部系统连接失败 |
| PMS_INT_AUTH_FAILED | 401 | 外部认证失败 |
| PMS_INT_TIMEOUT | 504 | 外部超时 |
| PMS_INT_RATE_LIMITED | 429 | 外部限流 |
| PMS_WEBHOOK_INVALID_URL | 422 | URL 非法（非 HTTPS/内网） |
| PMS_WEBHOOK_SIGN_INVALID | 401 | 签名校验失败 |
| PMS_WEBHOOK_DELIVERY_EXPIRED | 410 | 投递已过期 |

### A2.14.6 上传 / 附件（FILE）

| 错误码 | HTTP | 含义 |
| --- | --- | --- |
| PMS_FILE_TOO_LARGE | 413 | 文件超过大小限制 |
| PMS_FILE_TYPE_DENIED | 415 | 文件类型被拒 |
| PMS_VIRUS_DETECTED | 422 | 病毒扫描未通过 |
| PMS_UPLOAD_FAILED | 500 | 上传失败 |

### A2.14.7 审计（AUDIT）

| 错误码 | HTTP | 含义 |
| --- | --- | --- |
| PMS_AUDIT_LOG_TAMPERED | 500 | 日志哈希链校验失败 |
| PMS_SENSITIVE_OP_REASON_REQUIRED | 422 | 敏感操作必填原因 |

### A2.14.8 报表 / 数据（DATA）

| 错误码 | HTTP | 含义 |
| --- | --- | --- |
| PMS_QUERY_TIMEOUT | 504 | 查询超时 |
| PMS_RESULT_TOO_LARGE | 422 | 结果集超限 |
| PMS_FORMULA_INVALID | 422 | 自定义公式非法 |
| PMS_DIMENSION_NOT_ALLOWED | 403 | 维度不允许查询 |

## A2.15 OpenAPI Tag 命名（与文档分章一致）

```yaml
tags:
  - { name: "Project" }
  - { name: "WorkItem" }
  - { name: "Resource" }
  - { name: "Budget" }
  - { name: "TimeEntry" }
  - { name: "Expense" }
  - { name: "Procurement" }
  - { name: "EVM" }
  - { name: "Risk" }
  - { name: "Issue" }
  - { name: "Change" }
  - { name: "Defect" }
  - { name: "Review" }
  - { name: "Audit" }
  - { name: "Document" }
  - { name: "Deliverable" }
  - { name: "Knowledge" }
  - { name: "Workflow" }
  - { name: "Notification" }
  - { name: "Announcement" }
  - { name: "Subscription" }
  - { name: "Dashboard" }
  - { name: "Report" }
  - { name: "Dataset" }
  - { name: "Webhook" }
  - { name: "Integration" }
  - { name: "Auth" }
  - { name: "SSO" }
  - { name: "Role" }
  - { name: "Permission" }
  - { name: "Tenant" }
  - { name: "Dictionary" }
  - { name: "Template" }
  - { name: "CustomField" }
  - { name: "System" }
```

## A2.16 关键术语与缩写

| 术语 | 含义 |
| --- | --- |
| ETag | 资源的乐观锁标识 |
| HATEOAS | Hypermedia as the Engine of Application State（v1.0 不启用） |
| Bulk | 批量端点（多子操作，单次响应） |
| Upsert | POST 同时支持 create/update |
| Soft Delete | 软删（is_deleted=true） |
| Hard Delete | 硬删（仅合规清除场景，需审批） |
| Idempotency-Key | 幂等键，24h 内同 key 复用结果 |
| Cursor | 游标分页（深分页场景） |
| Page | 偏移分页（管理类列表） |
| JQL | JSON Query Language（自定义查询语法） |

## A2.17 客户端集成建议

### 2.17.1 推荐 SDK

| 语言 | 包名 | 维护方 |
| --- | --- | --- |
| TypeScript | `@pmo-pms/sdk-js` | 官方 |
| Java | `com.pmo-pms:sdk-java` | 官方 |
| Python | `pmo-pms-sdk` | 官方 |
| Go | `github.com/pmo-pms/sdk-go` | 官方 |

### 2.17.2 错误处理模板

```typescript
try {
  const res = await pms.projects.create(payload);
  return res;
} catch (e) {
  switch (e.code) {
    case 'PMS_TOKEN_EXPIRED':
      await refreshToken();
      return retry();
    case 'PMS_RATE_LIMITED':
      await sleep(e.retryAfter);
      return retry();
    case 'PMS_VERSION_CONFLICT':
      // 重新拉取 + 合并
      return mergeAndRetry();
    case 'PMS_BUDGET_EXCEEDED':
      // 提示用户调整
      throw new UserFacingError('预算不足');
    default:
      throw e;
  }
}
```

## A2.18 性能与限流承诺（API 层）

| 指标 | 目标 |
| --- | --- |
| P95 响应 | ≤ 500ms |
| P99 响应 | ≤ 1.5s |
| 单租户 QPS | 1,000（突发 2,000） |
| 总 QPS | 10,000（集群） |
| Webhook 投递 P95 | ≤ 2s |
| 长任务（报表/迁移） | 异步任务模式 + Webhook 回调 |

---

## A2.19 与 SRS 章节对应

| SRS 章节 | A2 Part |
| --- | --- |
| §3 项目管理 | Part1（A2.1）+ Part3（A2.5.3 变更） |
| §4 任务与工作项 | Part1（A2.2） |
| §5 资源与团队 | Part1（A2.3） |
| §6 成本/预算/工时 | Part2（A2.4） |
| §7 风险/问题/变更/缺陷 | Part3（A2.5） |
| §8 质量/评审/文档 | Part3（A2.5.5）+ Part4（A2.7） |
| §9 沟通协作 | Part5（A2.8.2/3/4） |
| §10 报表 | Part6（A2.9） |
| §11 流程引擎 | Part5（A2.8.1） |
| §12 系统管理与集成 | Part7 + Part8 |

---

**A2-API 规范 9 个 Part 全部完成（约 80KB 文本、280+ 端点、80+ 错误码）。**

下一步切换到 **A3-UI 原型 Part1（信息架构 + 组件清单）**。
