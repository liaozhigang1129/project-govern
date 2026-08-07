# A2 OpenAPI 3.0 规范 Part4 — 文档/交付物/知识库

> 本 Part 覆盖 A2.7 文档域端点。

---

## A2.7 文档、交付物、知识库域

### A2.7.1 文档（Document）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/projects/{id}/document-folders` | 目录树 |
| POST | `/projects/{id}/document-folders` | 新建目录 |
| PATCH | `/document-folders/{id}` | 更新目录 |
| DELETE | `/document-folders/{id}` | 删除目录 |
| GET | `/projects/{id}/documents` | 文档列表（支持 ?type=&status=） |
| POST | `/projects/{id}/documents` | 新建文档 |
| GET | `/documents/{id}` | 详情 |
| PATCH | `/documents/{id}` | 更新元数据 |
| DELETE | `/documents/{id}` | 归档 |
| POST | `/documents/{id}/move` | 移动到目录 |
| POST | `/documents/{id}/clone` | 复制 |
| GET | `/documents/{id}/versions` | 版本列表 |
| POST | `/documents/{id}/versions` | 上传新版本（multipart） |
| GET | `/document-versions/{id}` | 版本详情 |
| GET | `/document-versions/{id}/download?inline=true` | 下载 |
| POST | `/document-versions/{id}/submit` | 提交评审 |
| POST | `/document-versions/{id}/approve` | 审批通过 |
| POST | `/document-versions/{id}/reject` | 驳回 |
| POST | `/document-versions/{id}/publish` | 发布 |
| POST | `/document-versions/{id}/withdraw` | 撤回 |
| GET | `/document-versions/{id}/diff?againstId=...` | 版本对比 |
| POST | `/documents/{id}/read-confirm` | 阅读回执（acknowledge） |
| GET | `/documents/{id}/read-receipts` | 回执状态 |
| GET | `/document-templates` | 模板库 |
| POST | `/document-templates` | 新建模板 |
| GET | `/documents/search?q=...` | 全文搜索 |

#### 关键 Schema

```yaml
Document:
  type: object
  properties:
    id: { type: string }
    projectId: { type: string }
    folderId: { type: string }
    title: { type: string, maxLength: 500 }
    type: { type: string, enum: [REQUIREMENT, DESIGN, PLAN, REPORT, MINUTES, CONTRACT, TEMPLATE, SPEC, OTHER] }
    code: { type: string }
    currentVersionId: { type: string }
    status: { type: string, enum: [DRAFT, IN_REVIEW, APPROVED, PUBLISHED, ARCHIVED, OBSOLETE] }
    secretLevel: { type: string, enum: [PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED] }
    ownerId: { type: string }
    tags: { type: array, items: { type: string } }
    requireReadConfirm: { type: boolean }
    watermark: { type: boolean }
    disableDownload: { type: boolean }
    sizeBytes: { type: integer, format: int64 }
    lastModifiedAt: { type: string, format: date-time }
    isTemplate: { type: boolean }

DocumentVersion:
  type: object
  properties:
    id: { type: string }
    documentId: { type: string }
    versionLabel: { type: string, example: "1.3" }
    versionMajor: { type: integer }
    versionMinor: { type: integer }
    storageUrl: { type: string }
    sizeBytes: { type: integer, format: int64 }
    sha256: { type: string }
    mime: { type: string }
    changeSummary: { type: string }
    authorId: { type: string }
    status: { type: string, enum: [DRAFT, IN_REVIEW, APPROVED, REJECTED, PUBLISHED] }
    approverId: { type: string, nullable: true }
    publishedAt: { type: string, format: date-time, nullable: true }
    locked: { type: boolean }
```

#### 关键示例

**上传新版本（multipart）**
```yaml
POST /documents/doc-001/versions
Content-Type: multipart/form-data
Fields:
  file: <binary>
  changeSummary: "更新 v1.3 接口字段"
  isMajor: false
Response 201:
  DocumentVersion { id: dv-005, versionLabel: "1.3", status: DRAFT }
```

**版本对比**
```yaml
GET /document-versions/dv-005/diff?againstId=dv-004
Response 200:
  {
    "fromVersion": "1.2", "toVersion": "1.3",
    "stats": { "additions": 42, "deletions": 8, "modifications": 15 },
    "diffUrl": "/diff-storage/dv005-vs-dv004.html"
  }
```

**阅读回执（强回执）**
```yaml
POST /documents/doc-001/read-confirm
Request:
  versionId: dv-005
  acknowledged: true
Response 200:
  DocumentReadReceipt { userId: u-301, acknowledgedAt: "2025-04-15T10:00:00Z" }
```

**全文搜索**
```yaml
GET /documents/search?q=项目章程&projectId=p-001&type=PLAN&size=20
Response 200:
  {
    "items": [
      { "id": "doc-001", "title": "...", "highlights": "<em>项目章程</em>...", "score": 8.7 }
    ],
    "total": 23
  }
```

---

### A2.7.2 交付物（Deliverable）

| 方法 | 路径 |
| --- | --- |
| GET | `/projects/{id}/deliverables` |
| POST | `/projects/{id}/deliverables` |
| GET | `/deliverables/{id}` |
| PATCH | `/deliverables/{id}` |
| DELETE | `/deliverables/{id}` |
| POST | `/deliverables/{id}/submit` |
| POST | `/deliverables/{id}/deliver` |
| POST | `/deliverables/{id}/accept` |
| POST | `/deliverables/{id}/reject` |
| POST | `/deliverables/{id}/sign` | 电子签
| GET | `/deliverables/{id}/acceptance` | 验收单
| POST | `/deliverables/{id}/acceptance` | 创建验收单

```yaml
Deliverable:
  type: object
  properties:
    id: { type: string }
    projectId: { type: string }
    wbsId: { type: string }
    code: { type: string }
    name: { type: string, maxLength: 500 }
    type: { type: string, enum: [DOCUMENT, SOFTWARE, HARDWARE, REPORT, SERVICE, OTHER] }
    quantity: { type: number }
    unit: { type: string }
    planDate: { type: string, format: date }
    actualDate: { type: string, format: date, nullable: true }
    status: { type: string, enum: [NOT_STARTED, IN_PROGRESS, IN_REVIEW, APPROVED, DELIVERED, ACCEPTED, REJECTED, CANCELLED] }
    ownerId: { type: string }
    recipientId: { type: string, nullable: true }
    signRequired: { type: boolean }
    signedAt: { type: string, format: date-time, nullable: true }

DeliverableAcceptance:
  type: object
  properties:
    deliverableId: { type: string }
    inspectorId: { type: string }
    checklist: { type: array, items: { type: object } }
    result: { type: string, enum: [PASS, CONDITIONAL, FAIL] }
    comment: { type: string }
    conditions: { type: string, nullable: true }
    signedAt: { type: string, format: date-time, nullable: true }
    signatureUrl: { type: string, nullable: true }
```

---

### A2.7.3 知识库（Knowledge）

| 方法 | 路径 |
| --- | --- |
| GET | `/knowledge` |
| POST | `/knowledge` |
| GET | `/knowledge/{id}` |
| PATCH | `/knowledge/{id}` |
| DELETE | `/knowledge/{id}` |
| POST | `/knowledge/{id}/publish` |
| POST | `/knowledge/{id}/deprecate` |
| POST | `/knowledge/{id}/like` |
| POST | `/knowledge/{id}/view` | 埋点
| GET | `/knowledge/{id}/references` |
| POST | `/knowledge/{id}/references` |
| GET | `/knowledge/search?q=...&category=...` |
| GET | `/knowledge/recommend?contextType=...&contextId=...` | AI 上下文推荐

```yaml
KnowledgeEntry:
  type: object
  properties:
    id: { type: string }
    title: { type: string, maxLength: 500 }
    content: { type: string }
    contentHtml: { type: string }
    category: { type: string, enum: [LESSON, BEST_PRACTICE, FAQ, SOLUTION, PROCESS, GLOSSARY, OTHER] }
    tags: { type: array, items: { type: string } }
    projectId: { type: string, nullable: true }
    sourceType: { type: string, enum: [DOC, RETRO, ISSUE_RESOLVED, RISK_CLOSED, REVIEW, MANUAL] }
    authorId: { type: string }
    status: { type: string, enum: [DRAFT, PUBLISHED, DEPRECATED] }
    viewCount: { type: integer }
    likeCount: { type: integer }
    qualityScore: { type: number, minimum: 0, maximum: 5 }
```

#### AI 推荐（上下文感知）

```yaml
GET /knowledge/recommend?contextType=RISK&contextId=r-001&limit=5
Response 200:
  {
    "items": [
      {
        "id": "k-101",
        "title": "核心研发人员离职风险应对 SOP",
        "score": 0.92,
        "snippet": "建立 AB 角机制、知识沉淀于知识库..."
      }
    ]
  }
```

---

### A2.7.4 业务规则

- `secretLevel = RESTRICTED` 的文档自动开启 `disableDownload=true`；
- `requireReadConfirm=true` 时，未确认用户对文档的"标记已读"操作必须返回 422；
- 版本发布后旧版本不可修改（只读），需修改需"创建新版本"；
- 知识库条目去重：标题 + 标签 + 内容指纹 SHA256；
- 知识库全文搜索基于 Elasticsearch，向量检索基于 Milvus/Qdrant（AI 用）。

---

**Part4 完成。下一步 Part5：流程引擎与通知。**
