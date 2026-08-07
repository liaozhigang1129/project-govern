<script setup lang="ts">
/**
 * 项目 WBS 页面
 *
 * 上: EvmTrendCard (EVM 挣值趋势, P3.1)
 * 中: 视图切换(树 / 甘特) — 默认树
 * 下: 详情面板 + 编辑弹窗
 *
 * 顶栏入口:
 *  - 资源分配矩阵 (P3.2): /projects/{id}/assignments
 *  - 甘特图切换 (P3.3): WbsGanttView
 */
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import WbsTreeView from '@/components/WbsTreeView.vue'
import WbsTaskEditDialog from '@/components/WbsTaskEditDialog.vue'
import WbsGanttView from '@/components/WbsGanttView.vue'
import WbsNetworkView from '@/components/WbsNetworkView.vue'
import EvmTrendCard from '@/components/EvmTrendCard.vue'
import type { WbsTaskNode } from '@/api/wbs'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.id))

/** 视图模式: 树 / 甘特 (P3.3) / 网络图 (P3.2) */
const viewMode = ref<'tree' | 'gantt' | 'network'>('tree')

const editDialog = ref({
  visible: false,
  task: null as Partial<WbsTaskNode> | null,
  parent: null as WbsTaskNode | null,
})
const treeRef = ref<InstanceType<typeof WbsTreeView> | null>(null)
const selected = ref<WbsTaskNode | null>(null)

function onSelect(task: WbsTaskNode) {
  selected.value = task
}

function onEdit(task: WbsTaskNode) {
  editDialog.value = {
    visible: true,
    task: { ...task },
    parent: null,
  }
}

function onSaved() {
  // 重载树
  treeRef.value?.load()
}

/** 甘特图里点里程碑 → 切回树并选中该任务 */
function onGanttTaskClick(taskId: number) {
  viewMode.value = 'tree'
  // 等视图切回树后, 再点开
  setTimeout(() => {
    const tree = (treeRef.value as any)?.getNode?.(taskId)
    if (tree) {
      ;(treeRef.value as any).setCurrentKey?.(taskId)
    }
  }, 100)
}
</script>

<template>
  <div class="wbs-page">
    <el-page-header :icon="null" style="margin-bottom: 12px">
      <template #content>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span style="font-size: 18px; font-weight: 600">
            📋 WBS 工作分解
            <el-tag size="small" type="info" effect="plain" style="margin-left: 8px">
              项目 #{{ projectId }}
            </el-tag>
          </span>
          <div style="display: flex; gap: 8px">
            <el-button
              type="success"
              plain
              @click="router.push(`/projects/${projectId}/assignments`)"
            >
              👥 资源分配矩阵
            </el-button>
          </div>
        </div>
      </template>
    </el-page-header>

    <!-- P3.1: EVM 趋势卡片 (顶部) -->
    <EvmTrendCard :project-id="projectId" @snapshot="onSaved" />

    <!-- P3.3 视图切换: 树 / 甘特 -->
    <el-card shadow="never" v-loading="false">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <el-radio-group v-model="viewMode" size="default">
            <el-radio-button value="tree">🌲 树视图</el-radio-button>
            <el-radio-button value="gantt">📊 甘特图</el-radio-button>
            <el-radio-button value="network">🕸 网络图</el-radio-button>
          </el-radio-group>
          <span style="font-size: 12px; color: #909399">
            树: 结构 / 甘特: 时间排期 / 网络: 依赖关系 + 关键路径
          </span>
        </div>
      </template>

      <!-- 树视图 -->
      <div v-if="viewMode === 'tree'" class="wbs-page-grid">
        <!-- 左: 树 -->
        <div class="wbs-tree-col">
          <WbsTreeView
            ref="treeRef"
            :project-id="projectId"
            @select="onSelect"
            @edit="onEdit"
          />
        </div>

        <!-- 右: 详情面板 -->
        <div class="wbs-detail-col">
          <el-card v-if="selected" shadow="never">
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center">
                <span>
                  <b>{{ selected.wbsCode }}</b> · {{ selected.name }}
                </span>
                <el-button size="small" type="primary" @click="onEdit(selected)">
                  编辑
                </el-button>
              </div>
            </template>

            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="状态">
                <el-tag size="small" :type="selected.status === 'COMPLETED' ? 'success' : selected.status === 'IN_PROGRESS' ? 'primary' : 'info'">
                  {{ selected.status }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="类型">
                {{ selected.taskType }}
                <el-tag v-if="selected.critical" type="danger" size="small" effect="dark" style="margin-left: 6px">关键</el-tag>
                <el-tag v-if="selected.milestone" type="warning" size="small" effect="dark" style="margin-left: 6px">里程碑</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="进度">
                <el-progress
                  :percentage="selected.progressPct"
                  :stroke-width="14"
                  :color="selected.progressPct >= 100 ? '#67c23a' : selected.progressPct >= 60 ? '#409eff' : '#e6a23c'"
                />
              </el-descriptions-item>
              <el-descriptions-item label="权重 (1-10)">
                {{ selected.weight }}
              </el-descriptions-item>
              <el-descriptions-item label="计划区间">
                {{ selected.planStartDate || '—' }} → {{ selected.planEndDate || '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="实际区间">
                {{ selected.actualStartDate || '—' }} → {{ selected.actualEndDate || '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="工时">
                计划 {{ selected.planHours }}h / 实际 {{ selected.actualHours }}h
              </el-descriptions-item>
              <el-descriptions-item label="紧前任务" v-if="selected.predecessorIds?.length">
                <el-tag
                  v-for="pid in selected.predecessorIds"
                  :key="pid"
                  size="small"
                  effect="plain"
                  style="margin-right: 4px"
                >#{{ pid }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="交付物" v-if="selected.deliverable">
                {{ selected.deliverable }}
              </el-descriptions-item>
              <el-descriptions-item label="备注" v-if="selected.remark">
                {{ selected.remark }}
              </el-descriptions-item>
              <el-descriptions-item label="子任务数">
                {{ selected.children?.length || 0 }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
          <el-empty
            v-else
            description="点击左侧树节点查看详情"
            :image-size="80"
          />
        </div>
      </div>

      <!-- 甘特图视图 (P3.3) -->
      <div v-else-if="viewMode === 'gantt'">
        <WbsGanttView
          :project-id="projectId"
          @task-click="onGanttTaskClick"
        />
      </div>

      <!-- 网络图视图 (P3.2 + P3.3 关键路径) -->
      <div v-else-if="viewMode === 'network'">
        <WbsNetworkView
          :project-id="projectId"
          @task-click="onGanttTaskClick"
        />
      </div>
    </el-card>

    <WbsTaskEditDialog
      v-model="editDialog.visible"
      :project-id="projectId"
      :task="editDialog.task"
      :parent="editDialog.parent"
      @saved="onSaved"
    />
  </div>
</template>

<style scoped>
.wbs-page { display: flex; flex-direction: column; gap: 12px; }
.wbs-page-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 12px;
  align-items: start;
}
.wbs-detail-col { position: sticky; top: 12px; }
</style>
