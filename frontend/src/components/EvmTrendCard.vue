<script setup lang="ts">
/**
 * EvmTrendCard — EVM 趋势卡片 (P3.1)
 *
 * 顶部: 最新一次快照的 6 大数 + 健康度
 *   - BAC (Budget At Completion)   完工预算
 *   - PV  (Planned Value)          计划值
 *   - EV  (Earned Value)           挣值
 *   - AC  (Actual Cost)            实际成本
 *   - CPI = EV/AC (成本绩效指数, >1 节省, <1 超支)
 *   - SPI = EV/PV (进度绩效指数, >1 超前, <1 滞后)
 *
 * 底部: ECharts 双轴折线图
 *   - 左轴: PV / EV / AC (¥)
 *   - 右轴: CPI / SPI (1.0 基准线)
 *
 * Props:
 *  - projectId: 项目 id
 *  - days: 趋势天数 (默认 30, [7, 30, 90])
 * Events:
 *  - snapshot() 用户点"触发快照"按钮
 */
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getSnapshotsTrend, triggerSnapshot, type BudgetSnapshot } from '@/api/wbs'
import VChart from 'vue-echarts'

const props = defineProps<{
  projectId: number
}>()

const emit = defineEmits<{
  (e: 'snapshot'): void
}>()

const trend = ref<BudgetSnapshot[]>([])
const loading = ref(false)
const snapping = ref(false)
const days = ref<number>(30)

const DAYS_OPTIONS = [
  { value: 7,  label: '近 7 天' },
  { value: 30, label: '近 30 天' },
  { value: 90, label: '近 90 天' },
]

// ============================================================
// 健康度灯
// ============================================================
function healthLevel(cpi: number, spi: number): {
  level: 'GOOD' | 'WARN' | 'BAD'
  color: string
  bg: string
  text: string
} {
  // 双重判断: CPI 与 SPI 都 < 0.95 才算 BAD; 一个 < 0.9 算 WARN
  if (cpi >= 0.95 && spi >= 0.95) {
    return { level: 'GOOD', color: '#67c23a', bg: '#f0f9eb', text: '健康' }
  }
  if (cpi < 0.85 || spi < 0.85) {
    return { level: 'BAD', color: '#f56c6c', bg: '#fef0f0', text: '告警' }
  }
  return { level: 'WARN', color: '#e6a23c', bg: '#fdf6ec', text: '关注' }
}

const latest = computed(() => trend.value.length ? trend.value[trend.value.length - 1] : null)
const health = computed(() => {
  if (!latest.value) return null
  return healthLevel(Number(latest.value.cpi), Number(latest.value.spi))
})

// 偏差
const cv = computed(() => {
  if (!latest.value) return 0
  return Number(latest.value.ev) - Number(latest.value.ac)
})
const sv = computed(() => {
  if (!latest.value) return 0
  return Number(latest.value.ev) - Number(latest.value.pv)
})

// ============================================================
// ECharts option
// ============================================================
const chartOption = computed(() => {
  if (trend.value.length === 0) return {}

  const xData = trend.value.map(s => s.snapshotDate)
  const pvData = trend.value.map(s => Number(s.pv))
  const evData = trend.value.map(s => Number(s.ev))
  const acData = trend.value.map(s => Number(s.ac))
  const cpiData = trend.value.map(s => Number(s.cpi))
  const spiData = trend.value.map(s => Number(s.spi))

  return {
    grid: { left: 60, right: 60, top: 40, bottom: 40 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params: any[]) => {
        const date = params[0]?.axisValue
        let html = `<b>${date}</b><br/>`
        for (const p of params) {
          const v = typeof p.value === 'number'
            ? (p.seriesName.includes('CPI') || p.seriesName.includes('SPI')
                ? p.value.toFixed(3)
                : '¥' + p.value.toLocaleString())
            : p.value
          html += `${p.marker} ${p.seriesName}: <b>${v}</b><br/>`
        }
        return html
      },
    },
    legend: {
      data: ['PV 计划值', 'EV 挣值', 'AC 实际成本', 'CPI', 'SPI'],
      top: 0,
      textStyle: { fontSize: 12 },
    },
    xAxis: {
      type: 'category',
      data: xData,
      axisLabel: { fontSize: 11 },
    },
    yAxis: [
      {
        type: 'value',
        name: '金额 (¥)',
        position: 'left',
        axisLabel: {
          formatter: (v: number) => v >= 10000 ? `${(v / 10000).toFixed(0)}万` : v.toString(),
          fontSize: 11,
        },
      },
      {
        type: 'value',
        name: 'CPI/SPI',
        position: 'right',
        min: 0.5,
        max: 1.5,
        axisLabel: { fontSize: 11 },
        splitLine: { show: false },
      },
    ],
    series: [
      { name: 'PV 计划值', type: 'line', data: pvData, smooth: true,  itemStyle: { color: '#909399' } },
      { name: 'EV 挣值',   type: 'line', data: evData, smooth: true,  itemStyle: { color: '#409eff' } },
      { name: 'AC 实际成本', type: 'line', data: acData, smooth: true, itemStyle: { color: '#e6a23c' } },
      {
        name: 'CPI', type: 'line', yAxisIndex: 1, data: cpiData,
        markLine: { data: [{ yAxis: 1, label: { formatter: '基准 1.0' } }] },
        itemStyle: { color: '#67c23a' },
      },
      {
        name: 'SPI', type: 'line', yAxisIndex: 1, data: spiData,
        markLine: { data: [{ yAxis: 1 }] },
        itemStyle: { color: '#9c27b0' },
      },
    ],
  }
})

// ============================================================
// 数据加载 + 触发
// ============================================================
async function load() {
  loading.value = true
  try {
    trend.value = await getSnapshotsTrend(props.projectId, days.value)
  } catch (e: any) {
    ElMessage.error(`加载 EVM 趋势失败: ${e.message}`)
  } finally {
    loading.value = false
  }
}

async function onSnapshot() {
  snapping.value = true
  try {
    await triggerSnapshot(props.projectId, 'MANUAL from EVM card')
    ElMessage.success('快照已生成')
    emit('snapshot')
    await load()
  } catch (e: any) {
    ElMessage.error(`快照失败: ${e.message}`)
  } finally {
    snapping.value = false
  }
}

function onDaysChange() {
  load()
}

onMounted(load)
watch(() => props.projectId, load)
defineExpose({ load, latest })
</script>

<template>
  <el-card shadow="never" class="evm-card">
    <template #header>
      <div class="evm-card-header">
        <span>
          <b>📈 EVM 挣值分析</b>
          <el-tag v-if="health" :style="{ marginLeft: '8px', background: health.bg, color: health.color, border: 'none' }">
            {{ health.text }}
          </el-tag>
        </span>
        <div class="evm-card-actions">
          <el-select v-model="days" size="small" style="width: 110px" @change="onDaysChange">
            <el-option v-for="o in DAYS_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
          <el-button size="small" type="primary" :loading="snapping" @click="onSnapshot">
            触发快照
          </el-button>
        </div>
      </div>
    </template>

    <!-- 最新指标卡片 (无数据时显示空态) -->
    <div v-if="latest" class="evm-kpi-row">
      <div class="evm-kpi">
        <div class="evm-kpi-label">BAC 完工预算</div>
        <div class="evm-kpi-value">¥{{ Number(latest.bac).toLocaleString() }}</div>
      </div>
      <div class="evm-kpi">
        <div class="evm-kpi-label">PV 计划值</div>
        <div class="evm-kpi-value">¥{{ Number(latest.pv).toLocaleString() }}</div>
      </div>
      <div class="evm-kpi">
        <div class="evm-kpi-label">EV 挣值</div>
        <div class="evm-kpi-value evm-ev">¥{{ Number(latest.ev).toLocaleString() }}</div>
      </div>
      <div class="evm-kpi">
        <div class="evm-kpi-label">AC 实际成本</div>
        <div class="evm-kpi-value evm-ac">¥{{ Number(latest.ac).toLocaleString() }}</div>
      </div>
      <div class="evm-kpi">
        <div class="evm-kpi-label">CV 成本偏差</div>
        <div class="evm-kpi-value" :style="{ color: cv >= 0 ? '#67c23a' : '#f56c6c' }">
          {{ cv >= 0 ? '+' : '' }}{{ cv.toLocaleString() }}
        </div>
      </div>
      <div class="evm-kpi">
        <div class="evm-kpi-label">SV 进度偏差</div>
        <div class="evm-kpi-value" :style="{ color: sv >= 0 ? '#67c23a' : '#f56c6c' }">
          {{ sv >= 0 ? '+' : '' }}{{ sv.toLocaleString() }}
        </div>
      </div>
      <div class="evm-kpi evm-kpi-idx">
        <div class="evm-kpi-label">CPI 成本绩效</div>
        <div class="evm-kpi-value" :style="{ color: Number(latest.cpi) >= 1 ? '#67c23a' : '#f56c6c' }">
          {{ Number(latest.cpi).toFixed(3) }}
        </div>
        <div class="evm-kpi-hint">{{ Number(latest.cpi) >= 1 ? '节省' : '超支' }}</div>
      </div>
      <div class="evm-kpi evm-kpi-idx">
        <div class="evm-kpi-label">SPI 进度绩效</div>
        <div class="evm-kpi-value" :style="{ color: Number(latest.spi) >= 1 ? '#67c23a' : '#f56c6c' }">
          {{ Number(latest.spi).toFixed(3) }}
        </div>
        <div class="evm-kpi-hint">{{ Number(latest.spi) >= 1 ? '超前' : '滞后' }}</div>
      </div>
      <div class="evm-kpi">
        <div class="evm-kpi-label">EAC 完工估算</div>
        <div class="evm-kpi-value">¥{{ Number(latest.eac).toLocaleString() }}</div>
      </div>
    </div>

    <!-- 趋势图 -->
    <div v-loading="loading" class="evm-chart-wrap">
      <v-chart
        v-if="trend.length > 0"
        :option="chartOption"
        autoresize
        style="height: 320px; width: 100%"
      />
      <el-empty
        v-else
        description="尚无 EVM 快照,点右上「触发快照」生成首次"
        :image-size="80"
      />
    </div>
  </el-card>
</template>

<style scoped>
.evm-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.evm-card-actions { display: flex; gap: 8px; }

.evm-kpi-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
  gap: 8px;
  margin-bottom: 16px;
}
.evm-kpi {
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  text-align: center;
}
.evm-kpi-label { font-size: 11px; color: #909399; }
.evm-kpi-value { font-size: 18px; font-weight: 600; color: #303133; margin-top: 2px; }
.evm-kpi-hint  { font-size: 11px; color: #909399; margin-top: 2px; }
.evm-kpi-idx   { background: #fdf6ec; }
.evm-ev        { color: #409eff; }
.evm-ac        { color: #e6a23c; }

.evm-chart-wrap { min-height: 320px; }
</style>
