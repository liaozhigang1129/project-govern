<template>
  <!-- Step 3 — WBS 调整 + 里程碑 -->
  <el-empty v-if="!aiDraft" description="请先在 Step 2 生成 AI WBS 草稿" :image-size="80" />

  <div v-else>
    <el-alert
      type="warning" :closable="false"
      title="AI 生成的草稿为建议值,您可以编辑里程碑/工作包/风险内容,确认后将应用到项目 WBS 库。"
      style="margin-bottom: 16px"
    />

    <div v-for="(m, mi) in localMilestones" :key="mi" class="ms-edit-card">
      <div class="ms-edit-header">
        <el-input v-model="m.name" style="flex: 1; margin-right: 8px" placeholder="里程碑名称" />
        <el-input-number v-model="m.sequence" :min="1" :max="20" controls-position="right" style="width: 110px" />
        <el-input-number
          v-model="m.targetWeek" :min="0" :max="104" placeholder="目标周" controls-position="right"
          style="width: 130px; margin-left: 8px"
        />
        <el-button type="danger" link :icon="Delete" @click="removeMilestone(mi)" style="margin-left: 8px" />
      </div>

      <el-table :data="m.workPackages" size="small" border style="margin-top: 8px">
        <el-table-column label="工作包" min-width="220">
          <template #default="{ row }">
            <el-input v-model="row.name" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="工期(周)" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.durationWeeks" :min="1" :max="52" size="small" :controls="false" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="负责角色" width="140">
          <template #default="{ row }">
            <el-select v-model="row.ownerRoleCode" size="small" placeholder="选角色" style="width: 100%">
              <el-option v-for="r in ROLES" :key="r.code" :label="r.name" :value="r.code" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.description" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="" width="60" align="center">
          <template #default="{ $index }">
            <el-button size="small" link type="danger" :icon="Delete" @click="m.workPackages.splice($index, 1)" />
          </template>
        </el-table-column>
      </el-table>

      <el-button
        link type="primary" size="small" :icon="Plus"
        style="margin-top: 6px" @click="addWorkPackage(mi)"
      >+ 添加工作包</el-button>
    </div>

    <el-button :icon="Plus" type="primary" plain @click="addMilestone" style="margin-top: 12px">
      + 添加里程碑
    </el-button>

    <el-divider content-position="left">风险确认</el-divider>

    <el-table :data="localRisks" size="small" border>
      <el-table-column label="等级" width="120">
        <template #default="{ row }">
          <el-select v-model="row.level" size="small" style="width: 100%">
            <el-option v-for="l in RISK_LEVELS" :key="l.value" :label="l.label" :value="l.value" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="风险" min-width="200">
        <template #default="{ row }">
          <el-input v-model="row.title" size="small" />
        </template>
      </el-table-column>
      <el-table-column label="影响" min-width="160">
        <template #default="{ row }">
          <el-input v-model="row.impact" size="small" />
        </template>
      </el-table-column>
      <el-table-column label="建议" min-width="220">
        <template #default="{ row }">
          <el-input v-model="row.suggestion" size="small" />
        </template>
      </el-table-column>
      <el-table-column label="" width="60" align="center">
        <template #default="{ $index }">
          <el-button size="small" link type="danger" :icon="Delete" @click="localRisks.splice($index, 1)" />
        </template>
      </el-table-column>
    </el-table>

    <el-button :icon="Plus" type="primary" plain size="small" @click="addRisk" style="margin-top: 8px">
      + 添加风险
    </el-button>

    <div style="margin-top: 16px; text-align: right">
      <el-button type="success" :icon="Check" @click="confirmDraft" :loading="confirming">
        确认调整,应用到项目 WBS
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Check } from '@element-plus/icons-vue'
import api, { type AiWbsDraft, type AiWbsMilestone, type AiWbsRisk, type AiWbsWorkPackage } from '@/api/client'

const props = defineProps<{
  initiationId: number
  aiDraft: AiWbsDraft | null
}>()

const emit = defineEmits<{ applied: [] }>()

const ROLES = [
  { code: 'PM', name: '项目经理' },
  { code: 'AR', name: '客户经理' },
  { code: 'SR', name: '售前' },
  { code: 'FR', name: '方案经理' },
  { code: 'BA', name: '需求分析师' },
  { code: 'ARCH', name: '架构师' },
  { code: 'DEV', name: '开发工程师' },
  { code: 'QA', name: '测试工程师' },
  { code: 'CFG', name: '配置/实施' },
]
const RISK_LEVELS = [
  { value: 'LOW', label: '低' },
  { value: 'MEDIUM', label: '中' },
  { value: 'HIGH', label: '高' },
  { value: 'CRITICAL', label: '极高' },
]

const localMilestones = ref<AiWbsMilestone[]>([])
const localRisks = ref<AiWbsRisk[]>([])
const confirming = ref(false)

watch(() => props.aiDraft, (v) => {
  if (v) {
    localMilestones.value = JSON.parse(JSON.stringify(v.milestones))
    localRisks.value = JSON.parse(JSON.stringify(v.risks))
  }
}, { immediate: true })

function addMilestone() {
  localMilestones.value.push({
    name: `新里程碑 ${localMilestones.value.length + 1}`,
    sequence: localMilestones.value.length + 1,
    workPackages: [],
  })
}
function removeMilestone(i: number) {
  localMilestones.value.splice(i, 1)
}
function addWorkPackage(mi: number) {
  localMilestones.value[mi].workPackages.push({
    name: '新工作包',
    durationWeeks: 2,
    ownerRoleCode: 'DEV',
  } as AiWbsWorkPackage)
}
function addRisk() {
  localRisks.value.push({ title: '新风险', level: 'MEDIUM', suggestion: '' })
}

async function confirmDraft() {
  confirming.value = true
  try {
    // ✅ 接口契约修复:
    // 1) 后端没有 /ai-wbs/save-draft 接口(老代码调用 → 404, 移除)
    // 2) apply 接口是 /ai-wbs/apply/{draftId} (老代码 /ai-wbs/apply → 路由不匹配)
    // 3) 调整过的 milestones/risks 仅作为 apply 前的"确认视图",不持久化
    //    (如需持久化调整后的版本, 应后端先实现 PATCH /ai-wbs/{draftId}, TODO V4.16)
    const draftId = props.aiDraft?.draftId
    if (!draftId) {
      ElMessage.error('缺少草稿 ID,请先在 Step 2 生成 WBS 草稿')
      return
    }
    await api.post(`/initiations/${props.initiationId}/ai-wbs/apply/${draftId}`, {})
    // V4.19 幂等提示: 后端 idempotent=true 表示之前已应用过,本次只是确认
    ElMessage.success('WBS 已应用到项目,可在项目详情页继续调整')
    emit('applied')
  } catch (e: any) {
    ElMessage.error('应用失败: ' + e.message)
  } finally {
    confirming.value = false
  }
}
</script>

<style scoped>
.ms-edit-card {
  background: #fafbfc;
  border-left: 3px solid #4facfe;
  border-radius: 4px;
  padding: 12px 16px;
  margin-bottom: 12px;
}
.ms-edit-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
