<script setup lang="ts">
/**
 * WbsNetworkView — P3.2 WBS 任务依赖网络图 + P3.3 关键路径
 *
 * 顶部: 关键路径 KPI 卡片 (关键任务数 / 项目总工期 / 关键路径长度)
 * 中部: ECharts GraphChart 力导向图
 *   - 节点颜色: 完成度
 *   - 节点形状: 里程碑=菱形, 普通=圆
 *   - 关键路径节点: 红色加粗边框
 *   - 边: 普通=灰, 关键路径边=红色
 *   - 节点 tooltip: 任务名/状态/进度/工期/负责人
 *   - 边 tooltip: 紧前关系 + 是否关键
 *
 * 数据源: getWbsNetwork(projectId) → WbsNetworkResponse (后端已跑 CPM 算好 critical)
 *
 * 设计原则:
 *  - 不画父-子层级关系 (那是 WbsTreeView 干的事), 这里只画"前置-后置"依赖
 *  - 节点大小按工期 (planDurationDays) 缩放, 给用户"哪条最长"的直觉
 *  - 自动布局: 力导向 + 边绑定, 避免节点重叠
 */
import { computed, ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import VChart from 'vue-echarts'
import { getWbsNetwork, type WbsNetworkResponse, type WbsNetworkNode } from '@/api/wbs'

const props = defineProps<{
  projectId: number
}>()

const emit = defineEmits<{
  (e: 'task-click', taskId: number): void
}>()

const data = ref<WbsNetworkResponse | null>(null)
const loading = ref(false)

// ============================================================
// 颜色/状态映射
// ============================================================
function statusColor(status: string, progressPct: number): string {
  switch (status) {
    case 'COMPLETED':
      return '#67c23a' // 绿
    case 'IN_PROGRESS': {
      // 进度比例: 浅色 → 深色
      if (progressPct >= 70) return '#409eff' // 蓝, 接近完成
      if (progressPct >= 30) return '#5dade2' // 中蓝
      return '#85c1e9' // 浅蓝
    }
    case 'BLOCKED':
      return '#f56c6c' // 红
    case 'CANCELLED':
      return '#c0c4cc' // 灰
    case 'NOT_STARTED':
    default:
      return '#dcdfe6' // 浅灰
  }
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    NOT_STARTED: '未开始',
    IN_PROGRESS: '进行中',
    BLOCKED: '阻塞',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
  }
  return map[status] || status
}

// ============================================================
// KPI
// ============================================================
const kpi = computed(() => {
  if (!data.value) return null
  const d = data.value
  const criticalCount = d.criticalTaskIds?.length ?? 0
  // 项目总工期: 关键路径上的任务工期之和 (CPM 算法的项目工期)
  const critSet = new Set(d.criticalTaskIds ?? [])
  const totalDays = d.nodes
    .filter((n) => critSet.has(n.taskId))
    .reduce((sum, n) => sum + (n.planDurationDays ?? 0), 0)
  const totalTasks = d.nodes.length
  const criticalPct = totalTasks === 0 ? 0 : Math.round((criticalCount / totalTasks) * 100)
  return { criticalCount, totalTasks, criticalPct, totalDays }
})

// ============================================================
// ECharts option
// ============================================================
const chartOption = computed(() => {
  if (!data.value || data.value.nodes.length === 0) return {}

  // 节点最大工期, 用于 size 缩放基准
  const maxDur = Math.max(1, ...data.value.nodes.map((n) => n.planDurationDays ?? 1))

  const nodes = data.value.nodes.map((n: WbsNetworkNode) => {
    const isCritical = n.critical
    const dur = n.planDurationDays ?? 1
    // 节点 size: 基础 24, 工期越长越大, 关键路径再放大
    const baseSize = 24 + Math.min(30, (dur / maxDur) * 24)
    const size = isCritical ? baseSize + 8 : baseSize
    const fill = statusColor(n.status, n.progressPct)
    return {
      id: String(n.taskId),
      name: n.wbsCode,
      // symbolSize + 形状
      symbolSize: size,
      symbol: n.milestone ? 'diamond' : 'circle',
      // 类别: 0=普通, 1=里程碑, 2=关键路径
      category: isCritical ? 2 : n.milestone ? 1 : 0,
      // 富标签 (支持多行)
      value: n.name,
      // 节点原始数据 (tooltip 用)
      _raw: n,
      // 拖拽/布局
      draggable: true,
      // 边框
      itemStyle: {
        color: fill,
        borderColor: isCritical ? '#f56c6c' : '#fff',
        borderWidth: isCritical ? 3 : 1.5,
        shadowBlur: isCritical ? 10 : 0,
        shadowColor: isCritical ? 'rgba(245,108,108,0.5)' : 'transparent',
      },
      // 标签
      label: {
        show: true,
        formatter: (params: any) => {
          const r = params.data?._raw as WbsNetworkNode | undefined
          if (!r) return params.name
          // 短行: wbsCode, 任务名最多 12 字符
          const shortName = r.name.length > 12 ? r.name.slice(0, 11) + '…' : r.name
          return `${params.name}\n${shortName}`
        },
        fontSize: 11,
        color: '#303133',
        fontWeight: 'normal',
      },
    }
  })

  const links = data.value.edges.map((e) => ({
    source: String(e.fromTaskId),
    target: String(e.toTaskId),
    lineStyle: {
      color: e.isCriticalEdge ? '#f56c6c' : '#c0c4cc',
      width: e.isCriticalEdge ? 2.5 : 1,
      type: 'solid',
      curveness: 0.15,
    },
    // 箭头
    symbol: ['none', 'arrow'],
    symbolSize: [0, e.isCriticalEdge ? 8 : 6],
    _isCritical: e.isCriticalEdge,
  }))

  return {
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        if (params.dataType === 'edge' || params.dataType === 'line') {
          const src = data.value!.nodes.find((n) => String(n.taskId) === params.data.source)
          const tgt = data.value!.nodes.find((n) => String(n.taskId) === params.data.target)
          const crit = params.data._isCritical ? '🔴 关键路径' : '⚪ 普通'
          return `
            <b>紧前关系</b><br/>
            ${src?.wbsCode} ${src?.name ?? ''}<br/>
            ↓<br/>
            ${tgt?.wbsCode} ${tgt?.name ?? ''}<br/>
            <span style="font-size:11px;color:#909399">${crit}</span>
          `
        }
        if (params.dataType === 'node' && params.data._raw) {
          const r = params.data._raw as WbsNetworkNode
          return `
            <b>${r.wbsCode} ${r.name}</b><br/>
            状态: <b>${statusLabel(r.status)}</b> (${r.progressPct}%)<br/>
            工期: ${r.planDurationDays ?? '-'} 天 · 工时: ${r.planHours}h<br/>
            负责人: ${r.ownerName ?? '未指派'}<br/>
            计划: ${r.planStart ?? '-'} → ${r.planEnd ?? '-'}<br/>
            ${r.critical ? '<span style="color:#f56c6c;font-weight:bold">🔴 关键路径</span>' : ''}
            ${r.milestone ? '<span style="color:#e6a23c"> ◆ 里程碑</span>' : ''}
          `
        }
        return ''
      },
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#ebeef5',
      textStyle: { fontSize: 12 },
    },
    legend: {
      data: [
        { name: '普通任务', icon: 'circle' },
        { name: '里程碑', icon: 'diamond' },
        { name: '关键路径', icon: 'circle' },
      ],
      top: 0,
      textStyle: { fontSize: 12 },
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        focusNodeAdjacency: true,
        categories: [
          { name: '普通任务', itemStyle: { color: '#dcdfe6' } },
          { name: '里程碑', itemStyle: { color: '#e6a23c' } },
          { name: '关键路径', itemStyle: { color: '#f56c6c' } },
        ],
        force: {
          // 排斥力: 节点间距离
          repulsion: 220,
          // 边长度
          edgeLength: [60, 120],
          gravity: 0.05,
          // 布局迭代次数 (够用即停, 避免无限抖动)
          friction: 0.35,
        },
        // 边上的箭头
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: [0, 8],
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 3 },
        },
        // 滚轮缩放范围
        scaleLimit: { min: 0.3, max: 3 },
        // 初始动画
        animationDuration: 800,
        animationEasingUpdate: 'cubicOut',
        data: nodes,
        links,
      },
    ],
  }
})

// ============================================================
// 数据加载
// ============================================================
async function load() {
  loading.value = true
  try {
    data.value = await getWbsNetwork(props.projectId)
    if (data.value.criticalTaskIds.length === 0 && data.value.edges.length > 0) {
      // 提示: 关键路径为空可能意味着有环或所有任务都没有 plan 区间
      ElMessage.warning('未识别到关键路径, 请检查任务计划区间或紧前关系是否存在环')
    }
  } catch (e: any) {
    ElMessage.error('加载 WBS 网络图失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

function onChartClick(params: any) {
  if (params.dataType === 'node' && params.data?._raw) {
    emit('task-click', params.data._raw.taskId)
  }
}

onMounted(load)
watch(() => props.projectId, load)
defineExpose({ load })
</script>

<template>
  <div class="wbs-network">
    <!-- KPI 行 -->
    <div v-if="kpi" class="wbs-network-kpi">
      <div class="wbs-network-kpi-item">
        <div class="wbs-network-kpi-label">总任务</div>
        <div class="wbs-network-kpi-value">{{ kpi.totalTasks }}</div>
      </div>
      <div class="wbs-network-kpi-item kpi-crit">
        <div class="wbs-network-kpi-label">🔴 关键路径任务</div>
        <div class="wbs-network-kpi-value">
          {{ kpi.criticalCount }}
          <span class="wbs-network-kpi-pct">({{ kpi.criticalPct }}%)</span>
        </div>
      </div>
      <div class="wbs-network-kpi-item">
        <div class="wbs-network-kpi-label">📅 项目工期 (CPM)</div>
        <div class="wbs-network-kpi-value">{{ kpi.totalDays }} 天</div>
      </div>
      <div class="wbs-network-kpi-item">
        <div class="wbs-network-kpi-label">🔗 依赖边</div>
        <div class="wbs-network-kpi-value">{{ data?.edges.length ?? 0 }}</div>
      </div>
    </div>

    <!-- 网络图 -->
    <div v-loading="loading" class="wbs-network-chart">
      <v-chart
        v-if="data && data.nodes.length > 0"
        :option="chartOption"
        autoresize
        style="height: 520px; width: 100%"
        @click="onChartClick"
      />
      <el-empty
        v-else
        description="该项目暂无任务, 或所有任务都未设置紧前关系 (predecessor)"
        :image-size="100"
      />
    </div>

    <!-- 任务列表 (折叠, 点击切换) -->
    <el-collapse v-if="data" class="wbs-network-list">
      <el-collapse-item title="📋 关键路径任务清单" name="critical">
        <el-table :data="data.nodes.filter((n) => n.critical)" size="small" border>
          <el-table-column prop="wbsCode" label="WBS" width="90" />
          <el-table-column prop="name" label="任务名" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag
                :type="
                  row.status === 'COMPLETED' ? 'success' : row.status === 'IN_PROGRESS' ? 'primary' : 'info'
                "
                size="small"
              >
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="工期" width="80" align="right">
            <template #default="{ row }">{{ row.planDurationDays ?? '-' }} 天</template>
          </el-table-column>
          <el-table-column prop="ownerName" label="负责人" width="100" />
          <el-table-column label="计划" width="190">
            <template #default="{ row }">{{ row.planStart ?? '-' }} → {{ row.planEnd ?? '-' }}</template>
          </el-table-column>
        </el-table>
        <div v-if="data.criticalTaskIds.length === 0" class="wbs-network-list-empty">
          暂无关键路径任务 (请确保任务有 planStart/planEnd)
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.wbs-network {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.wbs-network-kpi {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 8px;
}
.wbs-network-kpi-item {
  padding: 10px 14px;
  background: #f5f7fa;
  border-radius: 6px;
}
.wbs-network-kpi-item.kpi-crit {
  background: #fef0f0;
  border: 1px solid #fbc4c4;
}
.wbs-network-kpi-label {
  font-size: 11px;
  color: #909399;
}
.wbs-network-kpi-value {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
  margin-top: 2px;
}
.wbs-network-kpi-pct {
  font-size: 13px;
  color: #909399;
  font-weight: normal;
}

.wbs-network-chart {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafafa;
  min-height: 520px;
}

.wbs-network-list {
  margin-top: 4px;
}
.wbs-network-list-empty {
  padding: 20px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
</style>
