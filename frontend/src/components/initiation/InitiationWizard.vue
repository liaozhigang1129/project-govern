<template>
  <!-- 6 步向导对话框 — 铁三角 → AI WBS → 资源 → 风险 → 毛利 -->
  <el-dialog
    v-model="visible"
    :title="`立项全流程 #${initiationId ?? '(待创建)'}`"
    width="1180px"
    top="4vh"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    destroy-on-close
    @close="onClose"
  >
    <!--
      步骤条高亮逻辑:
      - edit-basic / create: active 跟着用户操作走(active.value)
      - edit-supplement (补料) 且 currentStep 是审批阶段:
        把 Step 1 标绿 (已完成),把当前审批阶段对应的高亮
        - DEPT_LEAD / PMO_ADMIN / EXEC  → 高亮 Step 1 (审批人在看信息)
        - SUPPLEMENT                     → 高亮 Step 2 (申请人在补料)
        - 终态 (EXEC_APPROVED/REJECTED)  → 全部完成 (active=6)
    -->
    <el-steps :active="wizardActive" finish-status="success" align-center class="wizard-steps">
      <el-step title="基础信息" description="AR/SR/FR + 合同" />
      <el-step title="SOW & AI" description="上传 SOW · 生成 WBS" />
      <el-step title="WBS 调整" description="里程碑 · 工作包" />
      <el-step title="资源 & 交付" description="派遣 · 成本测算" />
      <el-step title="风险应对" description="应对动作 · 成本" />
      <el-step title="预算 & 毛利" description="冻结快照" />
    </el-steps>

    <div class="step-container">
      <Step1BasicForm
        v-show="active === 0"
        :form="form.basic"
        :disabled="step1Disabled"
        :departments="departments"
        :project-types="projectTypes"
        :project-levels="projectLevels"
        :pm-candidates="pmCandidates"
      />
      <Step2SowAndAi
        v-if="active === 1 && !!initiationId"
        ref="step2Ref"
        :initiation-id="initiationId!"
        :sow-files="form.sowFiles"
        :ai-draft="form.aiDraft"
        @update:sow-files="(v: SowFile[]) => (form.sowFiles = v)"
        @update:ai-draft="(v: AiWbsDraft | null) => (form.aiDraft = v)"
      />
      <Step3WbsAdjust
        v-if="active === 2 && !!initiationId"
        :initiation-id="initiationId!"
        :ai-draft="form.aiDraft"
        @applied="active = 3"
      />
      <Step4ResourceAndDelivery
        v-if="active === 3 && !!initiationId"
        ref="step4Ref"
        :initiation-id="initiationId!"
        :contract-amount="form.basic.contractAmount ?? 0"
        :risk-cost="form.riskTotal"
        :plan-range="null"
        @total-cost="(v: number) => (form.resourceTotal = v)"
        @contract-amount="(v: number) => onStep4ContractChange(v)"
      />
      <Step5RiskResponse
        v-if="active === 4 && !!initiationId"
        :initiation-id="initiationId!"
        @update:total-cost="v => (form.riskTotal = v)"
      />
      <Step6BudgetAndMargin
        v-if="active === 5 && !!initiationId"
        :initiation-id="initiationId!"
      />

      <el-empty
        v-if="active > 0 && !initiationId"
        description="请先完成 Step 1 立项草稿创建"
        :image-size="80"
        style="margin: 60px 0"
      />
    </div>

    <template #footer>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <el-button @click="visible = false">关闭</el-button>
        <div>
          <!--
            edit-basic (修改立项信息): 只允许保存 Step 1,不允许跳到 Step 2+
            - 隐藏「上一步 / 下一步」导航
            - Step 1 显示「保存修改」(走 PATCH /initiations/{id})
          -->
          <template v-if="mode === 'edit-basic'">
            <el-button
              type="primary"
              :loading="savingBasic"
              :disabled="!initiationId || loadingBasic"
              @click="updateInitiationBasic"
            >保存修改</el-button>
          </template>
          <template v-else>
            <el-button v-if="active > 0" :disabled="active === 0" @click="active--">上一步</el-button>
            <!-- 新建模式:Step 1 显示「创建立项并进入下一步」 -->
            <el-button
              v-if="active === 0 && mode === 'create'"
              type="primary" :loading="savingBasic" @click="createInitiation"
            >创建立项并进入下一步</el-button>
            <!-- edit-supplement (补料) 模式回到 Step 1:也允许保存基础信息 -->
            <el-button
              v-else-if="active === 0 && mode === 'edit-supplement'"
              type="primary" :loading="savingBasic" :disabled="!initiationId || loadingBasic"
              @click="updateInitiationBasic"
            >保存修改</el-button>
            <el-button
              v-else type="primary" @click="next"
            >{{ active === 5 ? '完成' : '下一步' }}</el-button>
          </template>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api, { type SowFile, type AiWbsDraft } from '@/api/client'
import Step1BasicForm, {
  type InitiationBasicForm,
  type DepartmentLite,
  type ProjectTypeOption,
  type ProjectLevelOption,
  type PmCandidate,
} from './Step1BasicForm.vue'
import Step2SowAndAi from './Step2SowAndAi.vue'
import Step3WbsAdjust from './Step3WbsAdjust.vue'
import Step4ResourceAndDelivery from './Step4ResourceAndDelivery.vue'
import Step5RiskResponse from './Step5RiskResponse.vue'
import Step6BudgetAndMargin from './Step6BudgetAndMargin.vue'

// V4.17: 部门树 / 字典 / PM 候选人 字典数据
const departments = ref<DepartmentLite[]>([])
const projectTypes = ref<ProjectTypeOption[]>([])
const projectLevels = ref<ProjectLevelOption[]>([])
const pmCandidates = ref<PmCandidate[]>([])

async function loadDictionaries() {
  try {
    const [dept, types, levels, optionsRes] = await Promise.all([
      api.get<DepartmentLite[]>('/departments/tree'),
      api.get<ProjectTypeOption[]>('/dict/project-types'),
      api.get<ProjectLevelOption[]>('/dict/project-levels'),
      // /users 默认是分页接口, 用 /users/options 拉全量
      api.get<{ id: number; username: string; fullName: string; primaryRoleCode: string }[]>('/users/options'),
    ])
    departments.value = dept ?? []
    projectTypes.value = types ?? []
    projectLevels.value = levels ?? []
    // 过滤 PM 角色 (PM / PMO_ADMIN), 业务方可手动改
    pmCandidates.value = (optionsRes ?? [])
      .filter(u => u.primaryRoleCode === 'PM' || u.primaryRoleCode === 'PMO_ADMIN')
      .map(u => ({
        id: u.id,
        username: u.username,
        fullName: u.fullName,
        primaryRole: { code: u.primaryRoleCode, id: 0, name: u.primaryRoleCode },
      }))
  } catch (e: any) {
    // 字典加载失败不阻断创建立项, 业务方可手填
    console.warn('[InitiationWizard] 字典加载失败:', e?.message)
  }
}
onMounted(loadDictionaries)

interface WizardForm {
  basic: InitiationBasicForm
  sowFiles: SowFile[]
  aiDraft: AiWbsDraft | null
  resourceTotal: number
  riskTotal: number
}

const props = defineProps<{
  modelValue: boolean
  initiationId: number | null
  /** 'create' 新建 | 'edit-basic' 只改基础信息 | 'edit-supplement' 补料 (Step 2+) */
  mode?: 'create' | 'edit-basic' | 'edit-supplement'
}>()
const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  created: [id: number]
  updated: [id: number]
}>()

// 兼容旧调用: 未传 mode 时, 有 initiationId 视作 edit-basic(只允许改基础信息)
const mode = computed<'create' | 'edit-basic' | 'edit-supplement'>(
  () => props.mode ?? (props.initiationId ? 'edit-basic' : 'create'),
)
// edit-basic 模式下整张 Step 1 表单锁定只读 — 只允许改基础信息
const step1Disabled = computed(() => false)

const visible = ref(props.modelValue)
watch(() => props.modelValue, v => (visible.value = v))
watch(visible, v => emit('update:modelValue', v))

const active = ref(0)
const savingBasic = ref(false)
const loadingBasic = ref(false)
const initiationId = ref<number | null>(props.initiationId)
const step2Ref = ref<any>(null)
const step4Ref = ref<any>(null)

/** 审批阶段 → 步骤条要亮哪一步。
 *  - DEPT_LEAD / PMO_ADMIN / EXEC: 审批人在「基础信息」这步 → 高亮 Step 1
 *  - SUPPLEMENT:                 申请人被审批人打回,正在补料 → 高亮 Step 2
 *  - undefined / '' / 终态:      立项已批准或没走到审批,默认跟着用户点击走
 *  - 其他字符串:                 兜底: 高亮 Step 1 (审批人查看)
 */
const APPROVAL_STEP_DEFAULT = 0
function activeByApprovalStep(stepCode?: string | null): number {
  if (!stepCode) return APPROVAL_STEP_DEFAULT
  const code = String(stepCode).toUpperCase()
  if (code === 'SUPPLEMENT') return 1 // 补料 → Step 2 (SOW)
  if (code === 'DEPT_LEAD' || code === 'PMO_ADMIN' || code === 'EXEC') return 0 // 审批阶段 → Step 1
  return APPROVAL_STEP_DEFAULT
}

/** 当前步骤条要高亮的下标。
 *  - edit-supplement 模式: 根据后端 currentStep 决定高亮哪一步
 *  - 其他模式: 跟用户操作走 (active.value)
 */
const wizardActive = computed(() => {
  if (mode.value === 'edit-supplement') {
    // SUPPLEMENT 状态下用户通常从 Step 2 开始补料,
    // 但如果当前审批人在 DEPT_LEAD/PMO_ADMIN/EXEC,则保持显示 Step 1 (供审批人查看)。
    // 简化策略: 用 detail.currentStep,SUPPLEMENT → 1,其它审批 → 0,缺失 → 1 (默认补料)
    return activeByApprovalStep(detailCurrentStep.value ?? 'SUPPLEMENT')
  }
  return active.value
})

/** 详情里的当前审批步骤(从后端拉,用于步骤条高亮) */
const detailCurrentStep = ref<string | null>(null)

function onStep4ContractChange(v: number) {
  // Step 4 改了合同金额,这里同步给 form.basic,这样再回到 Step 1 时合同金额已被更新
  form.basic.contractAmount = v
}

const form = reactive<WizardForm>({
  basic: {
    code: 'IR-' + Date.now(),
    title: '',
    contractCurrency: 'CNY',
    planWorkWeeks: 12,
  },
  sowFiles: [],
  aiDraft: null,
  resourceTotal: 0,
  riskTotal: 0,
})

// 监听 mode + initiationId 的组合变化:
// - 同一个 dialog 复用,mode 在「修改」→「补料」之间切换时只改 mode 不改 id,
//   这种情况下必须重新走对应的分支(active/load 数据)。
// - destroy-on-close 后重建时 immediate:true 也会按当前 mode 重新初始化一次。
watch(
  [() => props.mode, () => props.initiationId],
  async ([newMode, newId], [_oldMode, oldId]) => {
    initiationId.value = newId
    if (newMode === 'edit-basic' && newId) {
      // 修改基础信息: 只允许改 Step 1, 加载现有数据填到 form.basic
      active.value = 0
      await loadInitiationBasic(newId)
    } else if (newMode === 'edit-supplement' && newId) {
      // 补料: 直接跳到 Step 2 (SOW 上传), 仍可继续 Step 3~6
      active.value = 1
      // V4.24: 步骤条高亮依赖 detailCurrentStep,这里也要拉一次详情
      try {
        const i: any = await api.get(`/initiations/${newId}`)
        detailCurrentStep.value = i.currentStep ?? 'SUPPLEMENT'
      } catch {
        detailCurrentStep.value = 'SUPPLEMENT'
      }
      await nextTick()
      step2Ref.value?.loadSowFiles?.()
      step2Ref.value?.loadPaste?.()
      step2Ref.value?.loadAiDraft?.()
    } else if (newId && newId !== oldId) {
      // 兼容旧调用: 既有 initiationId 但未显式传 mode, 视作 edit-basic
      active.value = 0
      await loadInitiationBasic(newId)
    }
  },
  { immediate: true },
)

/** 编辑模式: 从后端拉现有立项, 填到 form.basic 让 Step 1 可编辑 */
async function loadInitiationBasic(id: number) {
  loadingBasic.value = true
  try {
    const i: any = await api.get(`/initiations/${id}`)
    // 后端返回的是 ProjectInitiation 全字段, 全部映射到 form.basic
    form.basic = {
      ...form.basic,
      code: i.code ?? form.basic.code,
      title: i.title ?? form.basic.title,
      clientName: i.clientName ?? '',
      clientContactName: i.clientContactName ?? '',
      clientContactPhone: i.clientContactPhone ?? '',
      arUserName: i.arUserName ?? '',
      srUserName: i.srUserName ?? '',
      frUserName: i.frUserName ?? '',
      contractAmount: i.contractAmount != null ? Number(i.contractAmount) : undefined,
      contractCurrency: i.contractCurrency ?? 'CNY',
      planWorkWeeks: i.planWorkWeeks ?? 12,
      background: i.background ?? '',
      goals: i.goals ?? '',
      scope: i.scope ?? '',
      departmentId: i.departmentId ?? undefined,
      pmUserId: i.pmUserId ?? undefined,
      projectTypeCode: i.projectTypeCode ?? undefined,
      projectLevelCode: i.projectLevelCode ?? undefined,
      expectedGrossMarginPct: i.expectedGrossMarginPct != null ? Number(i.expectedGrossMarginPct) : undefined,
      plannedStart: i.plannedStart ?? undefined,
      plannedEnd: i.plannedEnd ?? undefined,
      plannedLaunchDate: i.plannedLaunchDate ?? undefined,
    }
    // V4.24: 保存后端 currentStep, 用于步骤条高亮 (edit-supplement 模式下需要)
    detailCurrentStep.value = i.currentStep ?? null
  } catch (e: any) {
    ElMessage.error('加载立项信息失败: ' + e.message)
  } finally {
    loadingBasic.value = false
  }
}

/** 编辑模式: 保存 Step 1 修改 (走 PATCH /initiations/{id}) */
async function updateInitiationBasic() {
  if (!initiationId.value) return
  if (!form.basic.title?.trim()) {
    ElMessage.warning('请填写立项标题')
    return
  }
  savingBasic.value = true
  try {
    await api.patch(`/initiations/${initiationId.value}`, {
      title: form.basic.title,
      clientName: form.basic.clientName,
      clientContactName: form.basic.clientContactName,
      clientContactPhone: form.basic.clientContactPhone,
      arUserName: form.basic.arUserName,
      srUserName: form.basic.srUserName,
      frUserName: form.basic.frUserName,
      contractAmount: form.basic.contractAmount,
      contractCurrency: form.basic.contractCurrency,
      planWorkWeeks: form.basic.planWorkWeeks,
      background: form.basic.background,
      goals: form.basic.goals,
      scope: form.basic.scope,
      departmentId: form.basic.departmentId,
      pmUserId: form.basic.pmUserId,
      projectTypeCode: form.basic.projectTypeCode,
      projectLevelCode: form.basic.projectLevelCode,
      expectedGrossMarginPct: form.basic.expectedGrossMarginPct,
      plannedStart: form.basic.plannedStart,
      plannedEnd: form.basic.plannedEnd,
      plannedLaunchDate: form.basic.plannedLaunchDate,
    })
    ElMessage.success('立项信息已更新')
    emit('updated', initiationId.value)
  } catch (e: any) {
    ElMessage.error('保存失败: ' + e.message)
  } finally {
    savingBasic.value = false
  }
}

async function createInitiation() {
  if (!form.basic.title?.trim()) {
    ElMessage.warning('请填写立项标题')
    return
  }
  savingBasic.value = true
  try {
    const created = await api.post<{ id: number }>('/initiations', {
      code: form.basic.code,
      title: form.basic.title,
      clientName: form.basic.clientName,
      clientContactName: form.basic.clientContactName,
      clientContactPhone: form.basic.clientContactPhone,
      arUserName: form.basic.arUserName,
      srUserName: form.basic.srUserName,
      frUserName: form.basic.frUserName,
      contractAmount: form.basic.contractAmount,
      contractCurrency: form.basic.contractCurrency,
      planWorkWeeks: form.basic.planWorkWeeks,
      background: form.basic.background,
      goals: form.basic.goals,
      scope: form.basic.scope,
      // V4.17: 立项基础信息补全
      departmentId: form.basic.departmentId,
      pmUserId: form.basic.pmUserId,
      projectTypeCode: form.basic.projectTypeCode,
      projectLevelCode: form.basic.projectLevelCode,
      expectedGrossMarginPct: form.basic.expectedGrossMarginPct,
      plannedStart: form.basic.plannedStart,
      plannedEnd: form.basic.plannedEnd,
      plannedLaunchDate: form.basic.plannedLaunchDate,
    })
    initiationId.value = created.id
    ElMessage.success(`立项 #${created.id} 已创建,可上传 SOW`)
    emit('created', created.id)
    active.value = 1
  } catch (e: any) {
    ElMessage.error('创建失败: ' + e.message)
  } finally {
    savingBasic.value = false
  }
}

function next() {
  if (active.value < 5) {
    active.value++
  } else {
    visible.value = false
  }
}

function onClose() {
  // 关闭时根据 mode 决定回到哪个步骤:
  // - create / edit-supplement: 重新打开时从 Step 2 开始(SOW 补料)
  // - edit-basic: 重新打开时从 Step 1 开始(基础信息修改)
  if (mode.value === 'edit-basic') {
    active.value = 0
  } else {
    active.value = props.initiationId ? 1 : 0
  }
}
</script>

<style scoped>
.wizard-steps {
  margin-bottom: 24px;
}
.step-container {
  min-height: 460px;
  max-height: calc(100vh - 280px);
  overflow-y: auto;
  padding: 0 4px;
}
</style>
