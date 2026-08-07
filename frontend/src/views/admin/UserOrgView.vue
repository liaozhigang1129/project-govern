<template>
  <div class="org-page">
    <el-row :gutter="12">
      <!-- 左侧部门树 -->
      <el-col :span="7" :xs="24" :md="7">
        <el-card class="tree-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>🏢 组织架构</span>
              <el-button text :icon="Refresh" @click="loadDeptTree">刷新</el-button>
            </div>
          </template>

          <div class="legend">
            <el-tag size="small" type="success" effect="plain" style="margin-right:6px">
              💡 拖拽用户 → 部门节点 = 分配部门
            </el-tag>
            <el-tag size="small" type="warning" effect="plain" v-if="missingCount > 0">
              ⚠️ {{ missingCount }} 人未分配部门
            </el-tag>
          </div>

          <DepartmentTree
            :data="deptTree"
            :show-member-count="true"
            :default-expand-all="false"
            :draggable="true"
            node-key="id"
            @select="onDeptClick" @drop="onTreeDrop"
          />
        </el-card>

        <!-- 缺失部门用户快捷入口 -->
        <el-card class="missing-card" shadow="hover" v-if="missingCount > 0" style="margin-top: 12px">
          <template #header>
            <div class="card-header">
              <span>
                <el-icon color="#E6A23C"><Warning /></el-icon>
                未分配部门
                <el-badge :value="missingCount" :max="999" type="warning" style="margin-left:6px" />
              </span>
              <el-button text type="primary" @click="openAssignMissingDialog">分配</el-button>
            </div>
          </template>
        </el-card>
      </el-col>

      <!-- 右侧用户列表 -->
      <el-col :span="17" :xs="24" :md="17">
        <el-card class="user-card" shadow="hover" v-loading="loading">
          <template #header>
            <div class="card-header">
              <span>
                <el-icon><OfficeBuilding /></el-icon>
                <span v-if="selectedDept">
                  {{ selectedDept.name }}
                  <el-tag size="small" type="info" style="margin-left: 8px">
                    {{ orgUsers?.totalElements ?? 0 }} 人
                    <span v-if="includeSubDepts && deptDescendantCount > 0">
                      ({{ deptDescendantCount }} 个子部门)
                    </span>
                  </el-tag>
                </span>
                <span v-else style="color: #909399">← 请选择左侧部门</span>
              </span>

              <div class="card-actions" v-if="selectedDept">
                <el-switch
                  v-model="includeSubDepts"
                  active-text="含子部门"
                  inactive-text="仅本部门"
                  inline-prompt
                  @change="loadOrgUsers"
                  style="margin-right: 12px"
                />
                <el-button type="primary" size="small" :icon="Refresh" @click="loadOrgUsers">刷新</el-button>
              </div>
            </div>
          </template>

          <!-- 搜索 -->
          <el-input
            v-model="keyword"
            placeholder="搜索 姓名/账号/手机/邮箱 (回车搜索)"
            clearable
            :prefix-icon="Search"
            @keyup.enter="onKeywordSearch"
            @clear="onKeywordSearch"
            style="margin-bottom: 12px"
          />

          <!-- 用户表格 -->
          <el-table
            v-if="selectedDept"
            :data="orgUsers?.content ?? []"
            border
            stripe
            style="width: 100%"
            empty-text="此部门暂无用户"
            row-key="id"
            
          >
            <el-table-column width="40" label="">
              <template #default="{ row }">
                <el-icon class="drag-handle" :data-user-id="row.id"
                  @mousedown="onDragHandleMouseDown">⋮⋮</el-icon>
              </template>
            </el-table-column>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column label="账号" min-width="100">
              <template #default="{ row }">
                <span>{{ row.username }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="fullName" label="姓名" min-width="80" />
            <el-table-column label="角色" min-width="200">
              <template #default="{ row }">
                <!-- 主角色 (primaryRoleName) -->
                <el-tag v-if="row.primaryRoleName" size="small" type="primary">
                  {{ row.primaryRoleName }}
                </el-tag>
                <!-- 附加角色 (roleCodes 中除主角色外的其余 code → 转 name) -->
                <el-tag
                  v-for="code in (row.roleCodes ?? []).filter((c: any) => c !== row.primaryRoleCode)"
                  :key="code"
                  size="small"
                  type="info"
                  effect="plain"
                  style="margin-left: 4px"
                  :title="code"
                >{{ code }}</el-tag>
                <span v-if="!row.primaryRoleName && !(row.roleCodes?.length)" style="color: #c0c4cc">-</span>
              </template>
            </el-table-column>
            <el-table-column label="部门" min-width="240">
              <template #default="{ row }">
                <div v-if="row.departmentName" style="line-height: 1.3">
                  <div style="font-weight: 500">{{ row.departmentName }}</div>
                  <div
                    v-if="row.departmentPath && row.departmentPath !== row.departmentName"
                    style="font-size: 11px; color: #909399; margin-top: 2px"
                    :title="row.departmentPath"
                  >
                    {{ row.departmentPath }}
                  </div>
                </div>
                <el-tag v-else size="small" type="warning">未分配</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="职位" min-width="100">
              <template #default="{ row }">
                <span v-if="row.jobTitle">{{ row.jobTitle }}</span>
                <span v-else style="color: #c0c4cc">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="phone" label="手机" min-width="120" />
            <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                  {{ row.enabled ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  text
                  type="primary"
                  size="small"
                  @click="onMoveOut(row)"
                >移出本部门</el-button>
              </template>
            </el-table-column>

            <!-- 隐藏的可拖拽行 (通过 row-class-name) -->
          </el-table>

          <!-- 分页 -->
          <el-pagination
            v-if="selectedDept"
            v-model:current-page="page"
            v-model:page-size="size"
            :total="orgUsers?.totalElements ?? 0"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="loadOrgUsers"
            @size-change="onSizeChange"
            style="margin-top: 12px; justify-content: flex-end"
          />
        </el-card>
      </el-col>
    </el-row>

    <!-- 批量分配弹窗 (缺失部门) -->
    <BulkAssignDialog
      v-model:visible="assignDialog.visible"
      :title="`批量分配: ${assignDialog.sourceLabel} → ${assignDialog.targetLabel}`"
      :source-users="assignDialog.sourceUsers"
      @confirm="onAssignConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick, provide } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, OfficeBuilding, Warning } from '@element-plus/icons-vue'

import DepartmentTree from '@/components/DepartmentTree.vue'
import BulkAssignDialog from './UserOrgView.AssignDialog.vue'

import { departmentApi, type DepartmentNode } from '@/api/departments'
import { userApi, type UserListItem } from '@/api/users'

// ============================================================
// 1) 状态
// ============================================================
const deptTree = ref<DepartmentNode[]>([])
const selectedDept = ref<DepartmentNode | null>(null)
const includeSubDepts = ref(true)

const keyword = ref('')
const page = ref(1)
const size = ref(20)
const loading = ref(false)

const orgUsers = ref<{
  content: UserListItem[]
  totalElements: number
  totalPages: number
} | null>(null)

const deptDescendantCount = ref(0)
const missingCount = ref(0)

// 拖拽状态 (行级)
const draggingUserId = ref<number | null>(null)

// 批量分配弹窗
const assignDialog = ref({
  visible: false,
  sourceLabel: '',
  targetLabel: '',
  sourceUsers: [] as UserListItem[],
  pendingAssignments: null as null | Array<{ userId: number; departmentId: number | null }>,
})

// ============================================================
// 2) 加载
// ============================================================
async function loadDeptTree() {
  try {
    const res = await departmentApi.tree()
    deptTree.value = res
    countMissing()
  } catch (e) {
    ElMessage.error('加载部门树失败')
  }
}

async function countMissing() {
  try {
    const res = await departmentApi.missingUsers(0, 1)
    missingCount.value = (res as any)?.totalElements ?? 0
  } catch {
    missingCount.value = 0
  }
}

async function loadOrgUsers() {
  if (!selectedDept.value) return
  loading.value = true
  try {
    let departmentIds: number[] = [selectedDept.value.id]

    if (includeSubDepts.value) {
      const nodes = await departmentApi.descendantIds(selectedDept.value.id)
      departmentIds = Array.isArray(nodes) ? nodes.map((d: any) => d.id) : [selectedDept.value.id]
      deptDescendantCount.value = departmentIds.length - 1
    } else {
      deptDescendantCount.value = 0
    }

    const res = await userApi.listByDepartments(departmentIds, {
      keyword: keyword.value || undefined,
      page: page.value - 1,
      size: size.value,
    })
    orgUsers.value = res as any
  } catch (e) {
    ElMessage.error('加载用户失败')
  } finally {
    loading.value = false
    // 拖拽绑定: 给每行加 dragstart
    nextTick(() => bindRowDrag())
  }
}

// 拖拽 handle: 给 .drag-handle 加 draggable + listener
function bindRowDrag() {
  const handles = document.querySelectorAll<HTMLElement>('.drag-handle')
  handles.forEach((el) => {
    const userId = Number(el.getAttribute('data-user-id') || 0)
    if (!userId || el.dataset.dragBound === '1') return
    el.dataset.dragBound = '1'
    el.setAttribute('draggable', 'true')
    el.style.cursor = 'grab'
    el.addEventListener('dragstart', (e: DragEvent) => {
      onUserDragStart(e, userId)
    })
    el.addEventListener('dragend', () => {
      draggingUserId.value = null
    })
  })
}

function onDragHandleMouseDown(_e: MouseEvent) {
  // 视觉反馈: 行高亮
  // (CSS 处理)
}

// ============================================================
// 3) 事件
// ============================================================
function onDeptClick(node: DepartmentNode | null) {
  selectedDept.value = node
  page.value = 1
  loadOrgUsers()
}

function onKeywordSearch() {
  page.value = 1
  loadOrgUsers()
}

function onUserRowClass({ row }: { row: UserListItem }) {
  return 'draggable-user-row'
}

function onSizeChange() {
  page.value = 1
  loadOrgUsers()
}

// 拖拽 (从用户行到部门树节点)
function onUserDragStart(e: DragEvent, userId: number) {
  if (!e.dataTransfer) return
  draggingUserId.value = userId
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', String(userId))
  e.dataTransfer.setData('application/x-user-id', String(userId))
}

function onMoveOut(row: UserListItem) {
  ElMessageBox.confirm(
    `确认将「${row.fullName || row.username}」移出本部门?`,
    '移出部门',
    { type: 'warning' }
  ).then(async () => {
    try {
      await departmentApi.assignUser(row.id, null)
      ElMessage.success('已移出部门')
      loadOrgUsers()
      loadDeptTree()
      countMissing()
    } catch {
      ElMessage.error('操作失败')
    }
  }).catch(() => {})
}

function openAssignMissingDialog() {
  if (!selectedDept.value) {
    ElMessage.warning('请先选择目标部门')
    return
  }
  // 拉缺失部门的用户列表 (1 次)
  loadMissingSource().then((users) => {
    assignDialog.value = {
      visible: true,
      sourceLabel: '未分配部门',
      targetLabel: selectedDept.value!.name,
      sourceUsers: users,
      pendingAssignments: null,
    }
  })
}

async function loadMissingSource(): Promise<UserListItem[]> {
  try {
    const res = await departmentApi.missingUsers(0, 200)
    return (res as any)?.content ?? []
  } catch {
    return []
  }
}

async function onAssignConfirm(payload: { selectedIds: number[] }) {
  if (!selectedDept.value) return
  const targetId = selectedDept.value.id
  try {
    await departmentApi.bulkAssignUsers(payload.selectedIds, targetId)
    ElMessage.success(`已分配 ${payload.selectedIds.length} 人 → ${selectedDept.value.name}`)
    assignDialog.value.visible = false
    loadOrgUsers()
    loadDeptTree()
    countMissing()
  } catch (e: any) {
    ElMessage.error(e?.message || '分配失败')
  }
}

// ============================================================
// 4) 全局拖放: 监听 drop 事件, 由 DepartmentTree emit
// ============================================================
// DepartmentTree emit 'drop' 时调用
const onTreeDrop = async (payload: { userId: number; toDept: DepartmentNode }) => {
  const { userId, toDept } = payload
  if (!userId) {
    ElMessage.warning('未识别用户')
    return
  }
  if (!toDept?.id) {
    ElMessage.warning('无效部门')
    return
  }
  try {
    await departmentApi.assignUser(userId, toDept.id)
    ElMessage.success(`已分配 → ${toDept.name}`)
    if (selectedDept.value) loadOrgUsers()
    loadDeptTree()
    countMissing()
  } catch (err: any) {
    ElMessage.error(err?.message || '分配失败')
  }
  draggingUserId.value = null
}

// ============================================================
// 5) 生命周期
// ============================================================
onMounted(() => {
  loadDeptTree()
  // 监听全文档的 drop 事件, 配合 el-tree 节点
  document.addEventListener('dragover', (e) => e.preventDefault())
})

// 暴露给 DepartmentTree 通过 provide
provide('onUserDrop', onTreeDrop)
</script>

<style scoped>
.org-page { padding: 16px; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-actions {
  display: flex;
  align-items: center;
}
.legend {
  margin-bottom: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.tree-card { height: calc(100vh - 120px); overflow-y: auto; }
.user-card { min-height: calc(100vh - 120px); }
:deep(.el-table__row) {
  cursor: default;
}
.drag-handle {
  color: #c0c4cc;
  font-size: 16px;
  cursor: grab;
  user-select: none;
  padding: 4px;
  border-radius: 2px;
}
.drag-handle:hover {
  color: #409eff;
  background: #ecf5ff;
}
.drag-handle:active {
  cursor: grabbing;
  background: #d9ecff;
}
:deep(.el-tree-node__content):hover {
  background: #ecf5ff;
}
</style>
