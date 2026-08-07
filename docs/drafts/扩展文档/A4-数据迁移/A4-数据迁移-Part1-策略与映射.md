# A4 数据迁移方案 Part1 — 策略与映射总表

> 本 Part 覆盖：A4.1 迁移策略、A4.2 源系统清单、A4.3 数据范围、A4.4 字段映射、A4.5 清洗规则、A4.6 主数据治理。
> Part2 将提供 ETL 脚本、校验对账、异常回退、演练计划。

---

## A4.1 迁移策略

### A4.1.1 4 种迁移模式

| 模式 | 适用场景 | 优 | 劣 |
| --- | --- | --- | --- |
| **一次性全量** | 历史项目归档、字典、客户 | 简单、快 | 业务中断风险 |
| **增量同步** | 项目主数据、工时、任务 | 业务影响小 | 需对账、双写期 |
| **实时同步（CDC）** | 用户、组织、流程 | 实时性高 | 复杂度高 |
| **联邦查询** | 历史报表、ERP 财务 | 不迁移、按需查 | 性能受限 |

### A4.1.2 总体策略：双写 + 切流 + 退役

```
阶段 1 (T-12 → T-8): 试点迁移 + 双写校验
阶段 2 (T-8 → T-4):  全量迁移 + 业务切流
阶段 3 (T-4 → T-1):  并行运行 + 对账
阶段 4 (T-1 → T+4):  新系统单跑 + 老系统只读
阶段 5 (T+4 → T+12): 老系统归档退役
```

### A4.1.3 关键原则

- **业务不中断**：试点先行 + 灰度切流；
- **可回退**：每次切流前必须可回退（数据快照 + 切换脚本）；
- **可对账**：双写期每天对账，差异 ≤ 0.1% 才允许下一阶段；
- **可追溯**：所有迁移操作留痕（操作人/时间/SHA256）；
- **最小化**：只迁必要数据，垃圾数据治理后迁移；
- **不破坏主数据唯一性**：跨系统 ID 映射表是核心资产。

## A4.2 源系统清单

| 编号 | 系统 | 类型 | 数据量级 | 迁移模式 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| S-01 | 自研 Excel 立项表 | 文件 | ~200 项目 | 一次性 | P0 |
| S-02 | OA 审批系统 | 商用 | ~5000 单/月 | 增量 | P0 |
| S-03 | 自研项目跟踪 Excel | 文件 | ~150 项目 | 一次性 | P0 |
| S-04 | ERP（SAP/用友） | 商用 | 全量 | 实时（CDC）| P0 |
| S-05 | HR 系统（北森） | 商用 | 5000 用户 | 实时（CDC）| P0 |
| S-06 | ALM（自研/禅道） | 自研 | 50k 缺陷 | 一次性+增量 | P0 |
| S-07 | 文档管理（Confluence） | 商用 | 10k 文档 | 一次性 | P1 |
| S-08 | 企业邮箱 | 商用 | — | 不迁移（仅集成）| P2 |
| S-09 | 财务 NC | 商用 | 全量 | 实时（CDC）| P0 |
| S-10 | CRM | 商用 | 客户/合同 | 一次性 | P1 |
| S-11 | 旧项目库（Access） | 老旧 | 30 项目 | 一次性 | P2 |
| S-12 | 个人 PC 文件 | 散落 | 5GB | 一次性 | P3 |
| S-13 | 共享盘文档 | 文件 | 50GB | 一次性 | P1 |

## A4.3 数据范围与优先级

| 类别 | 实体 | 数量级 | 优先级 |
| --- | --- | --- | --- |
| **主数据** | 组织、用户、客户、供应商、币种 | 5 万 | P0 |
| **项目主数据** | 项目、阶段、模板 | 200-500 | P0 |
| **任务与计划** | WBS、任务、依赖 | 50 万 | P0 |
| **工时与成本** | 工时、预算、报销、采购 | 100 万 | P0 |
| **风险/变更/缺陷** | 风险、问题、变更、缺陷 | 30 万 | P0 |
| **文档** | 文档、版本、附件 | 50GB / 50k | P1 |
| **流程** | 流程定义、流程实例 | 5 万 | P1 |
| **历史报表** | 已发报表、订阅 | 5 千 | P2 |
| **日志/审计** | 操作日志、登录日志 | 千万级 | P2 |
| **附件大文件** | > 50MB 文件 | 500 个 | P3 |

## A4.4 字段映射总表（核心实体）

> 格式：源系统.表.字段 → 目标.表.字段（转换规则）
> 完整映射表见附表《A4-FieldMapping-v1.xlsx》。

### A4.4.1 用户（users）

| 源 S-05.HR.字段 | 目标 users.字段 | 转换规则 |
| --- | --- | --- |
| employee_id | employee_no | 直接 |
| name_cn | display_name | 直接 |
| name_en | display_name_en | 直接 |
| email | email | 小写 + 校验唯一 |
| mobile | mobile | E.164 格式 + 加密存储 |
| dept_id | dept_id | 经 dept_mapping 表转换 |
| position | position_name | 通过字典转换 → position_id |
| status=在职 | status=ACTIVE | 枚举映射 |
| status=离职 | status=OFFBOARDED + leave_date | 枚举映射 + 日期 |

### A4.4.2 组织（departments）

| 源 S-05.HR.字段 | 目标 departments.字段 | 转换规则 |
| --- | --- | --- |
| dept_code | code | 直接 |
| dept_name | name | 直接 |
| parent_dept_code | parent_id | 通过 path 物化 |
| cost_center | cost_center | 直接 |
| leader_employee_id | leader_id | 经 employee_mapping 转换 |
| is_virtual | type=VIRTUAL | 枚举映射 |

### A4.4.3 项目（projects）

| 源 S-01.Excel.字段 | 目标 projects.字段 | 转换规则 |
| --- | --- | --- |
| 项目编号 | code | 加年份前缀 |
| 项目名称 | name | 直接 |
| 项目类型 | type | 字典：研发/基建/营销/... |
| 业务部门 | business_unit_id | 经 dept_mapping 转换 |
| 项目经理 | pm_id | 经 employee_mapping 转换 |
| 计划开始 | start_date | YYYY-MM-DD |
| 计划结束 | end_date | YYYY-MM-DD |
| 预算(万元) | total_budget | × 10000 + 币种 CNY |
| 状态 | status | 字典：草稿/执行/结项 → DRAFT/ACTIVE/CLOSED |
| 立项日期 | created_at | 直接 |
| — | secret_level=INTERNAL | 默认值 |
| — | health_score=null | 默认值 |

### A4.4.4 任务（work_items）

| 源 S-03.Excel.字段 | 目标 work_items.字段 | 转换规则 |
| --- | --- | --- |
| 任务编号 | key | 加项目前缀 |
| 标题 | title | 直接 |
| 类型 | type | TASK（默认） |
| 负责人 | assignee_id | employee_mapping |
| 计划开始 | plan_start | YYYY-MM-DD |
| 计划结束 | due_date | YYYY-MM-DD |
| 实际完成 | actual_end | YYYY-MM-DD（如果有） |
| 状态 | status | 字典：未开始/进行中/已完成 → BACKLOG/IN_PROGRESS/DONE |
| 优先级 | priority | 字典：高/中/低 → P0/P1/P2 |
| 预估工时 | estimate_hours | 直接 |
| 实际工时 | actual_hours | SUM(time_entries) 优先 |
| 上级任务 | parent_id | 任务编号 → UUID 映射 |
| 标签 | labels | 用 ; 分隔 → array |

### A4.4.5 工时（time_entries）

| 源 S-02.OA.字段 | 目标 time_entries.字段 | 转换规则 |
| --- | --- | --- |
| 申请人 | user_id | employee_mapping |
| 填报日期 | work_date | YYYY-MM-DD |
| 项目编号 | project_id | project_mapping |
| 任务编号 | work_item_id | task_mapping（可空） |
| 工时(小时) | hours | 直接 |
| 工作内容 | description | 直接 |
| 审批人 | approver_id | employee_mapping |
| 状态 | status | 已审批 → APPROVED |
| 提交时间 | created_at | ISO datetime |

### A4.4.6 风险（risks）

| 源 S-03.Excel.字段 | 目标 risks.字段 | 转换规则 |
| --- | --- | --- |
| 风险编号 | code | 加项目前缀 |
| 描述 | title | 截断 200 字符 |
| 类别 | category | 字典 |
| 概率 P | probability | 1-5 |
| 影响 I | impact | 1-5 |
| 责任人 | owner_id | employee_mapping |
| 策略 | strategy | 字典：避免/减轻/转移/接受 → AVOID/MITIGATE/TRANSFER/ACCEPT |
| 状态 | status | 已关闭 → CLOSED |

### A4.4.7 缺陷（defects）

| 源 S-06.ALM.字段 | 目标 defects.字段 | 转换规则 |
| --- | --- | --- |
| bug_id | code + external_id | 直接 |
| title | title | 直接 |
| severity | severity | 字典：紧急/高/中/低 → S1/S2/S3/S4 |
| priority | priority | 字典 |
| status | status | 字典 |
| reporter | reporter_id | employee_mapping |
| assignee | assignee_id | employee_mapping |
| fix_version | fixed_version | 直接 |
| created | created_at | ISO datetime |
| source=ALM_SYNC | source=ALM_SYNC, alm_system="zhendao" | 标识来源 |

### A4.4.8 文档（documents）

| 源 S-07.Confluence.字段 | 目标 documents.字段 | 转换规则 |
| --- | --- | --- |
| page_id | external_id | 保留 |
| title | title | 直接 |
| space_key | project_code | 反查 project_mapping |
| content | content + content_html | 双存 |
| version | version_major/minor | 拆字段 |
| updated | last_modified_at | ISO datetime |
| author | author_id | employee_mapping |
| — | type=OTHER | 分类由文本推断 |
| 附件 | storage_url | 下载 → OSS |

### A4.4.9 预算（budgets）

| 源 S-09.NC.字段 | 目标 budgets.字段 | 转换规则 |
| --- | --- | --- |
| budget_no | code | 直接 |
| project_no | project_id | project_mapping |
| category_code | category | 字典：人力/材料/费用 → LABOR/OTHER/... |
| plan_amount | planned_amount | 直接 |
| actual_amount | actual_amount | 直接 |
| control_type | control_strategy | HARD/SOFT |

## A4.5 清洗规则

### A4.5.1 数据质量维度

| 维度 | 检查项 | 不达标处理 |
| --- | --- | --- |
| 完整性 | 必填字段非空 | 报告 + 退回到源系统负责人 |
| 准确性 | 格式校验（邮箱/手机/日期）| 自动修正 + 报告 |
| 一致性 | 跨表外键引用 | 孤儿记录 → 待人工处理 |
| 唯一性 | 唯一键冲突 | 合并 / 加后缀 |
| 时效性 | 截止日期 vs 创建日期 | 异常 → 报告 |
| 规范性 | 命名/编码规范 | 自动转换 + 报告 |

### A4.5.2 脱敏规则

| 字段 | 脱敏方式 | 还原 |
| --- | --- | --- |
| 身份证号 | SHA256 + 末 4 位 | 仅审计/管理员可还原 |
| 手机号 | 中间 4 位 *** | 仅本人/管理员可看全 |
| 银行账号 | AES-256 加密 | 仅财务 |
| 邮箱 | 全保留 | — |
| 姓名 | 全保留 | — |
| 工资 | 部门经理可见，其他不可见 | — |

### A4.5.3 标准化规则

| 类型 | 规则 |
| --- | --- |
| 日期 | ISO 8601 (YYYY-MM-DD) |
| 时间 | ISO 8601 (YYYY-MM-DDTHH:mm:ssZ) |
| 姓名 | 去空格、统一繁简 |
| 手机 | E.164 (+86138...) |
| 邮箱 | 全小写 |
| 地址 | 行政区划编码（GB/T 2260） |
| 编码 | 去除特殊字符（只保留 [A-Za-z0-9_-]） |
| 数值 | decimal(18,4)，币种 ISO 4217 |
| 文本 | 统一换行符 \n，去 BOM |

### A4.5.4 去重策略

- **用户**：以 `email` 为主键，`mobile` 辅；
- **项目**：以 `code + year` 为主键；
- **客户/供应商**：以 `tax_no`（税号）为主键；
- **冲突处理**：保留最近 updated_at，备份被合并记录到 `merged_records` 表。

### A4.5.5 校验规则（示例）

```yaml
- table: users
  rule: email_format
  check: email ~ '^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$'
  on_fail: report

- table: time_entries
  rule: hours_range
  check: 0.25 <= hours <= 24
  on_fail: reject

- table: projects
  rule: date_order
  check: end_date > start_date
  on_fail: reject
```

## A4.6 主数据治理

### A4.6.1 主数据范围

| 主数据 | 黄金记录来源 | 同步方向 |
| --- | --- | --- |
| 客户 | CRM → PMS | 拉取 |
| 供应商 | ERP → PMS | 拉取 |
| 币种/汇率 | 财务 → 全局 | 推送 |
| 组织/用户/岗位 | HR → PMS | 推送 |
| 项目模板 | PMO → 各项目 | 拉取 |
| 字典 | PMS 内置 | — |

### A4.6.2 ID 映射表（关键资产）

| 映射表 | 字段 | 用途 |
| --- | --- | --- |
| `id_map_user` | legacy_user_id ↔ user_uuid | 用户 |
| `id_map_project` | legacy_project_no ↔ project_uuid | 项目 |
| `id_map_task` | legacy_task_no ↔ work_item_uuid | 任务 |
| `id_map_dept` | legacy_dept_code ↔ department_uuid | 部门 |
| `id_map_vendor` | legacy_vendor_code ↔ vendor_uuid | 供应商 |
| `id_map_doc` | confluence_page_id ↔ document_uuid | 文档 |

> 映射表**永久保留**，供后续追溯 + 退役后查询。

### A4.6.3 主数据冲突解决矩阵

| 冲突类型 | 解决策略 |
| --- | --- |
| 同一实体多源（HR vs CRM） | 优先级：HR > CRM > Excel |
| 同一实体多版本 | 取最新 updated_at + 字段级合并 |
| 字段值不一致 | 记录差异，告警 PMO 经理确认 |
| 编码规则不一致 | 统一为目标系统编码 |

### A4.6.4 数据血缘

- 字段级血缘：源系统.表.字段 → 转换 → 目标.表.字段；
- 任务级血缘：ETL 任务 ID → 输入表 → 输出表；
- 报告级血缘：报表 → 数据集 → 字段 → 源表；
- 工具：OpenLineage / DataHub / 自研。

---

**Part1 完成。Part2 将覆盖：ETL 脚本、校验对账、异常回退、演练计划。**

是否继续 Part2？
