<script setup lang="ts">
/**
 * RiskFormDialog — 风险新建/编辑 弹窗 (P4)
 *
 * 设计要点:
 *  - 单组件, 同时处理新建和编辑 (props.risk 决定 isEdit)
 *  - 风险编号 code: 项目内唯一, 新建时给"建议值" (R-001, R-002...), 用户可改
 *  - 概率 / 影响用 slider 1-5, 实时显示 score (P×I) + level (LOW/MEDIUM/HIGH/CRITICAL)
 *    客户端不算 score / level 上送, 后端做唯一真源
 *  - 责任人下拉: 复用 userApi.list({enabled:true}), 跟 MilestoneDrawer 一致
 *  - 关联 WBS 任务 / 里程碑: 用 el-select + filterable, 拉项目下任务 / 里程碑列表
 *    (目前简化: 不做关联, 留 TODO, V1.0 先把主表跑通)
 *  - 状态: 新建默认 OPEN; 编辑保留原状态
 *
 * 交互:
 *  - v-model 双向绑定
 *  - 保存成功 emit 'saved' (含保存后的 risk), 父组件会刷新列表
 *  - 取消 / 关闭 emit 'update:modelValue' false
 */
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRiskStore } from '@/stores/risk'
import { userApi } from '@/api/gantt'
import type { AppUser } from '@/api/client'
import type { RiskItem, RiskLevel } from '@/api/risk'

const props = defineProps<{
  modelValue: boolean
  projectId: number
  /** 编辑时传完整 risk; 新建传 null */
  risk: RiskItem | null
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'saved', risk: RiskItem): void
}>()

const store = useRiskStore()
const formRef = ref()
const saving = ref(false)
const users = ref<AppUser[]>([])

const isEdit = computed(() => !!props.risk?.id)

// ============================================================
// form state (跟 RiskRequest 对齐, 17 字段, 13 显示在 UI)
// ============================================================
const form = ref({
  id: undefined as number | undefined,
  projectId: props.projectId,
  code: '',
  title: '',
  description: '' as string,
  category: 'TECHNICAL' as
    'TECHNICAL' | 'SCHEDULE' | 'COST' | 'QUALITY' | 'EXTERNAL' | 'ORGANIZATIONAL' | 'OTHER',
  probability: 3 as number, // 默认中
  impact: 3 as number, // 默认中
  status: 'OPEN' as 'OPEN' | 'MITIGATING' | 'CLOSED' | 'OCCURRED' | 'ACCEPTED',
  ownerUserId: null as number | null,
  mitigation: '' as string,
  contingency: '' as string,
  responseStrategy: null as
    null | 'AVOID' | 'MITIGATE' | 'TRANSFER' | 'ACCEPT' | 'EXPLOIT' | 'ENHANCE' | 'SHARE',
  identifiedDate: null as string | null,
  targetCloseDate: null as string | null,
  relatedWbsTaskId: null as number | null,
  relatedMilestoneId: null as number | null,
})

// ============================================================
// 实时算 score + level (仅 UI 显示, 不上送)
// ============================================================
const liveScore = computed(() => form.value.probability * form.value.impact)
function levelOf(score: number): RiskLevel {
  if (score >= 16) return 'CRITICAL'
  if (score >= 10) return 'HIGH'
  if (score >= 5) return 'MEDIUM'
  return 'LOW'
}
const liveLevel = computed<RiskLevel>(() => levelOf(liveScore.value))

function levelTagType(l: RiskLevel) {
  return { CRITICAL: 'danger', HIGH: 'warning', MEDIUM: '', LOW: 'success' }[l] as
    '' | 'success' | 'warning' | 'danger'
}

// ============================================================
// 打开时回填 / 重置
// ============================================================
watch(
  () => [props.modelValue, props.risk],
  ([v, r]) => {
    if (!v) return
    if (r) {
      // 编辑
      const t = r as RiskItem
      form.value = {
        id: t.id,
        projectId: t.projectId,
        code: t.code,
        title: t.title,
        description: t.description ?? '',
        category: (t.category ?? 'OTHER') as
          'TECHNICAL' | 'SCHEDULE' | 'COST' | 'QUALITY' | 'EXTERNAL' | 'ORGANIZATIONAL' | 'OTHER',
        probability: t.probability,
        impact: t.impact,
        status: t.status,
        ownerUserId: t.ownerUserId,
        mitigation: t.mitigation ?? '',
        contingency: t.contingency ?? '',
        responseStrategy: (t.responseStrategy as any) ?? null,
        identifiedDate: t.identifiedDate,
        targetCloseDate: t.targetCloseDate,
        relatedWbsTaskId: t.relatedWbsTaskId,
        relatedMilestoneId: t.relatedMilestoneId,
      }
    } else {
      // 新建: 给一个建议编号 R-001, 后续用户可改
      const suggestCode = suggestNextCode()
      form.value = {
        id: undefined,
        projectId: props.projectId,
        code: suggestCode,
        title: '',
        description: '',
        category: 'TECHNICAL',
        probability: 3,
        impact: 3,
        status: 'OPEN',
        ownerUserId: null,
        mitigation: '',
        contingency: '',
        responseStrategy: null,
        identifiedDate: new Date().toISOString().slice(0, 10),
        targetCloseDate: null,
        relatedWbsTaskId: null,
        relatedMilestoneId: null,
      }
    }
  },
  { immediate: true },
)

/** 从 store 已有的 listByProject 找下一个 R-XXX */
function suggestNextCode(): string {
  const existing = store.listByProject.get(props.projectId) ?? []
  const max = existing
    .map((r) => parseInt(r.code.match(/R-(\d+)/)?.[1] ?? '0', 10))
    .reduce((a, b) => Math.max(a, b), 0)
  return `R-${String(max + 1).padStart(3, '0')}`
}

// ============================================================
// 校验
// ============================================================
const rules = {
  code: [
    { required: true, message: '请输入风险编号', trigger: 'blur' },
    { max: 32, message: '最多 32 字符', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9\-_]+$/, message: '只允许字母/数字/-/_', trigger: 'blur' },
  ],
  title: [
    { required: true, message: '请输入风险标题', trigger: 'blur' },
    { max: 256, message: '最多 256 字符', trigger: 'blur' },
  ],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  probability: [
    { required: true, type: 'number', min: 1, max: 5, message: '概率需在 1-5', trigger: 'change' },
  ],
  impact: [{ required: true, type: 'number', min: 1, max: 5, message: '影响需在 1-5', trigger: 'change' }],
}

// ============================================================
// 责任人下拉
// ============================================================
async function loadUsers() {
  if (users.value.length > 0) return
  try {
    users.value = await userApi.list({ enabled: true })
  } catch {
    /* 兜底: 责任人下拉为空也能保存 (owner 可选) */
    users.value = []
  }
}
onMounted(loadUsers)
watch(
  () => props.modelValue,
  (v) => {
    if (v) loadUsers()
  },
)

// ============================================================
// 提交
// ============================================================
async function onSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      id: form.value.id,
      projectId: form.value.projectId,
      code: form.value.code.trim(),
      title: form.value.title.trim(),
      description: form.value.description || null,
      category: form.value.category,
      probability: form.value.probability,
      impact: form.value.impact,
      status: form.value.status,
      ownerUserId: form.value.ownerUserId,
      mitigation: form.value.mitigation || null,
      contingency: form.value.contingency || null,
      responseStrategy: form.value.responseStrategy,
      identifiedDate: form.value.identifiedDate,
      targetCloseDate: form.value.targetCloseDate,
      relatedWbsTaskId: form.value.relatedWbsTaskId,
      relatedMilestoneId: form.value.relatedMilestoneId,
    }
    const saved = await store.save(payload as any)
    ElMessage.success(isEdit.value ? '已更新' : '已创建')
    emit('saved', saved)
    emit('update:modelValue', false)
  } catch (e: any) {
    ElMessage.error(`保存失败: ${e.message}`)
  } finally {
    saving.value = false
  }
}

function onCancel() {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? `编辑风险 #${risk?.id}  ${risk?.code}` : '新建风险'"
    width="720px"
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" label-position="right">
      <!-- 基础信息 -->
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="编号" prop="code">
            <el-input v-model="form.code" placeholder="R-001" maxlength="32" show-word-limit />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="分类" prop="category">
            <el-select v-model="form.category" style="width: 100%">
              <el-option label="技术" value="TECHNICAL" />
              <el-option label="进度" value="SCHEDULE" />
              <el-option label="成本" value="COST" />
              <el-option label="质量" value="QUALITY" />
              <el-option label="外部" value="EXTERNAL" />
              <el-option label="组织" value="ORGANIZATIONAL" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="状态">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="已识别" value="OPEN" />
              <el-option label="应对中" value="MITIGATING" />
              <el-option label="已发生" value="OCCURRED" />
              <el-option label="已接受" value="ACCEPTED" />
              <el-option label="已关闭" value="CLOSED" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="标题" prop="title">
        <el-input
          v-model="form.title"
          placeholder="简明描述,例如 「核心模块第三方接口不稳定」"
          maxlength="256"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="详细描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          placeholder="风险触发条件 / 影响范围 / 历史经验 (可选)"
        />
      </el-form-item>

      <!-- 概率 / 影响 / 分数 (滑杆 + 实时算分) -->
      <el-row :gutter="12">
        <el-col :span="10">
          <el-form-item label="概率 (P)">
            <el-slider
              v-model="form.probability"
              :min="1"
              :max="5"
              :marks="{ 1: '极低', 2: '低', 3: '中', 4: '高', 5: '极高' }"
              show-stops
            />
          </el-form-item>
        </el-col>
        <el-col :span="10">
          <el-form-item label="影响 (I)">
            <el-slider
              v-model="form.impact"
              :min="1"
              :max="5"
              :marks="{ 1: '轻微', 2: '较小', 3: '中等', 4: '较大', 5: '严重' }"
              show-stops
            />
          </el-form-item>
        </el-col>
        <el-col :span="4">
          <el-form-item label="分数">
            <div class="score-display">
              <div :class="['score-num', `lvl-${liveLevel}`]">{{ liveScore }}</div>
              <el-tag :type="levelTagType(liveLevel)" effect="dark" size="small">
                {{ liveLevel }}
              </el-tag>
            </div>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 应对 -->
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="应对策略">
            <el-select v-model="form.responseStrategy" placeholder="未选择" clearable style="width: 100%">
              <el-option label="规避 AVOID" value="AVOID" />
              <el-option label="缓解 MITIGATE" value="MITIGATE" />
              <el-option label="转移 TRANSFER" value="TRANSFER" />
              <el-option label="接受 ACCEPT" value="ACCEPT" />
              <el-option label="开拓 EXPLOIT" value="EXPLOIT" />
              <el-option label="提高 ENHANCE" value="ENHANCE" />
              <el-option label="分享 SHARE" value="SHARE" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="责任人">
            <el-select
              v-model="form.ownerUserId"
              placeholder="选择负责人"
              clearable
              filterable
              style="width: 100%"
            >
              <el-option
                v-for="u in users"
                :key="u.id"
                :label="(u.fullName || u.username) + ' (' + u.username + ')'"
                :value="u.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="预防/缓解">
        <el-input
          v-model="form.mitigation"
          type="textarea"
          :rows="2"
          placeholder="预防措施 / 缓解措施 (选填)"
        />
      </el-form-item>

      <el-form-item label="应急/兜底">
        <el-input
          v-model="form.contingency"
          type="textarea"
          :rows="2"
          placeholder="风险真正发生时的兜底方案 (选填)"
        />
      </el-form-item>

      <!-- 日期 -->
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="识别日期">
            <el-date-picker
              v-model="form.identifiedDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="目标关闭">
            <el-date-picker
              v-model="form.targetCloseDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <el-button @click="onCancel">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">
        {{ isEdit ? '保存' : '创建' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.score-display {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 4px 0;
}
.score-num {
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
}
.score-num.lvl-LOW {
  color: #67c23a;
}
.score-num.lvl-MEDIUM {
  color: #909399;
}
.score-num.lvl-HIGH {
  color: #e6a23c;
}
.score-num.lvl-CRITICAL {
  color: #f56c6c;
}
</style>
