<script setup lang="ts">
/**
 * RiskDetailDrawer — 风险详情侧抽屉 (P4)
 *
 * 5 个 section:
 *   1. 头部 (编号 / 标题 / 等级 / 状态 / 关闭时间)
 *   2. 关键指标 (P×I 大色块 + 4 标签)
 *   3. 风险元数据 (分类/负责人/识别/目标/实际 + 描述 + 关联)
 *   4. 应对措施 (mitigation / contingency 双块)
 *   5. 应对行动 (内嵌 mini-table + 增改删)  —— 由 step-B 追加
 *   6. 历史时间轴 (el-timeline)               —— 由 step-B 追加
 *
 * 底部操作栏 + 样式 —— 由 step-C 追加
 *
 * 数据源: useRiskStore (Pinia)
 *   - risk: props 传入, 不在 store 重新查
 *   - responses: store.responsesByRisk.get(riskId)
 *   - history:   store.historyByRisk.get(riskId)
 *
 * 交互:
 *   - "编辑" → 抛 'edit' 事件, 父组件开 RiskFormDialog
 *   - "删除" → 二次确认 + store.remove + 关闭 drawer
 */
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Warning, User, Calendar, Document, Refresh, Edit, Delete, Plus } from '@element-plus/icons-vue'
import { useRiskStore } from '@/stores/risk'
import type { RiskItem, RiskLevel, RiskStatus, RiskHistoryItem } from '@/api/risk'

// ============================================================
// props / emit
// ============================================================
const props = defineProps<{
  modelValue: boolean
  risk: RiskItem | null
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'edit',   risk: RiskItem): void
}>()

const store = useRiskStore()
const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

// ============================================================
// 工具: 颜色 / 中文 / 标签
// ============================================================
function levelTagType(l: RiskLevel) {
  return { CRITICAL: 'danger', HIGH: 'warning', MEDIUM: '', LOW: 'success' }[l] as '' | 'success' | 'warning' | 'danger'
}
function statusTagType(s: RiskStatus) {
  return { OPEN: 'info', MITIGATING: 'warning', OCCURRED: 'danger', ACCEPTED: '', CLOSED: 'success' }[s] as '' | 'success' | 'warning' | 'info' | 'danger'
}
function statusLabel(s: RiskStatus) {
  return { OPEN: '已识别', MITIGATING: '应对中', OCCURRED: '已发生', ACCEPTED: '已接受', CLOSED: '已关闭' }[s] ?? s
}
function categoryLabel(c: string) {
  return { TECHNICAL: '技术', SCHEDULE: '进度', COST: '成本', QUALITY: '质量', EXTERNAL: '外部', ORGANIZATIONAL: '组织', OTHER: '其他' }[c] ?? c
}
function strategyLabel(s: string | null) {
  if (!s) return '—'
  return { AVOID: '规避 AVOID', MITIGATE: '缓解 MITIGATE', TRANSFER: '转移 TRANSFER', ACCEPT: '接受 ACCEPT', EXPLOIT: '开拓 EXPLOIT', ENHANCE: '提高 ENHANCE', SHARE: '分享 SHARE' }[s] ?? s
}

// score 配色 (跟 List / Matrix 一致)
function scoreColor(score: number) {
  if (score >= 16) return '#f56c6c'
  if (score >= 10) return '#e6a23c'
  return '#67c23a'
}

// ============================================================
// 派生: 应对行动 + 历史
// ============================================================
const responses = computed(() => {
  if (!props.risk?.id) return []
  return store.responsesByRisk.get(props.risk.id) ?? []
})
const history = computed<RiskHistoryItem[]>(() => {
  if (!props.risk?.id) return []
  return store.historyByRisk.get(props.risk.id) ?? []
})

// ============================================================
// 加载 (抽屉打开时拉响应 + 历史)
// ============================================================
async function load() {
  if (!props.risk?.id) return
  await Promise.all([
    store.loadResponses(props.risk.id),
    store.loadHistory(props.risk.id),
  ])
}
onMounted(load)
watch(() => [props.modelValue, props.risk?.id], ([v, id]) => {
  if (v && id) load()
})
defineExpose({ load })

// ============================================================
// 应对行动 (内嵌编辑)  —— step B 追加
// ============================================================
const respEditingId  = ref<number | 'new' | null>(null)  // 当前编辑的行 id, 'new' = 新建
const respDraft      = ref<{ action: string; ownerUserId: number | null; dueDate: string | null; status: 'PLANNED' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED'; note: string }>({
  action: '', ownerUserId: null, dueDate: null, status: 'PLANNED', note: '',
})
const respSaving     = ref(false)

function respStartNew() {
  respEditingId.value = 'new'
  respDraft.value = { action: '', ownerUserId: null, dueDate: null, status: 'PLANNED', note: '' }
}
function respStartEdit(id: number) {
  const r = responses.value.find(x => x.id === id)
  if (!r) return
  respEditingId.value = id
  respDraft.value = {
    action: r.action,
    ownerUserId: r.ownerUserId,
    dueDate: r.dueDate,
    status: r.status as any,
    note: r.note ?? '',
  }
}
function respCancel() {
  respEditingId.value = null
}
async function respSave() {
  if (!props.risk?.id || !respDraft.value.action.trim()) {
    ElMessage.warning('请填写应对动作')
    return
  }
  respSaving.value = true
  try {
    const payload: any = {
      id: respEditingId.value === 'new' ? undefined : (respEditingId.value as number),
      action: respDraft.value.action.trim(),
      ownerUserId: respDraft.value.ownerUserId,
      dueDate: respDraft.value.dueDate,
      status: respDraft.value.status,
      note: respDraft.value.note || null,
    }
    await store.saveResponse(props.risk.id, payload)
    ElMessage.success(respEditingId.value === 'new' ? '已新增' : '已更新')
    respEditingId.value = null
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e.message ?? ''))
  } finally {
    respSaving.value = false
  }
}
async function respDelete(id: number) {
  if (!props.risk?.id) return
  try {
    await ElMessageBox.confirm('确定删除该应对行动? 该操作会写历史, 可追溯.', '删除确认', { type: 'warning' })
    await store.removeResponse(props.risk.id, id)
    ElMessage.success('已删除')
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error('删除失败: ' + e.message)
  }
}

function respStatusLabel(s: string) {
  return { PLANNED: '已计划', IN_PROGRESS: '执行中', DONE: '已完成', CANCELLED: '已取消' }[s] ?? s
}
function respStatusType(s: string) {
  return { PLANNED: 'info', IN_PROGRESS: 'warning', DONE: 'success', CANCELLED: '' }[s] as '' | 'info' | 'warning' | 'success' | undefined
}

// ============================================================
// 历史时间轴 (action → 颜色 / 图标)  —— step B 追加
// ============================================================
function histActionLabel(a: string) {
  return {
    CREATED:        '🆕 风险登记',
    STATUS_CHANGED: '🔄 状态变更',
    SCORE_CHANGED:  '📊 分数变化',
    OWNER_CHANGED:  '👤 责任人变更',
    LEVEL_CHANGED:  '📈 等级变化',
    COMMENTED:      '💬 评论',
    RESPONSE_ADDED: '➕ 新增应对行动',
    RESPONSE_DONE:  '✅ 应对行动完成 / 删除',
    DELETED:        '🗑️ 风险已删除',
  }[a] ?? a
}
function histActionColor(a: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (a === 'CREATED')        return 'success'
  if (a === 'DELETED')        return 'danger'
  if (a === 'SCORE_CHANGED' || a === 'LEVEL_CHANGED') return 'warning'
  if (a === 'STATUS_CHANGED' || a === 'OWNER_CHANGED') return 'primary'
  if (a === 'RESPONSE_DONE')  return 'success'
  return 'info'
}
function histFieldValue(h: RiskHistoryItem): string {
  if (h.fieldName && h.oldValue != null && h.newValue != null) {
    return `${h.oldValue} → ${h.newValue}`
  }
  if (h.newValue) return h.newValue
  if (h.oldValue) return h.oldValue
  return ''
}

// ============================================================
// 删除风险
// ============================================================
async function onDelete() {
  if (!props.risk) return
  try {
    await ElMessageBox.confirm(
      `确定删除风险 ${props.risk.code} ${props.risk.title}? 该操作会写历史, 可追溯.`,
      '删除风险', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await store.remove(props.risk.id, props.risk.projectId)
    ElMessage.success('已删除')
    visible.value = false
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error('删除失败: ' + e.message)
  }
}

function onEdit() {
  if (props.risk) emit('edit', props.risk)
}
</script>

<template>
  <el-drawer
    v-model="visible"
    direction="rtl"
    size="640px"
    :with-header="false"
    :destroy-on-close="false"
  >
    <div v-if="!risk" class="rd-empty">
      <el-empty description="未选择风险" />
    </div>

    <div v-else v-loading="store.isLoading(`resp:${risk.id}`)" class="rd-drawer">
      <!-- ============================================================ -->
      <!-- 1. 头部 -->
      <!-- ============================================================ -->
      <header class="rd-header" :style="{ borderLeftColor: scoreColor(risk.score) }">
        <div class="rd-title-row">
          <el-tag :type="levelTagType(risk.level)" effect="dark" size="default">
            {{ risk.level }}
          </el-tag>
          <h2 class="rd-title">{{ risk.title }}</h2>
          <el-tag :type="statusTagType(risk.status)" size="default" effect="plain">
            {{ statusLabel(risk.status) }}
          </el-tag>
        </div>
        <div class="rd-subtitle">
          <el-icon><Warning /></el-icon>
          <span>{{ risk.code }}</span>
          <span style="color: #c0c4cc">·</span>
          <span>项目 #{{ risk.projectId }}</span>
          <span v-if="risk.actualCloseDate" style="color: #c0c4cc">·</span>
          <span v-if="risk.actualCloseDate">已于 {{ risk.actualCloseDate }} 关闭</span>
        </div>
      </header>

      <!-- ============================================================ -->
      <!-- 2. 关键指标 (P×I 大色块) -->
      <!-- ============================================================ -->
      <div class="rd-section">
        <h3 class="rd-section-title">关键指标</h3>
        <div class="rd-score-block" :style="{ background: scoreColor(risk.score) }">
          <div class="rd-score-num">{{ risk.score }}</div>
          <div class="rd-score-label">P × I</div>
        </div>
        <el-row :gutter="12" class="rd-kpi-row">
          <el-col :span="6">
            <div class="rd-kpi">
              <div class="rd-kpi-label">概率</div>
              <div class="rd-kpi-value">{{ risk.probability }} / 5</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="rd-kpi">
              <div class="rd-kpi-label">影响</div>
              <div class="rd-kpi-value">{{ risk.impact }} / 5</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="rd-kpi">
              <div class="rd-kpi-label">等级</div>
              <div class="rd-kpi-value">
                <el-tag :type="levelTagType(risk.level)" effect="dark" size="small">{{ risk.level }}</el-tag>
              </div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="rd-kpi">
              <div class="rd-kpi-label">应对策略</div>
              <div class="rd-kpi-value" style="font-size: 12px">{{ strategyLabel(risk.responseStrategy) }}</div>
            </div>
          </el-col>
        </el-row>
      </div>

      <!-- ============================================================ -->
      <!-- 3. 风险元数据 -->
      <!-- ============================================================ -->
      <div class="rd-section">
        <h3 class="rd-section-title">风险元数据</h3>
        <el-row :gutter="16">
          <el-col :span="12">
            <div class="rd-field">
              <el-icon><Warning /></el-icon>
              <span class="rd-field-label">分类</span>
              <span class="rd-field-value">{{ categoryLabel(risk.category ?? '') }}</span>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="rd-field">
              <el-icon><User /></el-icon>
              <span class="rd-field-label">责任人</span>
              <span class="rd-field-value">
                <template v-if="risk.ownerName">{{ risk.ownerName }}</template>
                <span v-else style="color: #c0c4cc">未指定</span>
              </span>
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="16" style="margin-top: 8px">
          <el-col :span="8">
            <div class="rd-field">
              <el-icon><Calendar /></el-icon>
              <span class="rd-field-label">识别</span>
              <span class="rd-field-value">{{ risk.identifiedDate || '—' }}</span>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="rd-field">
              <el-icon><Calendar /></el-icon>
              <span class="rd-field-label">目标关闭</span>
              <span class="rd-field-value">{{ risk.targetCloseDate || '—' }}</span>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="rd-field">
              <el-icon><Calendar /></el-icon>
              <span class="rd-field-label">实际关闭</span>
              <span class="rd-field-value">{{ risk.actualCloseDate || '—' }}</span>
            </div>
          </el-col>
        </el-row>
        <div v-if="risk.relatedWbsTaskName || risk.relatedMilestoneName" class="rd-relations">
          <span v-if="risk.relatedWbsTaskName" class="rd-rel-tag">
            <el-icon><Document /></el-icon> 关联任务: {{ risk.relatedWbsTaskName }}
          </span>
          <span v-if="risk.relatedMilestoneName" class="rd-rel-tag">
            <el-icon><Document /></el-icon> 关联里程碑: {{ risk.relatedMilestoneName }}
          </span>
        </div>
        <div v-if="risk.description" class="rd-multiline">
          <div class="rd-field-label" style="margin-bottom: 4px">详细描述</div>
          {{ risk.description }}
        </div>
      </div>

      <!-- ============================================================ -->
      <!-- 4. 应对措施 -->
      <!-- ============================================================ -->
      <div class="rd-section">
        <h3 class="rd-section-title">应对措施</h3>
        <div class="rd-mit-block">
          <div class="rd-mit-label">🛡️ 预防/缓解 (mitigation)</div>
          <div class="rd-multiline">
            {{ risk.mitigation || '— 尚未填写 —' }}
          </div>
        </div>
        <div class="rd-mit-block" style="margin-top: 12px">
          <div class="rd-mit-label">🚨 应急/兜底 (contingency)</div>
          <div class="rd-multiline">
            {{ risk.contingency || '— 尚未填写 —' }}
          </div>
        </div>
      </div>
      <!-- ============================================================ -->
      <!-- 5. 应对行动 (内嵌 CRUD) -->
      <!-- ============================================================ -->
      <div class="rd-section">
        <div class="rd-section-title-row">
          <h3 class="rd-section-title" style="margin: 0">应对行动 ({{ responses.length }})</h3>
          <el-button
            v-if="respEditingId !== 'new' && risk"
            size="small"
            type="primary"
            plain
            @click="respStartNew"
          >
            <el-icon><Plus /></el-icon> 新增
          </el-button>
        </div>

        <!-- 内嵌编辑面板 -->
        <div v-if="respEditingId !== null" class="rd-resp-edit">
          <el-form label-width="80px" size="small">
            <el-form-item label="动作" required>
              <el-input v-model="respDraft.action" placeholder="例如 联系厂商升级 / 准备备用方案" maxlength="256" show-word-limit />
            </el-form-item>
            <el-row :gutter="12">
              <el-col :span="8">
                <el-form-item label="状态">
                  <el-select v-model="respDraft.status" style="width: 100%">
                    <el-option label="已计划" value="PLANNED" />
                    <el-option label="执行中" value="IN_PROGRESS" />
                    <el-option label="已完成" value="DONE" />
                    <el-option label="已取消" value="CANCELLED" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="截止日">
                  <el-date-picker v-model="respDraft.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="责任人">
                  <el-input-number v-model="respDraft.ownerUserId" :min="1" placeholder="user_id" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="备注">
              <el-input v-model="respDraft.note" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="respSaving" @click="respSave">保存</el-button>
              <el-button @click="respCancel">取消</el-button>
            </el-form-item>
          </el-form>
        </div>

        <!-- 行动列表 (行内编辑 / 列表 切换) -->
        <div v-if="responses.length === 0 && respEditingId === null" class="rd-resp-empty">
          <el-empty :image-size="60" description="尚未制定应对行动" />
        </div>
        <table v-else-if="respEditingId === null" class="rd-resp-table">
          <thead>
            <tr>
              <th style="width: 50%">动作</th>
              <th>状态</th>
              <th>截止日</th>
              <th>完成时间</th>
              <th style="width: 100px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in responses" :key="r.id">
              <td>
                <div class="rd-resp-action">{{ r.action }}</div>
                <div v-if="r.note" class="rd-resp-note">{{ r.note }}</div>
              </td>
              <td>
                <el-tag :type="respStatusType(r.status)" size="small" effect="plain">
                  {{ respStatusLabel(r.status) }}
                </el-tag>
              </td>
              <td><span style="font-size: 12px; color: #606266">{{ r.dueDate || '—' }}</span></td>
              <td>
                <span v-if="r.completedAt" style="font-size: 12px; color: #67c23a">
                  {{ new Date(r.completedAt).toLocaleString('zh-CN') }}
                </span>
                <span v-else style="color: #c0c4cc">—</span>
              </td>
              <td>
                <el-button size="small" link type="primary" @click="respStartEdit(r.id)">编辑</el-button>
                <el-button size="small" link type="danger" @click="respDelete(r.id)">删除</el-button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- ============================================================ -->
      <!-- 6. 历史时间轴 -->
      <!-- ============================================================ -->
      <div class="rd-section">
        <h3 class="rd-section-title">变更历史 ({{ history.length }})</h3>
        <div v-if="history.length === 0" class="rd-resp-empty">
          <el-empty :image-size="60" description="暂无变更历史" />
        </div>
        <el-timeline v-else>
          <el-timeline-item
            v-for="h in history"
            :key="h.id"
            :type="histActionColor(h.action)"
            :timestamp="new Date(h.createdAt).toLocaleString('zh-CN')"
            placement="top"
          >
            <div class="rd-hist-line1">
              <span class="rd-hist-action">{{ histActionLabel(h.action) }}</span>
              <span v-if="h.operatorName" class="rd-hist-operator">— {{ h.operatorName }}</span>
            </div>
            <div v-if="histFieldValue(h)" class="rd-hist-line2">
              <span v-if="h.fieldName" class="rd-hist-field">{{ h.fieldName }}: </span>
              <span class="rd-hist-value">{{ histFieldValue(h) }}</span>
            </div>
            <div v-if="h.comment" class="rd-hist-comment">💬 {{ h.comment }}</div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </div>

    <!-- ============================================================ -->
    <!-- 底部操作栏 (sticky) -->
    <!-- ============================================================ -->
    <template v-if="risk" #footer>
      <div class="rd-footer">
        <div class="rd-footer-meta">
          <span style="color: #909399; font-size: 12px">
            创建 {{ risk.createdAt ? new Date(risk.createdAt).toLocaleString('zh-CN') : '—' }}
            <span v-if="risk.updatedAt && risk.updatedAt !== risk.createdAt">
              · 更新 {{ new Date(risk.updatedAt).toLocaleString('zh-CN') }}
            </span>
          </span>
        </div>
        <div class="rd-footer-actions">
          <el-button :icon="Refresh" @click="load">刷新</el-button>
          <el-button :icon="Delete" type="danger" plain @click="onDelete">删除风险</el-button>
          <el-button :icon="Edit" type="primary" @click="onEdit">编辑</el-button>
        </div>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.rd-drawer { padding: 0; }
.rd-empty { padding-top: 80px; }

/* 头部 */
.rd-header {
  padding: 16px 20px 14px;
  border-bottom: 1px solid #ebeef5;
  border-left: 4px solid #909399;
  background: #fafbfc;
}
.rd-title-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.rd-title { margin: 0; font-size: 18px; font-weight: 600; flex: 1; min-width: 0; }
.rd-subtitle {
  display: flex; align-items: center; gap: 6px;
  font-size: 12px; color: #909399; margin-top: 6px;
  flex-wrap: wrap;
}

/* 区块 */
.rd-section {
  padding: 14px 20px;
  border-bottom: 1px solid #f5f5f5;
}
.rd-section-title {
  font-size: 13px; font-weight: 600; color: #606266;
  margin: 0 0 12px 0; letter-spacing: 0.5px;
}

/* 关键指标 */
.rd-score-block {
  border-radius: 6px;
  padding: 14px 20px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 12px;
}
.rd-score-num { font-size: 36px; font-weight: 700; line-height: 1; }
.rd-score-label { font-size: 14px; opacity: 0.9; }
.rd-kpi-row { margin-top: 0; }
.rd-kpi {
  background: #fafafa;
  border-radius: 4px;
  padding: 8px 10px;
}
.rd-kpi-label { font-size: 12px; color: #909399; margin-bottom: 2px; }
.rd-kpi-value { font-size: 14px; font-weight: 600; color: #303133; }

/* 字段 */
.rd-field {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; line-height: 1.8;
}
.rd-field-label { color: #909399; min-width: 56px; }
.rd-field-value { color: #303133; flex: 1; }

/* 关联 */
.rd-relations {
  margin-top: 10px;
  display: flex; gap: 8px; flex-wrap: wrap;
}
.rd-rel-tag {
  background: #ecf5ff;
  color: #409eff;
  padding: 2px 8px;
  border-radius: 3px;
  font-size: 12px;
  display: inline-flex; align-items: center; gap: 4px;
}

/* 多行文本 */
.rd-multiline {
  background: #fafafa;
  padding: 8px 10px;
  border-radius: 4px;
  color: #303133;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
  margin-top: 4px;
}

/* 应对措施 */
.rd-mit-block { }
.rd-mit-label { font-size: 12px; color: #606266; margin-bottom: 4px; font-weight: 500; }

/* section 5: 应对行动 */
.rd-section-title-row {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 12px;
}
.rd-resp-edit {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
  margin-bottom: 12px;
}
.rd-resp-edit :deep(.el-form-item) { margin-bottom: 12px; }
.rd-resp-edit :deep(.el-form-item:last-child) { margin-bottom: 0; }
.rd-resp-empty { padding: 8px 0; }
.rd-resp-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.rd-resp-table th {
  background: #fafafa;
  text-align: left;
  padding: 8px 10px;
  font-weight: 600;
  color: #606266;
  border-bottom: 1px solid #ebeef5;
  font-size: 12px;
}
.rd-resp-table td {
  padding: 8px 10px;
  border-bottom: 1px solid #f5f5f5;
  vertical-align: top;
}
.rd-resp-table tr:last-child td { border-bottom: none; }
.rd-resp-action { color: #303133; }
.rd-resp-note {
  font-size: 12px; color: #909399; margin-top: 2px;
  white-space: pre-wrap; word-break: break-word;
}

/* section 6: 历史时间轴 */
.rd-hist-line1 {
  font-size: 13px; font-weight: 600; color: #303133;
  margin-bottom: 2px;
}
.rd-hist-action { margin-right: 4px; }
.rd-hist-operator { font-weight: 400; color: #909399; font-size: 12px; }
.rd-hist-line2 {
  font-size: 12px; color: #606266;
  background: #fafafa;
  padding: 4px 8px;
  border-radius: 3px;
  margin-top: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.rd-hist-field { color: #909399; }
.rd-hist-value { color: #303133; }
.rd-hist-comment {
  font-size: 12px; color: #909399;
  margin-top: 4px; font-style: italic;
}

/* 底部操作栏 */
.rd-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
.rd-footer-meta { flex: 1; min-width: 0; }
.rd-footer-actions {
  display: flex; gap: 8px; flex-wrap: wrap;
}
</style>
