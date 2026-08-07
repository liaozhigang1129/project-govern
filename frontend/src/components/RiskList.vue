<script setup lang="ts">
/**
 * RiskList — 风险登记册表格 (P4)
 *
 * 跟 WbsTreeView / WbsAssignmentMatrix 风格一致, 一张大表 + 筛选条.
 *
 * 上: 筛选条 (status / level / category) + 新建按钮
 * 中: 表格 — 编号/标题/分类/等级标签/状态标签/负责人/分数/操作
 * 下: 空态
 *
 * 状态 / 等级 / 分数用 Element Plus <el-tag>, 颜色按 level 走 (LOW/MEDIUM/HIGH/CRITICAL)
 *
 * 交互:
 *  - 行点击 → 抛 'select' 事件, 父组件 (RiskView) 决定是开 drawer 还是路由
 *  - 操作列: 编辑 / 删除 (走 emit, 父组件开 dialog)
 *
 * 数据源: useRiskStore (Pinia)
 *   - loadList(projectId)  → listByProject
 *   - loadList(projectId, true) → activeByProject (子集, 但状态过滤不同)
 *   - 默认显示 active, "全部" 切到 list
 */
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRiskStore } from '@/stores/risk'
import type { RiskItem, RiskLevel, RiskStatus } from '@/api/risk'

// 重新导出供父组件 import (保持单一定义源)
export type { RiskItem, RiskLevel, RiskStatus } from '@/api/risk'

const props = defineProps<{
  projectId: number
  /** 父组件控制: 'active' | 'all' */
  scope?: 'active' | 'all'
  /** 操作列是否显示删除 (PMO 视角可删, 个人视角只读) */
  canDelete?: boolean
}>()

const emit = defineEmits<{
  (e: 'select', risk: RiskItem): void
  (e: 'edit',   risk: RiskItem): void
  (e: 'create'): void
}>()

const store = useRiskStore()

// 筛选 (本地状态, 远端已经按 status 切过)
const filterLevel    = ref<RiskLevel | ''>('')
const filterCategory = ref<string>('')
const filterStatus   = ref<RiskStatus | ''>('')
const filterText     = ref('')        // 模糊匹配 code/title

const loading = computed(() => store.isLoading(`list:${props.projectId}:${props.scope !== 'all'}`))

const source = computed<RiskItem[]>(() => {
  const map = props.scope === 'all' ? store.listByProject : store.activeByProject
  return map.get(props.projectId) ?? []
})

const filtered = computed<RiskItem[]>(() => {
  const lvl = filterLevel.value
  const cat = filterCategory.value
  const st  = filterStatus.value
  const txt = filterText.value.trim().toLowerCase()
  return source.value.filter(r => {
    if (lvl && r.level !== lvl) return false
    if (cat && r.category !== cat) return false
    if (st  && r.status  !== st)  return false
    if (txt && !(`${r.code} ${r.title}`.toLowerCase().includes(txt))) return false
    return true
  })
})

// 颜色 / 标签映射
function levelTagType(lvl: RiskLevel): '' | 'success' | 'warning' | 'danger' | 'info' {
  switch (lvl) {
    case 'CRITICAL': return 'danger'
    case 'HIGH':     return 'warning'
    case 'MEDIUM':   return ''
    case 'LOW':      return 'success'
  }
}
function statusTagType(s: RiskStatus): '' | 'success' | 'warning' | 'info' | 'primary' {
  switch (s) {
    case 'OPEN':        return 'info'
    case 'MITIGATING':  return 'warning'
    case 'OCCURRED':    return 'danger' as any   // ElTag 实际支持, 但 union 没列
    case 'ACCEPTED':    return ''
    case 'CLOSED':      return 'success'
    default:            return 'info'
  }
}
function categoryLabel(c: string) {
  return {
    TECHNICAL: '技术', SCHEDULE: '进度', COST: '成本', QUALITY: '质量',
    EXTERNAL: '外部', ORGANIZATIONAL: '组织', OTHER: '其他',
  }[c] ?? c
}

// 行点击 → 父组件弹 drawer
function onRowClick(row: RiskItem) { emit('select', row) }
function onEdit(row: RiskItem, e: Event) {
  e.stopPropagation()
  emit('edit', row)
}

async function onDelete(row: RiskItem, e: Event) {
  e.stopPropagation()
  try {
    await ElMessageBox.confirm(
      `确定删除风险 ${row.code} ${row.title}? 此操作会写历史, 可追溯.`,
      '删除确认', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await store.remove(row.id, props.projectId)
    ElMessage.success('已删除')
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error('删除失败: ' + e.message)
  }
}

// 暴露给父组件
defineExpose({ load })

async function load() {
  await store.loadList(props.projectId, props.scope !== 'all')
}
onMounted(load)
watch(() => props.projectId, load)
watch(() => props.scope, load)
</script>

<template>
  <div class="risk-list" v-loading="loading">
    <!-- 筛选条 -->
    <el-form inline class="filter-bar" :model="{ l: filterLevel, c: filterCategory, s: filterStatus, t: filterText }">
      <el-form-item label="搜索">
        <el-input
          v-model="filterText"
          placeholder="编号 / 标题"
          clearable
          style="width: 180px"
          size="default"
        />
      </el-form-item>
      <el-form-item label="分类">
        <el-select v-model="filterCategory" placeholder="全部" clearable style="width: 130px">
          <el-option label="技术"   value="TECHNICAL" />
          <el-option label="进度"   value="SCHEDULE" />
          <el-option label="成本"   value="COST" />
          <el-option label="质量"   value="QUALITY" />
          <el-option label="外部"   value="EXTERNAL" />
          <el-option label="组织"   value="ORGANIZATIONAL" />
          <el-option label="其他"   value="OTHER" />
        </el-select>
      </el-form-item>
      <el-form-item label="等级">
        <el-select v-model="filterLevel" placeholder="全部" clearable style="width: 120px">
          <el-option label="低"   value="LOW" />
          <el-option label="中"   value="MEDIUM" />
          <el-option label="高"   value="HIGH" />
          <el-option label="严重" value="CRITICAL" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="scope === 'all'" label="状态">
        <el-select v-model="filterStatus" placeholder="全部" clearable style="width: 130px">
          <el-option label="已识别" value="OPEN" />
          <el-option label="应对中" value="MITIGATING" />
          <el-option label="已发生" value="OCCURRED" />
          <el-option label="已接受" value="ACCEPTED" />
          <el-option label="已关闭" value="CLOSED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="emit('create')">
          <el-icon><Plus /></el-icon> 新建风险
        </el-button>
        <el-button text @click="load">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 表格 -->
    <el-table
      :data="filtered"
      stripe
      border
      size="default"
      empty-text="该项目暂无风险 — 点击右上角「新建风险」开始登记"
      @row-click="onRowClick"
      style="cursor: pointer"
    >
      <el-table-column prop="code" label="编号" width="90" />
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column label="分类" width="80">
        <template #default="{ row }">
          <span style="color: #606266">{{ categoryLabel(row.category) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="等级" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="levelTagType(row.level)" effect="dark" size="small">
            {{ row.level }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small" effect="plain">
            {{
              row.status === 'OPEN' ? '已识别' :
              row.status === 'MITIGATING' ? '应对中' :
              row.status === 'OCCURRED' ? '已发生' :
              row.status === 'ACCEPTED' ? '已接受' :
              row.status === 'CLOSED' ? '已关闭' : row.status
            }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="P × I" width="80" align="center">
        <template #default="{ row }">
          <span style="color: #909399">{{ row.probability }} × {{ row.impact }}</span>
        </template>
      </el-table-column>
      <el-table-column label="分数" width="70" align="center">
        <template #default="{ row }">
          <span :style="{ fontWeight: 600, color: row.score >= 16 ? '#f56c6c' : row.score >= 10 ? '#e6a23c' : '#67c23a' }">
            {{ row.score }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="ownerName" label="负责人" width="100">
        <template #default="{ row }">
          <span v-if="row.ownerName">{{ row.ownerName }}</span>
          <span v-else style="color: #c0c4cc">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="onEdit(row, $event)">编辑</el-button>
          <el-button
            v-if="canDelete"
            size="small"
            link
            type="danger"
            @click="onDelete(row, $event)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 底部统计 -->
    <div class="footer" v-if="filtered.length">
      共 <b>{{ filtered.length }}</b> 条
      <span v-if="filtered.length !== source.length" style="color: #909399; margin-left: 8px">
        (已筛选自 {{ source.length }} 条)
      </span>
    </div>
  </div>
</template>

<style scoped>
.risk-list {
  background: #fff;
  border-radius: 6px;
  padding: 12px;
}
.filter-bar {
  margin-bottom: 8px;
}
.filter-bar :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 12px;
}
.footer {
  margin-top: 8px;
  font-size: 13px;
  color: #606266;
  text-align: right;
}
</style>
