<script setup lang="ts">
/**
 * P3 工时审批独立入口
 *  - 默认进 SUBMITTED Tab(SUBMITTED + 待审批数)
 *  - 三个 Tab:SUBMITTED(待审) / APPROVED(已批) / REJECTED(驳回后变 DRAFT 的,需要重新提交)
 *  - 支持:查看详情 / 单条批准 / 驳回(comment 必填 5+ 字) / 批量批准
 */
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar, Check, Close, Refresh, Document, View, Search } from '@element-plus/icons-vue'
import { timesheetApi, type TimesheetDetail, type TimesheetSummary } from '@/api/timesheet'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const isApprover = computed(() => {
  // 兼容 login 响应(role 字符串)与 /auth/me(primaryRole.code)
  const u: any = auth.user
  const r = (u?.role ?? u?.primaryRole?.code ?? '') as string
  return r === 'PMO_ADMIN' || r === 'ADMIN' || r === 'EXEC'
})

// ----- 三个 Tab -----
type StatusKey = 'SUBMITTED' | 'APPROVED' | 'DRAFT_REJECTED'
const activeTab = ref<StatusKey>('SUBMITTED')
const allList = ref<TimesheetSummary[]>([])
const loading = ref(false)

// 驳回后落到 DRAFT,目前用 submitterNote 包含【驳回】前缀来识别
const submittedRows = computed(() => allList.value.filter((r) => r.status === 'SUBMITTED'))
const approvedRows = computed(() => allList.value.filter((r) => r.status === 'APPROVED'))
const rejectedRows = computed(() =>
  allList.value.filter((r) => r.status === 'DRAFT' && (r.submitterNote ?? '').startsWith('【驳回】')),
)

// ----- 搜索/过滤 -----
const keyword = ref('')
const filteredRows = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  const src =
    activeTab.value === 'SUBMITTED'
      ? submittedRows.value
      : activeTab.value === 'APPROVED'
        ? approvedRows.value
        : rejectedRows.value
  if (!k) return src
  return src.filter(
    (r) =>
      (r.userName ?? '').toLowerCase().includes(k) || r.weekStart.includes(k) || String(r.userId).includes(k),
  )
})

// ----- 多选 -----
const selection = ref<TimesheetSummary[]>([])
function onSelectionChange(rows: TimesheetSummary[]) {
  selection.value = rows
}

// ----- 加载(后端 /timesheets 一把拉,前端切 Tab 不用重发) -----
async function load() {
  if (!isApprover.value) {
    ElMessage.error('只有 PMO_ADMIN/EXEC 可访问')
    return
  }
  loading.value = true
  try {
    // 一次拉 200 条(覆盖最近 4 周足够)
    const res = await timesheetApi.list({ page: 0, size: 200 })
    allList.value = res.content
  } catch (e: any) {
    ElMessage.error(e.message ?? '加载失败')
  } finally {
    loading.value = false
  }
}

watch(activeTab, () => {
  selection.value = []
})
onMounted(load)

// ----- 单条批准 -----
async function approveOne(row: TimesheetSummary) {
  try {
    await ElMessageBox.confirm(
      `批准 ${row.userName} 的 ${row.weekStart} 周报(${row.totalHours}h,${row.entryCount} 行)?`,
      '审批',
      { type: 'success' },
    )
  } catch {
    return
  }
  try {
    await timesheetApi.approve(row.id)
    ElMessage.success('已批准')
    await load()
  } catch (e: any) {
    ElMessage.error(e.message ?? '批准失败')
  }
}

// ----- 驳回(需 comment ≥ 5 字) -----
const rejectDialog = ref(false)
const rejectTarget = ref<TimesheetSummary | null>(null)
const rejectComment = ref('')

function openReject(row: TimesheetSummary) {
  rejectTarget.value = row
  rejectComment.value = ''
  rejectDialog.value = true
}

async function doReject() {
  if (!rejectTarget.value) return
  if (rejectComment.value.trim().length < 5) {
    ElMessage.warning('驳回理由至少 5 个字')
    return
  }
  try {
    await timesheetApi.reject(rejectTarget.value.id, rejectComment.value.trim())
    ElMessage.success('已驳回,提交人将收到理由')
    rejectDialog.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.message ?? '驳回失败')
  }
}

// ----- 批量批准 -----
async function batchApprove() {
  if (selection.value.length === 0) {
    ElMessage.warning('请先勾选要批准的周报')
    return
  }
  const ids = selection.value.map((r) => r.id)
  try {
    await ElMessageBox.confirm(`批量批准 ${ids.length} 份周报?`, '批量审批', { type: 'success' })
  } catch {
    return
  }
  try {
    const out = (await timesheetApi.batchApprove(ids)) as unknown as {
      approved: any[]
      requested: number
      successCount: number
    }
    ElMessage.success(`已批准 ${out.successCount}/${out.requested} 份(去重后)`)
    selection.value = []
    await load()
  } catch (e: any) {
    ElMessage.error(e.message ?? '批量批准失败')
  }
}

// ----- 详情抽屉 -----
const detailVisible = ref(false)
const detail = ref<TimesheetDetail | null>(null)
const detailLoading = ref(false)

async function showDetail(row: TimesheetSummary) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await timesheetApi.get(row.id)
  } catch (e: any) {
    ElMessage.error(e.message ?? '加载详情失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

// ----- 状态 Tag 颜色 -----
function statusTag(s: string) {
  return { DRAFT: 'info', SUBMITTED: 'warning', APPROVED: 'success' }[s] ?? 'info'
}

function fmtTime(s?: string) {
  return s ? s.replace('T', ' ').slice(0, 19) : '-'
}
</script>

<template>
  <div class="page">
    <el-card>
      <template #header>
        <div
          style="
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 12px;
          "
        >
          <div>
            <el-icon style="vertical-align: middle"><Check /></el-icon>
            <span style="font-size: 16px; font-weight: 600">工时审批中心</span>
            <span style="color: #909399; margin-left: 8px">仅 PMO_ADMIN / ADMIN / EXEC 可见</span>
          </div>
          <div style="display: flex; gap: 8px; align-items: center">
            <el-input
              v-model="keyword"
              placeholder="搜索 提交人/周次/用户ID"
              clearable
              style="width: 240px"
              :prefix-icon="Search"
            />
            <el-button :icon="Refresh" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <!-- KPI 顶部 -->
      <div style="display: flex; gap: 16px; margin-bottom: 12px; flex-wrap: wrap">
        <el-tag type="warning" size="large">待审批 {{ submittedRows.length }} 份</el-tag>
        <el-tag type="success" size="large">本周已批 {{ approvedRows.length }} 份</el-tag>
        <el-tag type="info" size="large">驳回待改 {{ rejectedRows.length }} 份</el-tag>
        <el-tag v-if="selection.length" type="primary" size="large">已选 {{ selection.length }} 份</el-tag>
      </div>

      <el-tabs v-model="activeTab">
        <!-- Tab 1:待审 -->
        <el-tab-pane :label="`待我审批 (${submittedRows.length})`" name="SUBMITTED">
          <div style="margin-bottom: 8px; display: flex; gap: 8px">
            <el-button type="success" :icon="Check" :disabled="selection.length === 0" @click="batchApprove">
              批量批准({{ selection.length }})
            </el-button>
          </div>
          <el-table
            :data="filteredRows"
            v-loading="loading"
            border
            @selection-change="onSelectionChange"
            empty-text="🎉 没有待审批的周报"
          >
            <el-table-column type="selection" width="48" />
            <el-table-column prop="weekStart" label="周次" width="180">
              <template #default="{ row }">{{ row.weekStart }} ~ {{ row.weekEnd }}</template>
            </el-table-column>
            <el-table-column prop="userName" label="提交人" width="120" />
            <el-table-column prop="totalHours" label="工时" width="80" />
            <el-table-column prop="projectCount" label="项目" width="70" />
            <el-table-column prop="entryCount" label="行数" width="70" />
            <el-table-column prop="submittedAt" label="提交时间" width="180">
              <template #default="{ row }">{{ fmtTime(row.submittedAt) }}</template>
            </el-table-column>
            <el-table-column prop="submitterNote" label="备注" min-width="200" show-overflow-tooltip />
            <el-table-column label="操作" width="280" fixed="right">
              <template #default="{ row }">
                <el-button :icon="View" size="small" link @click="showDetail(row)">查看</el-button>
                <el-button type="success" size="small" :icon="Check" @click="approveOne(row)">批准</el-button>
                <el-button type="danger" size="small" :icon="Close" @click="openReject(row)">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 2:已批 -->
        <el-tab-pane :label="`已审批 (${approvedRows.length})`" name="APPROVED">
          <el-table :data="filteredRows" v-loading="loading" border empty-text="暂无已审批记录">
            <el-table-column prop="weekStart" label="周次" width="180">
              <template #default="{ row }">{{ row.weekStart }} ~ {{ row.weekEnd }}</template>
            </el-table-column>
            <el-table-column prop="userName" label="提交人" width="120" />
            <el-table-column prop="totalHours" label="工时" width="80" />
            <el-table-column prop="projectCount" label="项目" width="70" />
            <el-table-column prop="entryCount" label="行数" width="70" />
            <el-table-column prop="approverName" label="审批人" width="120" />
            <el-table-column prop="approvedAt" label="审批时间" width="180">
              <template #default="{ row }">{{ fmtTime(row.approvedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button :icon="View" size="small" link @click="showDetail(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 3:驳回待改 -->
        <el-tab-pane :label="`驳回待改 (${rejectedRows.length})`" name="DRAFT_REJECTED">
          <el-table :data="filteredRows" v-loading="loading" border empty-text="暂无驳回记录">
            <el-table-column prop="weekStart" label="周次" width="180">
              <template #default="{ row }">{{ row.weekStart }} ~ {{ row.weekEnd }}</template>
            </el-table-column>
            <el-table-column prop="userName" label="提交人" width="120" />
            <el-table-column prop="totalHours" label="工时" width="80" />
            <el-table-column prop="approverName" label="驳回人" width="120" />
            <el-table-column prop="submitterNote" label="驳回理由" min-width="280" show-overflow-tooltip>
              <template #default="{ row }">
                <span style="color: #f56c6c">{{ row.submitterNote }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button :icon="View" size="small" link @click="showDetail(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 驳回对话框 -->
    <el-dialog v-model="rejectDialog" title="驳回周报" width="520px">
      <div v-if="rejectTarget" style="margin-bottom: 12px; color: #606266">
        <span>
          提交人:
          <b>{{ rejectTarget.userName }}</b>
          ｜ 周次:
          <b>{{ rejectTarget.weekStart }}</b>
        </span>
      </div>
      <el-form>
        <el-form-item label="驳回理由" required>
          <el-input
            v-model="rejectComment"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请说明驳回原因,提交人修改后可重新提交(至少 5 个字)"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialog = false">取消</el-button>
        <el-button type="danger" @click="doReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="detailVisible"
      :title="`工时周报详情 #${detail?.id ?? ''}`"
      size="60%"
      :destroy-on-close="true"
    >
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border style="margin-bottom: 16px">
            <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTag(detail.status) as any">{{ detail.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="提交人">{{ detail.userName }}</el-descriptions-item>
            <el-descriptions-item label="周次">
              {{ detail.weekStart }} ~ {{ detail.weekEnd }}
            </el-descriptions-item>
            <el-descriptions-item label="审批人">{{ detail.approverName ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="总工时">
              <b
                :style="{
                  color: detail.totalHours > 60 ? '#f56c6c' : detail.totalHours > 40 ? '#e6a23c' : '#67c23a',
                }"
              >
                {{ detail.totalHours }}h
              </b>
            </el-descriptions-item>
            <el-descriptions-item label="提交时间" :span="2">
              {{ fmtTime(detail.submittedAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">
              <span :style="{ color: (detail.submitterNote ?? '').startsWith('【驳回】') ? '#f56c6c' : '' }">
                {{ detail.submitterNote ?? '—' }}
              </span>
            </el-descriptions-item>
          </el-descriptions>

          <h4>每日明细 ({{ detail.entries.length }} 行,合计 {{ detail.totalHours }}h)</h4>
          <el-table :data="detail.entries" border size="small">
            <el-table-column prop="workDate" label="日期" width="120" />
            <el-table-column prop="projectId" label="项目ID" width="80" />
            <el-table-column prop="milestoneId" label="里程碑ID" width="100" />
            <el-table-column prop="hours" label="工时" width="80" />
            <el-table-column prop="description" label="描述" min-width="240" show-overflow-tooltip />
          </el-table>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
</style>
