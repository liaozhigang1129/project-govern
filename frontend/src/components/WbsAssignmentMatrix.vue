<script setup lang="ts">
/**
 * WbsAssignmentMatrix — 资源分配矩阵 (P3.2)
 *
 *  - 行 = WBS 任务 (扁平, 按 wbs_code 升序)
 *  - 列 = 项目下出现过的所有 userId
 *  - 单元格 = 该 (task, user) 的分配, 标"角色/工时"
 *  - 点击空单元格 → 弹"新增"  (选角色 + 工时)
 *  - 点击已有分配 → 弹"编辑"  (改角色/工时/删除)
 *
 * 数据源:
 *  - 任务: getWbsFlat(projectId)
 *  - 分配: getAssignmentsByProject(projectId)  (扁平, 前端按 (task, user) 索引)
 *  - 用户: userApi.list()  (列名, 简化为 fullName 或 username)
 */
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getWbsFlat,
  getAssignmentsByProject,
  upsertAssignment,
  deleteAssignment,
  type WbsTaskNode,
  type WbsAssignment,
} from '@/api/wbs'
import { userApi } from '@/api/gantt'
import type { AppUser } from '@/api/client'

// 与 upsertAssignment 入参一致 (内联)
type AssignmentReq = {
  id?: number
  wbsTaskId: number
  userId: number
  role: 'LEAD' | 'DOER' | 'REVIEWER' | 'QA' | 'OBSERVER'
  plannedHours: number
  actualHours?: number
  startDate?: string
  endDate?: string
}

const props = defineProps<{
  projectId: number
}>()

const emit = defineEmits<{
  (e: 'changed'): void
}>()

// ============================================================
// 数据
// ============================================================
const tasks = ref<WbsTaskNode[]>([])
const assignments = ref<WbsAssignment[]>([])
const users = ref<AppUser[]>([])
const loading = ref(false)

// 编辑弹窗
const dialog = ref({
  visible: false,
  mode: 'create' as 'create' | 'edit',
  assignment: null as WbsAssignment | null,
  form: {
    wbsTaskId: 0,
    wbsCode: '',
    userId: null as number | null,
    role: 'DOER' as 'LEAD' | 'DOER' | 'REVIEWER' | 'QA' | 'OBSERVER',
    plannedHours: 0,
    startDate: null as string | null,
    endDate: null as string | null,
  },
})
const saving = ref(false)

// ============================================================
// 派生: 用户列(去重,按 ID 升序) + 矩阵索引
// ============================================================
const userColumns = computed(() => {
  const ids = new Set<number>()
  for (const a of assignments.value) ids.add(a.userId)
  // 同时并入已加载的 AppUser (保持顺序: 先出现的分配)
  const result: { id: number; name: string }[] = []
  for (const id of ids) {
    const u = users.value.find(u => u.id === id)
    result.push({ id, name: u ? (u.fullName || u.username) : `#${id}` })
  }
  return result.sort((a, b) => a.id - b.id)
})

/** (taskId, userId) → Assignment */
const matrix = computed(() => {
  const m = new Map<string, WbsAssignment>()
  for (const a of assignments.value) {
    m.set(`${a.wbsTaskId}:${a.userId}`, a)
  }
  return m
})

function cellFor(taskId: number, userId: number): WbsAssignment | null {
  return matrix.value.get(`${taskId}:${userId}`) ?? null
}

// ============================================================
// 角色标签
// ============================================================
const ROLE_LABEL: Record<string, { label: string; color: string }> = {
  LEAD:     { label: '负责', color: '#f56c6c' },
  DOER:     { label: '执行', color: '#409eff' },
  REVIEWER: { label: '评审', color: '#67c23a' },
  QA:       { label: '测试', color: '#e6a23c' },
  OBSERVER: { label: '观察', color: '#909399' },
}

// ============================================================
// 行汇总 (按任务)
// ============================================================
function taskTotalHours(t: WbsTaskNode): number {
  let sum = 0
  for (const a of assignments.value) {
    if (a.wbsTaskId === t.id) sum += Number(a.plannedHours) || 0
  }
  return sum
}

function userTotalHours(uid: number): number {
  let sum = 0
  for (const a of assignments.value) {
    if (a.userId === uid) sum += Number(a.plannedHours) || 0
  }
  return sum
}

const grandTotalHours = computed(() =>
  assignments.value.reduce((s, a) => s + (Number(a.plannedHours) || 0), 0)
)

// ============================================================
// 加载
// ============================================================
async function load() {
  loading.value = true
  try {
    const [t, a, u] = await Promise.all([
      getWbsFlat(props.projectId),
      getAssignmentsByProject(props.projectId),
      userApi.list({ enabled: true }).catch(() => [] as AppUser[]),
    ])
    tasks.value = t
    assignments.value = a
    users.value = u
  } catch (e: any) {
    ElMessage.error(`加载资源矩阵失败: ${e.message}`)
  } finally {
    loading.value = false
  }
}

// ============================================================
// 单元格操作
// ============================================================
function onCellClick(t: WbsTaskNode, uid: number) {
  const exist = cellFor(t.id, uid)
  if (exist) {
    // 编辑
    dialog.value = {
      visible: true,
      mode: 'edit',
      assignment: exist,
      form: {
        wbsTaskId: t.id,
        wbsCode: t.wbsCode,
        userId: exist.userId,
        role: exist.role as any,
        plannedHours: Number(exist.plannedHours),
        startDate: exist.startDate,
        endDate: exist.endDate,
      },
    }
  } else {
    // 新增
    dialog.value = {
      visible: true,
      mode: 'create',
      assignment: null,
      form: {
        wbsTaskId: t.id,
        wbsCode: t.wbsCode,
        userId: uid,
        role: 'DOER',
        plannedHours: 0,
        startDate: null,
        endDate: null,
      },
    }
  }
}

function onAddNew(t: WbsTaskNode) {
  // 给任务加新分配, 让用户先选 user
  ElMessageBox.prompt(
    `为任务 ${t.wbsCode} ${t.name} 添加新分配, 请输入 userId`,
    '新增分配',
    { inputPattern: /^\d+$/, inputErrorMessage: '请输入数字 userId', confirmButtonText: '下一步', cancelButtonText: '取消' }
  ).then(({ value }) => {
    const uid = Number(value)
    dialog.value = {
      visible: true,
      mode: 'create',
      assignment: null,
      form: {
        wbsTaskId: t.id,
        wbsCode: t.wbsCode,
        userId: uid,
        role: 'DOER',
        plannedHours: 0,
        startDate: null,
        endDate: null,
      },
    }
  }).catch(() => { /* 取消 */ })
}

async function onSave() {
  if (!dialog.value.form.userId) {
    ElMessage.warning('请选择 userId')
    return
  }
  saving.value = true
  try {
    const req: AssignmentReq = {
      id: dialog.value.assignment?.id,
      wbsTaskId: dialog.value.form.wbsTaskId,
      userId: dialog.value.form.userId,
      role: dialog.value.form.role,
      plannedHours: dialog.value.form.plannedHours,
      startDate: dialog.value.form.startDate ?? undefined,
      endDate: dialog.value.form.endDate ?? undefined,
    }
    await upsertAssignment(req)
    ElMessage.success(dialog.value.mode === 'create' ? '已分配' : '已更新')
    dialog.value.visible = false
    emit('changed')
    await load()
  } catch (e: any) {
    ElMessage.error(`保存失败: ${e.message}`)
  } finally {
    saving.value = false
  }
}

async function onDelete() {
  if (!dialog.value.assignment) return
  try {
    await ElMessageBox.confirm('确认删除该分配?', '删除', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
    })
  } catch { return }
  try {
    await deleteAssignment(dialog.value.assignment.id)
    ElMessage.success('已删除')
    dialog.value.visible = false
    emit('changed')
    await load()
  } catch (e: any) {
    ElMessage.error(`删除失败: ${e.message}`)
  }
}

// ============================================================
// 生命周期
// ============================================================
onMounted(load)
watch(() => props.projectId, load)
defineExpose({ load })
</script>

<template>
  <div class="asn-matrix" v-loading="loading">
    <!-- 空态 -->
    <el-empty
      v-if="!loading && tasks.length === 0"
      description="该项目暂无 WBS 任务,先去 WBS 页拆任务再来分配"
    />

    <template v-else>
      <!-- 汇总 -->
      <div class="asn-summary">
        <span>任务数: <b>{{ tasks.length }}</b></span>
        <span>人员数: <b>{{ userColumns.length }}</b></span>
        <span>分配条数: <b>{{ assignments.length }}</b></span>
        <span>计划工时合计: <b>{{ grandTotalHours }}h</b></span>
      </div>

      <!-- 矩阵 -->
      <div class="asn-scroll">
        <table class="asn-table">
          <thead>
            <tr>
              <th class="asn-th-task">WBS 任务 \ 人员</th>
              <th
                v-for="u in userColumns"
                :key="u.id"
                class="asn-th-user"
                :title="`userId=${u.id}`"
              >
                {{ u.name }}
                <div class="asn-th-hours">{{ userTotalHours(u.id) }}h</div>
              </th>
              <th class="asn-th-sum">任务合计</th>
              <th class="asn-th-add">+</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="t in tasks" :key="t.id">
              <td class="asn-td-task" :title="t.name">
                <b>{{ t.wbsCode }}</b>
                <span class="asn-task-name">{{ t.name }}</span>
              </td>
              <td
                v-for="u in userColumns"
                :key="u.id"
                class="asn-td-cell"
                :class="{ filled: cellFor(t.id, u.id) }"
                @click="onCellClick(t, u.id)"
              >
                <template v-if="cellFor(t.id, u.id)">
                  <div
                    class="asn-role"
                    :style="{ background: ROLE_LABEL[cellFor(t.id, u.id)!.role]?.color || '#909399' }"
                  >
                    {{ ROLE_LABEL[cellFor(t.id, u.id)!.role]?.label || cellFor(t.id, u.id)!.role }}
                  </div>
                  <div class="asn-hours">{{ Number(cellFor(t.id, u.id)!.plannedHours) }}h</div>
                </template>
                <span v-else class="asn-plus">+</span>
              </td>
              <td class="asn-td-sum">{{ taskTotalHours(t) }}h</td>
              <td class="asn-td-add" @click="onAddNew(t)" title="新增分配">＋</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 图例 -->
      <div class="asn-legend">
        <span>角色:</span>
        <span v-for="(r, k) in ROLE_LABEL" :key="k" class="asn-legend-item">
          <span class="asn-role" :style="{ background: r.color, display: 'inline-block', width: '14px', height: '14px', borderRadius: '3px', verticalAlign: 'middle' }"></span>
          {{ r.label }}
        </span>
      </div>
    </template>

    <!-- 编辑/新增 弹窗 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.mode === 'create' ? '新增分配' : '编辑分配'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-descriptions :column="1" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="任务">
          <b>{{ dialog.form.wbsCode }}</b>
        </el-descriptions-item>
        <el-descriptions-item label="userId">
          {{ dialog.form.userId }}
        </el-descriptions-item>
      </el-descriptions>

      <el-form :model="dialog.form" label-width="100px">
        <el-form-item label="角色">
          <el-radio-group v-model="dialog.form.role">
            <el-radio-button value="LEAD">负责</el-radio-button>
            <el-radio-button value="DOER">执行</el-radio-button>
            <el-radio-button value="REVIEWER">评审</el-radio-button>
            <el-radio-button value="QA">测试</el-radio-button>
            <el-radio-button value="OBSERVER">观察</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="计划工时(h)">
          <el-input-number v-model="dialog.form.plannedHours" :min="0" :step="0.5" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker
            v-model="dialog.form.startDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker
            v-model="dialog.form.endDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button v-if="dialog.mode === 'edit'" type="danger" @click="onDelete">删除</el-button>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">
          {{ dialog.mode === 'create' ? '分配' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.asn-matrix { display: flex; flex-direction: column; gap: 12px; }

.asn-summary {
  display: flex; gap: 24px; font-size: 13px; color: #606266;
}
.asn-summary b { color: #303133; }

.asn-scroll { overflow-x: auto; }
.asn-table {
  border-collapse: collapse;
  width: 100%;
  font-size: 12px;
}
.asn-table th, .asn-table td {
  border: 1px solid #ebeef5;
  padding: 6px 8px;
  text-align: center;
  white-space: nowrap;
}
.asn-th-task { background: #f5f7fa; min-width: 200px; text-align: left; position: sticky; left: 0; z-index: 2; }
.asn-th-user { background: #f5f7fa; min-width: 80px; }
.asn-th-hours { font-size: 11px; color: #909399; font-weight: normal; margin-top: 2px; }
.asn-th-sum   { background: #fdf6ec; min-width: 80px; }
.asn-th-add   { background: #f5f7fa; width: 36px; }

.asn-td-task {
  text-align: left;
  background: #fafafa;
  position: sticky; left: 0; z-index: 1;
  max-width: 240px;
  overflow: hidden; text-overflow: ellipsis;
}
.asn-task-name { margin-left: 6px; color: #606266; }

.asn-td-cell { cursor: pointer; min-width: 80px; }
.asn-td-cell:hover { background: #ecf5ff; }
.asn-td-cell.filled { background: #f0f9eb; }
.asn-td-cell.filled:hover { background: #e1f3d8; }

.asn-role {
  display: inline-block;
  color: #fff;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  margin-bottom: 2px;
}
.asn-hours { font-size: 11px; color: #606266; }
.asn-plus  { color: #c0c4cc; font-size: 14px; }

.asn-td-sum { background: #fdf6ec; font-weight: 600; }
.asn-td-add {
  background: #fafafa; cursor: pointer; color: #409eff;
}
.asn-td-add:hover { background: #ecf5ff; }

.asn-legend {
  display: flex; gap: 12px; font-size: 12px; color: #606266; align-items: center;
}
.asn-legend-item { display: flex; gap: 4px; align-items: center; }
</style>
