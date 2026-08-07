<script setup lang="ts">
/**
 * WBS 树视图 — Element Plus el-tree 封装
 *
 * 设计要点:
 *  - 后端返回嵌套 children, 直接喂给 el-tree :data="tree"
 *  - 自定义节点内容: 编码 / 名称 / 状态 tag / 进度条 / 权重
 *  - 右键菜单: 新增子任务 / 编辑 / 删除 / 触发 EVM 快照
 *  - 拖拽暂不做(避免循环引用 + 排序复杂度)
 *
 * Props:
 *  - projectId: 项目 id
 * Events:
 *  - select(task) 选中节点
 *  - edit(task) 编辑任务
 *  - add-child(parent) 新增子任务
 *  - snapshot() 触发 EVM 快照
 */
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getWbsTree,
  getWbsProgress,
  saveWbsTask,
  deleteWbsTask,
  triggerSnapshot,
  type WbsTaskNode,
  type WbsProgressSummary,
} from '@/api/wbs'

const props = defineProps<{
  projectId: number
}>()

const emit = defineEmits<{
  (e: 'select', task: WbsTaskNode): void
  (e: 'edit', task: WbsTaskNode): void
  (e: 'snapshot'): void
}>()

// ============================================================
// 数据
// ============================================================
const tree = ref<WbsTaskNode[]>([])
const summary = ref<WbsProgressSummary | null>(null)
const loading = ref(false)
const snapping = ref(false)

// el-tree 展开状态
const defaultExpanded = ref<(number | string)[]>([])
const treeRef = ref()

// 选中节点 (高亮 + 详情面板用)
const selectedTask = ref<WbsTaskNode | null>(null)

// ============================================================
// 状态 → 颜色 映射
// ============================================================
const STATUS_TAG: Record<string, { type: 'info' | 'primary' | 'success' | 'warning' | 'danger'; label: string }> = {
  NOT_STARTED: { type: 'info',    label: '未开始' },
  IN_PROGRESS: { type: 'primary', label: '进行中' },
  BLOCKED:     { type: 'danger',  label: '阻塞' },
  COMPLETED:   { type: 'success', label: '已完成' },
  CANCELLED:   { type: 'info',    label: '已取消' },
}
const TASK_TYPE_ICON: Record<string, string> = {
  SUMMARY:     '📁',
  EXECUTION:   '📋',
  MILESTONE:   '◆',
  DELIVERABLE: '📦',
}

// ============================================================
// 加载
// ============================================================
async function load() {
  loading.value = true
  try {
    const [treeData, summaryData] = await Promise.all([
      getWbsTree(props.projectId),
      getWbsProgress(props.projectId),
    ])
    tree.value = treeData
    summary.value = summaryData
    // 默认展开根节点
    defaultExpanded.value = treeData.map(n => n.id)
  } catch (e: any) {
    ElMessage.error(`加载 WBS 失败: ${e.message}`)
  } finally {
    loading.value = false
  }
}

async function doSnapshot() {
  snapping.value = true
  try {
    await triggerSnapshot(props.projectId, 'MANUAL from WBS view')
    ElMessage.success('EVM 快照已生成')
    emit('snapshot')
  } catch (e: any) {
    ElMessage.error(`快照失败: ${e.message}`)
  } finally {
    snapping.value = false
  }
}

// ============================================================
// 节点操作
// ============================================================
function onNodeClick(node: WbsTaskNode) {
  selectedTask.value = node
  emit('select', node)
}

function onEdit(task: WbsTaskNode) {
  emit('edit', task)
}

async function onDelete(task: WbsTaskNode) {
  try {
    await ElMessageBox.confirm(
      `确认删除任务「${task.name}」(编码 ${task.wbsCode})?子任务不会级联删除,需要单独删。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteWbsTask(task.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    ElMessage.error(`删除失败: ${e.message}`)
  }
}

/**
 * 新增子任务 — 弹窗由父组件控制, 这里只 emit 事件
 * 父组件收到事件后, 打开 el-dialog 录入, 提交时调 saveWbsTask 即可
 */
function onAddChild(parent: WbsTaskNode | null) {
  // 顶层: parent = null; 子任务: parent = 选中节点
  const newNode: WbsTaskNode = {
    id: 0,
    projectId: props.projectId,
    parentId: parent?.id ?? null,
    wbsCode: '',
    name: '',
    taskType: 'EXECUTION',
    status: 'NOT_STARTED',
    ownerUserId: null,
    planStartDate: null,
    planEndDate: null,
    actualStartDate: null,
    actualEndDate: null,
    planHours: 0,
    actualHours: 0,
    progressPct: 0,
    weight: 1,
    critical: false,
    milestone: false,
    milestoneId: null,
    predecessorIds: [],
    deliverable: null,
    remark: null,
    createdAt: '',
    updatedAt: '',
    depth: parent ? parent.depth + 1 : 0,
    path: parent ? [...parent.path, ''] : [],
    children: [],
  }
  emit('edit', newNode)
}

// ============================================================
// 进度条颜色
// ============================================================
function progressColor(pct: number): string {
  if (pct >= 100) return '#67c23a'
  if (pct >= 60)  return '#409eff'
  if (pct >= 30)  return '#e6a23c'
  return '#909399'
}

// ============================================================
// 节点渲染
// ============================================================
function renderNode(h: any, ctx: any) {
  const node: WbsTaskNode = ctx.data
  const status = STATUS_TAG[node.status] || { type: 'info', label: node.status }

  // 主体: [图标] [编码] [名称] [状态] [进度条] [权重] [操作]
  return h('div', {
    class: 'wbs-node',
    style: { display: 'flex', alignItems: 'center', width: '100%', gap: '8px' },
  }, [
    // 图标
    h('span', { style: { fontSize: '14px', flex: '0 0 auto' } },
      TASK_TYPE_ICON[node.taskType] || '📋'),

    // 编码
    h('span', {
      style: {
        fontFamily: 'monospace',
        fontSize: '12px',
        color: '#909399',
        flex: '0 0 auto',
        minWidth: '50px',
      },
    }, node.wbsCode),

    // 名称(可点击)
    h('span', {
      style: {
        flex: '1 1 auto',
        fontWeight: node.critical ? 600 : 400,
        color: node.critical ? '#f56c6c' : '#303133',
        cursor: 'pointer',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
      },
      onClick: (e: Event) => { e.stopPropagation(); onNodeClick(node) },
    }, node.name),

    // 权重标签
    h('el-tag', {
      type: 'info',
      size: 'small',
      effect: 'plain',
      style: { flex: '0 0 auto' },
    }, () => `w=${node.weight}`),

    // 状态
    h('el-tag', {
      type: status.type,
      size: 'small',
      effect: 'dark',
      style: { flex: '0 0 auto' },
    }, () => status.label),

    // 进度条
    h('el-progress', {
      percentage: node.progressPct,
      color: progressColor(node.progressPct),
      strokeWidth: 8,
      textInside: false,
      style: { flex: '0 0 100px' },
      showText: false,
    }),

    // 进度数字
    h('span', {
      style: {
        fontSize: '12px',
        color: '#606266',
        flex: '0 0 36px',
        textAlign: 'right',
      },
    }, `${node.progressPct}%`),

    // 操作按钮
    h('div', {
      class: 'wbs-node-actions',
      style: { flex: '0 0 auto', display: 'flex', gap: '4px' },
    }, [
      h('el-button', {
        type: 'primary', size: 'small', link: true,
        onClick: (e: Event) => { e.stopPropagation(); onAddChild(node) },
      }, () => '＋子任务'),
      h('el-button', {
        type: 'primary', size: 'small', link: true,
        onClick: (e: Event) => { e.stopPropagation(); onEdit(node) },
      }, () => '编辑'),
      h('el-button', {
        type: 'danger', size: 'small', link: true,
        onClick: (e: Event) => { e.stopPropagation(); onDelete(node) },
      }, () => '删除'),
    ]),
  ])
}

// ============================================================
// 汇总卡片
// ============================================================
const summaryCards = computed(() => {
  if (!summary.value) return []
  const s = summary.value
  return [
    { label: '任务总数',     value: s.taskCount,            color: '#303133' },
    { label: '已完成',       value: s.completedCount,       color: '#67c23a' },
    { label: '进行中',       value: s.inProgressCount,      color: '#409eff' },
    { label: '阻塞',         value: s.blockedCount,         color: '#f56c6c' },
    { label: '未开始',       value: s.notStartedCount,      color: '#909399' },
    { label: '关键任务',     value: s.criticalCount,        color: '#e6a23c' },
    { label: '里程碑',       value: s.milestoneCount,       color: '#9c27b0' },
  ]
})

// ============================================================
// 生命周期
// ============================================================
onMounted(load)
watch(() => props.projectId, load)
defineExpose({ load, selectedTask })
</script>

<template>
  <div class="wbs-view">
    <!-- 顶部汇总条 -->
    <el-card v-if="summary" shadow="never" class="wbs-summary">
      <div class="wbs-summary-row">
        <div v-for="c in summaryCards" :key="c.label" class="wbs-summary-cell">
          <div class="wbs-summary-num" :style="{ color: c.color }">{{ c.value }}</div>
          <div class="wbs-summary-label">{{ c.label }}</div>
        </div>
        <div class="wbs-summary-cell wbs-summary-progress">
          <el-progress
            type="circle"
            :percentage="summary.weightedProgressPct"
            :width="60"
            :color="progressColor(summary.weightedProgressPct)"
          />
          <div class="wbs-summary-label">加权进度</div>
        </div>
        <div class="wbs-summary-cell">
          <div class="wbs-summary-num">{{ summary.totalPlanHours }}h</div>
          <div class="wbs-summary-label">计划工时</div>
        </div>
        <div class="wbs-summary-cell">
          <div class="wbs-summary-num">{{ summary.totalActualHours }}h</div>
          <div class="wbs-summary-label">实际工时</div>
        </div>
        <div class="wbs-summary-cell">
          <div class="wbs-summary-num">{{ summary.hoursBurnPct }}%</div>
          <div class="wbs-summary-label">工时燃尽</div>
        </div>
        <div class="wbs-summary-cell wbs-summary-actions">
          <el-button type="primary" :loading="snapping" @click="doSnapshot">
            触发 EVM 快照
          </el-button>
          <el-button type="success" plain @click="onAddChild(null)">
            ＋ 新建顶层任务
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 树 -->
    <el-card v-loading="loading" shadow="never">
      <template v-if="tree.length">
        <el-tree
          ref="treeRef"
          :data="tree"
          :props="{ children: 'children', label: 'name' }"
          node-key="id"
          :default-expanded-keys="defaultExpanded"
          :render-content="renderNode"
          empty-text="该项目暂无 WBS 任务,点右上「＋ 新建顶层任务」开始"
          @node-click="onNodeClick"
        />
      </template>
      <el-empty
        v-else
        description="该项目暂无 WBS 任务"
        :image-size="80"
      >
        <el-button type="primary" @click="onAddChild(null)">
          ＋ 新建第一个任务
        </el-button>
      </el-empty>
    </el-card>
  </div>
</template>

<style scoped>
.wbs-view { display: flex; flex-direction: column; gap: 12px; }
.wbs-summary :deep(.el-card__body) { padding: 12px 16px; }
.wbs-summary-row {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}
.wbs-summary-cell {
  text-align: center;
  min-width: 60px;
}
.wbs-summary-num {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
}
.wbs-summary-label {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.wbs-summary-progress {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.wbs-summary-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

:deep(.el-tree-node__content) {
  height: 36px;
}
:deep(.wbs-node) {
  font-size: 13px;
}
</style>
