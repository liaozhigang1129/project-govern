<template>
  <el-table
    v-if="treeData.length"
    v-loading="loading"
    :data="displayData"
    row-key="id"
    :tree-props="treeProps"
    :expand-row-keys="expandRowKeys"
    border
    :stripe="mode === 'plan'"
    size="small"
  >
    <!-- WBS tab columns (mode='wbs') -->
    <template v-if="mode === 'wbs'">
      <el-table-column prop="wbsCode" label="WBS" width="100" />
      <el-table-column prop="name" label="任务名称" min-width="200" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="关键" width="50" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.critical" type="danger" size="small" effect="dark">★</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="计划起止" width="200" align="center">
        <template #default="{ row }">
          <span v-if="row.planStartDate">{{ row.planStartDate }} → {{ row.planEndDate }}</span>
          <span v-else style="color: #c0c4cc">—</span>
        </template>
      </el-table-column>
      <el-table-column label="责任人" width="100" align="center">
        <template #default="{ row }">
          <span v-if="row.ownerUserId">{{ owners[row.ownerUserId] ?? '#' + row.ownerUserId }}</span>
          <span v-else style="color: #c0c4cc">—</span>
        </template>
      </el-table-column>
      <el-table-column label="工时 (实际/计划)" width="140" align="right">
        <template #default="{ row }">
          <strong>{{ row.actualHours }}</strong>
          / {{ row.planHours }}h
        </template>
      </el-table-column>
      <el-table-column label="进度" width="160" align="center">
        <template #default="{ row }">
          <el-progress :percentage="row.progressPct" :stroke-width="10" />
        </template>
      </el-table-column>
      <el-table-column label="交付物" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.deliverable" style="font-size: 12px">{{ row.deliverable }}</span>
          <span v-else style="color: #c0c4cc">—</span>
        </template>
      </el-table-column>
    </template>

    <!-- Plan tab columns (mode='plan') -->
    <template v-else>
      <el-table-column prop="wbsCode" label="#" width="80" />
      <el-table-column label="层级" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.children?.length" type="warning" size="small" effect="plain">📦 工作包</el-tag>
          <span v-else-if="row.taskType === 'SUMMARY'" style="color: #909399; font-size: 11px">汇总</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="任务" min-width="200" />
      <el-table-column label="关键" width="50" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.critical" type="danger" size="small">★</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="前驱" width="120" align="center">
        <template #default="{ row }">
          <span
            v-if="row.predecessorIds?.length"
            style="font-size: 11px; display: flex; gap: 2px; flex-wrap: wrap; justify-content: center"
          >
            <el-tag v-for="pid in row.predecessorIds" :key="pid" size="small" effect="plain">
              #{{ flatData.find((x: any) => x.id === pid)?.wbsCode ?? pid }}
            </el-tag>
          </span>
          <span v-else style="color: #c0c4cc">—</span>
        </template>
      </el-table-column>
      <el-table-column label="计划起止" width="190" align="center">
        <template #default="{ row }">
          <span v-if="row.planStartDate">{{ row.planStartDate }} → {{ row.planEndDate }}</span>
          <span v-else style="color: #c0c4cc">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="责任人" width="90" align="center">
        <template #default="{ row }">
          <span v-if="row.ownerUserId">{{ owners[row.ownerUserId] ?? '#' + row.ownerUserId }}</span>
          <span v-else style="color: #c0c4cc">—</span>
        </template>
      </el-table-column>
      <el-table-column label="计划工时" width="80" align="right">
        <template #default="{ row }">{{ row.planHours }}h</template>
      </el-table-column>
      <el-table-column label="实际工时" width="80" align="right">
        <template #default="{ row }">
          <strong :style="{ color: progressColor(row.actualHours, row.planHours) }">
            {{ row.actualHours }}h
          </strong>
        </template>
      </el-table-column>
      <el-table-column label="完成度" width="80" align="center">
        <template #default="{ row }">{{ row.progressPct }}%</template>
      </el-table-column>
      <el-table-column label="交付物" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.deliverable" style="font-size: 12px">{{ row.deliverable }}</span>
          <span v-else style="color: #c0c4cc">—</span>
        </template>
      </el-table-column>
    </template>

    <!-- 操作列 (通用) -->
    <el-table-column label="操作" width="200" align="center" fixed="right">
      <template #default="{ row }">
        <el-button size="small" type="primary" link @click="emit('add', row)">
          {{ mode === 'wbs' ? '+ 子' : '+ 拆解' }}
        </el-button>
        <el-button size="small" link @click="emit('edit', row)">编辑</el-button>
        <el-button size="small" type="danger" link @click="emit('delete', row)">删除</el-button>
      </template>
    </el-table-column>
  </el-table>
  <el-empty v-else description="该项目暂无 WBS 任务" :image-size="80" />
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface WbsTaskNode {
  id: number
  parentId: number | null
  wbsCode: string
  name: string
  taskType: string
  status: string
  ownerUserId: number | null
  planStartDate: string | null
  planEndDate: string | null
  actualStartDate: string | null
  actualEndDate: string | null
  planHours: number
  actualHours: number
  progressPct: number
  weight: number
  critical: boolean
  milestone: boolean
  predecessorIds: number[] | null
  deliverable: string | null
  remark: string | null
  children?: WbsTaskNode[]
}

const props = withDefaults(
  defineProps<{
    mode: 'wbs' | 'plan'
    treeData: WbsTaskNode[]
    flatData: WbsTaskNode[]
    owners: Record<number, string>
    loading?: boolean
    expandRowKeys?: number[]
  }>(),
  {
    loading: false,
    expandRowKeys: () => [],
  },
)

const emit = defineEmits<{
  (e: 'add', row: WbsTaskNode | null): void
  (e: 'edit', row: WbsTaskNode): void
  (e: 'delete', row: WbsTaskNode): void
}>()

// WBS tab 使用 flat + tree-props children (平铺但可折叠)
// Plan tab 使用 tree + tree-props (真正树形)
const displayData = computed(() => (props.mode === 'wbs' ? props.flatData : props.treeData))

const treeProps = computed(() => {
  if (props.mode === 'wbs') {
    return { children: 'children' }
  }
  return { children: 'children', hasChildren: 'hasChildren' }
})

function statusType(s: string) {
  switch (s) {
    case 'COMPLETED':
      return 'success'
    case 'IN_PROGRESS':
      return 'warning'
    case 'BLOCKED':
      return 'danger'
    case 'CANCELLED':
      return 'info'
    default:
      return 'info'
  }
}
function statusLabel(s: string) {
  const map: Record<string, string> = {
    NOT_STARTED: '未开始',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    BLOCKED: '阻塞',
    CANCELLED: '已取消',
  }
  return map[s] ?? s
}
function progressColor(actual: number, plan: number) {
  if (plan === 0) return '#909399'
  const ratio = actual / plan
  if (ratio > 1) return '#f56c6c' // 超支
  if (ratio > 0.8) return '#67c23a' // 健康
  if (ratio > 0.5) return '#e6a23c' // 落后
  return '#909399'
}
</script>
