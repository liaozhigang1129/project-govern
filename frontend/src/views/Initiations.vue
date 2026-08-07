<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import api, {
  type ApprovalRecord,
  type DecideRequest,
  type Initiation,
} from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import InitiationWizard from '@/components/initiation/InitiationWizard.vue'

// --- 列表 ---
const list = ref<Initiation[]>([])
const loading = ref(false)
const auth = useAuthStore()

// --- 查询条件 ---
const filters = ref({
  keyword: '',
  statusCode: '',
  currentStep: '',
  departmentId: '',
  startDate: '',
  endDate: '',
})

// 状态选项
const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '待审批', value: 'PENDING' },
  { label: '部门已批', value: 'DEPT_APPROVED' },
  { label: 'PMO已批', value: 'PMO_APPROVED' },
  { label: '已批准', value: 'EXEC_APPROVED' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '待补料', value: 'SUPPLEMENT' },
]

// 当前步骤选项
const stepOptions = [
  { label: '全部步骤', value: '' },
  { label: '部门负责人', value: 'DEPT_LEAD' },
  { label: 'PMO审批', value: 'PMO_ADMIN' },
  { label: '高管审批', value: 'EXEC' },
]

// 部门列表
const departments = ref<{ id: number; code: string; name: string }[]>([])

async function loadDepartments() {
  try {
    departments.value = await api.get('/departments')
  } catch {
    departments.value = []
  }
}

async function load() {
  loading.value = true
  try {
    const params: Record<string, string> = {}
    if (filters.value.keyword) params.keyword = filters.value.keyword
    if (filters.value.statusCode) params.statusCode = filters.value.statusCode
    if (filters.value.currentStep) params.currentStep = filters.value.currentStep
    if (filters.value.departmentId) params.departmentId = filters.value.departmentId
    if (filters.value.startDate) params.startDate = filters.value.startDate
    if (filters.value.endDate) params.endDate = filters.value.endDate
    
    list.value = await api.get('/initiations', { params })
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.value = {
    keyword: '',
    statusCode: '',
    currentStep: '',
    departmentId: '',
    startDate: '',
    endDate: '',
  }
  load()
}

onMounted(() => {
  loadDepartments()
  load()
})

// --- 6 步向导 ---
const wizardVisible = ref(false)
const wizardInitiationId = ref<number | null>(null)
// wizardMode:
//   'create'          - 新建立项, 走完 6 步
//   'edit-basic'      - 修改立项基础信息, 只显示 Step 1 (基础信息可改, 不允许动 Step 2+)
//   'edit-supplement' - 补料, 直接定位 Step 2 (SOW 上传 + 后续 AI/WBS/资源/风险/预算)
const wizardMode = ref<'create' | 'edit-basic' | 'edit-supplement'>('create')

function openCreate() {
  wizardInitiationId.value = null
  wizardMode.value = 'create'
  wizardVisible.value = true
}

function openEdit(row: Initiation) {
  // "修改立项信息" — 只允许改基础信息 (Step 1)
  wizardInitiationId.value = row.id
  wizardMode.value = 'edit-basic'
  wizardVisible.value = true
}

function openWizard(row: Initiation) {
  // "补料" — 上传 SOW (Step 2) 以及后续 AI WBS / 资源 / 风险 / 预算 步骤
  wizardInitiationId.value = row.id
  wizardMode.value = 'edit-supplement'
  wizardVisible.value = true
}

function onWizardCreated(id: number) {
  wizardInitiationId.value = id
  // 创建完成后, 用户通常会继续在向导里完成后续步骤, 保持打开
  // 但要把 mode 切到 supplement, 这样 Step 2+ 仍可继续补料
  wizardMode.value = 'edit-supplement'
  load() // refresh list
}

function onWizardUpdated(_id: number) {
  // 编辑模式保存后, 刷新列表与详情
  load()
  if (detail.value) {
    loadDetail(detail.value.id).catch(() => null)
  }
}

// --- 详情抽屉 ---
const drawerVisible = ref(false)
const detail = ref<Initiation | null>(null)
const records = ref<ApprovalRecord[]>([])
const recordsLoading = ref(false)
const decideLoading = ref(false)
const openPanels = ref<string[]>(['bg', 'goals', 'scope'])

async function openDetail(row: Initiation) {
  detail.value = row
  drawerVisible.value = true
  await Promise.all([loadDetail(row.id), loadRecords(row.id)])
}

async function loadDetail(id: number) {
  detail.value = await api.get(`/initiations/${id}`)
}

async function loadRecords(id: number) {
  recordsLoading.value = true
  try {
    records.value = await api.get(`/initiations/${id}/records`)
  } finally {
    recordsLoading.value = false
  }
}

const isTerminal = computed(() => {
  const code = detail.value?.status?.code
  return code === 'EXEC_APPROVED' || code === 'REJECTED'
})
const isApplicant = computed(() => auth.user?.id === detail.value?.applicantId)
const canResubmit = computed(() =>
  detail.value?.status?.code === 'SUPPLEMENT' && isApplicant.value
)

// --- 审批决定弹窗 ---
const decideDialog = ref(false)
const decideForm = ref<DecideRequest>({ decision: 'APPROVED', comment: '' })
const decideTitle = computed(() => {
  const map = { APPROVED: '通过审批', REJECTED: '驳回立项', SUPPLEMENT: '打回补材料' }
  return map[decideForm.value.decision] ?? '审批决定'
})

function openDecide(decision: 'APPROVED' | 'REJECTED' | 'SUPPLEMENT') {
  decideForm.value = { decision, comment: '' }
  decideDialog.value = true
}

async function submitDecide() {
  if (!detail.value) return
  decideLoading.value = true
  try {
    await api.post(`/initiations/${detail.value.id}/decide`, decideForm.value)
    ElMessage.success('已记录')
    decideDialog.value = false
    await Promise.all([loadDetail(detail.value.id), loadRecords(detail.value.id), load()])
  } finally {
    decideLoading.value = false
  }
}

const resubmitLoading = ref(false)
async function resubmit() {
  if (!detail.value) return
  await ElMessageBox.confirm(
    `确认「重新提交」此立项?状态将回到 PENDING,等待 ${detail.value.currentStep ?? '当前审批人'} 重审。`,
    '提示', { type: 'info' },
  ).catch(() => null)
  if (!detail.value) return
  resubmitLoading.value = true
  try {
    await api.post(`/initiations/${detail.value.id}/resubmit`, {})
    ElMessage.success('已重新提交')
    await Promise.all([loadDetail(detail.value.id), loadRecords(detail.value.id), load()])
  } finally {
    resubmitLoading.value = false
  }
}

function statusType(code?: string) {
  switch (code) {
    case 'EXEC_APPROVED': return 'success'
    case 'REJECTED': return 'danger'
    case 'PENDING':
    case 'DEPT_APPROVED':
    case 'PMO_APPROVED': return 'warning'
    case 'SUPPLEMENT': return 'info'
    default: return 'info'
  }
}

// --- 删除立项 ---
const deleteLoading = ref(false)
async function confirmDelete(row: Initiation) {
  const text = row.status?.code === 'EXEC_APPROVED'
    ? `「${row.code}」已终审通过,无法删除。`
    : row.projectId
    ? `「${row.code}」已关联项目 (P-#${row.projectId}),请先删除项目。`
    : `确认删除「${row.code} · ${row.title}」?删除后无法恢复。`
  await ElMessageBox.confirm(text, '删除立项', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
    confirmButtonClass: 'el-button--danger',
  }).catch(() => null)
  deleteLoading.value = true
  try {
    await api.delete(`/initiations/${row.id}`)
    ElMessage.success(`已删除「${row.code}」`)
    load()
  } finally {
    deleteLoading.value = false
  }
}

const canDelete = (row: Initiation) =>
  row.status?.code !== 'EXEC_APPROVED' && row.projectId == null
function decisionType(d: string) {
  if (d === 'APPROVED') return 'success'
  if (d === 'REJECTED') return 'danger'
  return 'info'
}
function decisionLabel(d: string) {
  if (d === 'APPROVED') return '通过'
  if (d === 'REJECTED') return '驳回'
  if (d === 'SUPPLEMENT') return '打回补料'
  return d
}
function fmt(dt?: string) {
  if (!dt) return '—'
  return dt.replace('T', ' ').slice(0, 19)
}
</script>

<template>
  <div class="page">
    <!-- 查询条件 -->
    <el-card class="filter-card" shadow="never">
      <el-form inline :model="filters" class="filter-form">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            placeholder="编号/标题"
            clearable
            @keyup.enter="load"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.statusCode" placeholder="全部状态" clearable style="width: 140px">
            <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="当前步骤">
          <el-select v-model="filters.currentStep" placeholder="全部步骤" clearable style="width: 140px">
            <el-option v-for="opt in stepOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="filters.departmentId" placeholder="全部部门" clearable style="width: 180px">
            <el-option label="全部部门" value="" />
            <el-option v-for="dept in departments" :key="dept.id" :label="`${dept.code} - ${dept.name}`" :value="String(dept.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="提交时间">
          <el-date-picker
            v-model="filters.startDate"
            type="date"
            placeholder="开始日期"
            value-format="YYYY-MM-DD"
            clearable
          />
          <span style="margin: 0 8px">~</span>
          <el-date-picker
            v-model="filters.endDate"
            type="date"
            placeholder="结束日期"
            value-format="YYYY-MM-DD"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="load">查询</el-button>
          <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>立项列表 (V3.0 全流程)</span>
          <el-button type="primary" @click="openCreate">+ 新建立项</el-button>
        </div>
      </template>
      <el-table
        v-loading="loading" :data="list" stripe row-key="id"
        @row-click="openDetail" style="cursor: pointer"
      >
        <el-table-column prop="code" label="编号" width="170" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status?.code)">{{ row.status?.name }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前步骤" width="120">
          <template #default="{ row }">
            <span v-if="row.currentStep">{{ row.currentStep }}</span>
            <span v-else style="color: #909399">—</span>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">
            <span style="color: #606266">{{ fmt(row.submittedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" @click.stop>
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click.stop="openEdit(row)">
              修改
            </el-button>
            <el-button size="small" type="primary" link @click.stop="openWizard(row)">
              补料
            </el-button>
            <el-button
              v-if="canDelete(row)"
              size="small" type="danger" link
              :loading="deleteLoading"
              @click.stop="confirmDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 6 步向导 -->
    <InitiationWizard
      v-model="wizardVisible"
      :initiation-id="wizardInitiationId"
      :mode="wizardMode"
      @created="onWizardCreated"
      @updated="onWizardUpdated"
    />

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="detail ? `${detail.code} · ${detail.title}` : '立项详情'"
      size="560px"
      direction="rtl"
    >
      <div v-if="detail" v-loading="recordsLoading">
        <el-descriptions :column="1" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.status?.code)">{{ detail.status?.name }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="当前步骤">
            <span v-if="detail.currentStep">{{ detail.currentStep }}</span>
            <span v-else style="color: #909399">— (终态)</span>
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ fmt(detail.submittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="关闭时间">{{ fmt(detail.closedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.projectId" label="已建项目 ID">
            <el-tag type="success" effect="plain">P-#{{ detail.projectId }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-collapse v-model="openPanels">
          <el-collapse-item title="立项背景" name="bg">
            <div style="white-space: pre-wrap; color: #303133">{{ detail.background }}</div>
          </el-collapse-item>
          <el-collapse-item title="目标" name="goals">
            <div style="white-space: pre-wrap; color: #303133">{{ detail.goals }}</div>
          </el-collapse-item>
          <el-collapse-item title="范围" name="scope">
            <div style="white-space: pre-wrap; color: #303133">{{ detail.scope }}</div>
          </el-collapse-item>
        </el-collapse>

        <div style="margin: 24px 0 12px; font-weight: 600; color: #303133">
          审批记录 ({{ records.length }})
        </div>
        <el-empty v-if="records.length === 0" description="暂无审批记录" :image-size="60" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="r in records" :key="r.id"
            :type="decisionType(r.decision)" :timestamp="fmt(r.decidedAt)"
          >
            <el-tag :type="decisionType(r.decision)" size="small">{{ decisionLabel(r.decision) }}</el-tag>
            <span style="margin-left: 8px; color: #909399; font-size: 12px">
              approver #{{ r.approverId }} · step #{{ r.stepId }}
            </span>
            <div v-if="r.comment" style="margin-top: 4px; color: #606266">{{ r.comment }}</div>
          </el-timeline-item>
        </el-timeline>

        <div v-if="!isTerminal" style="margin-top: 24px; padding-top: 16px; border-top: 1px solid #ebeef5">
          <div style="margin-bottom: 8px; color: #909399; font-size: 12px">
            <span v-if="isApplicant">身份:申请人</span>
            <span v-else>身份:审批人 ({{ auth.user?.role }})</span>
            · 当前步骤 <b>{{ detail.currentStep }}</b>
          </div>
          <el-button v-if="!isApplicant" type="success" @click="openDecide('APPROVED')">通过</el-button>
          <el-button v-if="!isApplicant" type="warning" @click="openDecide('SUPPLEMENT')">打回补料</el-button>
          <el-button v-if="!isApplicant" type="danger" @click="openDecide('REJECTED')">驳回</el-button>
          <el-button v-if="canResubmit" type="primary" :loading="resubmitLoading" @click="resubmit">
            重新提交(补料后)
          </el-button>
        </div>
        <el-alert
          v-else style="margin-top: 16px"
          :title="detail.status?.code === 'EXEC_APPROVED' ? '已批准,项目已自动创建' : '已驳回,流程结束'"
          :type="detail.status?.code === 'EXEC_APPROVED' ? 'success' : 'error'"
          :closable="false"
        />
      </div>
    </el-drawer>

    <!-- 审批决定弹窗 -->
    <el-dialog v-model="decideDialog" :title="decideTitle" width="480px">
      <el-form :model="decideForm" label-width="80px">
        <el-form-item label="意见">
          <el-input
            v-model="decideForm.comment" type="textarea" :rows="3"
            :placeholder="decideForm.decision === 'APPROVED' ? '可选,留个备注'
              : (decideForm.decision === 'SUPPLEMENT' ? '请说明要补什么材料' : '请说明驳回原因')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="decideDialog = false">取消</el-button>
        <el-button
          type="primary" :loading="decideLoading" @click="submitDecide"
          :disabled="decideForm.decision !== 'APPROVED' && !decideForm.comment?.trim()"
        >确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>
