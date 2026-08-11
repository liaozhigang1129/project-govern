---
status: active
created: 2026-08-11
updated: 2026-08-11
summary: WP-M7-04 v5 可视化与 AI 看板(8 角色仪表盘前端 + 数据质量看板 + 移动端 H5)— 16 子任务 / 3 端 / 14 视图
---

# Plan · WP-M7-04 v5 可视化与 AI 看板

> 对应 WBS 工作包:[`WP-M7-04 v5 可视化与 AI 看板`](../WBS.md#wp-m7-04-v5-可视化与-ai-看板)
> 对应里程碑:**M7**(v5 立项:AI·移动·治理)
> 对应 ADR:[ADR-005 v5 立项范围与关键决策](../decisions/005-m7-v5-scope.md) D3(H5)/D4(导出前端)/D5(数据集前端)/D7(安全前端)
> 对应 spec:
> - [`reporting.md`](../specs/reporting.md) — §1 角色仪表盘 + §5 数据质量治理
> - [`reporting-api.md`](../specs/reporting-api.md) — API 契约(前端消费)
> - [`mobile-h5.md`](../specs/mobile-h5.md) — 移动端 H5
> - [`frontend.md`](../specs/frontend.md) — 前端架构基线
> 对应前置 plan:
> - [`WP-M7-02 v5 数据模型增量`](../plans/2026-08-11-wp-m7-02-v5-data-model.md)
> - [`WP-M7-03 v5 报表后端 + 4 格式导出`](../plans/2026-08-11-wp-m7-03-reporting-export.md)
> 当前状态:**active**(2026-08-11 plan 落地,等 WP-M7-02/03 实施完后正式接入)
> 阻塞项:WP-M7-02 V7.0 schema + WP-M7-03 后端 API 落地

---

## 1. 目标与范围

### 1.1 一句话

实现 **8 角色仪表盘前端 + 数据质量看板(3 指标)+ 移动端 H5(4 Tab + 扫码 + 离线)**,
消费 WP-M7-03 后端 API,完成 v5 治理轴的"最后一公里" — **让用户看到数据**。
**不实现**:NLP 报告(Out of Scope)、第三方 BI 嵌入(D4 已拒)、原生 App(D3 已拒)。

### 1.2 范围内

- **3 端 / 14 视图**:
  - 桌面端 9 视图:8 角色仪表盘 + 数据质量看板
  - 移动端 4 视图:工时 / 任务 / 通知 / 我的
  - 共享 1 视图:登录(已存在,扩展)
- **1 个数据可视化框架集成**:ECharts 6.1(已就位) + vue-echarts 8.0
- **1 个响应式断点系统**:< 768px 切移动端布局(沿用 [mobile-h5.md §7](../specs/mobile-h5.md#7-响应式适配))
- **3 套 Widget 渲染器**:`KpiCardRenderer` / `ChartRenderer` / `TableRenderer`
- **1 个离线缓存模块**:IndexedDB(5 周历史 + 字典)
- **1 个扫码模块**:ZXing JS + Quagga JS(任务二维码)
- **1 个 Web Vitals 监控**:`web-vitals` npm 包(LCP/FID/CLS 上报)
- **1 个 A11y 自动化**:`@axe-core/playwright`(CI 集成)

### 1.3 出范围

- **NLP 报告生成**(Out of Scope,见 ADR-005 §2)
- **第三方 BI 嵌入**(D4 已拒)
- **原生 App**(D3 已拒)
- **报表后端 API**(WP-M7-03 范围,本 plan 只消费)
- **AI 模型训练/影子模式**(另立 AI 训练工作包)

---

## 2. 8 角色仪表盘(8 视图)

> 沿用 [reporting.md §1](../specs/reporting.md#1-角色化仪表盘8-角色--9-内容域) 定义;
> 路由:`/dashboards/role/<roleCode>`,由 `RoleDashboardResolver` 自动 redirect 到默认 dashboard。
> 实现策略:**1 个通用 DashboardView + 8 个角色默认配置**(避免 8 份重复代码)。

### 2.1 角色清单与重点

| 角色 | 默认 dashboard.code | 重点 widget |
|---|---|---|
| **PMO 总监** | DASH_PMO_DIRECTOR_HOME | 组合全景 / 投资分布 / RAG / 战略地图 / KPI 达成率 / Top 风险 |
| **PMO 经理** | DASH_PMO_MANAGER_HOME | 项目集健康度 / 阶段审计 / PM 排名 / 资源热点 |
| **项目经理(PM)** | DASH_PM_HOME | 我的项目 / 待办 / 风险 / 里程碑 / Sprint 燃尽 / 团队利用率 |
| **部门负责人** | DASH_DEPT_LEAD_HOME | 部门资源利用率 / 部门项目 RAG / 关键人风险 |
| **资源经理** | DASH_RESOURCE_MGR_HOME | 资源冲突 / 未来 4 周空缺 / 人员空档 |
| **业务负责人** | DASH_BIZ_OWNER_HOME | 收益达成 / ROI / 关键里程碑 / 变更概览 |
| **财务** | DASH_FINANCE_HOME | 预算 vs 实际 / 应收应付 / 合同回款 |
| **任务执行人** | DASH_TASK_USER_HOME | 我的任务 / Sprint / 待填工时 / 被@/评论 |
| (审计) | DASH_AUDIT_HOME | 操作日志 / 变更日志 / 敏感数据访问 |

### 2.2 通用 `DashboardView.vue`

```vue
<template>
  <div class="dashboard">
    <DashboardHeader :dashboard="data" @refresh="refresh" />
    <WidgetGrid :widgets="data.widgets" :params="filterParams" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { dashboardApi } from '@/api/reporting/dashboard'
import type { DashboardDataDto } from '@/types/reporting'

const route = useRoute()
const data = ref<DashboardDataDto | null>(null)
const filterParams = ref<Record<string, any>>({})

const refresh = async () => {
  const id = Number(route.params.id)
  data.value = await dashboardApi.getData(id, filterParams.value)
}

onMounted(refresh)
</script>
```

### 2.3 路由(扩展 `frontend/src/router/index.ts`)

```ts
{
  path: '/dashboards',
  children: [
    { path: 'role/:roleCode', component: () => import('@/views/reporting/RoleDashboardRedirect.vue') },
    { path: ':id', component: () => import('@/views/reporting/DashboardView.vue'), meta: { roles: ['PMO_DIRECTOR', 'PMO_MANAGER', ...] } },
    { path: ':id/data', component: () => import('@/views/reporting/DashboardDataView.vue') },
  ]
}
```

---

## 3. Widget 渲染器(3 套)

### 3.1 `KpiCardRenderer`

```vue
<template>
  <div class="kpi-card" :class="trend">
    <div class="kpi-label">{{ widget.title }}</div>
    <div class="kpi-value">{{ data.value | formatNumber }}</div>
    <div class="kpi-trend">
      <span v-if="data.delta > 0">↑ {{ data.delta }}%</span>
      <span v-else-if="data.delta < 0">↓ {{ Math.abs(data.delta) }}%</span>
      <span v-else>—</span>
    </div>
  </div>
</template>
```

### 3.2 `ChartRenderer`(ECharts 6.1)

```vue
<template>
  <v-chart :option="chartOption" autoresize class="chart" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, PieChart, GaugeChart, HeatmapChart, SankeyChart, FunnelChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'

use([CanvasRenderer, LineChart, BarChart, PieChart, GaugeChart, HeatmapChart, SankeyChart, FunnelChart,
     GridComponent, TooltipComponent, LegendComponent, TitleComponent])

const props = defineProps<{ widget: WidgetDto; data: any }>()

const chartOption = computed(() => {
  switch (props.widget.chartType) {
    case 'LINE': return buildLineOption(props.data)
    case 'BAR': return buildBarOption(props.data)
    case 'PIE': return buildPieOption(props.data)
    case 'GAUGE': return buildGaugeOption(props.data)
    case 'HEATMAP': return buildHeatmapOption(props.data)
    case 'SANKEY': return buildSankeyOption(props.data)
    case 'FUNNEL': return buildFunnelOption(props.data)
    case 'STACKED': return buildStackedOption(props.data)
    default: return {}
  }
})
</script>
```

### 3.3 `TableRenderer`(虚拟列表)

```vue
<template>
  <el-table-v2 :columns="columns" :data="data.rows" :width="700" :height="400" fixed />
</template>
```

> 用 `element-plus` 2.x 的 `el-table-v2`(虚拟滚动),支持 10,000+ 行流畅。

---

## 4. 数据质量看板(3 指标)

> 沿用 [reporting.md §5](../specs/reporting.md#5-数据质量治理)。
> 路由:`/data-quality`,权限:PMO_ADMIN + 部门负责人。

### 4.1 3 项核心指标

| 指标 | 含义 | 计算 |
|---|---|---|
| **完整率** | 关键字段非空比例 | `count(non_null) / count(total)` per field |
| **准确率** | 业务规则通过比例 | `count(通过校验) / count(total)` per record |
| **时效性** | 7 天内有更新的项目比例 | `count(updated_at > now - 7d) / count(total)` |

### 4.2 异常检测规则

| 异常 | 阈值 | 触发动作 |
|---|---|---|
| 超时未更新 | 项目 7 天无动态 | 建 `data_quality_alert` + 通知 PMO_ADMIN |
| 孤儿任务 | `wbs_task.project_id IS NULL` | 同上 |
| 超载人员 | 连续 4 周 > 50h/周 | 同上 |

### 4.3 `DataQualityView.vue`

```vue
<template>
  <div class="data-quality">
    <KpiCardRenderer :widget="kpiCompleteness" :data="completeness" />
    <KpiCardRenderer :widget="kpiAccuracy" :data="accuracy" />
    <KpiCardRenderer :widget="kpiTimeliness" :data="timeliness" />
    <ChartRenderer :widget="chartTrend" :data="trend" />
    <el-table :data="alerts">
      <el-table-column prop="type" label="异常类型" />
      <el-table-column prop="refId" label="对象" />
      <el-table-column prop="createdAt" label="告警时间" />
      <el-table-column prop="severity" label="严重度" />
    </el-table>
  </div>
</template>
```

### 4.4 后端 API(由 `WP-M7-04` 落地,不在 WP-M7-03)

- `GET /api/data-quality/indicators` — 3 指标(KPI)
- `GET /api/data-quality/trend?days=30` — 趋势(每日 3 指标)
- `GET /api/data-quality/alerts?severity=&from=&to=` — 异常告警列表
- `POST /api/data-quality/alerts/{id}/ack` — 确认告警
- `DataQualityService`(复用 `Alert` 引擎,见 [reporting.md §5](../specs/reporting.md#5-数据质量治理))

---

## 5. 移动端 H5(4 Tab + 4 视图)

> 沿用 [mobile-h5.md §1-§11](../specs/mobile-h5.md);SPA 路由 `/m/*`,与桌面端共享 Pinia。
> 响应式断点 < 768px 自动切移动端布局(`App.vue` 监听 `window.matchMedia`)。

### 5.1 4 Tab 路由

```ts
{
  path: '/m',
  component: () => import('@/layouts/MobileLayout.vue'),
  meta: { requiresMobile: true },
  children: [
    { path: 'timesheet', component: () => import('@/views/mobile/TimesheetView.vue') },     // 工时
    { path: 'tasks', component: () => import('@/views/mobile/TasksView.vue') },              // 任务
    { path: 'notifications', component: () => import('@/views/mobile/NotificationsView.vue') },// 通知
    { path: 'me', component: () => import('@/views/mobile/MeView.vue') },                    // 我的
  ]
}
```

### 5.2 `TimesheetView.vue`(周视图 + FAB)

```vue
<template>
  <div class="m-timesheet">
    <MobileHeader title="本周工时" :actions="['settings']" />
    <WeekSelector v-model="currentWeek" @change="loadWeek" />
    <ProgressBar :current="summary.totalHours" :target="40" :remaining-days="summary.remainingDays" />
    <DayCard v-for="day in days" :key="day.date" :day="day" @edit="editEntry" @delete="deleteEntry" @copy="copyDay" />
    <FabButton icon="+" @click="showAddSheet" />
    <AddEntrySheet v-model:visible="addVisible" @submit="submitEntry" @scan="showScanner" />
    <ScanDialog v-model:visible="scannerVisible" @decoded="onTaskScanned" />
  </div>
</template>
```

### 5.3 离线缓存(IndexedDB)

- 用 `idb` npm 包(轻量 Promise 包装)
- 5 周历史 + 项目/任务字典
- 冲突解决:CRDT 简化版(按 `client_updated_at` last-write-wins + 冲突日志)
- 失败重试:`navigator.onLine === false` 时入"待同步队列",恢复后批量提交

```ts
// composables/useOfflineCache.ts
import { openDB } from 'idb'

const DB_NAME = 'pmo-pms-mobile'
const DB_VERSION = 1

export async function initCache() {
  return openDB(DB_NAME, DB_VERSION, {
    upgrade(db) {
      db.createObjectStore('timesheet_entries', { keyPath: 'id' })
      db.createObjectStore('projects', { keyPath: 'id' })
      db.createObjectStore('tasks', { keyPath: 'id' })
      db.createObjectStore('pending_sync', { keyPath: 'id', autoIncrement: true })
    }
  })
}

export async function cacheWeek(week: string, entries: TimesheetEntry[]) {
  const db = await initCache()
  const tx = db.transaction('timesheet_entries', 'readwrite')
  await Promise.all(entries.map(e => tx.store.put({ ...e, week })))
  await tx.done
}

export async function loadCachedWeek(week: string) {
  const db = await initCache()
  return db.getAllFromIndex('timesheet_entries', 'week', week)
}
```

### 5.4 扫码模块

- 用 `@zxing/browser` + `@zxing/library`(JS 扫码,支持 QR + 条码)
- 任务二维码格式:`pmo-task://<taskId>?projectId=<p>`
- 解码后回填项目 + 任务字段

```ts
// composables/useScanner.ts
import { BrowserMultiFormatReader } from '@zxing/browser'

const reader = new BrowserMultiFormatReader()

export async function startScan(videoEl: HTMLVideoElement) {
  const result = await reader.decodeOnceFromVideoDevice(undefined, videoEl)
  if (result.getText().startsWith('pmo-task://')) {
    const url = new URL(result.getText())
    return {
      taskId: url.pathname.slice(1),
      projectId: url.searchParams.get('projectId')
    }
  }
  return null
}
```

### 5.5 业务规则(强制)

| 规则 | 实现 |
|---|---|
| 每日上限 12h 工作 + 4h 加班 | `validateDailyLimit()` 弹主管确认 |
| 每周目标 40h | `summary` 计算 |
| 跨周修改不允许 | 服务端已有,前端禁用编辑按钮 |
| 必填项 ≥ 5 字符描述 | `el-form` rules |
| 批量提交 > 3 项二次确认 | `ElMessageBox.confirm` |

---

## 6. Web Vitals 监控

- npm:`web-vitals` 5.x(LCP / FID / CLS / INP / TTFB)
- 上报:POST `/api/metrics/web-vitals`(`@WebVitals` 注解,后端写 `metric_event` 表)
- 门禁:`LCP < 2.5s / FID < 100ms / CLS < 0.1`(D8 决策)

```ts
// utils/vitals.ts
import { onLCP, onFID, onCLS, onINP, onTTFB } from 'web-vitals'
import axios from 'axios'

function report(name: string, value: number) {
  axios.post('/api/metrics/web-vitals', { name, value, ts: Date.now() })
}

onLCP(m => report('LCP', m.value))
onFID(m => report('FID', m.value))
onCLS(m => report('CLS', m.value))
onINP(m => report('INP', m.value))
onTTFB(m => report('TTFB', m.value))
```

---

## 7. A11y 自动化(`@axe-core/playwright`)

- npm:`@axe-core/playwright` 4.x + `playwright` 1.40(与后端 PNG 导出统一版本)
- CI:`npm run test:a11y` 跑 E2E + axe-core 扫描
- 门禁:0 critical / 0 serious violation

```ts
// tests/a11y/dashboard.spec.ts
import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

test('PMO Director Dashboard A11y', async ({ page }) => {
  await page.goto('/dashboards/role/PMO_DIRECTOR')
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa'])
    .analyze()
  const critical = results.violations.filter(v =>
    v.impact === 'critical' || v.impact === 'serious')
  expect(critical).toEqual([])
})
```

---

## 8. 响应式 + 移动端布局

- 断点:`< 768px` 移动端,`>= 768px` 桌面端
- `App.vue` 监听 `matchMedia('(max-width: 767px)')`,动态切换 `MobileLayout` / `DesktopLayout`
- 折叠屏 / 平板:`< 1024px` 简化侧边栏(只保留 icon)

```vue
<!-- App.vue -->
<template>
  <component :is="layout" v-if="layout">
    <RouterView />
  </component>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import DesktopLayout from '@/layouts/DesktopLayout.vue'
import MobileLayout from '@/layouts/MobileLayout.vue'

const layout = ref<any>(null)

const updateLayout = () => {
  layout.value = window.matchMedia('(max-width: 767px)').matches
    ? MobileLayout : DesktopLayout
}

onMounted(() => {
  updateLayout()
  window.matchMedia('(max-width: 767px)').addEventListener('change', updateLayout)
})
</script>
```

---

## 9. Pinia store 扩展

- `stores/reporting.ts` — Dashboard / Dataset / Report / Export / Subscription
- `stores/mobile.ts` — currentWeek / offlineQueue / scanner state
- 持久化:`pinia-plugin-persistedstate`(已就位,扩展 `mobile` 命名空间)

```ts
// stores/reporting.ts
import { defineStore } from 'pinia'
import { dashboardApi } from '@/api/reporting/dashboard'

export const useReportingStore = defineStore('reporting', () => {
  const currentDashboard = ref<DashboardDataDto | null>(null)
  const filters = ref<Record<string, any>>({})

  const loadDashboard = async (id: number) => {
    currentDashboard.value = await dashboardApi.getData(id, filters.value)
  }

  return { currentDashboard, filters, loadDashboard }
}, { persist: { storage: sessionStorage } })
```

---

## 10. 实现步骤(顺序执行,每步独立 commit)

### T-01 前端 ECharts 集成(已就位验证)

- 检查 `echarts` 6.1 + `vue-echarts` 8.0 + `element-plus` 2.x + `idb` 7.x 是否在 `frontend/package.json`
- 加:`@zxing/browser` + `@zxing/library` + `web-vitals` 5 + `@axe-core/playwright` 4
- 验证:`npm install` 成功

### T-02 API 客户端层(`frontend/src/api/reporting/`)

- `dashboard.ts` / `dataset.ts` / `report.ts` / `export.ts` / `subscription.ts` / `data-quality.ts`
- TypeScript 类型:对应后端 DTO
- Axios 拦截器:401 → 跳登录,403 → toast
- 验证:`npm run type-check` 成功

### T-03 通用 `DashboardView.vue` + `WidgetGrid.vue`

- 通用布局容器 + 加载状态 + 错误重试
- 单测:`DashboardView.spec.ts` (Vitest)
- 验证:`npm run test:unit` 全绿

### T-04 3 个 Widget 渲染器

- `KpiCardRenderer.vue` / `ChartRenderer.vue` / `TableRenderer.vue`
- 单元测试覆盖:每种 chartType 至少 1 case
- 验证:`npm run test:unit` 全绿

### T-05 8 角色默认配置 + 路由

- `frontend/src/config/role-dashboards.ts`(8 角色默认 dashboard.code 映射)
- 路由 `/dashboards/role/:roleCode` redirect
- 验证:登录 PMO_ADMIN 后跳转到 DASH_PMO_ADMIN_HOME

### T-06 数据质量看板视图

- `DataQualityView.vue` + `KpiCardRenderer` × 3 + `ChartRenderer`(趋势) + `el-table`(告警)
- 单测:`DataQualityView.spec.ts`
- 验证:`npm run test:unit` 全绿

### T-07 数据质量后端 API + 服务(5 端点)

- `DataQualityController` + `DataQualityService`
- 复用 `Alert` 引擎(异常告警)
- 单测 + 集成测试
- 验证:`mvn -B test -Dtest='DataQuality*' -Djacoco.skip=true` 全绿

### T-08 移动端 `MobileLayout.vue` + 4 Tab 路由

- 底部 tab 栏(图标 + 文字)
- 4 子路由(占位页)
- 响应式断点集成(App.vue)
- 验证:`npm run dev` 移动端模拟器 OK

### T-09 `TimesheetView.vue`(周视图 + FAB + 进度条)

- 7 个 DayCard + 周选择器 + 进度条
- 集成现有 `TimesheetController` API
- 单测 + E2E(`tests/e2e/timesheet.spec.ts`)
- 验证:`npm run test:e2e` 全绿

### T-10 添加工时弹层 + 扫码

- `AddEntrySheet.vue`(el-drawer)
- `useScanner.ts`(ZXing)
- `ScanDialog.vue`(getUserMedia)
- 单测:5 case
- 验证:`npm run test:unit` + 手动扫码测试

### T-11 离线缓存模块(IndexedDB)

- `useOfflineCache.ts`(idb wrapper)
- 5 周历史 + 字典缓存
- 冲突解决(CRDT 简化)
- 单元测试:`useOfflineCache.spec.ts`(缓存读写 + 冲突 5 case)
- 验证:`npm run test:unit` 全绿

### T-12 业务规则 + 9 项强制规则

- 每日上限 / 每周目标 / 必填项 / 批量确认
- `composables/useTimesheetValidation.ts`
- 单元测试
- 验证:`npm run test:unit` 全绿

### T-13 Web Vitals 监控

- `utils/vitals.ts`(web-vitals 5.x)
- 上报 API:后端 `MetricController` + `metric_event` 表
- 验证:浏览器 console 看到上报 + 后端 `metric_event` 收到

### T-14 A11y 自动化

- `@axe-core/playwright` 配置
- 8 角色仪表盘 + 4 移动端 Tab 扫描
- CI 集成:`npm run test:a11y`
- 验证:0 critical / 0 serious violation

### T-15 性能优化

- 路由懒加载(已就位,验证)
- 组件按需引入(`unplugin-vue-components`)
- ECharts 按需引入(已用 `use()`)
- 图片懒加载(`el-image` lazy)
- 验证:`npm run build` 产物 < 2MB(gzip)

### T-16 端到端 E2E + 文档同步

- `tests/e2e/reporting.spec.ts`(桌面端 8 角色 + 看板)
- `tests/e2e/mobile.spec.ts`(移动端 4 Tab + 离线 + 扫码)
- `WBS.md` / `STATUS.md` / `CHANGELOG.md` 同步
- `make docs-lint` 全绿

---

## 11. 验收标准(DoD)

### 11.1 前端

- [ ] 8 角色默认 dashboard 全部上线,角色 → dashboard 映射表可配置
- [ ] 9 类报表模板至少落地 5 类(状态 / EVM / 资源 / 风险 / 预算)
- [ ] 数据治理看板 3 指标(完整率 / 准确率 / 时效性)可视化
- [ ] 移动端 4 Tab 全部可用
- [ ] 扫码 @ 任务可用(ZXing)
- [ ] 离线缓存可用(IndexedDB 5 周)
- [ ] Web Vitals 上报(LCP / FID / CLS)
- [ ] A11y 通过 axe-core(0 critical / 0 serious)

### 11.2 性能(D8 门禁)

- [ ] H5 首屏 < 1s
- [ ] H5 滑动 60fps
- [ ] LCP < 2.5s / FID < 100ms / CLS < 0.1
- [ ] 桌面端首屏 < 2s

### 11.3 测试

- [ ] `npm run test:unit` 全绿
- [ ] `npm run test:e2e` 全绿(桌面 + 移动)
- [ ] `npm run test:a11y` 全绿(0 violation)
- [ ] `mvn -B test -Djacoco.skip=true` 现有测试不破坏
- [ ] `mvn -B test -Dtest='DataQuality*' -Djacoco.skip=true` 新增 5 端点 + 集成测试全过

### 11.4 文档

- [ ] `WBS.md` WP-M7-04 状态 → 🟡 active
- [ ] `STATUS.md` last_head 同步
- [ ] `CHANGELOG.md` M7-04 entry
- [ ] `make docs-lint` 全绿

---

## 12. 风险登记

| # | 风险 | 概率 | 影响 | 缓解 |
|:--:|---|:--:|:--:|---|
| R-M7-04-01 | ECharts 按需引入不全,导致打包过大 | 中 | 中 | T-01 显式 `use([...])` 注册 + T-15 验证 < 2MB |
| R-M7-04-02 | IndexedDB 在 Safari iOS 隐私模式不可用 | 高 | 中 | T-11 feature detect + fallback 到 localStorage(只缓存 1 周) |
| R-M7-04-03 | ZXing 在低端 Android 卡顿 | 中 | 中 | T-10 改用 `quagga2`(纯 JS 条码,无视频解码) |
| R-M7-04-04 | A11y 大量 violation 短期难修 | 中 | 中 | T-14 标记 `known issues`,D8 门禁仅 critical/serious 必过 |
| R-M7-04-05 | 8 角色 dashboard 重复工作量 | 高 | 中 | T-05 1 个通用 `DashboardView` + 8 角色配置,避免 8 份重复 |
| R-M7-04-06 | Web Vitals 上报量爆炸 | 中 | 低 | T-13 采样 10%(用 `web-vitals` 自带 `reportAllChanges: false`) |
| R-M7-04-07 | 移动端路由与桌面端冲突 | 低 | 中 | T-08 独立 `/m/*` 命名空间,App.vue 监听断点切换 |
| R-M7-04-08 | 数据质量看板 3 指标计算慢(全表扫描) | 中 | 中 | T-07 用 `report_snapshot` 物化数据(沿用 D5 决策) |

---

## 13. 关联

- WBS:[`WP-M7-04 v5 可视化与 AI 看板`](../WBS.md#wp-m7-04-v5-可视化与-ai-看板)(本 plan 落地)
- Spec:
  - [`reporting.md`](../specs/reporting.md) — §1 角色仪表盘 + §5 数据质量治理
  - [`reporting-api.md`](../specs/reporting-api.md) — API 契约
  - [`mobile-h5.md`](../specs/mobile-h5.md) — 移动端 H5
  - [`frontend.md`](../specs/frontend.md) — 前端架构基线
- Plan:
  - [`WP-M7-01 v5 立项评审`](../plans/2026-08-07-wp-m7-01-v5-scope-freeze.md)(前置依赖)
  - [`WP-M7-02 v5 数据模型增量`](../plans/2026-08-11-wp-m7-02-v5-data-model.md)(前置依赖 V7.0 schema)
  - [`WP-M7-03 v5 报表后端 + 4 格式导出`](../plans/2026-08-11-wp-m7-03-reporting-export.md)(前置依赖后端 API)
- ADR:[ADR-005 v5 立项范围与关键决策](../decisions/005-m7-v5-scope.md) D3(H5)/D4(导出前端)/D5(数据集前端)/D7(安全前端)/D8(门禁)

---

## 评审记录

| 日期 | 评审人 | 意见 |
|---|---|---|
| 2026-08-11 | PMO | 通过 plan,等 WP-M7-03 后端 API 落地后启动前端 |
| 2026-08-11 | 架构师 | 通过 §2-§5 设计,T-05 1 通用 + 8 配置 方案可行 |
| 2026-08-11 | 前端 | 通过 §3 渲染器拆分,§5.4 扫码 ZXing 方案 OK |
| 2026-08-11 | QA | 通过 §11 验收 + §12 风险,A11y 用 axe-core 自动化 OK |
| 2026-08-11 | SRE | 通过 §6 Web Vitals 上报,建议采样 10% 降低后端压力 |
| ⏳ D+7 | Sponsor | 待整合会议拍板 → 启动 |