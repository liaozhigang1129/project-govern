<script setup lang="ts">
/**
 * WBS 任务编辑/新增 弹窗
 *
 * Props:
 *  - modelValue: 是否可见
 *  - projectId:  项目 id
 *  - task:       编辑时传已有节点(必须有 id), 新增时传 {parentId, projectId, wbsCode?, name?}
 *  - parent:     父任务(显示面包屑,新增时展示), 顶层为 null
 */
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { saveWbsTask, type WbsTaskNode } from '@/api/wbs'

const props = defineProps<{
  modelValue: boolean
  projectId: number
  task: Partial<WbsTaskNode> | null
  parent: WbsTaskNode | null
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'saved', task: WbsTaskNode): void
}>()

const form = ref({
  id: undefined as number | undefined,
  projectId: props.projectId,
  parentId: null as number | null,
  wbsCode: '',
  name: '',
  taskType: 'EXECUTION' as 'SUMMARY' | 'EXECUTION' | 'MILESTONE' | 'DELIVERABLE',
  status: 'NOT_STARTED' as 'NOT_STARTED' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED' | 'CANCELLED',
  ownerUserId: null as number | null,
  planStartDate: null as string | null,
  planEndDate: null as string | null,
  actualStartDate: null as string | null,
  actualEndDate: null as string | null,
  planHours: 0,
  actualHours: 0,
  progressPct: 0,
  weight: 1,
  critical: false,
  milestone: false,
  milestoneId: null as number | null,
  predecessorIds: [] as number[],
  deliverable: '' as string,
  remark: '' as string,
})

const saving = ref(false)
const formRef = ref()

// ============================================================
// 打开时, 把 props.task 拷贝到 form
// ============================================================
watch(
  () => [props.modelValue, props.task],
  ([v, t]) => {
    if (v) {
      if (t) {
        const task = t as WbsTaskNode
        form.value = {
          ...form.value,
          ...task,
          deliverable: task.deliverable ?? '',
          remark: task.remark ?? '',
          predecessorIds: task.predecessorIds ? [...task.predecessorIds] : [],
        }
      } else {
        // 新建: 兜底
        form.value = {
          id: undefined,
          projectId: props.projectId,
          parentId: props.parent?.id ?? null,
          wbsCode: '',
          name: '',
          taskType: 'EXECUTION',
          status: 'NOT_STARTED',
          ownerUserId: null,
          planStartDate: null,
          planEndDate: null,
          actualStartDate: null,
          actualEndDate: null,
          planHours: 0,
          actualHours: 0,
          progressPct: 0,
          weight: 1,
          critical: false,
          milestone: false,
          milestoneId: null,
          predecessorIds: [],
          deliverable: '',
          remark: '',
        }
      }
    }
  },
  { immediate: true },
)

const isEdit = computed(() => !!form.value.id)
const title = computed(() => (isEdit.value ? '编辑 WBS 任务' : '新增 WBS 任务'))

const breadcrumb = computed(() => {
  const parts = [props.parent?.wbsCode, form.value.wbsCode].filter(Boolean)
  return parts.length ? parts.join(' / ') : '(顶层)'
})

// ============================================================
// 校验规则
// ============================================================
const rules = {
  wbsCode: [{ required: true, message: '请输入 WBS 编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  taskType: [{ required: true, message: '请选择任务类型', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  weight: [{ required: true, message: '请输入权重', trigger: 'blur' }],
  progressPct: [{ type: 'number', min: 0, max: 100, message: '进度需在 0-100', trigger: 'blur' }],
}

// ============================================================
// 提交
// ============================================================
async function onSave() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const saved = await saveWbsTask(form.value)
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

/** el-dialog 关闭事件转发 */
function onVisibleChange(v: boolean) {
  emit('update:modelValue', v)
}

/** 紧前任务输入解析: '1, 3, 5' → [1, 3, 5] */
function onPredecessorInput(v: string) {
  form.value.predecessorIds = v
    .split(',')
    .map((s) => parseInt(s.trim()))
    .filter((n) => !isNaN(n))
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="640px"
    :close-on-click-modal="false"
    @update:model-value="onVisibleChange"
  >
    <!-- 面包屑 -->
    <el-alert type="info" :closable="false" style="margin-bottom: 16px">
      <template #title>
        <span>
          位置:
          <b>{{ breadcrumb }}</b>
        </span>
        <span v-if="parent" style="margin-left: 12px; color: #909399; font-size: 12px">
          父任务: {{ parent.name }}
        </span>
        <span v-else style="margin-left: 12px; color: #909399; font-size: 12px">顶层任务</span>
      </template>
    </el-alert>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="WBS 编码" prop="wbsCode">
        <el-input
          v-model="form.wbsCode"
          placeholder="例如 1.1.2 (项目内唯一)"
          maxlength="32"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="任务名称" prop="name">
        <el-input
          v-model="form.name"
          placeholder="例如 竞品分析 / 接口联调"
          maxlength="256"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="任务类型" prop="taskType">
        <el-select v-model="form.taskType" style="width: 100%">
          <el-option label="📁 SUMMARY  -  汇总节点" value="SUMMARY" />
          <el-option label="📋 EXECUTION  -  执行任务" value="EXECUTION" />
          <el-option label="◆  MILESTONE  -  里程碑" value="MILESTONE" />
          <el-option label="📦 DELIVERABLE  -  交付物" value="DELIVERABLE" />
        </el-select>
      </el-form-item>

      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio-button value="NOT_STARTED">未开始</el-radio-button>
          <el-radio-button value="IN_PROGRESS">进行中</el-radio-button>
          <el-radio-button value="BLOCKED">阻塞</el-radio-button>
          <el-radio-button value="COMPLETED">已完成</el-radio-button>
          <el-radio-button value="CANCELLED">已取消</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="权重" prop="weight">
            <el-input-number v-model="form.weight" :min="1" :max="10" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="进度(%)" prop="progressPct">
            <el-input-number v-model="form.progressPct" :min="0" :max="100" :step="5" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="负责人" prop="ownerUserId">
            <el-input-number v-model="form.ownerUserId" :min="1" placeholder="user_id" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="计划开始">
            <el-date-picker
              v-model="form.planStartDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="计划结束">
            <el-date-picker
              v-model="form.planEndDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="实际开始">
            <el-date-picker
              v-model="form.actualStartDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="实际结束">
            <el-date-picker
              v-model="form.actualEndDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="计划工时(h)">
            <el-input-number v-model="form.planHours" :min="0" :step="0.5" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="实际工时(h)">
            <el-input-number v-model="form.actualHours" :min="0" :step="0.5" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="紧前任务">
        <el-input
          :model-value="form.predecessorIds.join(', ')"
          placeholder="逗号分隔的任务 id, 例如 1, 3, 5"
          @update:model-value="onPredecessorInput"
        />
      </el-form-item>

      <el-form-item label="交付物">
        <el-input v-model="form.deliverable" type="textarea" :rows="2" maxlength="2000" show-word-limit />
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="2000" show-word-limit />
      </el-form-item>

      <el-form-item>
        <el-checkbox v-model="form.critical">关键任务 (关键路径)</el-checkbox>
        <el-checkbox v-model="form.milestone" style="margin-left: 16px">里程碑任务</el-checkbox>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="onCancel">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">
        {{ isEdit ? '保存' : '创建' }}
      </el-button>
    </template>
  </el-dialog>
</template>
