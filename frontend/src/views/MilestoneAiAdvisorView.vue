<script setup lang="ts">
/**
 * 里程碑 AI 预警看板 (P5-智能预警, rule-engine v1.0)
 *
 * 数据流:
 *  列表: GET /api/milestone-ai/advisory?projectId=&status=
 *  汇总: GET /api/milestone-ai/summary?projectId=
 *  详情: GET /api/milestone-ai/advisory/{id}  (含 5 维信号)
 *  落地: POST /api/milestone-ai/apply/{id}
 *  拒绝: POST /api/milestone-ai/reject/{id}
 *  批跑: POST /api/milestone-ai/run-batch?scope=...
 *
 * 设计:
 *  - 顶部 4 个 KPI 卡片 (CRITICAL / WARNING / INFO / 待处理)
 *  - 主体 el-table (按严重度倒序), 行内 5 维信号进度条
 *  - 抽屉 el-drawer 显示单条建议详情 (含原因/建议 JSON)
 *  - 工具栏: 项目切换 + 状态过滤 + 手动跑单/批跑
 */
import { onMounted, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  listAdvisory,
  getAdvisory,
  getSummary,
  applyAdvisory,
  rejectAdvisory,
  runAdvisor,
  runBatch,
  type MilestoneAiAdvisoryDto,
  type Severity,
  type AdvisoryStatus,
  type SignalType
} from '@/api/milestoneAi'
import api from '@/api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  MagicStick, Warning, Bell, Aim, Histogram, Refresh,
  Lightning, CircleCheck, CircleClose, Position
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

// ============================================================
// 项目切换
// ============================================================
const projectList = ref<any[]>([])
const projectId = computed(() => {
  const q = route.query.projectId
  return q ? Number(q) : null
})

async function loadProjects() {
  try {
    const r: any = await api.get('/projects?page=0&size=200')
    projectList.value = Array.isArray(r) ? r : (r.content || r.data?.content || r.data || [])
  } catch (e) {
    ElMessage.error('加载项目列表失败: ' + (e as any).message)
  }
}

function onProjectChange(v: number | undefined) {
  if (!v) return
  router.push({ path: '/milestones/ai-advisor', query: { projectId: v } })
}

// ============================================================
// 列表 / 汇总
// ============================================================
const list = ref<MilestoneAiAdvisoryDto[]>([])
const summary = ref<any>(null)
const loading = ref(false)
const statusFilter = ref<AdvisoryStatus | 'ALL'>('PENDING')
const severityFilter = ref<Severity | 'ALL'>('ALL')

async function load() {
  if (!projectId.value) return
  loading.value = true
  try {
    const params: any = { projectId: projectId.value }
    if (statusFilter.value !== 'ALL') params.status = statusFilter.value
    if (severityFilter.value !== 'ALL') params.severity = severityFilter.value
    list.value = await listAdvisory(params)
    summary.value = await getSummary(projectId.value)
  } catch (e) {
    ElMessage.error('加载建议失败: ' + (e as any).message)
    list.value = []
    summary.value = null
  } finally {
    loading.value = false
  }
}

watch(() => route.query.projectId, (q) => { if (q) load() })
watch([statusFilter, severityFilter], () => { if (projectId.value) load() })

onMounted(async () => {
  await loadProjects()
  if (!projectId.value && projectList.value.length > 0) {
    const first = projectList.value[0]
    router.replace({ path: '/milestones/ai-advisor', query: { projectId: first.id } })
  }
})

// ============================================================
// 详情抽屉
// ============================================================
const drawerOpen = ref(false)
const detail = ref<MilestoneAiAdvisoryDto | null>(null)
const detailLoading = ref(false)

async function openDetail(row: MilestoneAiAdvisoryDto) {
  drawerOpen.value = true
  detailLoading.value = true
  detail.value = row
  try {
    detail.value = await getAdvisory(row.id)
  } catch (e) {
    ElMessage.error('加载详情失败: ' + (e as any).message)
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  drawerOpen.value = false
  detail.value = null
}

// ============================================================
// 操作: 跑单 / 批跑 / 落地 / 拒绝
// ============================================================
const runSingleLoading = ref(false)
async function handleRunSingle() {
  if (!projectId.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  runSingleLoading.value = true
  try {
    // 1) 取项目下的 milestone (复用 MilestoneAnalysis)
    const miles: any[] = await api.get(`/milestones?projectId=${projectId.value}`)
    const terminal = miles.filter(m => m.status !== 'DONE' && m.status !== 'CANCELLED')
    if (terminal.length === 0) {
      ElMessage.info('该项目无活跃里程碑')
      return
    }
    let created = 0
    for (const m of terminal) {
      try {
        await runAdvisor(projectId.value, m.id)
        created++
      } catch (_) { /* 单条失败忽略 */ }
    }
    ElMessage.success(`已扫描 ${terminal.length} 个里程碑, 新建 ${created} 条建议`)
    await load()
  } finally {
    runSingleLoading.value = false
  }
}

const batchLoading = ref(false)
async function handleRunBatch() {
  try {
    await ElMessageBox.confirm(
      '批跑将对当前用户权限下所有项目跑规则引擎, 可能耗时较久, 是否继续?',
      '批量分析',
      { confirmButtonText: '开始跑', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }
  batchLoading.value = true
  try {
    const r = await runBatch({ scope: 'PORTFOLIO', daysToPlan: 60 })
    ElMessage.success(
      `扫描 ${r.scanned} 个里程碑 · 新建 ${r.newAdvisories} 条建议 · 跳过 ${r.skipped} · 耗时 ${r.durationMs}ms`
    )
    await load()
  } catch (e) {
    ElMessage.error('批跑失败: ' + (e as any).message)
  } finally {
    batchLoading.value = false
  }
}

async function handleApply(row: MilestoneAiAdvisoryDto) {
  try {
    await ElMessageBox.confirm(
      `将建议 "${row.milestoneName}" 一键落地为风险, 概率 ${row.suggestedProbability}/5, 影响 ${row.suggestedImpact}/5?`,
      '落地确认',
      { confirmButtonText: '落地为风险', cancelButtonText: '取消', type: 'success' }
    )
  } catch { return }
  try {
    const updated = await applyAdvisory(row.id)
    ElMessage.success(`已落地为风险 #${updated.appliedRiskId}`)
    await load()
    if (detail.value?.id === row.id) detail.value = updated
  } catch (e) {
    ElMessage.error('落地失败: ' + (e as any).message)
  }
}

const rejectDialogOpen = ref(false)
const rejectReason = ref('')
const rejectingId = ref<number | null>(null)

function openRejectDialog(row: MilestoneAiAdvisoryDto) {
  rejectingId.value = row.id
  rejectReason.value = ''
  rejectDialogOpen.value = true
}

async function handleConfirmReject() {
  if (!rejectingId.value) return
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写拒绝理由')
    return
  }
  try {
    const updated = await rejectAdvisory(rejectingId.value, rejectReason.value.trim())
    ElMessage.success('已拒绝')
    rejectDialogOpen.value = false
    await load()
    if (detail.value?.id === updated.id) detail.value = updated
  } catch (e) {
    ElMessage.error('拒绝失败: ' + (e as any).message)
  }
}

// ============================================================
// 工具函数
// ============================================================
function colorBySeverity(s: Severity) {
  return s === 'CRITICAL' ? '#f56c6c' : s === 'WARNING' ? '#e6a23c' : '#67c23a'
}
function colorBySignal(t: SignalType) {
  switch (t) {
    case 'OVERDUE': return '#f56c6c'
    case 'SPI': return '#e6a23c'
    case 'PHASE_LAG': return '#909399'
    case 'VELOCITY': return '#409eff'
    case 'HISTORICAL': return '#67c23a'
    default: return '#909399'
  }
}
function signalLabel(t: SignalType) {
  switch (t) {
    case 'OVERDUE': return '逾期'
    case 'SPI': return 'SPI'
    case 'PHASE_LAG': return '阶段滞后'
    case 'VELOCITY': return '速度变化'
    case 'HISTORICAL': return '历史命中'
    default: return t
  }
}
function statusLabel(s: AdvisoryStatus) {
  return s === 'PENDING' ? '待处理'
    : s === 'APPLIED' ? '已落地'
    : s === 'REJECTED' ? '已拒绝'
    : s === 'EXPIRED' ? '已过期'
    : s
}
function statusType(s: AdvisoryStatus) {
  return s === 'PENDING' ? 'warning'
    : s === 'APPLIED' ? 'success'
    : s === 'REJECTED' ? 'info'
    : 'info'
}
function categoryLabel(c: string) {
  return c === 'SCHEDULE' ? '进度'
    : c === 'COST' ? '成本'
    : c === 'SCOPE' ? '范围'
    : c === 'QUALITY' ? '质量'
    : c === 'RESOURCE' ? '资源'
    : c === 'EXTERNAL' ? '外部'
    : c
}

// 优先按 severity (CRITICAL > WARNING > INFO), 同 severity 按 score 倒序
const sortedList = computed(() => {
  const order: Record<Severity, number> = { CRITICAL: 0, WARNING: 1, INFO: 2 }
  return [...list.value].sort((a, b) => {
    const oa = order[a.severity] ?? 9
    const ob = order[b.severity] ?? 9
    if (oa !== ob) return oa - ob
    return b.score - a.score
  })
})

// KPI 卡片数据
const kpis = computed(() => {
  const s = summary.value
  if (!s) return { critical: 0, warning: 0, info: 0, pending: 0 }
  return {
    critical: s.critical ?? 0,
    warning: s.warning ?? 0,
    info: s.info ?? 0,
    pending: s.pending ?? 0
  }
})
</script>
<template>
  <div class="mai-page">
    <!-- 顶部: 项目选择 + 工具栏 -->
    <el-card shadow="never" class="toolbar">
      <div class="toolbar-row">
        <span class="title">
          <el-icon style="vertical-align: middle;"><MagicStick /></el-icon>
          里程碑 AI 预警 · 规则引擎 v1.0
        </span>
        <el-select
          :model-value="projectId"
          @update:model-value="onProjectChange"
          placeholder="选择项目"
          style="width: 280px"
          filterable
        >
          <el-option
            v-for="p in projectList"
            :key="p.id"
            :label="p.name || p.code"
            :value="p.id"
          />
        </el-select>
        <el-button type="primary" :loading="runSingleLoading" @click="handleRunSingle">
          <el-icon><Lightning /></el-icon> 跑当前项目
        </el-button>
        <el-button type="success" :loading="batchLoading" @click="handleRunBatch">
          <el-icon><Position /></el-icon> 批跑(我的权限范围)
        </el-button>
        <el-button @click="load" :loading="loading">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </el-card>

    <!-- 4 个 KPI 卡片 -->
    <el-row :gutter="12" class="kpi-row" v-if="summary">
      <el-col :span="6">
        <el-card shadow="hover" class="kpi kpi-critical">
          <div class="kpi-icon"><el-icon><Warning /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-num">{{ kpis.critical }}</div>
            <div class="kpi-label">🔴 CRITICAL</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="kpi kpi-warning">
          <div class="kpi-icon"><el-icon><Bell /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-num">{{ kpis.warning }}</div>
            <div class="kpi-label">🟡 WARNING</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="kpi kpi-info">
          <div class="kpi-icon"><el-icon><Aim /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-num">{{ kpis.info }}</div>
            <div class="kpi-label">🟢 INFO</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="kpi kpi-pending">
          <div class="kpi-icon"><el-icon><Histogram /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-num">{{ kpis.pending }}</div>
            <div class="kpi-label">⏳ 待处理</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 过滤 + 列表 -->
    <el-card shadow="never" class="list-card" v-loading="loading">
      <div class="filter-row">
        <el-radio-group v-model="statusFilter" size="small">
          <el-radio-button label="ALL">全部</el-radio-button>
          <el-radio-button label="PENDING">待处理</el-radio-button>
          <el-radio-button label="APPLIED">已落地</el-radio-button>
          <el-radio-button label="REJECTED">已拒绝</el-radio-button>
        </el-radio-group>
        <el-radio-group v-model="severityFilter" size="small" style="margin-left: 12px">
          <el-radio-button label="ALL">所有严重度</el-radio-button>
          <el-radio-button label="CRITICAL">CRITICAL</el-radio-button>
          <el-radio-button label="WARNING">WARNING</el-radio-button>
          <el-radio-button label="INFO">INFO</el-radio-button>
        </el-radio-group>
        <span class="muted" style="margin-left: auto;">
          共 {{ sortedList.length }} 条 · 置信度均值 {{ summary?.avgScore?.toFixed(1) ?? '—' }}
        </span>
      </div>

      <el-table
        :data="sortedList"
        stripe
        size="default"
        empty-text="暂无建议, 可点击右上角「跑当前项目」开始"
        @row-click="openDetail"
        :row-style="{ cursor: 'pointer' }"
      >
        <el-table-column label="严重度" width="110">
          <template #default="{ row }">
            <el-tag :color="colorBySeverity(row.severity)" effect="dark" size="small">
              {{ row.severity }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="milestoneName" label="里程碑" min-width="160" show-overflow-tooltip />
        <el-table-column prop="phaseName" label="阶段" width="120" show-overflow-tooltip />

        <el-table-column label="评分" width="180">
          <template #default="{ row }">
            <div class="score-cell">
              <el-progress
                :percentage="Math.min(100, row.score)"
                :color="colorBySeverity(row.severity)"
                :stroke-width="10"
              />
              <span class="score-num">{{ row.score.toFixed(1) }}</span>
              <span class="conf">· 置信 {{ (row.confidence * 100).toFixed(0) }}%</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="5 维信号" min-width="320">
          <template #default="{ row }">
            <div class="signals">
              <div class="sig" v-for="sig in [
                { t: 'OVERDUE', v: row.signalOverdue },
                { t: 'SPI', v: row.signalSpi },
                { t: 'PHASE_LAG', v: row.signalPhaseLag },
                { t: 'VELOCITY', v: row.signalVelocity },
                { t: 'HISTORICAL', v: row.signalHistorical }
              ]" :key="sig.t">
                <span class="sig-label" :style="{ color: colorBySignal(sig.t) }">{{ signalLabel(sig.t) }}</span>
                <el-progress
                  :percentage="Math.min(100, sig.v)"
                  :color="colorBySignal(sig.t)"
                  :stroke-width="6"
                  :show-text="false"
                  style="flex: 1; margin: 0 6px;"
                />
                <span class="sig-num">{{ sig.v.toFixed(1) }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="类别" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ categoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="建议" width="90">
          <template #default="{ row }">
            <span class="muted">P {{ row.suggestedProbability }}/5</span>
            <span class="muted" style="margin-left: 6px">I {{ row.suggestedImpact }}/5</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              type="success"
              size="small"
              @click.stop="handleApply(row)"
            >
              <el-icon><CircleCheck /></el-icon> 落地
            </el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              type="danger"
              size="small"
              plain
              @click.stop="openRejectDialog(row)"
            >
              <el-icon><CircleClose /></el-icon> 拒绝
            </el-button>
            <el-button
              v-if="row.status === 'APPLIED' && row.appliedRiskId"
              type="primary"
              size="small"
              link
              @click.stop="router.push(`/risks/${row.appliedRiskId}`)"
            >
              查看风险 #{{ row.appliedRiskId }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="drawerOpen"
      :title="`建议详情 · #${detail?.id ?? ''}`"
      size="540px"
      direction="rtl"
      @close="closeDetail"
    >
      <div v-if="detail" v-loading="detailLoading">
        <el-descriptions :column="2" border size="small" class="desc">
          <el-descriptions-item label="里程碑">{{ detail.milestoneName }}</el-descriptions-item>
          <el-descriptions-item label="阶段">{{ detail.phaseName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="计划日期">{{ detail.milestonePlanDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="里程碑状态">{{ detail.milestoneStatusCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="严重度">
            <el-tag :color="colorBySeverity(detail.severity)" effect="dark" size="small">{{ detail.severity }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="评分">{{ detail.score.toFixed(1) }} · 置信 {{ (detail.confidence * 100).toFixed(0) }}%</el-descriptions-item>
          <el-descriptions-item label="类别">{{ categoryLabel(detail.category) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="建议概率/影响" :span="2">P {{ detail.suggestedProbability }}/5 · I {{ detail.suggestedImpact }}/5</el-descriptions-item>
          <el-descriptions-item label="模型版本" :span="2">{{ detail.modelVersion }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="section">📊 5 维信号明细</h4>
        <el-table :data="detail.signals || []" size="small" border>
          <el-table-column label="信号" width="110">
            <template #default="{ row }">
              <el-tag :color="colorBySignal(row.signalType)" effect="dark" size="small">
                {{ signalLabel(row.signalType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="intensity" label="强度" width="80">
            <template #default="{ row }">
              <span :style="{ color: colorBySignal(row.signalType), fontWeight: 600 }">
                {{ row.intensity.toFixed(1) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="weight" label="权重" width="80">
            <template #default="{ row }">{{ (row.weight * 100).toFixed(0) }}%</template>
          </el-table-column>
          <el-table-column prop="score" label="得分" width="80">
            <template #default="{ row }">
              <strong>{{ row.score.toFixed(1) }}</strong>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="说明" />
          <el-table-column label="数据" width="60">
            <template #default="{ row }">
              <el-tag v-if="row.missing" type="info" size="small">缺失</el-tag>
              <el-tag v-else type="success" size="small">齐全</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <h4 class="section">🔍 原因 (Reasons)</h4>
        <ul class="reasons">
          <li v-for="(r, i) in detail.reasonsJson" :key="i">{{ r }}</li>
          <li v-if="!detail.reasonsJson?.length" class="muted">—</li>
        </ul>

        <h4 class="section">💡 建议动作 (Suggestions)</h4>
        <ul class="suggestions">
          <li v-for="(s, i) in detail.suggestionsJson" :key="i">
            <el-tag :color="colorBySignal(s.signal)" effect="plain" size="small">
              {{ signalLabel(s.signal) }}
            </el-tag>
            <span style="margin-left: 8px;">{{ s.action }}</span>
          </li>
          <li v-if="!detail.suggestionsJson?.length" class="muted">—</li>
        </ul>

        <div v-if="detail.status === 'REJECTED' && detail.rejectReason" class="rejected">
          <strong>拒绝理由:</strong> {{ detail.rejectReason }}
        </div>
        <div v-if="detail.status === 'APPLIED' && detail.appliedRiskId" class="applied">
          ✅ 已落地为风险
          <el-button type="primary" size="small" link @click="router.push(`/risks/${detail.appliedRiskId}`)">
            查看风险 #{{ detail.appliedRiskId }}
          </el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 拒绝理由弹窗 -->
    <el-dialog v-model="rejectDialogOpen" title="拒绝建议" width="420px">
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="4"
        placeholder="请说明拒绝理由, 至少 5 个字"
        maxlength="200"
        show-word-limit
      />
      <template #footer>
        <el-button @click="rejectDialogOpen = false">取消</el-button>
        <el-button type="danger" @click="handleConfirmReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.mai-page {
  padding: 12px;
}
.toolbar {
  margin-bottom: 12px;
}
.toolbar-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-right: 8px;
}
.kpi-row {
  margin-bottom: 12px;
}
.kpi {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-left: 4px solid #909399;
}
.kpi-critical { border-left-color: #f56c6c; }
.kpi-warning  { border-left-color: #e6a23c; }
.kpi-info     { border-left-color: #67c23a; }
.kpi-pending  { border-left-color: #409eff; }
.kpi :deep(.el-card__body) {
  display: flex;
  align-items: center;
  padding: 12px 16px;
}
.kpi-icon {
  font-size: 28px;
  margin-right: 12px;
  color: #909399;
}
.kpi-critical .kpi-icon { color: #f56c6c; }
.kpi-warning  .kpi-icon { color: #e6a23c; }
.kpi-info     .kpi-icon { color: #67c23a; }
.kpi-pending  .kpi-icon { color: #409eff; }
.kpi-body { flex: 1; }
.kpi-num {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
}
.kpi-label {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.list-card { margin-top: 4px; }
.filter-row {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}
.muted { color: #909399; font-size: 12px; }
.score-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}
.score-num {
  font-weight: 600;
  min-width: 40px;
  text-align: right;
}
.conf {
  font-size: 11px;
  color: #909399;
}
.signals { display: flex; flex-direction: column; gap: 4px; }
.sig {
  display: flex;
  align-items: center;
  font-size: 12px;
}
.sig-label { width: 50px; font-weight: 600; }
.sig-num { width: 36px; text-align: right; color: #606266; }
.section {
  margin: 20px 0 8px;
  font-size: 14px;
  color: #303133;
  border-left: 3px solid #409eff;
  padding-left: 8px;
}
.reasons, .suggestions {
  margin: 0;
  padding-left: 20px;
  line-height: 1.8;
}
.rejected, .applied {
  margin-top: 16px;
  padding: 10px 12px;
  border-radius: 4px;
  font-size: 13px;
}
.rejected { background: #fef0f0; color: #f56c6c; }
.applied  { background: #f0f9eb; color: #67c23a; }
</style>
