# PR-7: 测试与质量保障

> **版本**: v0.1 (草稿)
> **作者**: QA 组 + 后端
> **评审**: @QA @后端 @SRE @架构师
> **更新**: 2025-06-10
> **状态**: ⏳ 评审中
> **依赖**: PR-1..PR-6

---

## 1. 测试策略

### 1.1 测试金字塔

```
              ╱╲
             ╱  ╲         E2E (Playwright)
            ╱ 5% ╲        - 5 关键用户旅程
           ╱──────╲
          ╱        ╲      集成 (Testcontainers + MockMvc)
         ╱   20%    ╲     - 31 端点
        ╱────────────╲
       ╱              ╲   单元 (JUnit 5 + Mockito)
      ╱     75%        ╲  - 域服务 + 工具类
     ╱──────────────────╲
```

### 1.2 测试分层目标

| 层 | 工具 | 数量目标 | 覆盖率 | 速度 |
|---|---|:---:|:---:|:---:|
| 单元 | JUnit 5 + Mockito | 200+ | 80% | < 30s |
| 集成 | Spring Boot Test + Testcontainers | 80+ | 60% | < 5min |
| E2E | Playwright | 5 场景 | 关键路径 100% | < 10min |
| 性能 | JMeter | 13 接口 | p95 达标 | < 30min |
| 安全 | OWASP ZAP | 自动扫描 | 0 high | < 15min |
| 兼容 | BrowserStack 手测 | 5 浏览器 | 全过 | 1 工作日 |

### 1.3 测试原则

- **FIRST**: Fast / Independent / Repeatable / Self-validating / Timely
- **AAA**: Arrange / Act / Assert
- **真实数据**: 用工厂 (Factory) 生成, 不写死
- **不留 TODO**: skip 用 @Disabled 标注原因
- **测试代码也要 review**: PR 强制

### 1.4 测试数据

- **Faker**: java-faker 1.0.2, 随机数据
- **Fixture**: src/test/resources/fixtures/*.json
- **DB 隔离**: Testcontainers MySQL 8.0, 每测试一个新库
- **种子**: 字典数据走 Flyway, 测试走 SQL 脚本

## 2. 测试用例矩阵

### 2.1 WBS 任务 (10 类 / 35 例)

| # | 类别 | 场景 | 期望 | 自动化 |
|:---:|---|---|---|:---:|
| 1 | CRUD | 创建根任务 | 200, parentId=null | ✅ |
| 2 | | 创建子任务 | 200, parentId=父 | ✅ |
| 3 | | 更新 wbsCode 重名 | 400 WBS-001 | ✅ |
| 4 | | 更新 progressPct=150 | 400 WBS-002 | ✅ |
| 5 | | 更新 weight=0 | 400 WBS-003 | ✅ |
| 6 | | 软删任务 | deleted_at 设值 | ✅ |
| 7 | 树操作 | 移动到子节点 (环) | 400 WBS-009 | ✅ |
| 8 | | auto-reorder 检测环 | 400 WBS-008, 返回环 | ✅ |
| 9 | 依赖 | predecessorIds 含自身 | 400 WBS-006 | ✅ |
| 10 | | predecessorIds 形成环 (A→B→A) | 400 WBS-006 | ✅ |

### 2.2 EVM (3 类 / 10 例)

| # | 类别 | 场景 | 期望 | 自动化 |
|:---:|---|---|---|:---:|
| 11 | 快照 | 触发快照 (有 BAC) | 200, 写入 snapshot | ✅ |
| 12 | | 触发快照 (无 BAC) | 400 EVM-001 | ✅ |
| 13 | | 趋势 30 天 | 200, 30 条数据 | ✅ |
| 14 | | 趋势 90 天 (含索引) | EXPLAIN 使用索引 | ✅ |
| 15 | | 趋势 0 天 | 200, 空数组 | ✅ |

### 2.3 Risk (8 类 / 25 例)

| # | 类别 | 场景 | 期望 | 自动化 |
|:---:|---|---|---|:---:|
| 16 | CRUD | 创建风险 (P=4, I=5, score=20) | 200, score=20 | ✅ |
| 17 | | 创建 P=6 | 400 RISK-002 | ✅ |
| 18 | | 软删风险 | deleted_at | ✅ |
| 19 | 状态 | OPEN → IN_PROGRESS | 200 | ✅ |
| 20 | | CLOSED → OPEN | 400 RISK-004 | ✅ |
| 21 | | code 重复 | 409 RISK-005 | ✅ |
| 22 | 应对 | 新建应对 | 200 | ✅ |
| 23 | | 删除应对 (软删) | 200 | ✅ |
| 24 | 矩阵 | 5×5 矩阵 50 风险 | 200, count 正确 | ✅ |
| 25 | 健康度 | 3 个高风险 (score>=15) | RED | ✅ |

### 2.4 鉴权 (3 类 / 8 例)

| # | 类别 | 场景 | 期望 | 自动化 |
|:---:|---|---|---|:---:|
| 26 | 角色 | OBSERVER 调 POST | 403 COMMON-003 | ✅ |
| 27 | | DOER 调 POST | 403 COMMON-003 | ✅ |
| 28 | | EXEC 调 GET 矩阵 | 200 | ✅ |
| 29 | 越权 | PM 看其他项目 | 403 / 404 | ✅ |
| 30 | | DOER 看其他用户任务 | 403 / 404 | ✅ |

## 3. 自动化测试

### 3.1 单元测试 (JUnit 5 + Mockito)

**覆盖目标**:
- Service 层: 80% 行覆盖
- Domain 层 (CpmCalculator, RiskHealthCalculator): 100% 行覆盖
- 工具类: 100% 行覆盖

**示例 (CpmCalculator)**:

```java
@DisplayName("CPM 关键路径计算")
class CpmCalculatorTest {
    @InjectMocks CpmCalculator cpm;
    @Mock WbsTaskRepository repo;
    
    @Test
    @DisplayName("3 任务线性依赖, 关键路径 = 全部")
    void linearChain() {
        // Arrange: A → B → C
        WbsTask a = WbsTask.builder().id(1L).duration(3).build();
        WbsTask b = WbsTask.builder().id(2L).duration(5).predecessorIds(List.of(1L)).build();
        WbsTask c = WbsTask.builder().id(3L).duration(2).predecessorIds(List.of(2L)).build();
        when(repo.findByProjectId(1L)).thenReturn(List.of(a, b, c));
        
        // Act
        CpmResult result = cpm.calculate(List.of(a, b, c));
        
        // Assert
        assertThat(a.getEs()).isEqualTo(0);
        assertThat(a.getEf()).isEqualTo(3);
        assertThat(b.getEs()).isEqualTo(3);
        assertThat(b.getEf()).isEqualTo(8);
        assertThat(c.getEs()).isEqualTo(8);
        assertThat(c.getEf()).isEqualTo(10);
        assertThat(result.getCriticalPath()).containsExactlyInAnyOrder(1L, 2L, 3L);
    }
}
```

### 3.2 集成测试 (Spring Boot Test + Testcontainers)

**覆盖目标**:
- Controller 31 端点: 100% 路径覆盖
- Repository: 80% (含自定义 native query)
- 安全链: 5 角色 × 读/写 全部矩阵

**示例 (WbsTaskController)**:

```java
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class WbsTaskControllerIT {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("pmo_test");
    
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    
    @Test
    @WithMockUser(roles = "PM")
    void createTask_duplicateCode_returns400() throws Exception {
        // Given: 已有 wbsCode=1.1
        mvc.perform(post("/api/wbs/tasks")
            .contentType(APPLICATION_JSON)
            .content(taskJson("1.1")))
            .andExpect(status().isOk());
        
        // When: 再建
        mvc.perform(post("/api/wbs/tasks")
            .contentType(APPLICATION_JSON)
            .content(taskJson("1.1")))
            // Then
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(4001))
            .andExpect(jsonPath("$.message", containsString("WBS-001")));
    }
}
```

### 3.3 E2E 测试 (Playwright)

**5 个关键旅程**:

```typescript
// 1. PM 创建 WBS 任务
test('PM 创建项目并新建任务', async ({ page }) => {
  await login(page, 'pm@test.com');
  await page.click('text=新建项目');
  await page.fill('input[name=name]', 'E2E测试项目');
  await page.click('button:has-text("确定")');
  await page.click('text=新建任务');
  await page.fill('input[name=wbsCode]', '1.1');
  await page.fill('input[name=name]', '需求分析');
  await page.click('button:has-text("保存")');
  await expect(page.locator('text=需求分析')).toBeVisible();
});

// 2. PMO 触发快照看 EVM
// 3. PM 创建风险看矩阵
// 4. EXEC 看健康度仪表盘
// 5. DOER 越权被拒
```

### 3.4 CI 集成 (GitLab CI)

```yaml
# .gitlab-ci.yml
stages:
  - unit
  - integration
  - e2e
  - quality

unit:
  stage: unit
  script: ./mvnw test -Punit
  coverage: '/Coverage: \d+.\d+%/'
  artifacts: target/site/jacoco/

integration:
  stage: integration
  services: [docker:dind]
  script: ./mvnw verify -Pintegration
  needs: [unit]

e2e:
  stage: e2e
  image: mcr.microsoft.com/playwright:v1.42
  script: npm run test:e2e
  needs: [integration]

quality:
  stage: quality
  script:
    - sonar-scanner
  needs: [e2e]
```

### 3.5 SonarQube 门禁

- **新代码覆盖率**: ≥ 80%
- **重复率**: ≤ 3%
- **复杂度**: 单方法 ≤ 15
- **Bug**: 0
- **Vulnerability**: 0 high
- **Code Smell**: < 50 / 千行

## 4. 性能/安全/兼容性

### 4.1 性能压测 (JMeter)

**目标** (见 PR-5 §4.1): 13 个关键端点 p95 达标

**场景**:
```xml
<!-- ThreadGroup: 100 并发用户 -->
<ThreadGroup>
  <stringProp name="ThreadGroup.num_threads">100</stringProp>
  <stringProp name="ThreadGroup.ramp_time">30</stringProp>
  <stringProp name="ThreadGroup.duration">300</stringProp>
  <stringProp name="ThreadGroup.delay">0</stringProp>
</ThreadGroup>
```

**采样点**:
- ramp-up 30s (100 用户渐入)
- 持续 5min (5min × 100 用户 = 30000 请求)
- 包含: 70% 读, 25% 写, 5% 复杂查询 (CPM, 趋势 90 天)

**通过标准**:
- p95 < 阈值 (见 PR-5 §4.1)
- 错误率 < 0.1%
- 吞吐量 > 200 QPS

**报告**: jmeter-reports/index.html, 含响应时间分布图

### 4.2 安全扫描 (OWASP ZAP)

**主动扫描**:
- Spider: 爬取所有 /api/* (OpenAPI 导入)
- Active Scan: SQL 注入 / XSS / CSRF / IDOR
- 认证: 4 个测试账号 (各角色)

**通过标准**:
- High: 0
- Medium: < 5
- Low: < 20 (修复可延后)

**报告**: zap-reports/YYYYMMDD.html

### 4.3 兼容性测试

| 浏览器 | 版本 | 自动 | 手动 |
|---|:---:|:---:|:---:|
| Chrome | 100, 120, latest | ✅ (Playwright) | — |
| Edge | 100, 120, latest | ✅ | — |
| Firefox | 100, 120, latest | ✅ | — |
| Safari | 15, 16, 17 | ❌ | ✅ (1 工作日) |
| 移动 Safari | iOS 15+ | ❌ | ✅ |
| Chrome Android | 最新 | ❌ | ✅ |

**通过标准**: 渲染一致, 无 JS 报错, 交互正常

### 4.4 回归测试

**触发条件**:
- PR 合入 main
- 每周一凌晨自动跑全量
- 上线前 1 次全量

**范围**:
- P1/P2 全部 30 个端点 (确保不退化)
- P3 全部 31 个端点

**时长**: 集成测试 < 5min, E2E < 10min, 总计 < 20min

### 4.5 数据迁移验证 (P3 上线前)

- [ ] Staging 跑 Flyway V3 完整流程
- [ ] 备份恢复演练 (2h 备份, 1h 恢复)
- [ ] 索引使用率检查 (EXPLAIN)
- [ ] 数据校验 (count, sum, hash)
- [ ] 性能基线对比 (迁移前 vs 后, ±10% 可接受)

## 5. 缺陷管理

### 5.1 严重级别 (4 级)

| 级别 | 定义 | SLA 修复 | 例子 |
|:---:|---|:---:|---|
| P0 | 阻塞/数据丢失/安全 | 4h | 软删导致唯一约束冲突 |
| P1 | 主流程不可用 | 1 工作日 | 创建任务报 500 |
| P2 | 非主流程有 workaround | 3 工作日 | EVM 图表显示不全 |
| P3 | 体验/UI/文案 | 1 周 | 提示文字错别字 |

### 5.2 缺陷状态机

```
New → Triaged → InProgress → Fixed → Verified → Closed
              ↓                                ↑
              → Rejected ←───────────────── Reopened
```

### 5.3 上线门槛 (硬指标)

| 指标 | 上线最低 | 优秀 |
|---|:---:|:---:|
| P0 缺陷 | 0 | 0 |
| P1 缺陷 | 0 | 0 |
| P2 缺陷 | ≤ 3 | 0 |
| P3 缺陷 | ≤ 10 | ≤ 5 |
| 已知缺陷率 (KDR) | < 2 / 千行 | < 1 / 千行 |
| 漏测率 (生产发现 / 测试发现) | < 10% | < 5% |

### 5.4 缺陷登记模板 (Jira)

```
标题: [模块] 一句话描述
环境: Chrome 120 / Win 11 / staging
复现步骤:
  1. xxx
  2. xxx
期望: xxx
实际: xxx
截图/录屏: ...
日志/traceId: ...
级别: P1
经办: @xxx
```

### 5.5 根因分析 (RCA, 仅 P0/P1)

- 5 Why 分析
- 鱼骨图 (人/机/料/法/环/测)
- 改进措施: 立即 + 短期 + 长期
- 归档到 wiki/RCA/

## 6. 质量门禁

### 6.1 PR 合入门禁 (自动)

```yaml
# GitHub/GitLab branch protection
required_status_checks:
  - unit-test (覆盖率 ≥ 80%)
  - integration-test (全部通过)
  - sonar-scan (0 bug, 0 high vuln)
  - lint (0 error)
  - security-scan (0 high)
```

### 6.2 上线门禁 (W8 前必须全过)

- [ ] 全部自动化测试通过 (单 + 集成 + E2E)
- [ ] SonarQube 0 bug, 0 high vuln
- [ ] OWASP ZAP 0 high, medium < 5
- [ ] JMeter p95 全部达标
- [ ] 兼容性 5 浏览器全过
- [ ] 数据迁移演练成功 (Staging)
- [ ] 回滚演练成功 (Staging)
- [ ] 0 P0/P1 缺陷
- [ ] 0 严重性能问题
- [ ] 文档同步 (API + 错误码 + 部署)

### 6.3 发布后监控 (W6-W8)

- **错误率**: < 0.1%, 超阈值告警
- **p95**: 不劣化 > 20%
- **CPU/内存**: < 70%
- **DB 慢查询**: < 5 / 小时
- **业务指标**: 创建任务数, 风险闭环率, 趋势查询数

### 6.4 度量指标 (每周 review)

| 指标 | 目标 | 实际 | 趋势 |
|---|:---:|:---:|:---:|
| 自动化覆盖率 | 80% | TBD | ↑ |
| 缺陷逃逸率 | < 10% | TBD | ↓ |
| 测试用例执行率 | 100% | TBD | — |
| MTTR (平均修复时间) | < 4h (P0) | TBD | ↓ |
| 部署频率 | 1/周 | TBD | ↑ |
| 变更失败率 | < 5% | TBD | ↓ |

