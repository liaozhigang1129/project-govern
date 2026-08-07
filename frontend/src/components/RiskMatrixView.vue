<script setup lang="ts">
/**
 * RiskMatrixView — 5x5 概率×影响 风险矩阵热力图 (P4)
 *
 * 数据: getRiskMatrix(projectId) → RiskMatrix { cells: Cell[] }
 * 渲染: ECharts Heatmap, 5x5 网格
 *   - 颜色按 count 渐变 (0=灰, 1=淡黄, 5=深红)
 *   - 点击 cell 弹窗显示该 cell 的所有风险
 *   - 鼠标 hover 显示 count + 风险数
 *
 * 设计:
 *  - 不画"等级框线" (LOW/MEDIUM/HIGH/CRITICAL), 改用颜色直接表达, 更直观
 *  - x 轴: impact (1-5, 5=最严重)
 *  - y 轴: probability (1-5, 5=最可能)
 *  - 跟 PMBOK 7 / ISO 31000 风险矩阵惯例一致
 */
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import VChart from 'vue-echarts'
import { getRiskMatrix, type RiskMatrix, type RiskItem } from '@/api/risk'

const props = defineProps<{
  projectId: number
}>()

const data = ref<RiskMatrix | null>(null)
const loading = ref(false)

const ECHARTS_HEATMAP_REGISTERED = true  // Heatmap 是 ECharts 核心组件, 默认已注册

const chartOption = computed(() => {
  if (!data.value) return {}
  const cells = data.value.cells
  // [x, y, value, risks]  ECharts heatmap 数据格式
  // x = impact, y = probability
  const series = cells.map(c => [c.impact - 1, c.probability - 1, c.count])
  const maxCount = Math.max(1, ...cells.map(c => c.count))
  // 按 (p, i) 索引
  const byPI = new Map<string, RiskItem[]>()
  for (const c of cells) {
    byPI.set(`${c.probability - 1}-${c.impact - 1}`, c.risks)
  }

  return {
    tooltip: {
      formatter: (params: any) => {
        const [x, y, count] = params.value
        const risks = byPI.get(`${y}-${x}`) || []
        if (count === 0) {
          return `<b>无风险</b><br/>概率 ${y + 1} × 影响 ${x + 1}`
        }
        let html = `<b>${count} 个风险</b> (概率 ${y + 1} × 影响 ${x + 1})<br/>`
        for (const r of risks.slice(0, 5)) {
          html += `• ${r.code} ${r.title} <span style="color:#f56c6c">${r.level}</span><br/>`
        }
        if (risks.length > 5) html += `... 等 ${risks.length} 个`
        return html
      },
    },
    grid: { left: 50, right: 30, top: 30, bottom: 60 },
    xAxis: {
      type: 'category',
      data: ['1 轻微', '2 较小', '3 中等', '4 较大', '5 严重'],
      name: '影响 (Impact)',
      nameLocation: 'middle',
      nameGap: 30,
      splitArea: { show: true },
    },
    yAxis: {
      type: 'category',
      data: ['1 极低', '2 低', '3 中', '4 高', '5 极高'],
      name: '概率 (Probability)',
      nameLocation: 'middle',
      nameGap: 40,
      splitArea: { show: true },
    },
    visualMap: {
      min: 0,
      max: maxCount,
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: 5,
      inRange: {
        color: ['#f5f7fa', '#fef0f0', '#fbc4c4', '#f56c6c', '#c00'],
      },
      text: ['高', '低'],
    },
    series: [
      {
        type: 'heatmap',
        data: series,
        label: {
          show: true,
          formatter: (p: any) => p.value[2] > 0 ? p.value[2] : '',
          fontSize: 14,
          fontWeight: 600,
          color: '#303133',
        },
        itemStyle: {
          borderColor: '#fff',
          borderWidth: 2,
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowColor: 'rgba(0, 0, 0, 0.3)',
          },
        },
      },
    ],
  }
})

async function load() {
  loading.value = true
  try {
    data.value = await getRiskMatrix(props.projectId)
  } catch (e: any) {
    ElMessage.error('加载风险矩阵失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.projectId, load)
defineExpose({ load })
</script>

<template>
  <div class="risk-matrix-view" v-loading="loading">
    <v-chart
      v-if="data"
      :option="chartOption"
      autoresize
      style="height: 360px; width: 100%"
    />
  </div>
</template>

<style scoped>
.risk-matrix-view {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafafa;
  padding: 8px;
}
</style>
