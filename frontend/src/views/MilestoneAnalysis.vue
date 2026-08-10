<script setup lang="ts">
/**
 * 项目里程碑分析 (V3.1:按 PHASE 桶 = 7 阶段)
 *  - 主视图: 7 个 phase 桶卡片 (立项/需求/设计/开发/测试/上线运维/维保)
 *  - 下钻: 点击 phase 桶 → status 子桶弹窗 → milestone name → 命中项目列表 (3 级)
 *  - 范围: company/bu/pl (按角色自动限权; PL 只能看自己)
 *  - 部门: 树形选择器 (el-tree-select)
 *
 * 数据: GET /api/milestones/analysis/distribution + /api/milestones/analysis/projects
 */
import { computed, onMounted, ref, watch } from 'vue'
import { Calendar, Flag, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import VChart from 'vue-echarts'

import { useAuthStore } from '@/stores/auth'
import {
  fetchAnalysis,
  fetchDrillDown,
  PHASE_LIST,
  STATUS_LIST,
  type MilestoneAnalysis,
  type MilestoneDrillDown,
  type MilestoneProjectRow,
  type MilestoneAnalysisQuery,
} from '@/api/milestoneAnalysis'
import { departmentApi, type DepartmentNode } from '@/api/departments'
import api from '@/api/client'

const auth = useAuthStore()

// ============== 筛选条件 ==============
const scope = ref<MilestoneAnalysisQuery['scope']>('company')
const period = ref<MilestoneAnalysisQuery['period']>('this_month')
const customFrom = ref<string>('')
const customTo = ref<string>('')
const buId = ref<number | null>(null)
const plId = ref<number | null>(null)

// 角色:PMO_ADMIN/EXEC 限 company 之外可选 bu/pl;DEPT_LEAD 默认 bu;PM 限 pl
const userRole = computed<string>(() => (auth.user as any)?.role ?? '')
const scopeOptions = computed(() => {
  const opts: { value: 'company' | 'bu' | 'pl'; label: string }[] = [{ value: 'company', label: '全公司' }]
  if (['PMO_ADMIN', 'ADMIN', 'EXEC', 'DEPT_LEAD'].includes(userRole.value)) {
    opts.push({ value: 'bu', label: '按部门 (BU)' })
  }
  if (['PMO_ADMIN', 'ADMIN', 'EXEC', 'DEPT_LEAD', 'PM'].includes(userRole.value)) {
    opts.push({ value: 'pl', label: '按项目经理 (PL)' })
  }
  return opts
})

// ============== 部门树 + PL 列表 ==============
const deptTree = ref<DepartmentNode[]>([])
const plList = ref<Array<{ id: number; fullName: string; username: string }>>([])
async function loadDeptTree() {
  try {
    deptTree.value = await departmentApi.tree()
  } catch (e) {
    ElMessage.error('加载部门树失败: ' + (e as any).message)
  }
}
async function loadPlList() {
  try {
    const users: any = await api.get('/users', { params: { page: 0, size: 200, roleCode: 'PM' } })
    const raw: any[] = Array.isArray(users)
      ? users
      : (users?.content ?? users?.data?.content ?? users?.data ?? [])
    plList.value = raw.map((u: any) => ({
      id: u.id,
      fullName: u.fullName ?? u.username,
      username: u.username,
    }))
  } catch (e) {
    ElMessage.error('加载 PL 列表失败: ' + (e as any).message)
  }
}
function onBuChange() {
  plId.value = null
}

// ============== 主视图加载 ==============
const data = ref<MilestoneAnalysis | null>(null)
const loading = ref(false)
const viewMode = ref<'grid' | 'matrix' | 'bar' | 'pie'>('grid')

// ============== 图表 ==============
/** 7x4 矩阵热力图配置 */
const matrixOption = computed(() => {
  if (!data.value) return null
  const phases = data.value.byPhase ?? []
  const phId = phases.map((b) => b.phaseId)
  const phName = phases.map((b) => phaseMeta(b.phaseId).name)
  // status 在 Y 轴 (下→上: PENDING/IN_PROGRESS/COMPLETED/DELAYED)
  const stList = STATUS_LIST
  // 数据点 [x=phaseIndex, y=statusIndex, value=count]
  const cells: [number, number, number][] = []
  let max = 0
  phases.forEach((b, i) => {
    const bucket = data.value!.phases?.[b.phaseId]
    stList.forEach((s, j) => {
      const c = bucket?.byStatus?.[s.code] ?? 0
      cells.push([i, j, c])
      if (c > max) max = c
    })
  })
  return {
    tooltip: {
      position: 'top',
      formatter: (p: any) => {
        const phase = phName[p.data[0]]
        const status = stList[p.data[1]]
        return `${phase} · ${status.name}<br/><b>${p.data[2]}</b> 个里程碑`
      },
    },
    grid: { top: 30, left: 80, right: 30, bottom: 60 },
    xAxis: {
      type: 'category',
      data: phName,
      splitArea: { show: true },
      axisLabel: { color: '#606266', fontSize: 12 },
    },
    yAxis: {
      type: 'category',
      data: stList.map((s) => s.name),
      splitArea: { show: true },
      axisLabel: { color: '#606266', fontSize: 12 },
    },
    visualMap: {
      min: 0,
      max: Math.max(max, 1),
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: 5,
      inRange: { color: ['#f5f7fa', '#67c23a'] },
      textStyle: { color: '#606266' },
    },
    series: [
      {
        name: '里程碑数',
        type: 'heatmap',
        data: cells,
        label: {
          show: true,
          color: '#fff',
          fontWeight: 600,
          formatter: (p: any) => p.data[2] || '',
        },
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        emphasis: {
          itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.3)' },
        },
      },
    ],
  }
})

/** 7 phase 堆叠柱状图配置 */
const barOption = computed(() => {
  if (!data.value) return null
  const phases = data.value.byPhase ?? []
  const stList = STATUS_LIST
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 0, textStyle: { color: '#606266' } },
    grid: { top: 50, left: 50, right: 30, bottom: 30 },
    xAxis: {
      type: 'category',
      data: phases.map((b) => phaseMeta(b.phaseId).name),
      axisLabel: { color: '#606266' },
    },
    yAxis: { type: 'value', axisLabel: { color: '#909399' } },
    series: stList.map((s) => ({
      name: s.name,
      type: 'bar',
      stack: 'total',
      barWidth: '60%',
      emphasis: { focus: 'series' },
      itemStyle: {
        color: s.color,
        borderRadius: s === stList[stList.length - 1] ? [4, 4, 0, 0] : 0,
        opacity: highlightedStatus.value && highlightedStatus.value !== s.code ? 0.3 : 1,
      },
      data: phases.map((b) => data.value!.phases?.[b.phaseId]?.byStatus?.[s.code] ?? 0),
    })),
  }
})

/** 4 status 饼图配置 (本期总量) */
const pieOption = computed(() => {
  if (!data.value) return null
  const phases = data.value.byPhase ?? []
  const stList = STATUS_LIST
  const totals: Record<string, number> = {}
  stList.forEach((s) => {
    totals[s.code] = 0
  })
  phases.forEach((b) => {
    const bucket = data.value!.phases?.[b.phaseId]
    if (bucket)
      stList.forEach((s) => {
        totals[s.code] += bucket.byStatus?.[s.code] ?? 0
      })
  })
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { color: '#606266' } },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}\n{c}' },
        labelLine: { show: true, length: 10, length2: 10 },
        data: stList.map((s) => ({ name: s.name, value: totals[s.code], itemStyle: { color: s.color } })),
      },
    ],
  }
})

/** 矩阵单元格点击 → 复用 nameDrill, 带 phaseId+statusCode 过滤 */
function onMatrixClick(params: any) {
  // heatmap 点击 params.data = [phaseIndex, statusIndex, value]
  if (!params.data) return
  const [phaseIdx, statusIdx] = params.data
  const phaseId = data.value?.byPhase?.[phaseIdx]?.phaseId
  const statusCode = STATUS_LIST[statusIdx]?.code
  if (phaseId == null || !statusCode) return
  openNameDrill(phaseId, statusCode)
}

/** 饼图扇形高亮 status code (联动柱状图) */
const highlightedStatus = ref<string | null>(null)
/** 鼠标移到饼图扇形 → 高亮柱状图同 status 段 */
function onPieHover(params: any) {
  highlightedStatus.value = params?.name
    ? (STATUS_LIST.find((s) => s.name === params.name)?.code ?? null)
    : null
}
function onPieLeave() {
  highlightedStatus.value = null
}

/** 导出当前视图为 PNG (ECharts getDataURL) */
async function exportCharts() {
  const chart = document.querySelector<HTMLElement>('.ma-chart > div, .ma-chart canvas')
  // vue-echarts 8.x 暴露 instance 通过 v-chart 元素的 __echarts__ 属性
  const vChartEl = document.querySelector<any>('.ma-chart')
  const inst = vChartEl?.__echarts__ || vChartEl?.$_echarts_instance
  if (!inst) {
    ElMessage.warning('当前视图无图表, 无需导出')
    return
  }
  const url = inst.getDataURL({ pixelRatio: 2, backgroundColor: '#fff' })
  const a = document.createElement('a')
  a.href = url
  const tag = viewMode.value
  a.download = `里程碑分析-${tag}-${new Date().toISOString().slice(0, 10)}.png`
  a.click()
  ElMessage.success('已导出 PNG')
}

async function load() {
  loading.value = true
  try {
    const params: MilestoneAnalysisQuery = {
      scope: scope.value,
      period: period.value,
    }
    if (period.value === 'custom') {
      if (!customFrom.value || !customTo.value) {
        ElMessage.warning('自定义模式请选择开始和结束日期')
        return
      }
      params.from = customFrom.value
      params.to = customTo.value
    }
    if (scope.value === 'bu' && buId.value) params.buId = buId.value
    if (scope.value === 'pl' && plId.value) params.plId = plId.value
    data.value = await fetchAnalysis(params)
  } catch (e) {
    ElMessage.error('加载失败: ' + (e as any).message)
    data.value = null
  } finally {
    loading.value = false
  }
}

// 切换 scope → 清掉无关条件 → 重载
watch(scope, () => {
  if (scope.value !== 'bu') buId.value = null
  if (scope.value !== 'pl') plId.value = null
  load()
})
watch([period, buId, plId, customFrom, customTo], () => load())

onMounted(async () => {
  await Promise.all([loadDeptTree(), loadPlList()])
  await load()
})

// ============== 7 phase 视图 ==============
function phaseMeta(id: number) {
  return PHASE_LIST.find((p) => p.id === id) ?? { id, code: '?', name: '?', color: '#909399' }
}
function statusMeta(code: string) {
  return STATUS_LIST.find((s) => s.code === code) ?? { code, name: code, color: '#909399' }
}
function phaseBucket(phaseId: number) {
  return data.value?.phases[String(phaseId)]
}

// ============== 2 级弹窗: status 子桶 ==============
const phaseDrill = ref({
  visible: false,
  loading: false,
  phaseId: null as number | null,
  phaseName: '',
  data: null as MilestoneDrillDown | null,
})
async function openPhaseDrill(phaseId: number) {
  const meta = phaseMeta(phaseId)
  phaseDrill.value = {
    visible: true,
    loading: true,
    phaseId,
    phaseName: meta.name,
    data: null,
  }
  try {
    phaseDrill.value.data = await fetchDrillDown(buildDrillParams({ phaseId }))
  } catch (e) {
    ElMessage.error('加载阶段详情失败: ' + (e as any).message)
  } finally {
    phaseDrill.value.loading = false
  }
}

// ============== 3 级弹窗: 命中项目 ==============
const nameDrill = ref({
  visible: false,
  loading: false,
  phaseId: null as number | null,
  phaseName: '',
  statusCode: '' as string,
  statusName: '',
  milestoneName: '',
  data: null as MilestoneDrillDown | null,
})
function buildDrillParams(extra: { phaseId?: number; statusCode?: string; milestoneName?: string } = {}) {
  const params: any = { scope: scope.value, period: period.value }
  if (period.value === 'custom') {
    params.from = customFrom.value
    params.to = customTo.value
  }
  if (scope.value === 'bu' && buId.value) params.buId = buId.value
  if (scope.value === 'pl' && plId.value) params.plId = plId.value
  if (extra.phaseId != null) params.phaseId = extra.phaseId
  if (extra.statusCode) params.statusCode = extra.statusCode
  if (extra.milestoneName) params.milestoneName = extra.milestoneName
  return params
}
async function openNameDrill(
  phaseId: number,
  statusCode?: string,
  statusName?: string,
  milestoneName?: string,
) {
  const phaseMetaObj = phaseMeta(phaseId)
  nameDrill.value = {
    visible: true,
    loading: true,
    phaseId,
    phaseName: phaseMetaObj.name,
    statusCode: statusCode ?? '',
    statusName: statusName ?? '',
    milestoneName: milestoneName ?? '',
    data: null,
  }
  try {
    nameDrill.value.data = await fetchDrillDown(
      buildDrillParams({
        phaseId,
        statusCode,
        milestoneName,
      }),
    )
  } catch (e) {
    ElMessage.error('加载命中项目失败: ' + (e as any).message)
  } finally {
    nameDrill.value.loading = false
  }
}

// 当前范围标签 (用于下钻弹窗 footer)
const scopeLabel = computed(() => {
  if (scope.value === 'bu' && buId.value) {
    const findName = (nodes: DepartmentNode[]): string | null => {
      for (const n of nodes) {
        if (n.id === buId.value) return n.name
        if (n.children?.length) {
          const r = findName(n.children)
          if (r) return r
        }
      }
      return null
    }
    return findName(deptTree.value) ?? `部门#${buId.value}`
  }
  if (scope.value === 'pl' && plId.value) {
    const p = plList.value.find((x) => x.id === plId.value)
    return p ? `${p.fullName} (${p.username})` : `PM#${plId.value}`
  }
  return '全公司'
})
</script>
<template>
  <div class="ma-page" style="padding: 16px">
    <!-- 顶部筛选条 -->
    <el-page-header :icon="null" style="margin-bottom: 12px">
      <template #content>
        <div style="display: flex; align-items: center; gap: 8px">
          <el-icon><Flag /></el-icon>
          <span style="font-size: 18px; font-weight: 600">项目里程碑分析</span>
          <el-tag v-if="data" type="info" effect="plain" size="small">
            <el-icon style="vertical-align: middle"><Calendar /></el-icon>
            {{ data.periodLabel }} · {{ scopeLabel }}
          </el-tag>
        </div>
      </template>
      <template #extra>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </el-page-header>

    <el-card shadow="never" style="margin-bottom: 16px">
      <div class="ma-filter">
        <div class="ma-filter-item">
          <label>数据范围</label>
          <el-select v-model="scope" style="width: 160px" placeholder="选择范围">
            <el-option v-for="o in scopeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </div>

        <div v-if="scope === 'bu'" class="ma-filter-item">
          <label>部门 (BU)</label>
          <el-tree-select
            v-model="buId"
            :data="deptTree"
            :props="{ value: 'id', label: 'name', children: 'children' }"
            placeholder="选择部门(树形)"
            check-strictly
            clearable
            style="width: 240px"
            @change="onBuChange"
          />
        </div>

        <div v-if="scope === 'pl'" class="ma-filter-item">
          <label>项目经理 (PL)</label>
          <el-select v-model="plId" placeholder="选择项目经理" filterable clearable style="width: 240px">
            <el-option
              v-for="p in plList"
              :key="p.id"
              :label="`${p.fullName} (${p.username})`"
              :value="p.id"
            />
          </el-select>
        </div>

        <div class="ma-filter-item">
          <label>时间窗</label>
          <el-select v-model="period" style="width: 140px">
            <el-option label="本周" value="this_week" />
            <el-option label="本月" value="this_month" />
            <el-option label="下周" value="next_week" />
            <el-option label="下月" value="next_month" />
            <el-option label="自定义" value="custom" />
          </el-select>
        </div>

        <div v-if="period === 'custom'" class="ma-filter-item">
          <label>起止日期</label>
          <el-date-picker
            v-model="customFrom"
            type="date"
            placeholder="开始"
            value-format="YYYY-MM-DD"
            style="width: 150px"
          />
          <span>~</span>
          <el-date-picker
            v-model="customTo"
            type="date"
            placeholder="结束"
            value-format="YYYY-MM-DD"
            style="width: 150px"
          />
        </div>
      </div>
    </el-card>

    <!-- 总数 -->
    <div v-if="data" class="ma-summary">
      <el-statistic
        title="窗口内总里程碑数 (7 阶段累计)"
        :value="data.totalMilestones"
        :value-style="{ color: '#303133', fontSize: '20px', fontWeight: 600 }"
      />
    </div>

    <!-- 视图模式切换 -->
    <div class="ma-view-switch">
      <el-button-group style="margin-right: 8px">
        <el-button size="default" @click="exportCharts" :disabled="!data">
          <el-icon><Download /></el-icon>
          <span>导出图表</span>
        </el-button>
      </el-button-group>
      <el-radio-group v-model="viewMode" size="default">
        <el-radio-button value="grid">📊 7 桶卡片</el-radio-button>
        <el-radio-button value="matrix">🔥 7×4 热力图</el-radio-button>
        <el-radio-button value="bar">📈 堆叠柱状图</el-radio-button>
        <el-radio-button value="pie">🥧 status 分布</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 7 phase 桶卡片 -->
    <div v-loading="loading" class="ma-buckets">
      <div
        v-for="bucket in data?.byPhase ?? []"
        :key="bucket.phaseId"
        class="ma-bucket"
        :class="{ 'is-empty': bucket.count === 0 }"
        :style="{
          '--phase-color': phaseMeta(bucket.phaseId).color,
          borderTop: `4px solid ${phaseMeta(bucket.phaseId).color}`,
        }"
        @click="openPhaseDrill(bucket.phaseId)"
      >
        <div class="ma-bucket-header">
          <div class="ma-bucket-title">
            <el-icon class="ma-bucket-icon"><Flag /></el-icon>
            <span class="ma-bucket-name">{{ bucket.phaseName }}</span>
            <el-tag size="small" effect="plain" type="info">#{{ bucket.phaseId }}</el-tag>
          </div>
          <span class="ma-bucket-count" :style="{ color: phaseMeta(bucket.phaseId).color }">
            {{ bucket.count }}
          </span>
        </div>

        <!-- 内嵌 4 段 status 进度条 -->
        <div v-if="bucket.count > 0" class="ma-status-bar">
          <div
            v-for="s in STATUS_LIST"
            :key="s.code"
            class="ma-status-seg"
            :style="{
              background: s.color,
              flex: phaseBucket(bucket.phaseId)?.byStatus?.[s.code] ?? 0,
            }"
            :title="`${s.name}: ${phaseBucket(bucket.phaseId)?.byStatus?.[s.code] ?? 0}`"
          />
        </div>
        <div v-else class="ma-status-bar-empty" />

        <!-- status 数字小行 -->
        <div v-if="bucket.count > 0" class="ma-status-legend">
          <div
            v-for="s in STATUS_LIST"
            :key="s.code"
            class="ma-status-item"
            :class="{ 'ma-status-zero': !phaseBucket(bucket.phaseId)?.byStatus?.[s.code] }"
          >
            <span class="ma-status-dot" :style="{ background: s.color }" />
            <span class="ma-status-label">{{ s.name }}</span>
            <span class="ma-status-num">{{ phaseBucket(bucket.phaseId)?.byStatus?.[s.code] ?? 0 }}</span>
          </div>
        </div>

        <!-- 桶内 name 明细 (前 5 个, 多的 +N) -->
        <div class="ma-bucket-names" @click.stop>
          <div
            v-for="(item, idx) in (phaseBucket(bucket.phaseId)?.byName ?? []).slice(0, 5)"
            :key="`${bucket.phaseId}-${idx}`"
            class="ma-name-row"
            @click.stop="
              openNameDrill(bucket.phaseId, item.statusCode, statusMeta(item.statusCode).name, item.name)
            "
          >
            <span class="ma-name">{{ item.name }}</span>
            <el-tag
              size="small"
              effect="plain"
              :color="statusMeta(item.statusCode).color"
              :style="{ color: '#fff', border: 'none' }"
            >
              ×{{ item.count }}
            </el-tag>
          </div>
          <div
            v-if="(phaseBucket(bucket.phaseId)?.byName?.length ?? 0) > 5"
            class="ma-name-row ma-name-more"
            @click.stop="openPhaseDrill(bucket.phaseId)"
          >
            +{{ (phaseBucket(bucket.phaseId)?.byName?.length ?? 0) - 5 }} 更多...
          </div>
          <div v-if="!phaseBucket(bucket.phaseId)?.byName?.length" class="ma-muted">无数据</div>
        </div>
      </div>
    </div>

    <!-- 7×4 矩阵热力图 -->
    <div v-if="viewMode === 'matrix'" v-loading="loading" class="ma-chart-card">
      <div class="ma-chart-title">
        <el-icon><DataLine /></el-icon>
        <span>7 阶段 × 4 状态 矩阵热力图</span>
        <span class="ma-chart-sub">点击单元格可下钻到命中项目</span>
      </div>
      <v-chart
        v-if="matrixOption && (data?.byPhase?.length ?? 0) > 0"
        :option="matrixOption"
        :init-options="{ renderer: 'canvas' }"
        class="ma-chart"
        @click="onMatrixClick"
      />
      <el-empty v-else description="暂无数据" :image-size="80" />
    </div>

    <!-- 7 phase 堆叠柱状图 -->
    <div v-if="viewMode === 'bar'" v-loading="loading" class="ma-chart-card">
      <div class="ma-chart-title">
        <el-icon><Histogram /></el-icon>
        <span>7 阶段里程碑分布 (按 status 堆叠)</span>
        <span class="ma-chart-sub">鼠标移入饼图扇形, 柱状图联动高亮</span>
      </div>
      <v-chart
        v-if="barOption && (data?.byPhase?.length ?? 0) > 0"
        :option="barOption"
        :init-options="{ renderer: 'canvas' }"
        class="ma-chart"
      />
      <el-empty v-else description="暂无数据" :image-size="80" />
    </div>

    <!-- 4 status 饼图 (全量分布) -->
    <div v-if="viewMode === 'pie'" v-loading="loading" class="ma-chart-card">
      <div class="ma-chart-title">
        <el-icon><PieChart /></el-icon>
        <span>本期里程碑状态分布 (4 status 全量)</span>
        <span class="ma-chart-sub">鼠标移入扇形, 柱状图联动</span>
      </div>
      <v-chart
        v-if="pieOption && (data?.byPhase?.length ?? 0) > 0"
        :option="pieOption"
        :init-options="{ renderer: 'canvas' }"
        class="ma-chart"
        @mouseover="onPieHover"
        @mouseout="onPieLeave"
      />
      <el-empty v-else description="暂无数据" :image-size="80" />
    </div>

    <!-- 2 级弹窗: status 子桶 + 里程碑名列表 -->
    <el-dialog
      v-model="phaseDrill.visible"
      :title="`${phaseDrill.phaseName} · 阶段详情`"
      width="800px"
      :close-on-click-modal="false"
    >
      <el-skeleton v-if="phaseDrill.loading" :rows="6" animated />
      <template v-else-if="phaseDrill.data">
        <el-descriptions :column="3" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="范围">{{ scopeLabel }}</el-descriptions-item>
          <el-descriptions-item label="周期">
            {{ phaseDrill.data.from }} ~ {{ phaseDrill.data.to }}
          </el-descriptions-item>
          <el-descriptions-item label="命中项目">{{ phaseDrill.data.total }}</el-descriptions-item>
        </el-descriptions>

        <h4 style="margin: 0 0 8px">按状态分</h4>
        <div class="ma-status-pills">
          <div
            v-for="s in STATUS_LIST"
            :key="s.code"
            class="ma-status-pill"
            :style="{ borderLeft: `3px solid ${s.color}` }"
          >
            <span class="ma-pill-name">{{ s.name }}</span>
            <span class="ma-pill-num">
              {{ phaseBucket(phaseDrill.phaseId ?? 0)?.byStatus?.[s.code] ?? 0 }}
            </span>
          </div>
        </div>

        <h4 style="margin: 16px 0 8px">里程碑名列表 (按 status 着色)</h4>
        <el-table :data="phaseBucket(phaseDrill.phaseId ?? 0)?.byName ?? []" stripe size="small">
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag
                size="small"
                :style="{ background: statusMeta(row.statusCode).color, color: '#fff', border: 'none' }"
              >
                {{ statusMeta(row.statusCode).name }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="里程碑名" prop="name" min-width="200" show-overflow-tooltip />
          <el-table-column label="项目数" prop="count" width="80" align="center" />
          <el-table-column label="操作" width="80" fixed="right" align="center">
            <template #default="{ row }">
              <el-link
                type="primary"
                :underline="false"
                @click="openNameDrill(phaseDrill.phaseId ?? 0, row.statusCode, statusMeta(row.statusCode).name, row.name)"
              >
                项目列表
              </el-link>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>

    <!-- 3 级弹窗: 命中项目 -->
    <el-dialog
      v-model="nameDrill.visible"
      :title="`${nameDrill.phaseName}${nameDrill.statusName ? ' · ' + nameDrill.statusName : ''}${nameDrill.milestoneName ? ' · ' + nameDrill.milestoneName : ''} · 命中项目`"
      width="1000px"
      :close-on-click-modal="false"
    >
      <el-skeleton v-if="nameDrill.loading" :rows="6" animated />
      <template v-else-if="nameDrill.data">
        <el-descriptions :column="4" border size="small" style="margin-bottom: 12px">
          <el-descriptions-item label="过滤路径" :span="4">{{ nameDrill.data.filters }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="nameDrill.data.projects" stripe size="small" empty-text="无命中项目">
          <el-table-column label="项目编号" prop="projectCode" width="180" show-overflow-tooltip />
          <el-table-column label="项目名称" prop="projectName" min-width="180" show-overflow-tooltip />
          <el-table-column label="里程碑名" prop="milestoneName" min-width="160" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag
                size="small"
                :style="{ background: statusMeta(row.statusCode).color, color: '#fff', border: 'none' }"
              >
                {{ row.statusName }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="部门" prop="departmentName" width="100" show-overflow-tooltip />
          <el-table-column label="PM" prop="pmName" width="90" show-overflow-tooltip />
          <el-table-column label="计划日期" prop="planDate" width="100" />
          <el-table-column label="实际日期" prop="actualDate" width="100">
            <template #default="{ row }">
              <span :style="{ color: row.actualDate ? '#67C23A' : '#c0c4cc' }">
                {{ row.actualDate || '—' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="权重" prop="weight" width="60" align="center" />
          <el-table-column label="操作" width="70" fixed="right" align="center">
            <template #default="{ row }">
              <el-link type="primary" :underline="false" :href="`/projects/${row.projectId}`" target="_blank">
                详情
              </el-link>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <span style="color: #909399; font-size: 12px">共 {{ nameDrill.data?.total ?? 0 }} 个项目</span>
      </template>
    </el-dialog>
  </div>
</template>
<style scoped>
/* ============== 视图模式切换 ============== */
.ma-view-switch {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
:deep(.ma-view-switch .el-radio-button__inner) {
  font-size: 13px;
  padding: 8px 14px;
}

/* ============== 图表卡片 ============== */
.ma-chart-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  margin-bottom: 16px;
}
.ma-chart-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  padding-bottom: 12px;
  margin-bottom: 8px;
  border-bottom: 1px dashed #e4e7ed;
}
.ma-chart-title .el-icon {
  color: #409eff;
  font-size: 16px;
}
.ma-chart-sub {
  margin-left: auto;
  font-size: 12px;
  color: #909399;
  font-weight: 400;
}
.ma-chart {
  height: 420px;
  width: 100%;
}
:deep(.ma-chart canvas) {
  border-radius: 4px;
}

/* ============== 顶部筛选条 ============== */
.ma-filter {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
}
.ma-filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ma-filter-item label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}
.ma-filter-item :deep(.el-select),
.ma-filter-item :deep(.el-tree-select),
.ma-filter-item :deep(.el-date-editor) {
  transition: all 0.2s;
}
.ma-filter-item :deep(.el-select:hover),
.ma-filter-item :deep(.el-tree-select:hover) {
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.1);
  border-radius: 4px;
}

/* ============== 总数统计 ============== */
.ma-summary {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #f0f9ff 0%, #fafbfc 100%);
  border-radius: 8px;
  border: 1px solid #e1f0ff;
}
.ma-summary :deep(.el-statistic__head) {
  font-size: 13px;
  color: #606266;
}
.ma-summary :deep(.el-statistic__content) {
  font-size: 22px;
  font-weight: 700;
  color: #1890ff;
}

/* ============== 7 phase 桶 grid ============== */
.ma-buckets {
  display: grid;
  /* 大屏 4 列, 中屏 3 列, 小屏 2 列, 极小屏 1 列 */
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}

/* ============== 桶卡片 ============== */
.ma-bucket {
  background: #fff;
  border-radius: 8px;
  padding: 12px 14px;
  border: 1px solid #ebeef5;
  min-height: 220px;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}
.ma-bucket::before {
  /* 顶条用 ::before 渲染,避免 border 影响 padding */
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--phase-color, #909399);
  opacity: 0.9;
}
.ma-bucket:hover {
  background: #fff;
  border-color: #c0c4cc;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}
.ma-bucket:active {
  transform: translateY(0);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.ma-bucket.is-empty {
  opacity: 0.6;
  background: #fafbfc;
}
.ma-bucket.is-empty:hover {
  opacity: 0.85;
}

/* 桶头: 阶段名 + 数量 */
.ma-bucket-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0 8px;
  border-bottom: 1px dashed #e4e7ed;
  margin-top: 4px;
}
.ma-bucket-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.ma-bucket-icon {
  font-size: 15px;
  color: var(--phase-color, #909399);
}
.ma-bucket-phase-tag {
  font-size: 10px;
  padding: 0 4px;
  height: 16px;
  line-height: 16px;
  background: var(--phase-color, #909399);
  color: #fff;
  border: none;
}
.ma-bucket-count {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.1;
  font-family: 'Helvetica Neue', Arial, sans-serif;
}

/* ============== 4 段 status 进度条 ============== */
.ma-status-bar {
  display: flex;
  height: 8px;
  border-radius: 4px;
  overflow: hidden;
  margin-top: 10px;
  background: #f0f2f5;
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.04);
}
.ma-status-bar-empty {
  height: 8px;
  margin-top: 10px;
  background: #f5f7fa;
  border-radius: 4px;
}
.ma-status-seg {
  height: 100%;
  transition: flex-grow 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}
.ma-status-seg:hover {
  filter: brightness(1.15);
}
.ma-status-seg:hover::after {
  /* 悬停显示数字小气泡 */
  content: attr(data-tip);
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  background: #303133;
  color: #fff;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 11px;
  white-space: nowrap;
  margin-bottom: 4px;
  z-index: 10;
}

/* ============== status 数字图例 (2x2 grid) ============== */
.ma-status-legend {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 8px;
  margin-top: 8px;
  font-size: 11px;
}
.ma-status-item {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #606266;
  transition: opacity 0.2s;
}
.ma-status-item.ma-status-zero {
  opacity: 0.35;
}
.ma-status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.05);
}
.ma-status-label {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ma-status-num {
  font-weight: 600;
  color: #303133;
  font-family: 'Helvetica Neue', Arial, sans-serif;
}

/* ============== 桶内 name 明细 ============== */
.ma-bucket-names {
  margin-top: 8px;
  max-height: 180px;
  overflow-y: auto;
  /* 自定义滚动条 */
  scrollbar-width: thin;
  scrollbar-color: #c0c4cc transparent;
}
.ma-bucket-names::-webkit-scrollbar {
  width: 4px;
}
.ma-bucket-names::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 2px;
}
.ma-name-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 5px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.15s;
  margin-bottom: 1px;
}
.ma-name-row:hover {
  background: #e6f0ff;
  transform: translateX(2px);
}
.ma-name-row.ma-name-more {
  color: #909399;
  text-align: center;
  font-style: italic;
  padding: 6px;
  background: #f5f7fa;
  border: 1px dashed #dcdfe6;
}
.ma-name-row.ma-name-more:hover {
  background: #e6f0ff;
  color: #409eff;
  border-color: #409eff;
  border-style: solid;
}
.ma-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 68%;
  color: #303133;
}
.ma-name-tag {
  font-size: 10px;
  height: 18px;
  line-height: 18px;
  padding: 0 5px;
  border: none;
  font-weight: 600;
}
.ma-muted {
  color: #c0c4cc;
  text-align: center;
  font-size: 11px;
  padding: 12px 0;
  font-style: italic;
}

/* ============== 2 级弹窗 (status 子桶) ============== */
.ma-status-pills {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.ma-status-pill {
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: all 0.2s;
}
.ma-status-pill:hover {
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.ma-pill-name {
  font-size: 12px;
  color: #606266;
  font-weight: 500;
}
.ma-pill-num {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
  font-family: 'Helvetica Neue', Arial, sans-serif;
}

/* ============== 弹窗标题加粗 ============== */
:deep(.el-dialog__title) {
  font-weight: 600;
  font-size: 15px;
}
:deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 12px;
  margin-right: 0;
}
:deep(.el-descriptions__label) {
  font-weight: 500;
  color: #303133;
}

/* ============== 弹窗内表格行高紧凑 ============== */
:deep(.el-dialog .el-table__row) {
  transition: background 0.15s;
}
:deep(.el-dialog .el-table__row:hover > td) {
  background: #f0f9ff !important;
}

/* ============== 3 级弹窗 footer 居中 ============== */
:deep(.el-dialog__footer) {
  text-align: center;
}

/* ============== 桶卡片进入动画 ============== */
@keyframes ma-fade-in-up {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.ma-bucket {
  animation: ma-fade-in-up 0.3s ease-out backwards;
}
.ma-bucket:nth-child(1) {
  animation-delay: 0.02s;
}
.ma-bucket:nth-child(2) {
  animation-delay: 0.04s;
}
.ma-bucket:nth-child(3) {
  animation-delay: 0.06s;
}
.ma-bucket:nth-child(4) {
  animation-delay: 0.08s;
}
.ma-bucket:nth-child(5) {
  animation-delay: 0.1s;
}
.ma-bucket:nth-child(6) {
  animation-delay: 0.12s;
}
.ma-bucket:nth-child(7) {
  animation-delay: 0.14s;
}

/* ============== 响应式 ============== */
@media (max-width: 1280px) {
  .ma-buckets {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  }
}
@media (max-width: 900px) {
  .ma-buckets {
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 10px;
  }
  .ma-bucket {
    padding: 10px 12px;
    min-height: 180px;
  }
  .ma-bucket-count {
    font-size: 20px;
  }
}
</style>
