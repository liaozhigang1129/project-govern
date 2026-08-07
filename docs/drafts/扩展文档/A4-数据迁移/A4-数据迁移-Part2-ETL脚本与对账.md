# A4 数据迁移方案 Part2 — ETL 架构、脚本、对账与演练

> Part1 已完成策略与映射。本 Part 覆盖 ETL 架构、脚本、校验对账、回退、演练。

---

## A4.7 ETL 架构

### A4.7.1 总体架构图

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  源系统 13 个 │    │  Airflow     │    │  PMS 目标库  │
│  (S-01~S-13) │───▶│  + Python    │───▶│  + DLQ       │
│  + CDC       │    │  + 监控告警  │    │  + 血缘      │
└──────────────┘    └──────────────┘    └──────────────┘
       │                  │                    │
       ▼                  ▼                    ▼
   临时区/落盘         元数据                 校验对账
   (Parquet/CSV)    (id_map_*, 状态)         (每日报告)
```

### A4.7.2 技术选型

| 层 | 选型 | 备选 | 理由 |
| --- | --- | --- | --- |
| 调度 | Apache Airflow 2.x | Argo, DolphinScheduler | 成熟、生态广 |
| 计算 | Python 3.11 + Pandas + PySpark | — | 小数据 Pandas，大数据 Spark |
| 临时存储 | MinIO (S3 兼容) | HDFS, OSS | 灵活、低成本 |
| 监控 | Prometheus + Grafana | Datadog | 已有栈 |
| 告警 | Alertmanager + 飞书/钉钉 | PagerDuty | 已有 |
| 元数据 | DataHub | OpenMetadata, Atlas | 主流 |
| 血缘 | OpenLineage + Marquez | — | 标准化 |
| 校验 | Great Expectations | Soda | 成熟 |
| 日志 | ELK / EFK | Loki | 已有 |
| 密钥 | HashiCorp Vault | K8s Secret | 安全 |

### A4.7.3 Airflow 部署

- **架构**：CeleryExecutor / KubernetesExecutor；
- **多租户**：每个源系统一个 DAG Pool；
- **资源**：3 worker × 4 CPU × 8GB；
- **调度周期**：
  - 全量：DAG 一次性（手动触发 + 监控）；
  - 增量：每天 02:00-04:00；
  - CDC：每 5 分钟；
  - 实时：事件驱动（Kafka → Python consumer）。

### A4.7.4 连接管理

#### 连接池

| 源系统 | 连接方式 | 凭据来源 |
| --- | --- | --- |
| S-01/03 Excel | SFTP/SharePoint | Vault |
| S-02 OA | REST API + OAuth2 | Vault |
| S-04 ERP (SAP) | RFC (PyRFC) | Vault |
| S-05 HR | REST API + OAuth2 | Vault |
| S-06 ALM | REST API + API Key | Vault |
| S-07 Confluence | REST API + Basic | Vault |
| S-09 财务 NC | REST API + OAuth2 | Vault |
| PMS 目标库 | PostgreSQL (主) + 只读副本 | Vault |

#### Airflow Connections 示例

```python
# airflow/connections/source_oa.py
from airflow.models import Connection
conn = Connection(
    conn_id="source_oa",
    conn_type="http",
    host="oa.example.com",
    schema="https",
    login="${OA_CLIENT_ID}",       # 引用 Vault
    password="${OA_CLIENT_SECRET}",
    port=443,
    extra={"api_base": "/api/v1", "timeout": 30}
)
```

### A4.7.5 CDC（Change Data Capture）

#### 实现方式

| 源 | CDC 方式 | 工具 |
| --- | --- | --- |
| PostgreSQL (HR/ALM) | 逻辑复制槽 | Debezium |
| MySQL (CRM) | binlog | Debezium |
| Oracle (ERP) | Redo Log | OGG / Debezium Oracle |
| SQL Server (NC) | CDC 表 | Debezium |
| REST API 无 CDC | 增量轮询 + watermark | 自研 |

#### Debezium 拓扑

```
源库 ──binlog/redo──▶ Debezium Connector ──Kafka Topic──▶ Python Consumer ──PMS DB
                                                                  │
                                                                  └─▶ DLQ (失败)
```

#### Topic 设计

| Topic | Partition Key | 保留 |
| --- | --- | --- |
| `pms.cdc.s05.hr.users` | user_id | 7d |
| `pms.cdc.s05.hr.departments` | dept_id | 7d |
| `pms.cdc.s04.erp.projects` | project_id | 7d |
| `pms.cdc.s09.nc.cost_actuals` | period+project_id | 7d |

#### 幂等保证

```python
# 用 upsert + version 字段保证幂等
def upsert_with_version(table, record):
    sql = f"""
    INSERT INTO {table} (id, ..., version, updated_at)
    VALUES (%(id)s, ..., %(version)s, NOW())
    ON CONFLICT (id) DO UPDATE
    SET ... = EXCLUDED...,
        version = GREATEST({table}.version, EXCLUDED.version),
        updated_at = NOW()
    WHERE EXCLUDED.version > {table}.version
    """
```

### A4.7.6 数据落地区（Staging）

#### 三层架构

```
1. landing/   原始数据（Parquet，保留 30 天）
2. staging/   清洗后数据（Parquet，保留 7 天）
3. warehouse/ 加载到 PMS 目标表
```

#### 目录示例

```
s3://pms-migration/
├── landing/
│   ├── s01_excel/
│   │   ├── 2025-04-15/
│   │   │   ├── projects.parquet
│   │   │   └── members.parquet
│   ├── s02_oa/
│   │   └── 2025-04-15/
│   │       ├── approvals.parquet
│   ├── s05_hr/
│   │   └── cdc/
│   │       └── users/
│   │           └── 2025-04-15T02.json
├── staging/
│   ├── 2025-04-15/
│   │   ├── users_clean.parquet
│   │   ├── projects_clean.parquet
├── mapping/
│   ├── id_map_user.parquet
│   ├── id_map_project.parquet
├── logs/
└── reports/
    ├── 2025-04-15-validation.html
```

### A4.7.7 监控

#### 4 大类指标

| 类别 | 指标 | 告警阈值 |
| --- | --- | --- |
| **任务级** | DAG 成功率、Task 失败率、运行时长 | 失败率 > 5% |
| **数据级** | 抽取行数、加载行数、丢弃率 | 丢弃率 > 1% |
| **延迟级** | 端到端延迟、CDC Lag | CDC lag > 5min |
| **质量级** | 校验失败率、对账差异率 | 差异 > 0.1% |

#### Grafana 仪表盘

- 概览：DAG 状态矩阵（绿/黄/红）；
- 性能：运行时长 P50/P95/P99；
- 数据：每日行数趋势；
- 质量：校验通过率趋势；
- 血缘：上游→下游链路图。

#### 告警规则（示例）

```yaml
groups:
  - name: migration_alerts
    rules:
      - alert: ETLDagFailed
        expr: airflow_dag_run_status{state="failed"} == 1
        for: 5m
        labels: { severity: critical }
        annotations:
          summary: "DAG {{ $labels.dag_id }} 失败"

      - alert: CDC_Lag_High
        expr: debezium_source_lag_ms > 300000
        for: 5m
        labels: { severity: warning }
```

### A4.7.8 重跑与幂等

- **重试策略**：3 次（指数退避 1m/5m/30m）；
- **幂等键**：
  - 抽取：源系统时间戳 + 行 hash；
  - 加载：主键 upsert；
- **断点续传**：从 staging 恢复，无需重新抽取；
- **手动重跑**：清空目标表目标批次 + 重新加载。

### A4.7.9 安全

- 凭据全部从 Vault 注入，**绝不**硬编码；
- 临时文件加密落盘（SSE-KMS）；
- 传输 TLS 1.2+；
- 操作审计（who/when/where/what）；
- 最小权限（IAM）：mig-worker 角色仅可写目标库指定 schema；
- 网络隔离：迁移 worker 在 DMZ，目标库在 Intranet，通过堡垒机 + 网关代理。

---

**§A4.7 完成。下一步 §A4.8 DAG 模板与校验对账。**

---

## A4.8 DAG 模板与 Python 样例

### A4.8.1 Airflow DAG 模板（以"项目增量"为例）

```python
# dags/s04_erp_projects_incremental.py
from airflow import DAG
from airflow.operators.python import PythonOperator
from datetime import datetime, timedelta
from etl.tasks import extract, transform, load, validate, report

default_args = {
    "owner": "migration",
    "retries": 3,
    "retry_delay": timedelta(minutes=5),
    "execution_timeout": timedelta(hours=2),
}

with DAG(
    dag_id="s04_erp_projects_incremental",
    description="ERP 项目主数据增量同步",
    schedule_interval="0 2 * * *",   # 每天 02:00
    start_date=datetime(2025, 4, 1),
    catchup=False,
    max_active_runs=1,
    default_args=default_args,
    tags=["migration", "s04", "incremental"],
) as dag:

    extract_task = PythonOperator(
        task_id="extract",
        python_callable=extract.from_sap_table,
        op_kwargs={
            "source": "S04",
            "table": "PROJ",
            "watermark": "{{ ds }}",
            "target": "s3://pms-migration/landing/s04/projects/{{ ds }}.parquet",
        },
    )

    transform_task = PythonOperator(
        task_id="transform",
        python_callable=transform.clean_projects,
        op_kwargs={
            "input": "s3://pms-migration/landing/s04/projects/{{ ds }}.parquet",
            "output": "s3://pms-migration/staging/projects/{{ ds }}.parquet",
            "mapping_file": "mappings/s04_projects.yaml",
        },
    )

    validate_task = PythonOperator(
        task_id="validate",
        python_callable=validate.great_expectations,
        op_kwargs={
            "dataset": "s3://pms-migration/staging/projects/{{ ds }}.parquet",
            "suite": "projects_v1",
        },
    )

    load_task = PythonOperator(
        task_id="load",
        python_callable=load.to_pms,
        op_kwargs={
            "input": "s3://pms-migration/staging/projects/{{ ds }}.parquet",
            "target_table": "projects",
            "strategy": "upsert",
        },
    )

    report_task = PythonOperator(
        task_id="report",
        python_callable=report.daily_summary,
        op_kwargs={"dag_id": "s04_erp_projects_incremental"},
    )

    extract_task >> transform_task >> validate_task >> load_task >> report_task
```

### A4.8.2 核心 Python 样例（ETL Tasks）

#### 抽取（Extract）

```python
# etl/tasks/extract.py
import pandas as pd
from etl.connectors.sap import SAPConnector

def from_sap_table(source, table, watermark, target, **ctx):
    """从 SAP RFC 抽取指定表增量数据"""
    conn = SAPConnector.from_vault(source)
    sql = f"""
      SELECT * FROM {table}
      WHERE last_changed_at > '{watermark}'
        AND last_changed_at < '{watermark}'::date + INTERVAL '1 day'
    """
    df = pd.read_sql(sql, conn.engine)
    df.to_parquet(target, index=False)
    ctx["ti"].xcom_push(key="row_count", value=len(df))
    return len(df)
```

#### 转换（Transform）

```python
# etl/tasks/transform.py
import pandas as pd
import yaml
from etl.mappings.id_map import IdMap

def clean_projects(input, output, mapping_file, **ctx):
    df = pd.read_parquet(input)
    with open(mapping_file) as f:
        m = yaml.safe_load(f)
    idmap = IdMap.load("project")

    # 1. 字段重命名
    df = df.rename(columns=m["rename"])

    # 2. 枚举转换
    for src, tgt, fn in m["enums"]:
        df[tgt] = df[src].map(fn)

    # 3. ID 映射（legacy_no -> uuid）
    df["project_id"] = df["legacy_no"].map(idmap.to_uuid)

    # 4. 派生字段
    df["code"] = df["legacy_no"]
    df["secret_level"] = "INTERNAL"
    df["health_score"] = None
    df["version"] = 1

    # 5. 数据脱敏
    df["created_by"] = "migration"

    # 6. 字段裁剪
    df = df[list(m["target_fields"])]

    df.to_parquet(output, index=False)
    ctx["ti"].xcom_push(key="row_count", value=len(df))
```

#### 加载（Load）

```python
# etl/tasks/load.py
import pandas as pd
from etl.connectors.pms import PmsConnector

def to_pms(input, target_table, strategy, **ctx):
    df = pd.read_parquet(input)
    conn = PmsConnector.from_vault("pms_target")
    if strategy == "upsert":
        # 分批 upsert（每批 5000 行）
        inserted, updated, failed = 0, 0, 0
        for chunk in np.array_split(df, max(1, len(df) // 5000)):
            try:
                res = conn.upsert(target_table, chunk.to_dict("records"))
                inserted += res["inserted"]
                updated += res["updated"]
            except Exception as e:
                failed += len(chunk)
                conn.send_to_dlq(target_table, chunk, str(e))
        ctx["ti"].xcom_push(key="inserted", value=int(inserted))
        ctx["ti"].xcom_push(key="updated", value=int(updated))
        ctx["ti"].xcom_push(key="failed", value=int(failed))
        if failed / max(1, len(df)) > 0.05:
            raise AirflowFailException(f"失败率 {failed/len(df):.1%} 超 5%")
    elif strategy == "append":
        conn.bulk_insert(target_table, df.to_dict("records"))
```

#### 校验（Validate — Great Expectations）

```python
# etl/tasks/validate.py
import great_expectations as gx

def great_expectations(dataset, suite, **ctx):
    context = gx.get_context(mode="file", project_root_dir="/opt/ge")
    batch = context.get_batch(dataset=dataset)
    results = context.run_validation_operator(
        "action_list_operator",
        assets_to_validate=[batch],
        run_id=f"run_{ctx['ds_nodash']}",
        suite_name=suite,
    )
    success = results["success"]
    if not success:
        raise AirflowFailException(f"GE 校验失败：{results['statistics']}")
    return success
```

### A4.8.3 字段映射配置（YAML）

```yaml
# mappings/s04_projects.yaml
rename:
  proj_no: legacy_no
  proj_name: name
  proj_type: type_raw
  pm_emp_no: pm_emp_no
  plan_start: start_date
  plan_end: end_date
  budget: total_budget_raw
  status_code: status_raw
  created: created_at

enums:
  - [type_raw, type, {1: "RND", 2: "INFRA", 3: "MKT", 4: "COMPL"}]
  - [status_raw, status, {A: "ACTIVE", C: "CLOSED", H: "SUSPENDING", D: "DRAFT"}]

target_fields:
  - project_id
  - code
  - name
  - type
  - status
  - pm_id
  - business_unit_id
  - start_date
  - end_date
  - currency
  - total_budget
  - secret_level
  - created_at
  - version
```

## A4.9 数据校验与对账

### A4.9.1 校验类型矩阵

| 校验类型 | 工具 | 频率 | 失败处理 |
| --- | --- | --- | --- |
| Schema 校验 | Great Expectations | 每批 | 拒绝加载 |
| 字段格式 | GE / Pandera | 每批 | 拒绝/纠正 |
| 业务规则 | GE / 自定义 | 每批 | 拒绝/报告 |
| 行数对账 | 自研 | 每日 | 报告 + 告警 |
| 抽样比对 | 自研 | 每周 | 报告 |
| 全量对账 | 自研（双跑） | 切流前后 | 阻塞切流 |
| 跨表外键 | PG FK | 加载时 | 拒绝 |
| 一致性约束 | PG CHECK | 加载时 | 拒绝 |

### A4.9.2 Great Expectations 套件示例

```yaml
# great_expectations/suites/projects_v1.yml
expectations:
  - expectation_type: expect_column_values_to_not_be_null
    kwargs: { column: "code" }
  - expectation_type: expect_column_values_to_match_regex
    kwargs:
      column: "code"
      regex: "^PRJ-[A-Z0-9]+-\\d{2}-\\d{4}$"
  - expectation_type: expect_column_values_to_be_between
    kwargs:
      column: "total_budget"
      min_value: 0
      max_value: 1000000000
  - expectation_type: expect_column_pair_values_A_to_be_greater_than_B
    kwargs: { column_A: "end_date", column_B: "start_date" }
  - expectation_type: expect_column_values_to_be_in_set
    kwargs: { column: "status", value_set: ["DRAFT", "PENDING", "ACTIVE", "CLOSING", "CLOSED", "ARCHIVED"] }
  - expectation_type: expect_table_row_count_to_be_between
    kwargs: { min_value: 100, max_value: 100000 }
```

### A4.9.3 对账报表（每日生成）

#### 对账维度

| 维度 | 源 | 目标 | 差异处理 |
| --- | --- | --- | --- |
| 项目数 | S-04 ERP / S-01 Excel | projects | 差异 > 0 应告警 |
| 用户数 | S-05 HR | users | 差异 > 0 告警 |
| 任务数 | S-03 Excel | work_items | 差异 > 1% 告警 |
| 工时合计 | S-02 OA | time_entries | 差异 > 0.5% 告警 |
| 风险数 | S-03 Excel | risks | 差异 > 0 告警 |
| 缺陷数 | S-06 ALM | defects | 差异 > 0 告警 |
| 文档数 | S-07 Confluence | documents | 差异 > 0 告警 |
| 预算合计 | S-09 NC | budgets | 差异 > 0.1% 告警 |

#### 报表样例（HTML + CSV）

```python
# etl/reports/daily_reconciliation.py
def gen_daily_report(execution_date):
    metrics = {
        "projects": count_compare("s04", "projects", execution_date),
        "users":    count_compare("s05", "users", execution_date),
        "tasks":    count_compare("s03", "work_items", execution_date),
        "time_entries": sum_compare("s02", "time_entries", "hours", execution_date),
        "risks":    count_compare("s03", "risks", execution_date),
        "defects":  count_compare("s06", "defects", execution_date),
        "docs":     count_compare("s07", "documents", execution_date),
        "budgets":  sum_compare("s09", "budgets", "planned_amount", execution_date),
    }
    render_html(metrics, execution_date)        # s3://pms-migration/reports/{date}.html
    render_csv(metrics, execution_date)
    send_to_email(metrics, ["pmo-team@example.com"])
    for k, v in metrics.items():
        if v["diff_pct"] > THRESHOLDS.get(k, 0.001):
            send_alert(k, v)
```

#### 报表示例（HTML 片段）

```html
<h1>PMS 数据迁移对账报告 - 2025-04-15</h1>
<table border="1">
  <tr><th>维度</th><th>源系统</th><th>PMS</th><th>差异</th><th>差异率</th><th>状态</th></tr>
  <tr><td>项目数</td><td>285</td><td>285</td><td>0</td><td>0%</td><td>✅</td></tr>
  <tr><td>用户数</td><td>5,128</td><td>5,126</td><td>-2</td><td>0.04%</td><td>✅</td></tr>
  <tr><td>任务数</td><td>52,310</td><td>51,898</td><td>-412</td><td>0.79%</td><td>⚠️</td></tr>
  <tr><td>工时合计</td><td>128,450h</td><td>128,440h</td><td>-10h</td><td>0.01%</td><td>✅</td></tr>
  <tr><td>缺陷数</td><td>3,892</td><td>3,892</td><td>0</td><td>0%</td><td>✅</td></tr>
</table>
```

### A4.9.4 抽样比对（每周）

- **抽样比例**：1% 或 50 条（取大者）；
- **抽样规则**：随机 + 关键记录（必含 P0 项目、最近 30 天活跃项目）；
- **比对字段**：≥ 80% 关键字段；
- **差异 > 1%** → 告警 + 人工复核。

### A4.9.5 业务规则校验（自定义 Python）

```python
# etl/validators/business_rules.py
def check_budget_not_negative(df): return (df["planned_amount"] >= 0).all()
def check_time_hours_range(df):    return df["hours"].between(0.25, 24).all()
def check_project_dates(df):       return (df["end_date"] > df["start_date"]).all()
def check_active_pm(df):           return df[df["status"] == "ACTIVE"]["pm_id"].notna().all()
def check_no_duplicate_code(df):   return df["code"].is_unique
def check_user_email_format(df):   return df["email"].str.match(r"^[\w.+-]+@[\w-]+\.[\w.-]+$").all()

RULES = [
    ("projects", check_budget_not_negative),
    ("time_entries", check_time_hours_range),
    ("projects", check_project_dates),
    ("projects", check_active_pm),
    ("projects", check_no_duplicate_code),
    ("users", check_user_email_format),
]
```

---

**§A4.8 + §A4.9 完成。下一步 §A4.10 异常分类与回退。**

---

## A4.10 异常分类与回退策略

### A4.10.1 异常三级分类

| 级别 | 含义 | 响应 | 触发条件示例 |
| --- | --- | --- | --- |
| **FATAL** | 阻塞性、需立即人工介入 | 立即停止迁移 + 升级 PMO 总监 + 启动回退 | 源系统不可用、目标库宕机、数据被破坏 |
| **ERROR** | 影响单批次，可自动跳过 | 跳过本批 + DLQ + 告警 + 下一批继续 | 单行 schema 不匹配、外键冲突、校验失败 |
| **WARN** | 提示性，不影响数据 | 记录 + 报告 | 字段截断、格式异常、孤立记录 |

### A4.10.2 异常分类详细矩阵

| 异常 | 级别 | 触发条件 | 处理 |
| --- | --- | --- | --- |
| 源系统连接超时 | FATAL | 重试 3 次仍失败 | 停止 + 升级 |
| 源系统 401/403 | FATAL | 凭据失效 | 立即停止 + 通知 SRE |
| 抽取行数为 0 | ERROR | 期望 > 0 实际 = 0 | DLQ + 告警 |
| 抽取行数 < 预期 50% | ERROR | 阈值检查 | DLQ + 告警 + 复核 |
| Schema 不匹配 | ERROR | 关键字段缺失 | DLQ + 告警 + 自动隔离 |
| 业务规则校验失败 | ERROR | 阈值 1% | 拒绝本批 |
| 业务规则失败率 > 5% | FATAL | 大面积问题 | 停止 + 升级 |
| 加载失败（外键） | ERROR | 孤立引用 | DLQ + 等待依赖 |
| 加载失败（唯一键） | ERROR | 重复 | DLQ + 人工 |
| 加载失败（CHECK） | ERROR | 约束违反 | DLQ + 告警 |
| CDC Lag > 5min | WARN | 流量突增 | 自动扩容 + 告警 |
| CDC Lag > 30min | ERROR | 系统异常 | 告警 + 干预 |
| CDC Lag > 2h | FATAL | 严重积压 | 停止 + 升级 |
| 字段格式异常 | WARN | 邮箱/手机/日期 | 自动纠正 + 报告 |
| 字段截断 | WARN | 超过最大长度 | 截断 + 标记 |
| 对账差异 > 0.1% | WARN | 容忍范围内 | 报告 |
| 对账差异 > 1% | ERROR | 超阈值 | 阻塞下一阶段 |
| 对账差异 > 5% | FATAL | 数据丢失 | 停止 + 回退 |
| 临时存储失败 | FATAL | 磁盘满/对象存储 5xx | 立即停止 |
| Vault 凭据失效 | FATAL | 解密失败 | 立即停止 |

### A4.10.3 回退策略：3 个粒度

| 粒度 | 触发场景 | RTO | 操作 |
| --- | --- | --- | --- |
| **任务级** | 单 DAG 失败 | < 5min | Airflow 单独重跑该 DAG |
| **阶段级** | 整个阶段（如试点/全量）失败 | < 30min | 回滚到上一阶段数据快照 |
| **全局级** | 重大事故（数据被破坏） | < 2h | 切回老系统 + 启动灾备 |

### A4.10.4 任务级回退

```python
# 操作：重跑单 DAG
airflow dags trigger --conf '{"rerun_date": "2025-04-15"}' s04_erp_projects_incremental

# 操作：清空目标表目标批次
TRUNCATE TABLE staging.projects WHERE batch_id = '2025-04-15';

# 操作：恢复临时文件
aws s3 cp s3://pms-migration/backup/landing/2025-04-15/ ./landing/2025-04-15/ --recursive
```

### A4.10.5 阶段级回退

#### 快照策略

| 阶段 | 快照时机 | 保留 |
| --- | --- | --- |
| 试点迁移前 | 试点前 24h | 永久（试点快照） |
| 试点完成后 | 试点结束 | 永久（基线快照） |
| 全量迁移前 | 切流前 1h | 永久（切流快照） |
| 切流后 | 切流完成 | 90 天 |

#### 回退步骤

```bash
#!/bin/bash
# scripts/rollback_stage.sh
# 用法：./rollback_stage.sh 2025-04-15-staging

set -e
STAGE=$1
LOG="rollback-${STAGE}-$(date +%Y%m%d%H%M%S).log"
echo "[$(date)] 开始回退 $STAGE" | tee -a $LOG

# 1. 停写：禁用目标库写权限
psql -c "REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public FROM pms_writer;" | tee -a $LOG

# 2. 备份当前状态
pg_dump -Fc pms > backup/pre-rollback-${STAGE}.dump | tee -a $LOG

# 3. 恢复快照
pg_restore -Fc --clean --if-exists -d pms \
  snapshots/${STAGE}.dump | tee -a $LOG

# 4. 校验
psql -c "SELECT count(*) FROM projects;" | tee -a $LOG

# 5. 通知
./scripts/notify.sh "阶段回退完成: $STAGE" success | tee -a $LOG

echo "[$(date)] 回退完成" | tee -a $LOG
```

### A4.10.6 全局级回退

#### 决策矩阵

| 场景 | 是否回退 | 决策人 |
| --- | --- | --- |
| 数据被破坏/丢失 > 5% | **是** | PMO 总监 |
| 性能严重不达标（P95 > 5x 目标） | **是** | 研发负责人 |
| 安全事件 | **是** | CISO |
| 关键功能不可用 > 4h | **是** | PMO 总监 |
| 业务方拒绝采纳 | **是（业务决策）** | 业务负责人 |
| 单模块问题 | **否（局部回退）** | 模块负责人 |

#### 全局回退 Runbook

```
T+0:  PMO 总监决定全局回退
T+5:  通知所有相关方（IM 群 + 短信）
T+10: 启动老系统只读模式（如尚未启动）
T+15: DNS 切回老系统
T+20: 验证业务恢复
T+30: 启动新系统数据快照保存（取证）
T+2h: 召开复盘会（PMO + 研发 + 业务）
T+24h: 输出复盘报告 + 改进措施
T+72h: 决定是否继续/重试/终止
```

### A4.10.7 DLQ（死信队列）设计

| 字段 | 描述 |
| --- | --- |
| id | UUID |
| target_table | 目标表 |
| source_system | 源系统 |
| payload | JSON / Parquet chunk |
| error_message | 错误描述 |
| error_stack | 堆栈 |
| retry_count | 已重试次数 |
| status | PENDING / RESOLVED / ABANDONED |
| created_at | 进入 DLQ 时间 |
| resolved_at | 处理时间 |
| resolved_by | 处理人 |

#### DLQ 处理流程

```
异常记录 → 写入 DLQ（Parquet → 临时区）
        ↓
Airflow 每日 03:00 汇总 DLQ
        ↓
发送邮件给数据治理负责人
        ↓
人工 / 半自动修复（修改映射 / 修正源数据）
        ↓
手动重跑特定批次
        ↓
成功 → 标记 RESOLVED
失败 3 次 → 标记 ABANDONED
```

### A4.10.8 Runbook（高频场景）

#### 场景 1：源系统 ERP 不可用

```
1. 收到告警：ETLDagFailed (s04_erp_projects_incremental)
2. 检查 ERP 系统状态：./scripts/check_erp.sh
3. 若 ERP 宕机：
   - 通知 SRE + ERP 供应商
   - 暂停所有 S-04 相关 DAG
   - 等待恢复（最长 2h）
4. 若 ERP 恢复：
   - 重跑失败的 DAG（带 watermark=今天）
   - 校验数据完整性
   - 输出事件报告
5. 若 2h 仍未恢复：
   - 升级 PMO 经理
   - 启动灾备（手工 Excel 模式）
```

#### 场景 2：校验失败率超过 5%

```
1. 收到告警：ValidationFailureRate
2. 暂停后续批次（airflow dags pause <dag_id>）
3. 拉取 DLQ 数据 → 分析失败模式
4. 三种可能：
   a) 源数据问题 → 退回业务方修正 → 重跑
   b) 映射配置错误 → 修复 mapping yaml → 从 staging 重跑
   c) 目标 schema 变更 → DBA 修复 → 重跑
5. 修复后：清空目标批次 → 从 staging 重跑
6. 验证 → 恢复 DAG
7. 输出 RCA 报告
```

#### 场景 3：CDC 积压严重

```
1. 收到告警：CDC_Lag_High > 30min
2. 检查 Kafka 消费速率：./scripts/kafka_lag.sh
3. 若消费者正常但生产速率高：
   - 扩容消费者（+3 pod）
   - 检查下游 PMS 写性能
4. 若消费者异常：
   - 重启消费者
   - 检查 DLQ
5. 若持续 > 2h：
   - 暂停非关键 DAG
   - 升级 SRE
```




---

## A4.11 演练计划 + 报告模板

> **目标**:在生产前 2-3 周完成 3 次演练(影子 / 蓝绿 / 全量),输出标准化演练报告,作为切流日决策依据。
> **位置**:Part2 末尾,作为 §A4.10 异常 Runbook 的"前置保障"。

### A4.11.1 演练类型与节奏

| 演练 | 范围 | 时机 | 决策依据 |
| --- | --- | --- | --- |
| **影子演练**(T-3w) | ETL 全流程跑通,数据**只入 staging 不入目标** | T-3 周(单日) | 验证 DAG / 校验 / 监控告警 |
| **蓝绿演练**(T-2w) | 真实数据双写到新旧两套,仅新**对账 + 抽样比对**,不切流 | T-2 周(1-2 天) | 验证数据一致性 / 性能 / 回退 |
| **全量演练**(T-1w) | 生产时间段,真实环境,1% → 5% → 50% 灰度 | T-1 周(半天) | 验证切流脚本 / 应急 SOP / 通知 |

### A4.11.2 影子演练执行步骤(以"项目增量"为例)

#### 演练准备 Checklist

- [ ] staging 环境就绪(MySQL + Kafka + Airflow + Grafana)
- [ ] 源 ERP 数据快照(SQL dump)
- [ ] 目标 PMS 库初始 schema(空库,带 schema)
- [ ] 演练执行人 1 + 监控 1 + 决策 1(共 3 人到位)
- [ ] 演练时间窗口预定(2 小时)
- [ ] 应急联系人(DBA / SRE / PMO)

#### 执行 Runbook

```
1. 启动 Airflow: docker-compose -f staging/airflow.yaml up -d
2. 清空 staging 库: TRUNCATE staging.pms_project, milestone, ...;
3. 触发 DAG: airflow dags trigger migration_project_incremental
4. 监控:
   - Grafana: "Migration" dashboard 实时观察
   - 日志: tail -f /var/log/airflow/migration_project_incremental.log
5. 等待完成(预计 30 min, 50 万行项目数据)
6. 校验:
   - 全量: SELECT COUNT(*) FROM staging.pms_project
   - 抽样: 1% 行做 MD5 对比
7. 决策: 全部通过 → 进入蓝绿演练;失败 → 阻塞,根因分析
```

#### 失败处置

- 任务失败:截图 + 日志 + Airflow Task Instance ID → 写"问题登记表"
- 性能不达标:> 2h 还未完成 → 扩容 worker / 优化 SQL
- 数据不一致:MD5 不一致行 → 标红 → 修复 mapping → 重跑

### A4.11.3 蓝绿演练执行步骤

#### 关键差异(与影子相比)

- 数据**真实**,从源 ERP 实时抽取
- **双写**:旧 PMS + 新 PMS 同时接收
- **不切流**:用户仍访问旧 PMS
- **对账**:每 5 分钟跑一次差异比对

#### 执行 Runbook

```
1. 启动双写开关: featur...[truncated]


---

## A4.11 演练计划 + 报告模板

> **目标**:在生产前 2-3 周完成 3 次演练(影子 / 蓝绿 / 全量),输出标准化演练报告,作为切流日决策依据。
> **位置**:Part2 末尾,作为 §A4.10 异常 Runbook 的"前置保障"。

### A4.11.1 演练类型与节奏

| 演练 | 范围 | 时机 | 决策依据 |
| --- | --- | --- | --- |
| **影子演练**(T-3w) | ETL 全流程跑通,数据**只入 staging 不入目标** | T-3 周(单日) | 验证 DAG / 校验 / 监控告警 |
| **蓝绿演练**(T-2w) | 真实数据双写到新旧两套,仅新**对账 + 抽样比对**,不切流 | T-2 周(1-2 天) | 验证数据一致性 / 性能 / 回退 |
| **全量演练**(T-1w) | 生产时间段,真实环境,1% → 5% → 50% 灰度 | T-1 周(半天) | 验证切流脚本 / 应急 SOP / 通知 |

### A4.11.2 影子演练执行步骤(以"项目增量"为例)

#### 演练准备 Checklist

- [ ] staging 环境就绪(MySQL + Kafka + Airflow + Grafana)
- [ ] 源 ERP 数据快照(SQL dump)
- [ ] 目标 PMS 库初始 schema(空库,带 schema)
- [ ] 演练执行人 1 + 监控 1 + 决策 1(共 3 人到位)
- [ ] 演练时间窗口预定(2 小时)
- [ ] 应急联系人(DBA / SRE / PMO)

#### 执行 Runbook

```
1. 启动 Airflow: docker-compose -f staging/airflow.yaml up -d
2. 清空 staging 库(仅演练用): TRUNCATE staging.pms_project, milestone, ...;
3. 触发 DAG: airflow dags trigger migration_project_incremental
4. 监控:
   - Grafana: "Migration" dashboard 实时观察
   - 日志: tail -f /var/log/airflow/migration_project_incremental.log
5. 等待完成(预计 30 min,50 万行项目数据)
6. 校验:
   - 全量: SELECT COUNT(*) FROM staging.pms_project
   - 抽样: 1% 行做 MD5 对比
7. 决策: 全部通过 → 进入蓝绿演练;失败 → 阻塞,根因分析
```

> 注:TRUNCATE staging 仅限 staging 环境,生产环境演练**严禁**使用本步骤。

#### 失败处置

- 任务失败:截图 + 日志 + Airflow Task Instance ID → 写"问题登记表"
- 性能不达标:> 2h 还未完成 → 扩容 worker / 优化 SQL
- 数据不一致:MD5 不一致行 → 标红 → 修复 mapping → 重跑

### A4.11.3 蓝绿演练执行步骤

#### 关键差异(与影子相比)

- 数据**真实**,从源 ERP 实时抽取
- **双写**:旧 PMS + 新 PMS 同时接收
- **不切流**:用户仍访问旧 PMS
- **对账**:每 5 分钟跑一次差异比对

#### 执行 Runbook

```
1. 启动双写开关: feature_flag=dual_write=true
2. 源 ERP 实时抽取(双写同时落旧/新库)
3. 每 5 分钟跑对账:
   diff_count = SELECT count(*) FROM (SELECT * FROM old MINUS SELECT * FROM new)
   ... (详细 SQL 见 §A4.9)
4. 性能对比:
   - 旧库 P95 写延迟 vs 新库 P95 写延迟
   - 旧库 P95 读延迟 vs 新库 P95 读延迟
5. 抽样比对: 1000 个项目手工核对字段
6. 异常回退: 任何 P0 异常 → 立即关 dual_write → 切回单写旧库
7. 演练结束(48h): 输出对账报告
```

#### 决策矩阵

| 差异率 | 性能比 | 抽样准确率 | 决策 |
| --- | --- | --- | --- |
| < 0.01% | 新 ≤ 旧 × 1.2 | ≥ 99.9% | 🟢 进全量演练 |
| 0.01% - 0.1% | 新 ≤ 旧 × 1.5 | ≥ 99.5% | 🟡 修复后再演练 |
| > 0.1% 或 性能 > 旧 × 1.5 | 任意 | < 99% | 🔴 阻塞,根因分析 |

### A4.11.4 全量演练(灰度切流)

#### 灰度阶段

| 阶段 | 流量 | 持续 | 监控项 | 决策 |
| --- | --- | --- | --- | --- |
| 影子 | 1%(写新库) | 1 h | 错误率 / 延迟 / 校验 | 进入 5% |
| 5% | 5% 写新库 | 4 h | 同上 + 业务 KPI | 进入 50% |
| 50% | 50% | 12 h | 同上 | 进入 100% |
| 100% | 全部 | 持续 | 7×24 | 稳态 |

> **异常回退**:任何阶段 P0 异常 → 立即切回旧库(执行 `./scripts/rollback.sh` < 5 分钟)

#### 切流脚本(示例)

```bash
#!/usr/bin/env bash
# scripts/cutover.sh <from> <to>
#  from: 0=旧库 1=新库
set -euo pipefail
FROM=${1:?from required}
TO=${2:?to required}

# 1. 摘流量(LB / DNS / 网关)
./scripts/drain_traffic.sh --seconds=30

# 2. 等待 drain 完成
sleep 30

# 3. 双写关闭(单写)
./scripts/single_write.sh $TO

# 4. 流量切到目标
./scripts/switch_traffic.sh $TO

# 5. 恢复(可选)
./scripts/restore_traffic.sh

echo "✅ 切流完成 from=$FROM to=$TO,时间: $(date)"
```

#### 回退脚本(示例)

```bash
#!/usr/bin/env bash
# scripts/rollback.sh <target>
set -euo pipefail
TARGET=${1:?target required, 0=旧库 1=新库}

# 1. 紧急停写(避免新数据落到错误库)
./scripts/emergency_stop_write.sh

# 2. 流量回切
./scripts/switch_traffic.sh $TARGET

# 3. 单写切回
./scripts/single_write.sh $TARGET

# 4. 通知 PMO + Sponsor
./scripts/notify_rollback.sh

# 5. 输出回退报告
./scripts/rollback_report.sh > /tmp/rollback-$(date +%Y%m%d-%H%M%S).log

echo "🔴 回退完成 target=$TARGET,时间: $(date)"
```

### A4.11.5 演练报告模板(标准化)

> **报告归档路径**:`docs/迁移演练/演练报告-{类型}-{日期}.md`
> **评审人**:PMO + DBA + SRE + 业务代表,3 个工作日内出"通过 / 修复后通过 / 阻塞"决议

```markdown
# 数据迁移演练报告

> 演练类型: [影子 / 蓝绿 / 全量]
> 演练日期: YYYY-MM-DD HH:MM ~ HH:MM
> 演练时长: X 小时 Y 分钟
> 报告人: XXX
> 参与人: 执行 X / 监控 Y / 决策 Z
> 报告日期: YYYY-MM-DD

## 1. 演练结论(单选)
- [ ] ✅ **通过** — 数据一致 / 性能达标 / 监控告警正常,可进入下一阶段
- [ ] 🟡 **修复后通过** — 发现 N 个 P1 问题,需 3 天内修复并重跑
- [ ] 🔴 **阻塞** — 发现 P0 问题,需根因分析,延后切流

## 2. 演练范围
- 演练 DAG: [project / milestone / timesheet / ...]
- 数据量: [X 万行 / Y GB]
- 演练环境: [staging / 蓝绿 / 灰度 X%]
- 持续时间: [X 小时]

## 3. 执行结果
| 指标 | 计划 | 实际 | 偏差 | 状态 |
| --- | --- | --- | --- | --- |
| 全量行数 | X | Y | Z% | 🟢/🟡/🔴 |
| 耗时 | X min | Y min | Z% | 🟢/🟡/🔴 |
| 校验通过率 | 100% | X% | Z% | 🟢/🟡/🔴 |
| MD5 一致行 | 100% | X% | Z% | 🟢/🟡/🔴 |
| 性能 P95 | < X ms | Y ms | Z% | 🟢/🟡/🔴 |
| 错误率 | < 0.1% | X% | — | 🟢/🟡/🔴 |
| 告警次数 | 0 | X | — | 🟢/🟡/🔴 |

## 4. 发现的问题
### P0(阻塞级,需立即修复)
- [问题 1]: 描述 / 截图 / 复现步骤 / 影响 / 责任人 / 修复方案 / 预计完成日
### P1(重要,需 3 天内修复)
- [问题 2]: ...
### P2(轻微,记录到 backlog)
- [问题 3]: ...

## 5. 性能数据
- 旧库 P95 写延迟: X ms / 新库 P95 写延迟: Y ms(差 Z%)
- 旧库 P95 读延迟: X ms / 新库 P95 读延迟: Y ms(差 Z%)
- 吞吐量: 旧 X 行/s vs 新 Y 行/s(差 Z%)

## 6. 监控告警
- 演练期间触发告警 N 次(列表 + 是否误报 + 是否需调阈值)
- 建议调整: 阈值 / 通知人 / 抑制规则

## 7. 回退验证(若涉及)
- 回退触发场景: [P0 异常 / 性能不达标 / 业务反馈]
- 回退执行时长: X 分钟(< 5 分钟达标)
- 回退后数据完整性: 100%
- 回退 Runbook 修订: [N 处]

## 8. 改进事项
- 流程: ...
- 技术: ...
- 文档: ...
- 培训: ...

## 9. 附件
- Airflow DAG 执行截图
- Grafana dashboard 截图
- 对账报表(diff_*.csv)
- 日志片段(/var/log/airflow/...)
- 录音/录像(若适用)

## 10. 签字
- 演练执行: _________ 日期: _____
- 监控: _________ 日期: _____
- 决策(PMO): _________ 日期: _____
- 技术评审(DBA / SRE): _________ 日期: _____
```

### A4.11.6 演练日历模板(给 PMO)

| 周 | 一 | 二 | 三 | 四 | 五 |
| --- | --- | --- | --- | --- | --- |
| T-3w | 准备 | 准备 | **影子** | 报告评审 | 修复 |
| T-2w | 准备 | **蓝绿 D1** | **蓝绿 D2** | 报告评审 | 修复 |
| T-1w | 准备 | **全量 1%** | **全量 5%** | **全量 50%** | **全量 100%** |
| T-0 | **切流日** | 稳态 | 稳态 | 稳态 | 稳态 |

> **关键控制点**:
> - 每次演练前 1 天开 15 分钟预备会(人/数据/工具/应急确认)
> - 每次演练后 1 个工作日内出报告,3 个工作日内决议
> - 任何 🔴 阻塞 → 不进入下一阶段

### A4.11.7 演练 Checklist(打印版,演练当天用)

```
演练前 24h:
  [ ] 演练环境就绪
  [ ] 数据快照完成
  [ ] 应急联系人确认(手机畅通)
  [ ] 决策人到场
  [ ] 监控仪表盘预加载
  [ ] 切流/回退脚本 dry-run 通过

演练前 1h:
  [ ] 三方会议(执行/监控/决策)
  [ ] Airflow scheduler 健康检查
  [ ] Grafana dashboard 已开
  [ ] 通讯群建立(钉钉/微信/企业微信)

演练中:
  [ ] 记录每 5 分钟状态
  [ ] 异常立即上报
  [ ] 切流决策走群投票
  [ ] 全程录像/截图

演练后 1h:
  [ ] 关闭 Airflow DAG 触发
  [ ] 备份演练日志
  [ ] 输出现场纪要
  [ ] 3 天内出报告
```

---

**§A4.11 完成。Part2(ETL + 校验 + 回退 + 演练)2/2 实质完成。**
