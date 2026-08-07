# PR-6: 技术方案与数据迁移

> **版本**: v0.1 (草稿)
> **作者**: 架构组 + DBA
> **评审**: @后端 @架构师 @DBA @SRE @QA
> **更新**: 2025-06-10
> **状态**: ⏳ 评审中
> **依赖**: PR-1..PR-5

---

## 1. 技术栈选型

### 1.1 后端

| 项 | 选型 | 版本 | 理由 |
|---|---|:---:|---|
| 语言 | Java | 17 | 团队熟, LTS 至 2029 |
| 框架 | Spring Boot | 3.2.x | 现行 |
| ORM | Spring Data JPA + Hibernate | 6.4.x | 现行 |
| 数据库 | MySQL | 8.0 | 现行 |
| 迁移 | Flyway | 10.x | 现行 |
| 安全 | Spring Security + JWT | 6.x | 现行 |
| 缓存 | Caffeine | 3.x | 轻量本地缓存 |
| 限流 | Bucket4j | 8.x | 令牌桶 |
| 文档 | springdoc-openapi | 2.3.x | OpenAPI 3.0 |
| 测试 | JUnit 5 + Mockito | 5.10 | 现行 |

### 1.2 前端

| 项 | 选型 | 版本 | 理由 |
|---|---|:---:|---|
| 框架 | Vue | 3.4.x | 现行 |
| UI | Element Plus | 2.6.x | 现行 |
| 图表 | ECharts | 5.5.x | vue-echarts 包装 |
| 甘特 | DHTMLX Gantt | 8.0 | 商业版已购 |
| 网络图 | vis-network | 9.1 | 关键路径展示 |
| 状态 | Pinia | 2.1.x | 现行 |
| HTTP | axios | 1.6.x | 现行 |

### 1.3 基础设施

- **部署**: Docker + K8s (1 节点 2 副本)
- **CI/CD**: GitLab CI + ArgoCD
- **监控**: Prometheus + Grafana
- **日志**: Loki + Promtail
- **告警**: AlertManager → 飞书

## 2. 架构总览

### 2.1 分层

```
┌─────────────────────────────────────┐
│  Vue 3 SPA (Element Plus + ECharts) │
└────────────────┬────────────────────┘
                 │ /api/v1/* (JWT in Header)
┌────────────────▼────────────────────┐
│  Nginx (TLS 终止 + 静态资源)        │
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│  Spring Boot 3 (Tomcat 10)          │
│  ┌──────────────────────────────┐  │
│  │ Controller (31 个)            │  │
│  ├──────────────────────────────┤  │
│  │ Service (业务编排, 事务边界)   │  │
│  ├──────────────────────────────┤  │
│  │ Repository (JPA + Native)     │  │
│  ├──────────────────────────────┤  │
│  │ Domain (Entity + Domain Svc)  │  │
│  └──────────────────────────────┘  │
└────────────────┬────────────────────┘
                 │ JDBC + HikariCP
┌────────────────▼────────────────────┐
│  MySQL 8.0 (1 主 1 从)              │
│  - pmo (主库, 23 张表)             │
│  - pmo_ro (从库, 只读查询)          │
└─────────────────────────────────────┘
```

### 2.2 模块划分

```
com.pmo.pms/
├── wbs/                    # WBS 模块
│   ├── controller/         # WbsTaskController, WbsAssignmentController, WbsSnapshotController
│   ├── service/            # 业务逻辑
│   ├── repository/         # JPA Repository
│   ├── domain/             # 实体 + 域服务
│   └── dto/                # 前后端契约
├── evm/                    # EVM 模块
├── risk/                   # Risk 模块
├── common/                 # 通用 (响应包装, 异常, 工具)
├── security/               # Spring Security + JWT
└── config/                 # 配置类
```

### 2.3 关键设计

- **DTO 转换**: MapStruct 1.5.x, 编译期生成
- **事务**: @Transactional 在 Service, 只读查询加 readOnly=true
- **审计**: JPA Auditing (createdAt/updatedAt/createdBy/updatedBy)
- **软删**: @SQLDelete + @Where 注解, 全局生效
- **乐观锁**: @Version 字段, 防并发覆盖

## 3. 关键算法 (CPM + 风险矩阵 + EVM)

### 3.1 CPM 关键路径算法 (Java 实现)

**输入**: 项目全部 WBS 任务 (含 predecessorIds, planStart, planEnd, duration)
**输出**: 每个任务的 ES/EF/LS/LF/Float + 关键路径集合

**算法**: 拓扑排序 + 正向遍历 + 反向遍历

```java
public class CpmCalculator {
    public CpmResult calculate(List<WbsTask> tasks) {
        Map<Long, WbsTask> byId = tasks.stream()
            .collect(toMap(WbsTask::getId, identity()));
        
        // 1. 拓扑排序 (Kahn)
        List<WbsTask> sorted = topologicalSort(tasks);
        
        // 2. 正向: ES = max(EF of predecessors)
        for (WbsTask t : sorted) {
            int es = t.getPredecessorIds().stream()
                .mapToInt(pid -> byId.get(pid).getEf())
                .max().orElse(0);
            int ef = es + t.getDuration();
            t.setEs(es);
            t.setEf(ef);
        }
        
        // 3. 反向: LF = min(LS of successors)
        for (WbsTask t : sorted.reverse()) {
            int lf = successorsOf(t, byId).stream()
                .mapToInt(s -> s.getLs())
                .min().orElse(t.getEf());
            int ls = lf - t.getDuration();
            t.setLs(ls);
            t.setLf(lf);
        }
        
        // 4. Float = LS - ES, Float=0 即关键
        Set<Long> criticalPath = new HashSet<>();
        for (WbsTask t : tasks) {
            t.setFloat(t.getLs() - t.getEs());
            if (t.getFloat() == 0) criticalPath.add(t.getId());
        }
        
        return new CpmResult(tasks, criticalPath);
    }
}
```

**复杂度**: O(V + E), V 任务数, E 依赖边数
**性能**: 100 任务 < 50ms, 500 任务 < 200ms (含数据库)

### 3.2 风险矩阵算法

**输入**: 全部风险 (含 probability 1-5, impact 1-5)
**输出**: 5×5 网格, 每格 {count, ids, averageScore}

```java
public RiskMatrix buildMatrix(List<Risk> risks) {
    RiskMatrix matrix = new RiskMatrix();
    for (Risk r : risks) {
        int p = r.getProbability();
        int i = r.getImpact();
        MatrixCell cell = matrix.getCell(p, i);
        cell.add(r);
    }
    return matrix;
}
```

**复杂度**: O(N), 简单分桶
**性能**: 50 风险 < 10ms

### 3.3 EVM 计算 (SQL 函数)

**指标**:
- `PV(t) = BAC × (计划完成工作 / 总计划工作)` (BCWS)
- `EV(t) = BAC × (实际完成工作 / 总计划工作)` (BCWP)
- `AC(t) = sum(actualCost)` (实际成本)
- `SV = EV - PV`, `CV = EV - AC`
- `SPI = EV / PV`, `CPI = EV / AC`
- `EAC = BAC / CPI`, `ETC = EAC - AC`, `VAC = BAC - EAC`

```sql
-- 趋势接口 SQL (简化)
SELECT 
    snapshot_date,
    bac,
    pv,
    ev,
    ac,
    ROUND((ev - pv) / NULLIF(pv, 0), 4) AS spi,
    ROUND((ev - ac) / NULLIF(ac, 0), 4) AS cpi,
    ROUND(bac / NULLIF(ev/NULLIF(ac, 0), 0), 2) AS eac
FROM wbs_budget_snapshot
WHERE project_id = ? AND snapshot_date BETWEEN ? AND ?
ORDER BY snapshot_date;
```

**执行频率**: 触发式 (POST /snapshots/trigger) + 每日定时 23:00

### 3.4 风险健康度

```java
public RiskHealth calcHealth(List<Risk> active, List<RiskHistory> recent) {
    int highCount = active.stream()
        .filter(r -> r.getScore() >= 15).toList().size();
    int newCount = recent.stream()
        .filter(h -> h.getAction() == CREATE).toList().size();
    int overdueCount = active.stream()
        .filter(r -> r.getDueDate() != null 
                  && r.getDueDate().isBefore(today)
                  && r.getStatus() != CLOSED).toList().size();
    
    if (highCount >= 3 || newCount >= 5) return RED;
    if (highCount >= 1 || overdueCount >= 1) return YELLOW;
    return GREEN;
}
```

## 4. 性能优化策略

### 4.1 数据库层

- **索引**: 8 个关键索引 (见 §5.3)
- **覆盖索引**: 趋势查询 (project_id, snapshot_date) 覆盖 pv/ev/ac/bac
- **慢 SQL 日志**: > 200ms 自动记, 每日 review

### 4.2 应用层

- **Caffeine 缓存**: 1 分钟 TTL
  - 风险矩阵 (key: projectId, change on update)
  - 风险健康度 (key: projectId, change on update)
  - WBS 任务树 (key: projectId, invalidate on task update)
- **批量加载**: WBS 树一次查全部, Map 索引 O(1) 组装
- **懒加载**: 集合 (assignments) 默认 LAZY
- **只读事务**: 所有 GET Service 加 readOnly=true

### 4.3 SQL 函数 vs 应用层

| 操作 | 选 SQL | 选 Java |
|---|:---:|:---:|
| 趋势 30 天 | ✅ (GROUP BY 快 3x) | ❌ |
| 风险矩阵 | ❌ (简单分桶) | ✅ |
| CPM | ❌ (复杂图算法) | ✅ (O(V+E)) |
| 健康度 | ❌ (需 Java 8 stream) | ✅ |

### 4.4 前端优化

- **ECharts**: 图表懒加载, 路由切走销毁
- **虚拟滚动**: el-table-v2, 1000+ 任务不卡
- **防抖**: 搜索/筛选 300ms
- **Tree 折叠**: 默认折叠到 2 级

### 4.5 容量评估

- **任务数**: 单项目上限 1000, 全市 50 项目 = 5 万行
- **快照数**: 每日 1 条, 1 年 365 条/项目, 1.8 万行/年
- **风险数**: 单项目 100, 全市 5000 行
- **5 年总量**: ~50 万行, MySQL 8.0 轻松支撑

## 5. 数据迁移与回滚

### 5.1 Flyway 脚本清单 (V3 增量)

| 脚本 | 内容 | 风险 |
|---|---|:---:|
| V3.0.0 | 9 张新表 (P3 全部) | 中 |
| V3.0.1 | 8 个索引 | 低 |
| V3.0.2 | 2 个种子数据 (风险类别, 应对策略) | 低 |
| V3.0.3 | 1 个视图 (v_active_risks) | 低 |

**回滚脚本**: U3.0.0 ~ U3.0.3 (对应反操作)

### 5.2 新增 9 张表

| # | 表名 | 模块 | 字段数 |
|:---:|---|:---:|:---:|
| 1 | wbs_task | WBS | 18 |
| 2 | wbs_assignment | WBS | 8 |
| 3 | wbs_predecessor | WBS | 4 |
| 4 | wbs_budget_snapshot | EVM | 10 |
| 5 | risk | Risk | 16 |
| 6 | risk_response | Risk | 12 |
| 7 | risk_history | Risk | 8 |
| 8 | risk_category | 字典 | 5 |
| 9 | response_strategy | 字典 | 5 |

**字典数据** (seed):
- risk_category: 技术/管理/外部/资源 4 类
- response_strategy: 规避/转移/减轻/接受/利用 5 类

### 5.3 8 个关键索引

```sql
-- WBS
CREATE INDEX idx_wbs_task_project ON wbs_task(project_id, deleted_at);
CREATE INDEX idx_wbs_task_parent ON wbs_task(parent_id);
CREATE UNIQUE INDEX uk_wbs_task_code ON wbs_task(project_id, wbs_code, deleted_at);
CREATE INDEX idx_wbs_asgn_task ON wbs_assignment(wbs_task_id, deleted_at);
CREATE INDEX idx_wbs_asgn_user ON wbs_assignment(user_id, deleted_at);

-- EVM
CREATE INDEX idx_evm_project_date ON wbs_budget_snapshot(project_id, snapshot_date);

-- Risk
CREATE INDEX idx_risk_project ON risk(project_id, status, deleted_at);
CREATE UNIQUE INDEX uk_risk_code ON risk(project_id, code, deleted_at);
```

### 5.4 数据迁移步骤 (生产)

```
T-3天:  备份 pmo 库 (mysqldump, 2h 内完成)
T-1天:  Staging 演练, 性能基线对比
T+0 02:00  DDL: 锁表 30s (V3.0.0 新表无锁)
T+0 02:01  DML: V3.0.1 索引 ALGORITHM=INPLACE (不停机)
T+0 02:10  Seed: V3.0.2 字典数据 (10 行)
T+0 02:11  验证: SELECT count(*), 索引使用 EXPLAIN
T+0 02:20  监控 30 分钟
T+0 02:50  标完成, 解锁变更窗口
```

### 5.5 回滚预案

| 场景 | 触发 | 操作 | 时间 |
|---|---|---|:---:|
| DDL 失败 | V3.0.0 异常 | U3.0.0 删表, 5min | < 10min |
| 索引建失败 | V3.0.1 锁表 | 杀会话 + U3.0.1, 5min | < 10min |
| 应用启动失败 | 兼容性 | git revert HEAD, 重新部署, 10min | < 15min |
| 数据错乱 | 验证 SQL 不匹配 | 切回 P2.5 版本, 10min | < 15min |
| 性能不达标 | p95 > 阈值 | 同上, 10min | < 15min |

**总回滚 SLA**: < 15 分钟

### 5.6 灰度发布 (W6, 见 PR-5 §8.1)

- 10% 流量 24 小时
- 监控: 错误率 < 0.1%, p95 < 阈值
- 自动回滚: 错误率 > 1% 触发

## 6. 风险与备选方案

### 6.1 风险矩阵

| # | 风险 | 概率 | 影响 | 缓解 | 备选 |
|:---:|---|:---:|:---:|---|---|
| 1 | CPM 在 1000 任务项目性能不达标 | 中 | 高 | 预计算 + 缓存, 增量更新 | 退化为仅 ES/EF 计算 |
| 2 | DHTMLX 商业授权到期 | 低 | 中 | 已购 5 年许可, 2029 前无忧 | 退化为 G2 (开源) |
| 3 | vis-network 维护停滞 | 中 | 低 | 锁版本 9.1, 监控 issue | 退化为 ECharts graph |
| 4 | EVM 函数精度问题 | 中 | 中 | DECIMAL(15,2) 不用 FLOAT | 应用层兜底 |
| 5 | 软删唯一约束冲突 | 高 | 中 | 软删 + 复合唯一 (deleted_at IS NULL) | 改硬删 |
| 6 | 历史数据 P1 → P3 不兼容 | 低 | 高 | 零结构变更, 不需迁移 | 回退到 P1.5 部署 |
| 7 | 团队对 vis-network 不熟 | 中 | 中 | 预研 1 周, 跑通 demo | 换 echarts-graph |
| 8 | CPM predecessorIds 形成环 | 中 | 中 | 创建时实时检测 (WBS-006) | 改用图数据库 |
| 9 | 50 项目性能瓶颈 | 低 | 高 | 缓存 + 只读从库, 提前压测 | 拆库按项目 hash |
| 10 | 飞书告警漏报 | 低 | 高 | 双重通道 (短信兜底) | 电话轮询 |

### 6.2 关键决策备选

**D1: 网络图库选型**
- ✅ **vis-network**: 9.1, 边箭头 + 关键路径高亮好
- 备选 ECharts graph: 中文社区好, 但关键路径样式弱
- 备选 Cytoscape.js: 功能强, 学习成本高

**D2: 软删实现**
- ✅ **@SQLDelete + @Where**: 透明, 不污染业务代码
- 备选 字段 + Specification: 灵活, 容易漏
- 备选 硬删: 简单, 不可恢复

**D3: 进度计算**
- ✅ **DB 函数**: DECIMAL 精度保证, 复用 SQL
- 备选 应用层: 灵活, 数据量大慢
- 备选 物化视图: 复杂, 不灵活

**D4: 缓存策略**
- ✅ **Caffeine 本地**: 简单, 1 分钟 TTL
- 备选 Redis: 集群一致, 多一份运维
- 备选 不缓存: 简单, 性能差

### 6.3 技术债 (P3 暂不修)

- 暂不支持任务跨项目依赖
- 暂不支持 EVM 预测区间 (Monte Carlo)
- 暂不支持风险响应自动化 (webhook)
- 暂不支持移动端原生 (P3 期响应式)
- 暂不支持离线编辑 (本地存储 + 同步)

