<template>
  <!-- 手动 WBS 编辑弹窗 (绕开 el-dialog 内部状态机 + Vue 3.5 patch bug) -->
  <div v-show="modelValue" class="wbs-dialog-overlay" @click.self="close">
    <div class="wbs-dialog">
      <header class="wbs-dialog__header">
        <span class="wbs-dialog__title">
          {{ mode === 'add' ? '新增 WBS 任务' : '编辑 WBS 任务' }}
        </span>
        <button class="wbs-dialog__close" @click="close" type="button" aria-label="Close">×</button>
      </header>
      <main class="wbs-dialog__body">
        <el-form
          v-if="form"
          :model="form"
          label-width="100px"
          label-position="right"
          size="default"
        >
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="WBS 编码">
                <el-input v-model="form.wbsCode" disabled>
                  <template #append>
                    <el-tooltip content="切换上级任务时自动重算 (顶级=最大编号+1 / 子级=父.子编号+1)" placement="top">
                      <el-icon><span>🔄</span></el-icon>
                    </el-tooltip>
                  </template>
                </el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="上级任务">
                <el-select
                  :model-value="form.parentId"
                  placeholder="(无) - 顶级任务"
                  clearable
                  filterable
                  style="width: 100%"
                  @update:model-value="(v: number | null | undefined) => emit('parent-change', v ?? null)"
                >
                  <el-option
                    v-for="t in parentCandidates"
                    :key="t.id"
                    :label="`${t.wbsCode} ${t.name}`"
                    :value="t.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col :span="24">
              <el-form-item label="任务名称" required>
                <el-input v-model="form.name" placeholder="例如: 需求调研与业务建模" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="任务类型">
                <el-select v-model="form.taskType" style="width: 100%">
                  <el-option label="SUMMARY  汇总" value="SUMMARY" />
                  <el-option label="EXECUTION  执行" value="EXECUTION" />
                  <el-option label="MILESTONE  里程碑" value="MILESTONE" />
                  <el-option label="DELIVERABLE  交付物" value="DELIVERABLE" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态">
                <el-select v-model="form.status" style="width: 100%">
                  <el-option label="未开始" value="NOT_STARTED" />
                  <el-option label="进行中" value="IN_PROGRESS" />
                  <el-option label="已完成" value="COMPLETED" />
                  <el-option label="阻塞" value="BLOCKED" />
                  <el-option label="已取消" value="CANCELLED" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="责任人">
                <el-select v-model="form.ownerUserId" placeholder="选择责任人" clearable style="width: 100%">
                  <el-option
                    v-for="(name, id) in owners"
                    :key="id"
                    :label="name"
                    :value="Number(id)"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16">
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

          <el-row :gutter="16">
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

          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="计划工时">
                <el-input-number v-model="form.planHours" :min="0" :step="8" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="实际工时">
                <el-input-number v-model="form.actualHours" :min="0" :step="8" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="完成度 (%)">
                <el-input-number v-model="form.progressPct" :min="0" :max="100" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="权重">
                <el-input-number v-model="form.weight" :min="1" :max="10" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="关键路径">
                <el-switch v-model="form.critical" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="里程碑">
                <el-switch v-model="form.milestone" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col :span="24">
              <el-form-item label="前驱任务">
                <el-select
                  v-model="form.predecessorIds"
                  multiple
                  collapse-tags
                  collapse-tags-tooltip
                  placeholder="选择前驱任务 (可多选)"
                  style="width: 100%"
                >
                  <el-option
                    v-for="t in allTasks"
                    :key="t.id"
                    :label="`${t.wbsCode} ${t.name}`"
                    :value="t.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col :span="24">
              <el-form-item label="交付物">
                <el-input v-model="form.deliverable" placeholder="例如: 需求规格说明书 SRS" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col :span="24">
              <el-form-item label="备注">
                <el-input v-model="form.remark" type="textarea" :rows="3" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </main>
      <footer class="wbs-dialog__footer">
        <el-button @click="close">取消</el-button>
        <el-button type="primary" :loading="saving" @click="emit('save', form)">保存</el-button>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
interface WbsForm {
  id: number
  parentId: number | null
  wbsCode: string
  name: string
  taskType: string
  status: string
  ownerUserId: number | null
  planStartDate: string
  planEndDate: string
  actualStartDate: string
  actualEndDate: string
  planHours: number
  actualHours: number
  progressPct: number
  weight: number
  critical: boolean
  milestone: boolean
  predecessorIds: number[] | null
  deliverable: string
  remark: string
}

defineProps<{
  modelValue: boolean
  mode: 'add' | 'edit'
  form: WbsForm | null
  owners: Record<number, string>
  allTasks: Array<{ id: number; wbsCode: string; name: string }>
  parentCandidates: Array<{ id: number; wbsCode: string; name: string }>
  saving?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'save', form: WbsForm | null): void
  (e: 'parent-change', val: number | null): void
}>()

function close() {
  emit('update:modelValue', false)
}
</script>

<style scoped>
/* WBS 编辑弹窗样式 (与整体页面 PMO 风格匹配) */
.wbs-dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 2005;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  backdrop-filter: blur(2px);
}
.wbs-dialog {
  background: var(--pmo-card, #ffffff);
  border-radius: 10px;
  width: 820px;
  max-width: 100%;
  max-height: calc(100vh - 32px);
  display: flex;
  flex-direction: column;
  box-shadow: 0 12px 48px 8px rgba(0, 0, 0, 0.18), 0 0 1px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  animation: wbsDlgIn 0.18s ease-out;
}
@keyframes wbsDlgIn {
  from { transform: translateY(-12px) scale(0.98); opacity: 0; }
  to   { transform: translateY(0) scale(1); opacity: 1; }
}
.wbs-dialog__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--pmo-border, #e4e7ed);
  background: linear-gradient(135deg, #f8fbff, #ffffff);
}
.wbs-dialog__title {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}
.wbs-dialog__title::before {
  content: '';
  display: inline-block;
  width: 4px;
  height: 18px;
  background: linear-gradient(180deg, #4facfe, #00f2fe);
  border-radius: 2px;
}
.wbs-dialog__close {
  background: none;
  border: 0;
  font-size: 24px;
  line-height: 1;
  color: #909399;
  cursor: pointer;
  padding: 0 4px;
  transition: color 0.15s, transform 0.15s;
}
.wbs-dialog__close:hover {
  color: #F56C6C;
  transform: rotate(90deg);
}
.wbs-dialog__body {
  padding: 20px 24px;
  overflow: auto;
  flex: 1 1 auto;
  background: #fafbfc;
}
.wbs-dialog__body :deep(.el-form-item) { margin-bottom: 18px; }
.wbs-dialog__body :deep(.el-form-item__label) { font-weight: 500; color: #606266; }
.wbs-dialog__footer {
  padding: 14px 24px;
  border-top: 1px solid var(--pmo-border, #e4e7ed);
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  background: #fcfcfd;
}
</style>
