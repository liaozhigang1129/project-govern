---
status: active
created: 2026-08-07
updated: 2026-08-07
summary: 测试策略(3 层金字塔 + 单测约定 + 跑测命令)
---

# 测试策略(Testing)

> 单一事实来源:3 层测试金字塔、单测文件清单、跑测命令、测试约定。
> 对应来源:[`legacy/pmo-pms-mvp-design.md` §9](legacy/pmo-pms-mvp-design.md)

---

## 1. 3 层金字塔

```
        ╱  ╲
       ╱ E2E╲           Cypress / Node 18 零依赖
      ╱──────╲          慢,贵,模拟真实用户
     ╱ 契约测试 ╲        Postman collection(29) + smoke.sh(16)
    ╱────────────╲       中速,验 API 契约
   ╱   单元/集成    ╲     JUnit 5 + H2 (PG mode)
  ╱──────────────────╲   快速,验业务规则
```

| 层 | 工具 | 数量 | 跑在哪 |
|---|---|---|---|
| **单元 / 集成** | JUnit 5 + AssertJ + `@DataJpaTest` (H2) | 78+ | `mvn test` · CI `backend-test` |
| **契约** | Postman + Newman + 自写 smoke.sh | 29 + 16 | CI `integration-smoke` · 开发者本地 |
| **E2E** | Node 18 原生 `fetch`(零依赖) | 30 (4 suite) | CI 不跑(可加)· 开发者本地 |
| **E2E(选装)** | Cypress 13 | 4 case | 开发者本地 GUI,**不进 CI**(避免冗余) |

---

## 2. 测试约定

- **不 mock 太多**:`@DataJpaTest` 直接用 H2 跑真 SQL,验证 JPQL
- **不 mock 安全**:`@SpringBootTest` + `@WithMockUser`,**真实过一遍过滤器链**
- **测试数据**:`@BeforeEach` 现场 seed 字典,不用 SQL fixture
- **断言**:AssertJ 链式 `assertThat(x).isEqualTo(y)`,不写 JUnit 老的 `assertEquals`

---

## 3. 跑测命令

```bash
# 后端
cd backend && mvn test          # 78+/78+ ✅(H2 in-memory)

# 契约
bash docs/testing/postman/smoke.sh    # 16 个核心调用 ✅
# 或 newman 跑 Postman
newman run docs/testing/postman/pmo-pms.postman_collection.json \
  -e docs/testing/postman/pmo-pms.postman_environment.json

# E2E(需前后端都起着)
cd frontend && pnpm e2e         # 4 suite 30 case ✅
```

---

## 4. 测试方案(testing/)目录约定

- `strategy.md`(本文档):测试金字塔、覆盖目标、门禁标准
- 每个特性/模块一个测试方案文件,从对应 spec 派生(spec 是测试的判定基准)
- 新增模块时:spec 改了 → 对应 testing/ 文件必须同步更新
