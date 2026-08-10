<script setup lang="ts">
/**
 * WbsGanttView — P3.3 WBS 任务级甘特图
 *
 * 设计:
 *  - 数据源: getWbsGantt(projectId) → WbsGanttResponse
 *  - **不改** GanttView.vue (它太复杂, 改动风险大, 联动拖拽/SVG/远程光标都不动)
 *  - 这里只做"形状适配": 把 WbsGanttRow[] 转成 GanttBar[] 期望的格式
 *    然后把"项目级" GanttResponse 喂给 GanttView, 让它原样渲染
 *
 *  - WbsTask.milestone=true  → 在该任务行内画一个里程碑菱形(planEnd 当 planDate)
 *  - WbsTask.critical=true  → bar 边框加红
 *  - WbsTask.name 截断 / wbsCode 加粗显示, 不再像项目那样显示编号
 */
import { computed, ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import GanttView, { type GanttResponse, type GanttBar, type GanttMilestone } from '@/components/GanttView.vue'
import { getWbsGantt, type WbsGanttRow, type WbsGanttResponse } from '@/api/wbs'

const props = defineProps<{
  projectId: number
}>()

const emit = defineEmits<{
  (e: 'task-click', taskId: number): void
}>()

const data = ref<WbsGanttResponse | null>(null)
const loading = ref(false)

/** 把 WbsGanttResponse 适配成 GanttResponse, 喂给 GanttView */
const ganttData = computed<GanttResponse | null>(() => {
  if (!data.value) return null
  const d = data.value
  // 每个任务 = 一个 "bar", 任务代码当 projectCode, 任务名当 projectName
  const bars: GanttBar[] = d.rows.map((r: WbsGanttRow) => {
    // 关键路径 / 里程碑标识
    const milestones: GanttMilestone[] = r.milestone
      ? [
          {
            id: r.taskId, // 借任务 id 当 milestone id
            name: `${r.wbsCode} ${r.name}`,
            planDate: r.planEnd ?? r.planStart ?? '',
            actualDate: r.actualEnd ?? null,
            status: r.status as string,
            weight: r.weight || 5,
            phaseId: null,
            phaseName: null,
          },
        ]
      : []
    return {
      projectId: r.taskId, // 借 id 当 projectId (GanttView 内部用, 唯一即可)
      projectCode: r.wbsCode,
      projectName: r.name,
      planStart: r.planStart,
      planEnd: r.planEnd,
      actualStart: r.actualStart,
      actualEnd: r.actualEnd,
      progressPct: r.progressPct,
      milestones,
    }
  })
  return {
    rangeFrom: d.rangeFrom,
    rangeTo: d.rangeTo,
    projectCount: d.taskCount,
    bars,
  }
})

async function load() {
  loading.value = true
  try {
    data.value = await getWbsGantt(props.projectId)
  } catch (e: any) {
    ElMessage.error('加载 WBS 甘特图失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

/** GanttView 发出 milestone-click 时, 找对应任务, 转成 task-click 透传出去 */
function onMilestoneClick(m: GanttMilestone & { projectId: number }) {
  emit('task-click', m.projectId)
}

onMounted(load)
watch(() => props.projectId, load)
</script>

<template>
  <div class="wbs-gantt">
    <!-- 头部小工具条 -->
    <div class="wbs-gantt-toolbar">
      <el-tag size="small" type="info" effect="plain">📊 WBS 任务甘特图 (复用 GanttView, 0 改源)</el-tag>
      <span class="wbs-gantt-stat">
        可绘制任务:
        <b>{{ data?.taskCount ?? 0 }}</b>
      </span>
      <el-button size="small" @click="load" :loading="loading">刷新</el-button>
    </div>

    <!-- 数据空态 -->
    <el-empty
      v-if="!loading && (!data || data.taskCount === 0)"
      description="该项目暂无带计划区间的任务, 先去 WBS 树给任务填 planStart/planEnd"
    />

    <!-- 真正的甘特图(GanttView 原样渲染) -->
    <GanttView
      v-else-if="ganttData"
      :data="ganttData"
      :loading="loading"
      mode="auto"
      @milestone-click="onMilestoneClick"
    />
  </div>
</template>

<style scoped>
.wbs-gantt {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.wbs-gantt-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #606266;
}
.wbs-gantt-stat b {
  color: #303133;
}
</style>
