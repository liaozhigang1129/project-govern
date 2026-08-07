<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import api, {
  type KpiResponse,
  type ProjectCard,
  type BuDistributionRow,
  type PlDistributionRow,
} from '@/api/client'
import VChart from 'vue-echarts'

const kpi = ref<KpiResponse | null>(null)
const projects = ref<ProjectCard[]>([])
const statusDist = ref<Record<string, number>>({})
const healthDist = ref<Record<string, number>>({})
const buDist = ref<BuDistributionRow[]>([])
const plDist = ref<PlDistributionRow[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

onMounted(async () => {
  loading.value = true
  error.value = null
  try {
    const [k, p, s, h, bu, pl] = await Promise.allSettled([
      api.get<KpiResponse>('/dashboard/kpis'),
      api.get<ProjectCard[]>('/projects'),
      api.get<Record<string, number>>('/dashboard/status-distribution'),
      api.get<Record<string, number>>('/dashboard/health-distribution'),
      api.get<BuDistributionRow[]>('/dashboard/bu-distribution'),
      api.get<PlDistributionRow[]>('/dashboard/pl-distribution'),
    ])
    if (k.status === 'fulfilled') kpi.value = k.value
    if (p.status === 'fulfilled') projects.value = p.value
    if (s.status === 'fulfilled') statusDist.value = s.value ?? {}
    if (h.status === 'fulfilled') healthDist.value = h.value ?? {}
    if (bu.status === 'fulfilled') buDist.value = bu.value ?? []
    if (pl.status === 'fulfilled') plDist.value = pl.value ?? []
    if (k.status === 'rejected') error.value = k.reason?.message ?? '加载失败'
  } finally {
    loading.value = false
  }
})

const statusChartOpt = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { bottom: 0, left: 'center' },
  series: [{
    type: 'pie',
    radius: ['35%', '60%'],
    avoidLabelOverlap: true,
    itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
    label: { show: true, formatter: '{b}\n{d}%' },
    data: Object.entries(statusDist.value).map(([name, value]) => ({ name, value })),
  }],
}))

const healthChartOpt = computed(() => {
  const colorMap: Record<string, string> = {
    正常: '#67C23A',
    关注: '#E6A23C',
    严重: '#F56C6C',
  }
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, left: 'center' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}\n{d}%' },
      data: Object.entries(healthDist.value).map(([name, value]) => ({
        name, value,
        itemStyle: { color: colorMap[name] ?? '#909399' },
      })),
    }],
  }
})

// BU 分布: 横向柱状图(项目数量) + 标注平均进度
const buChartOpt = computed(() => {
  if (buDist.value.length === 0) return {}

  const buColors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#b37feb']
  const categories = buDist.value.map(r => r.buName)
  const counts = buDist.value.map(r => r.projectCount)
  const avgs = buDist.value.map(r => r.avgProgress)

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any[]) => {
        const idx = params[0].dataIndex
        const row = buDist.value[idx]
        return `${row.buName}<br/>项目数: <b>${row.projectCount}</b><br/>平均进度: <b>${row.avgProgress}%</b>`
      },
    },
    grid: { left: 100, right: 40, top: 20, bottom: 30 },
    xAxis: { type: 'value', name: '项目数' },
    yAxis: { type: 'category', data: categories, inverse: true },
    series: [
      {
        type: 'bar',
        data: counts.map((v, i) => ({
          value: v,
          itemStyle: { color: buColors[i % buColors.length], borderRadius: [0, 4, 4, 0] },
        })),
        barWidth: '50%',
        label: {
          show: true,
          position: 'right',
          formatter: (params: any) => {
            return `${params.value} 个 | 均进度 ${avgs[params.dataIndex]}%`
          },
        },
      },
    ],
  }
})

// PL 分布: 横向柱状图
const plChartOpt = computed(() => {
  if (plDist.value.length === 0) return {}

  const categories = plDist.value.map(r => `${r.plName} (${r.buName})`)
  const counts = plDist.value.map(r => r.projectCount)
  const avgs = plDist.value.map(r => r.avgProgress)

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any[]) => {
        const idx = params[0].dataIndex
        const row = plDist.value[idx]
        return `${row.plName} (${row.buName})<br/>项目数: <b>${row.projectCount}</b><br/>平均进度: <b>${row.avgProgress}%</b>`
      },
    },
    grid: { left: 160, right: 40, top: 20, bottom: 30 },
    xAxis: { type: 'value', name: '项目数' },
    yAxis: { type: 'category', data: categories, inverse: true },
    series: [
      {
        type: 'bar',
        data: counts.map((v) => ({
          value: v,
          itemStyle: { color: '#b37feb', borderRadius: [0, 4, 4, 0] },
        })),
        barWidth: '50%',
        label: {
          show: true,
          position: 'right',
          formatter: (params: any) => {
            return `${params.value} 个 | 均进度 ${avgs[params.dataIndex]}%`
          },
        },
      },
    ],
  }
})
</script>

<template>
  <div class="page" v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" :closable="false" style="margin-bottom: 16px" />

    <!-- KPI 卡片 -->
    <el-row :gutter="16">
      <el-col :span="6">
        <div class="kpi-card kpi-card--blue">
          <div class="kpi-card__label">执行中项目</div>
          <div class="kpi-card__value">{{ kpi?.activeCount ?? '-' }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="kpi-card kpi-card--red">
          <div class="kpi-card__label">逾期项目</div>
          <div class="kpi-card__value">{{ kpi?.overdueProjects ?? '-' }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="kpi-card kpi-card--green">
          <div class="kpi-card__label">本月结项</div>
          <div class="kpi-card__value">{{ kpi?.closedThisMonth ?? '-' }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="kpi-card kpi-card--orange">
          <div class="kpi-card__label">本月新立项</div>
          <div class="kpi-card__value">{{ kpi?.newInitiationsThisMonth ?? '-' }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 第一行: 状态分布 + 健康度 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>项目状态分布</span>
          </template>
          <v-chart
            v-if="Object.keys(statusDist).length"
            :option="statusChartOpt"
            style="height: 300px"
          />
          <el-empty v-else description="暂无数据" :image-size="80" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>项目健康度</span>
          </template>
          <v-chart
            v-if="Object.keys(healthDist).length"
            :option="healthChartOpt"
            style="height: 300px"
          />
          <el-empty v-else description="暂无数据" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二行: BU 分布 + PL 分布 (新增) -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>按业务单元 (BU) 分布</span>
          </template>
          <v-chart
            v-if="buDist.length"
            :option="buChartOpt"
            style="height: 300px"
          />
          <el-empty v-else description="暂无数据" :image-size="80" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>按产品线 (PL) 分布</span>
          </template>
          <v-chart
            v-if="plDist.length"
            :option="plChartOpt"
            style="height: 300px"
          />
          <el-empty v-else description="暂无数据" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 活跃项目表 -->
    <el-card style="margin-top: 16px">
      <template #header>活跃项目 (Top {{ projects.length }})</template>
      <el-table :data="projects" stripe empty-text="暂无项目">
        <el-table-column prop="code" label="编号" width="180" />
        <el-table-column prop="name" label="名称" />
        <el-table-column label="BU" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.bu" effect="plain" type="info" size="small">{{ row.bu.name }}</el-tag>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="PL" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.pl" effect="plain" type="info" size="small">{{ row.pl.name }}</el-tag>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="项目经理" width="110">
          <template #default="{ row }">
            <span v-if="row.pmUserName">{{ row.pmUserName }}</span>
            <span v-else style="color: #c0c4cc">未指定</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            {{ row.type?.name ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status?.code === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status?.name ?? '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="健康度" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.health" :color="row.health.colorHex" effect="dark">
              {{ row.health.name }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="160">
          <template #default="{ row }">
            <el-progress :percentage="row.progressPct" :status="row.progressPct >= 100 ? 'success' : ''" />
          </template>
        </el-table-column>
        <el-table-column prop="planEndDate" label="计划结束" width="120" />
      </el-table>
    </el-card>
  </div>
</template>
