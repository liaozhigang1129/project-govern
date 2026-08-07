<template>
  <!-- Step 2 — SOW 文件上传 + AI WBS 智能生成 -->
  <div>
    <el-alert
      type="info"
      :closable="false"
      title="SOW (Statement of Work) 是客户的工作说明书,AI 将基于其内容自动生成 WBS + 里程碑 + 风险。"
      style="margin-bottom: 16px"
    />

    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span style="font-weight: 600">📄 SOW 文件</span>
          <el-tag v-if="sowFiles.length" type="success" effect="plain">
            已上传 {{ sowFiles.length }} 个
          </el-tag>
        </div>
      </template>

      <el-upload
        :action="uploadUrl"
        :headers="uploadHeaders"
        :before-upload="beforeUpload"
        :on-success="onUploadSuccess"
        :on-error="onUploadError"
        :show-file-list="false"
        accept=".pdf,.doc,.docx,.xls,.xlsx,.pptx,.md,.txt"
        :disabled="uploading"
      >
        <el-button type="primary" :loading="uploading" :icon="Upload">选择 SOW 文件</el-button>
        <template #tip>
          <div style="color: #909399; font-size: 12px; margin-top: 8px">
            支持 PDF / Word / Excel / PPT / Markdown / TXT,单个文件不超过 50MB
          </div>
        </template>
      </el-upload>

      <el-table v-if="sowFiles.length" :data="sowFiles" size="small" style="margin-top: 16px">
        <el-table-column prop="fileName" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="上传时间" width="170">
          <template #default="{ row }">{{ fmt(row.uploadedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="downloadSow(row)">下载</el-button>
            <el-button size="small" link type="danger" @click="deleteSow(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span style="font-weight: 600">🤖 AI 智能 WBS 转化</span>
          <el-button
            type="primary" :loading="generating" :disabled="!sowFiles.length"
            :icon="MagicStick" @click="triggerGenerate"
          >
            {{ aiDraft ? '重新生成' : '生成 WBS 草稿' }}
          </el-button>
        </div>
      </template>

      <!-- V4.23: SOW 抽取诊断条 — 让用户立刻看到"上传的 PDF/Word 是否被 AI 真的读到了" -->
      <template v-if="aiDraft?.sourceMeta">
        <el-alert
          v-if="aiDraft.sourceMeta.failedFiles > 0"
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
        >
          <template #title>
            {{ aiDraft.sourceMeta.extractedFiles }} / {{ aiDraft.sourceMeta.usedFiles }} 个 SOW 文件成功抽取文本,{{ aiDraft.sourceMeta.failedFiles }} 个失败
          </template>
          <div style="font-size: 12px; margin-top: 4px; color: #606266">
            失败文件可能是扫描件 / 加密 PDF / 损坏文件,AI 没法读到内容。建议:
            <ol style="margin: 4px 0 0 20px; padding: 0">
              <li>把失败文件转成 .docx / .md / .txt 再上传,或</li>
              <li>把关键内容复制粘贴到 SOW 粘贴文本框(见 Step 1)</li>
            </ol>
          </div>
        </el-alert>
        <el-alert
          v-else-if="aiDraft.sourceMeta.usedFiles > 0"
          type="success"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
        >
          <template #title>
            {{ aiDraft.sourceMeta.extractedFiles }} / {{ aiDraft.sourceMeta.usedFiles }} 个 SOW 文件已抽取,AI 基于这些内容生成
          </template>
        </el-alert>
      </template>

      <el-empty v-if="!aiDraft" description="先上传 SOW 文件,然后点击「生成 WBS 草稿」" :image-size="80" />

      <template v-else>
<!-- V4.23: 抽到的内容统计 -->
      <el-row v-if="aiDraft?.sourceMeta" :gutter="16" style="margin-bottom: 12px">
        <el-col :span="6">
          <el-statistic title="里程碑" :value="aiDraft.milestones.length" />
        </el-col>
        <el-col :span="6">
          <el-statistic
            title="工作包"
            :value="aiDraft.milestones.reduce((a, m) => a + m.workPackages.length, 0)"
          />
        </el-col>
        <el-col :span="6">
          <el-statistic
            title="风险项"
            :value="aiDraft.risks.length"
            :value-style="{ color: '#F56C6C' }"
          />
        </el-col>
        <el-col :span="6">
          <el-statistic
            title="高/极高风险"
            :value="aiDraft.risks.filter(r => r.level === 'HIGH' || r.level === 'CRITICAL').length"
            :value-style="{ color: '#E6A23C' }"
          />
        </el-col>
      </el-row>

      <!-- V4.23: SOW 文件抽取明细表 — 让用户精确知道"哪份 PDF 没抽到 / 为什么" -->
      <el-collapse v-if="aiDraft?.sourceMeta?.fileExtractions?.length" style="margin-bottom: 12px">
        <el-collapse-item title="🔍 SOW 文件抽取明细 (V4.23)" name="extract">
          <el-table :data="aiDraft.sourceMeta.fileExtractions" size="small" border>
            <el-table-column prop="fileName" label="文件名" min-width="220" show-overflow-tooltip />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.extracted" type="success" size="small">✓ 已抽取</el-tag>
                <el-tag v-else type="danger" size="small">✗ 未抽取</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="抽取字符" width="100" align="right">
              <template #default="{ row }">{{ row.chars.toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="失败原因" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.extracted" style="color: #67c23a">文本已并入 AI 输入</span>
                <span v-else style="color: #f56c6c">{{ explainReason(row.reason) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-collapse-item>
      </el-collapse>

        <el-tabs v-model="activeTab">
          <el-tab-pane label="里程碑 + 工作包" name="ms">
            <div v-for="m in aiDraft.milestones" :key="m.sequence" class="milestone-card">
              <div class="milestone-header">
                <el-tag type="primary" effect="dark">M{{ m.sequence }}</el-tag>
                <span class="milestone-name">{{ m.name }}</span>
                <el-tag v-if="m.targetWeek" size="small" type="info">
                  目标 W{{ m.targetWeek }}
                </el-tag>
              </div>
              <el-table :data="m.workPackages" size="small" :show-header="false" style="margin-top: 8px">
                <el-table-column label="工作包" prop="name" min-width="200" />
                <el-table-column label="工期" width="100">
                  <template #default="{ row }">{{ row.durationWeeks }} 周</template>
                </el-table-column>
                <el-table-column label="负责角色" width="120">
                  <template #default="{ row }">
                    <el-tag v-if="row.ownerRoleCode" size="small">{{ row.ownerRoleCode }}</el-tag>
                    <span v-else style="color: #909399">—</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <el-tab-pane label="交付风险" name="rk">
            <el-alert
              type="warning" :closable="false" show-icon
              title="如风险与 SOW 无实质关联,请点击「删除」剔除,避免纳入项目交付风险清单。"
              style="margin-bottom: 12px"
            />
            <el-table :data="aiDraft.risks" size="small" border>
              <el-table-column label="等级" width="100">
                <template #default="{ row }">
                  <el-tag :type="riskLevelType(row.level)" size="small">{{ row.level }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="title" label="风险" min-width="200" />
              <el-table-column prop="impact" label="影响" min-width="120" show-overflow-tooltip />
              <el-table-column label="SOW 证据" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">
                  <span v-if="getEvidence(row).length" style="color: #67c23a">
                    <el-icon style="vertical-align: middle"><CircleCheckFilled /></el-icon>
                    {{ getEvidence(row).join(' / ') }}
                  </span>
                  <span v-else style="color: #f56c6c">
                    <el-icon style="vertical-align: middle"><WarningFilled /></el-icon>
                    无 SOW 依据
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="suggestion" label="建议" min-width="220" show-overflow-tooltip />
              <el-table-column label="操作" width="180" align="center" fixed="right">
                <template #default="{ row, $index }">
                  <el-button
                    size="small" link type="primary"
                    :icon="View" @click="showEvidenceDialog(row)"
                  >查看证据</el-button>
                  <el-popconfirm
                    title="确认从风险清单中删除此条?"
                    confirm-button-text="删除"
                    cancel-button-text="取消"
                    @confirm="removeRisk($index)"
                  >
                    <template #reference>
                      <el-button size="small" link type="danger" :icon="Delete">删除</el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-card>

    <!-- SOW 证据查看弹窗 — V4.25 让用户在删除风险前能看到该风险的 SOW 原文依据 -->
    <el-dialog
      v-model="evidenceDialogVisible"
      :title="`风险 SOW 证据 — ${evidenceDialogData?.title ?? ''}`"
      width="640px"
    >
      <div v-if="evidenceDialogData">
        <el-descriptions :column="1" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="等级">
            <el-tag :type="riskLevelType(evidenceDialogData.level)" size="small">
              {{ evidenceDialogData.level }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="风险">{{ evidenceDialogData.title }}</el-descriptions-item>
          <el-descriptions-item label="建议">{{ evidenceDialogData.suggestion || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Evidence 关键词">
            <template v-if="evidenceDialogData.evidence.length">
              <el-tag
                v-for="(k, i) in evidenceDialogData.evidence" :key="i"
                size="small" type="success" effect="plain" style="margin-right: 6px"
              >{{ k }}</el-tag>
            </template>
            <span v-else style="color: #f56c6c">无 evidence — 该风险在 SOW 中没有可定位的依据</span>
          </el-descriptions-item>
        </el-descriptions>

        <div style="font-weight: 600; margin-bottom: 8px">SOW 原文命中片段</div>
        <template v-if="evidenceDialogData.snippets.length">
          <el-card
            v-for="(s, i) in evidenceDialogData.snippets" :key="i"
            shadow="never" style="margin-bottom: 8px"
          >
            <div style="font-size: 12px; color: #909399; margin-bottom: 4px">
              关键词: <el-tag size="small" type="success">{{ s.kw }}</el-tag>
            </div>
            <div style="white-space: pre-wrap; color: #303133; line-height: 1.6">{{ s.context }}</div>
          </el-card>
        </template>
        <el-empty
          v-else description="后端未下发 SOW 原文摘要,evidence 关键词是从风险桶触发规则而来,无法定位原文位置"
          :image-size="80"
        />
      </div>
      <template #footer>
        <el-button @click="evidenceDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Upload, MagicStick, View, Delete,
  CircleCheckFilled, WarningFilled,
} from '@element-plus/icons-vue'
import api, { type SowFile, type AiWbsDraft, type AiWbsRisk } from '@/api/client'

const props = defineProps<{
  initiationId: number
  sowFiles: SowFile[]
}>()

const emit = defineEmits<{
  'update:sowFiles': [files: SowFile[]]
  'update:aiDraft': [draft: AiWbsDraft | null]
}>()

// el-upload 走原生 XHR,不走 axios,所以必须显式拼 /api 前缀。
// 开发环境 Vite devServer 已把 /api 代理到后端 8088 (见 vite.config.ts),所以直接用相对路径即可。
const uploadUrl = `/api/initiations/${props.initiationId}/sow`
const uploadHeaders = { Authorization: `Bearer ${localStorage.getItem('token') ?? ''}` }
const uploading = ref(false)
const generating = ref(false)
const activeTab = ref('ms')
const aiDraft = ref<AiWbsDraft | null>(null)
emit('update:aiDraft', aiDraft.value)

function beforeUpload(file: File) {
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.error('文件不能超过 50MB')
    return false
  }
  uploading.value = true
  return true
}

async function onUploadSuccess(res: any) {
  uploading.value = false
  if (res && res.code === 0) {
    ElMessage.success('上传成功')
    await loadSowFiles()
  } else {
    ElMessage.error(res?.message ?? '上传失败')
  }
}
function onUploadError() {
  uploading.value = false
  ElMessage.error('上传失败')
}

async function loadSowFiles() {
  try {
    const list = await api.get<SowFile[]>(`/initiations/${props.initiationId}/sow`)
    emit('update:sowFiles', list)
  } catch (e: any) {
    ElMessage.error('加载 SOW 列表失败: ' + e.message)
  }
}

function downloadSow(row: SowFile) {
  const a = document.createElement('a')
  a.href = `/initiations/${props.initiationId}/sow/${row.id}/download`
  a.download = row.fileName
  a.target = '_blank'
  document.body.appendChild(a)
  a.click()
  a.remove()
}

async function deleteSow(row: SowFile) {
  await ElMessageBox.confirm(`确认删除「${row.fileName}」?`, '提示', { type: 'warning' }).catch(() => null)
  if (!row.id) return
  try {
    await api.delete(`/initiations/${props.initiationId}/sow/${row.id}`)
    ElMessage.success('已删除')
    await loadSowFiles()
  } catch (e: any) {
    ElMessage.error('删除失败: ' + e.message)
  }
}

async function triggerGenerate() {
  generating.value = true
  try {
    // V4.x: 后端返回 {code, message, data:{draftId, draft:{...}}, ...}
    // 顶层 axios 拦截器只剥掉外层 {code,...}, 这里 data.data?.draft 才是真正的 AiWbsDraft
    const resp: any = await api.post(`/initiations/${props.initiationId}/ai-wbs/generate`, {
      granularityWeeks: 2,
    })
    const payload = resp?.data ?? resp
    const draft = payload?.draft ?? payload
    const draftId = payload?.draftId
    if (!draft || !Array.isArray(draft.milestones)) {
      ElMessage.error(resp?.message ?? 'AI 生成返回结构异常,请查看控制台')
      console.error('[AI WBS] 异常返回结构:', resp)
      return
    }
    // 把 draftId 挂回 draft 方便 Step 3 apply
    if (draftId != null && draft.draftId == null) {
      draft.draftId = draftId
    }
    aiDraft.value = draft as AiWbsDraft
    emit('update:aiDraft', draft as AiWbsDraft)
    // V4.23: 根据抽取结果给用户最直接的反馈
    const meta = draft.sourceMeta
    if (meta) {
      if (meta.failedFiles > 0) {
        ElMessage.warning(
          `AI 已生成,但 ${meta.failedFiles} / ${meta.usedFiles} 个 SOW 文件抽取失败 ��� 点下方"SOW 文件抽取明细"看详情`
        )
      } else if (meta.usedFiles > 0 && meta.extractedFiles === meta.usedFiles) {
        ElMessage.success(`已生成 WBS 草稿 (基于 ${meta.extractedFiles} 个 SOW 文件)`)
      } else {
        ElMessage.success('已生成 WBS 草稿,请在 Step 3 调整')
      }
    } else {
      ElMessage.success('已生成 WBS 草稿,请在 Step 3 调整')
    }
  } catch (e: any) {
    ElMessage.error('生成失败: ' + (e?.message ?? e))
  } finally {
    generating.value = false
  }
}

/**
 * V4.23: 把后端的英文 reason 转成用户能看懂的中文提示
 */
function explainReason(reason?: string): string {
  switch (reason) {
    case 'extractor_returned_empty':
      return '文件可能是扫描件 / 加密 PDF / 不支持的内容格式 — 建议转 .docx / .md 再上传,或把内容粘贴到 SOW 文本框'
    case 'file_missing_on_disk':
      return '服务器找不到磁盘文件 — 请重新上传'
    default:
      if (reason?.startsWith('exception:')) {
        return `解析异常 (${reason.slice(10)}) — 请重新上传或换格式`
      }
      return reason ?? '未知原因'
  }
}

function formatSize(b: number) {
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  return (b / 1024 / 1024).toFixed(2) + ' MB'
}
function fmt(dt?: string) {
  return dt ? dt.replace('T', ' ').slice(0, 19) : '—'
}
function riskLevelType(l: string) {
  if (l === 'CRITICAL') return 'danger'
  if (l === 'HIGH') return 'warning'
  if (l === 'MEDIUM') return ''
  return 'info'
}

/**
 * 取该风险在 SOW 中命中的 evidence 关键词 — 后端 AiWbsRisk.evidence 是触发关键词数组,
 * 也就是 SOW 原文里能找到的子串。这里直接展示给用户,作为"该风险有 SOW 依据"的证据。
 */
function getEvidence(row: any): string[] {
  const ev = row?.evidence
  return Array.isArray(ev) ? ev.filter(Boolean) : []
}

/**
 * 用户在 Step 2 删除 AI 草稿风险:仅修改前端 aiDraft 状态,等待 Step 3 调整后再落库
 * (apply 时后端会读 draft,删除的不会进入 WBS apply)
 */
function removeRisk(index: number) {
  if (!aiDraft.value?.risks) return
  const removed = aiDraft.value.risks[index]
  aiDraft.value.risks.splice(index, 1)
  // 通知父组件 (InitiationWizard) 同步更新 form.aiDraft
  emit('update:aiDraft', aiDraft.value)
  ElMessage.success(`已剔除风险: ${removed?.title ?? ''}`)
}

// "查看证据"弹窗 — 显示该风险对应的 evidence 关键词 + 在 SOW 原文里的命中片段
const evidenceDialogVisible = ref(false)
const evidenceDialogData = ref<{
  title: string
  level: string
  suggestion: string
  evidence: string[]
  snippets: Array<{ kw: string; context: string }>
} | null>(null)

function showEvidenceDialog(row: any) {
  const ev = getEvidence(row)
  // 从后端 sourceMeta 拿到 SOW 原文片段
  const snippets: Array<{ kw: string; context: string }> = []
  const sowText = (aiDraft.value as any)?.sourceMeta?.sowText
    ?? (aiDraft.value as any)?.sowText
    ?? ''
  if (sowText && ev.length) {
    for (const kw of ev) {
      if (!kw) continue
      const idx = sowText.indexOf(kw)
      if (idx >= 0) {
        const start = Math.max(0, idx - 40)
        const end = Math.min(sowText.length, idx + kw.length + 40)
        snippets.push({ kw, context: '…' + sowText.slice(start, end) + '…' })
      }
    }
  }
  evidenceDialogData.value = {
    title: row?.title ?? '',
    level: row?.level ?? '',
    suggestion: row?.suggestion ?? '',
    evidence: ev,
    snippets,
  }
  evidenceDialogVisible.value = true
}

/**
 * V4.x 修复: 打开补料 wizard 时, 拉一下最近一份 AI 草稿, 让 milestone/risks/sourceMeta 立刻渲染
 * 后端 /ai-wbs/latest 返 {draftId, draft:{milestones,risks,...}, ...}, 同样要拆到 draft
 */
async function loadAiDraft() {
  if (!props.initiationId) return
  try {
    const resp: any = await api.get(`/initiations/${props.initiationId}/ai-wbs/latest`)
    const payload = resp?.data ?? resp
    const draft = payload?.draft
    if (!draft || !Array.isArray(draft.milestones)) return
    if (payload?.draftId != null && draft.draftId == null) {
      draft.draftId = payload.draftId
    }
    aiDraft.value = draft as AiWbsDraft
    emit('update:aiDraft', draft as AiWbsDraft)
  } catch (e) {
    // 没有草稿或 404 都忽略, 安静退出
  }
}

defineExpose({ loadSowFiles, loadAiDraft, aiDraft })
</script>

<style scoped>
.milestone-card {
  background: #fafbfc;
  border-left: 3px solid #4facfe;
  border-radius: 4px;
  padding: 12px 16px;
  margin-bottom: 12px;
}
.milestone-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.milestone-name {
  font-weight: 600;
  color: #303133;
  flex: 1;
}
</style>
