# A1 数据字典 Part5 — 文档、交付物、知识库

> 本节覆盖 A1.6 文档/知识域全部表。

## A1.6 文档、交付物、知识库域

### A1.6.1 document_folders（文档目录）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| project_id | Ref | ✅ | |
| parent_id | Ref | — | 自引用 |
| name | S(200) | ✅ | |
| path | S(1000) | ✅ | 物化路径：`/project/phase/doc/` |
| sort | I | ✅ | |
| owner_id | Ref | ✅ | |
| permissions | J | — | 文件夹级权限覆盖 |

**唯一**：`uniq_folder_path (project_id, path)`。

### A1.6.2 documents（文档主表）

| 字段 | 类型 | 必填 | 枚举/约束 | 说明 |
| --- | --- | --- | --- | --- |
| id | S(32) | ✅ | PK | |
| project_id | Ref | ✅ | | |
| folder_id | Ref | ✅ | FK→document_folders | |
| title | S(500) | ✅ | | |
| type | E | ✅ | REQUIREMENT, DESIGN, PLAN, REPORT, MINUTES, CONTRACT, TEMPLATE, SPEC, OTHER | |
| code | S(50) | — | | 文档编号 |
| current_version_id | Ref | — | FK→document_versions | 当前版本 |
| status | E | ✅ | DRAFT, IN_REVIEW, APPROVED, PUBLISHED, ARCHIVED, OBSOLETE | |
| secret_level | E | ✅ | PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED | |
| owner_id | Ref | ✅ | |
| tags | Arr<S> | — | |
| require_read_confirm | B | ✅ | false | 强制回执 |
| watermark | B | ✅ | false | |
| disable_download | B | ✅ | false | |
| external_link | S(500) | — | 外链 |
| ocr_status | E | — | PENDING, DONE, FAILED | OCR 索引 |
| classification | J | — | 自动分类结果 |
| is_template | B | ✅ | false | |
| template_id | Ref | — | FK→documents | 模板来源 |
| size_bytes | L | ✅ | 最新版本大小 |
| last_modified_at | DT | ✅ | |

**索引**：
- `idx_doc_project_type (project_id, type)`
- `idx_doc_status (status)`
- `idx_doc_modified (project_id, last_modified_at DESC)`
- `idx_doc_folder (folder_id)`

### A1.6.3 document_versions（文档版本）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| document_id | Ref | ✅ | |
| version_major | I | ✅ | 主版本 |
| version_minor | I | ✅ | 次版本 |
| version_label | S(20) | ✅ | 如 1.3 |
| storage_url | S(500) | ✅ | OSS path |
| size_bytes | L | ✅ | |
| sha256 | S(64) | ✅ | |
| mime | S(100) | ✅ | |
| change_summary | Txt | — | |
| author_id | Ref | ✅ | |
| status | E | ✅ | DRAFT, IN_REVIEW, APPROVED, REJECTED, PUBLISHED |
| approver_id | Ref | — | |
| approved_at | DT | — | |
| published_at | DT | — | |
| locked | B | ✅ | false 锁定后不可改 |
| diff_from_prev | J | — | 差异（可选） |

**唯一**：`uniq_dv (document_id, version_major, version_minor)`。

### A1.6.4 document_reviews（文档评审）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| document_id | Ref | ✅ | |
| version_id | Ref | ✅ | |
| status | E | ✅ | OPEN, APPROVED, REJECTED, WITHDRAWN |
| reviewer_ids | Arr<Ref> | ✅ | |
| workflow_instance_id | Ref | — | |
| deadline | DT | — | |
| closed_at | DT | — | |

### A1.6.5 document_read_receipts（阅读回执）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| document_id | Ref | ✅ | |
| version_id | Ref | ✅ | |
| user_id | Ref | ✅ | |
| first_read_at | DT | ✅ | |
| last_read_at | DT | ✅ | |
| read_count | I | ✅ | 1 |
| acknowledged | B | ✅ | false（强回执时为 true） |
| acknowledged_at | DT | — | |

**唯一**：`uniq_drr (document_id, version_id, user_id)`。

### A1.6.6 knowledge_entries（知识库条目）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| title | S(500) | ✅ | |
| content | Txt | ✅ | |
| content_html | Txt | — | |
| category | E | ✅ | LESSON, BEST_PRACTICE, FAQ, SOLUTION, PROCESS, GLOSSARY, OTHER |
| tags | Arr<S> | ✅ | |
| project_id | Ref | — | 来源项目 |
| source_type | E | — | DOC, RETRO, ISSUE_RESOLVED, RISK_CLOSED, REVIEW, MANUAL |
| source_id | S(32) | — | |
| author_id | Ref | ✅ | |
| status | E | ✅ | DRAFT, PUBLISHED, DEPRECATED |
| view_count | I | ✅ | 0 |
| like_count | I | ✅ | 0 |
| embedding_id | S(64) | — | 向量索引 ID（AI 用） |
| quality_score | D | — | 0-5 |

**索引**：
- `idx_kb_category (category, status)`
- `idx_kb_tags (tags)`
- `idx_kb_modified (last_modified_at DESC)`

### A1.6.7 knowledge_references（知识引用关系）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| knowledge_id | Ref | ✅ | |
| ref_type | E | ✅ | PROJECT, TASK, RISK, ISSUE, DOC, REVIEW |
| ref_id | S(32) | ✅ | |
| relation | E | ✅ | SOURCE, RELATED, APPLIED, REFERENCE |

**唯一**：`uniq_kref (knowledge_id, ref_type, ref_id)`。

### A1.6.8 deliverables（交付物）

| 字段 | 类型 | 必填 | 枚举/约束 | 说明 |
| --- | --- | --- | --- | --- |
| id | S(32) | ✅ | | |
| project_id | Ref | ✅ | | |
| wbs_id | Ref | ✅ | FK→wbs_nodes | 关联 WBS 节点 |
| code | S(40) | ✅ | UNIQUE | |
| name | S(500) | ✅ | | |
| type | E | ✅ | DOCUMENT, SOFTWARE, HARDWARE, REPORT, SERVICE, OTHER | |
| description | Txt | — | | |
| spec | J | — | | 规格/SOW |
| quantity | D | ✅ | 1 | |
| unit | S(20) | — | | |
| plan_date | D | ✅ | | |
| actual_date | D | — | | |
| status | E | ✅ | NOT_STARTED, IN_PROGRESS, IN_REVIEW, APPROVED, DELIVERED, ACCEPTED, REJECTED, CANCELLED | |
| owner_id | Ref | ✅ | | |
| recipient_id | Ref | — | | 客户/干系人 |
| acceptance_form_id | Ref | — | | 验收单 |
| sign_required | B | ✅ | false | 是否需签字 |
| signed_at | DT | — | | |
| signed_file_url | S(500) | — | | |

**索引**：
- `idx_dlv_project_status (project_id, status)`
- `idx_dlv_wbs (wbs_id)`
- `idx_dlv_plan_date (plan_date, status)`

### A1.6.9 deliverable_acceptance（交付物验收单）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| deliverable_id | Ref | ✅ | |
| version_id | Ref | — | 关联文档/物料版本 |
| inspector_id | Ref | ✅ | 验收人 |
| checklist | J | ✅ | 检查项 JSON |
| result | E | ✅ | PASS, CONDITIONAL, FAIL |
| comment | Txt | — | |
| signed_at | DT | — | |
| signature_url | S(500) | — | 电子签图 |
| conditions | Txt | — | 有条件通过时必填 |

### A1.6.10 document_templates（文档模板）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | S(32) | ✅ | |
| tenant_id | S(32) | ✅ | |
| name | S(200) | ✅ | |
| category | E | ✅ | CHARTER, PLAN, REPORT, MINUTES, RISK, OTHER |
| industry | S(50) | — | |
| file_url | S(500) | ✅ | |
| fields | J | — | 模板占位符与字段映射 |
| is_active | B | ✅ | true |
| is_built_in | B | ✅ | false |

---
