<script setup lang="ts">
/**
 * MilestoneDrawer — 里程碑详情抽屉(P1.5 收尾)
 *
 * 用途:甘特图上点击里程碑菱形 → 弹此抽屉,展示:
 *  - 状态 + 实际日期
 *  - 责任人(全名)
 *  - 交付物 / 备注
 *  - 改期(只读展示,改走拖拽 + PATCH)
 *  - 改状态(标记为已完成等)
 *
 * 数据源:GanttView 传过来的 milestone 对象(已有 planDate/actualDate 等),
 *      补全从 /milestones/by-project 接口拉一次最新 owner/deliverable/remark/status
 *      (甘特图接口为了瘦身只回 id/name/planDate/actualDate/status/weight 5 字段)
 */
import { ref, watch, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar, User, Document, Edit, Check, Refresh } from '@element-plus/icons-vue'
import { milestoneApi, userApi, type Milestone, type MilestoneStatusCode } from '@/api/gantt'
import type { AppUser } from '@/api/client'

const props = defineProps<{
  modelValue: boolean
  /** GanttView 传过来的轻量 milestone(id, projectId, name, planDate, actualDate, status.code, weight) */
  milestone: {
    id: number
    projectId: number
    name: string
    planDate: string
    actualDate: string | null
    status: string
    weight: number
  } | null
  /** owner 名字解析(可选)— Workload.vue 一次性传入 */
  userMap?: Map<number, { fullName: string; username: string }>
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'refresh'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

// 完整 milestone(从后端拉)
const detail = ref<Milestone | null>(null)
const loading = ref(false)
const users = ref<AppUser[]>([])

// 编辑状态
const editing = ref(false)
const editForm = ref<{ planDate: string; weight: number; ownerUserId: number | null; deliverable: string; remark: string }>({
  planDate: '',
  weight: 1,
  ownerUserId: null,
  deliverable: '',
  remark: ''
})
const saving = ref(false)

// ---------- 监听:打开抽屉时拉详情 ----------
watch(() => [props.modelValue, props.milestone?.id], async ([vis, mid]) => {
  if (!vis || !mid) return
  await loadDetail(mid as number)
  // 顺便拉用户列表(只一次缓存)
  if (users.value.length === 0) {
    try { users.value = await userApi.list({ enabled: true }) } catch { /* 兜底 */ }
  }
}, { immediate: false })

async function loadDetail(id: number) {
  loading.value = true
  try {
    const list = await milestoneApi.list(props.milestone!.projectId)
    detail.value = list.find(m => m.id === id) ?? null
    if (detail.value) {
      editForm.value = {
        planDate: detail.value.planDate,
        weight: detail.value.weight,
        ownerUserId: detail.value.ownerUserId,
        deliverable: detail.value.deliverable ?? '',
        remark: detail.value.remark ?? ''
      }
    }
  } catch (e: any) {
    ElMessage.error(e.message ?? '加载里程碑详情失败')
  } finally {
    loading.value = false
  }
}

// ---------- 派生 ----------
const ownerUser = computed(() => {
  if (!detail.value?.ownerUserId) return null
  // 优先用 props 传进来的 map,再用自己拉的 users
  if (props.userMap?.has(detail.value.ownerUserId)) {
    return props.userMap.get(detail.value.ownerUserId)!
  }
  return users.value.find(u => u.id === detail.value!.ownerUserId) ?? null
})

const statusColor = computed(() => {
  const map: Record<string, string> = {
    PENDING: '#909399',
    IN_PROGRESS: '#e6a23c',
    COMPLETED: '#67c23a',
    DELAYED: '#f56c6c'
  }
  return map[detail.value?.status?.code ?? ''] ?? '#909399'
})

const isOverdue = computed(() => {
  if (!detail.value || !detail.value.planDate) return false
  if (detail.value.status?.code === 'COMPLETED') return false
  return new Date(detail.value.planDate) < new Date(new Date().toDateString())
})

// ---------- 操作 ----------
async function saveEdit() {
  if (!detail.value) return
  saving.value = true
  try {
    await milestoneApi.update(detail.value.id, {
      planDate: editForm.value.planDate,
      weight: editForm.value.weight,
      ownerUserId: editForm.value.ownerUserId ?? undefined,
      deliverable: editForm.value.deliverable,
      remark: editForm.value.remark
    })
    ElMessage.success('已保存')
    editing.value = false
    await loadDetail(detail.value.id)
    emit('refresh')
  } catch (e: any) {
    ElMessage.error(e.message ?? '保存失败')
  } finally {
    saving.value = false
  }
}

async function changeStatus(status: MilestoneStatusCode) {
  if (!detail.value) return
  try {
    if (status === 'COMPLETED') {
      await ElMessageBox.confirm(
        `将里程碑"${detail.value.name}"标记为已完成,系统将自动写入实际完成日期为今天。继续?`,
        '确认完成', { confirmButtonText: '标记完成', cancelButtonText: '取消', type: 'success' }
      )
    }
    await milestoneApi.putStatus(detail.value.id, status)
    ElMessage.success('状态已更新')
    await loadDetail(detail.value.id)
    emit('refresh')
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

function close() {
  visible.value = false
  editing.value = false
  detail.value = null
}
</script>

<template>
  <el-drawer
    v-model="visible"
    direction="rtl"
    size="540px"
    :with-header="false"
    :destroy-on-close="false"
    @close="close"
  >
    <div v-loading="loading" class="ms-drawer">
      <!-- 空态 -->
      <div v-if="!detail" class="ms-empty">
        <el-empty description="未选择里程碑" />
      </div>

      <template v-else>
        <!-- 头部 -->
        <header class="ms-header" :style="{ borderLeftColor: statusColor }">
          <div class="ms-title-row">
            <span class="ms-status-dot" :style="{ background: statusColor }" />
            <h2 class="ms-title">{{ detail.name }}</h2>
            <el-tag
              v-if="detail.status"
              :color="statusColor"
              effect="dark"
              size="small"
              style="color:#fff; border:none"
            >
              {{ detail.status.name }}
            </el-tag>
            <el-tag v-if="isOverdue" type="danger" effect="plain" size="small">已逾期</el-tag>
          </div>
          <div class="ms-subtitle">
            所属项目 #{{ detail.projectId }} · 序号 {{ detail.sequence }} · 权重 {{ detail.weight }}
          </div>
        </header>

        <!-- 详情 -->
        <div class="ms-section">
          <h3 class="ms-section-title">关键日期</h3>
          <el-row :gutter="16">
            <el-col :span="12">
              <div class="ms-field">
                <el-icon><Calendar /></el-icon>
                <span class="ms-field-label">计划完成</span>
                <span class="ms-field-value">
                  <template v-if="!editing">{{ detail.planDate }}</template>
                  <el-date-picker
                    v-else v-model="editForm.planDate" type="date"
                    format="YYYY-MM-DD" value-format="YYYY-MM-DD" size="small" style="width:130px"
                  />
                </span>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="ms-field">
                <el-icon><Check /></el-icon>
                <span class="ms-field-label">实际完成</span>
                <span class="ms-field-value">{{ detail.actualDate || '—' }}</span>
              </div>
            </el-col>
          </el-row>
          <div v-if="detail.completedAt" class="ms-field" style="margin-top:8px">
            <el-icon><Check /></el-icon>
            <span class="ms-field-label">完成时间</span>
            <span class="ms-field-value" style="font-size:12px; color:#909399">
              {{ new Date(detail.completedAt).toLocaleString('zh-CN') }}
            </span>
          </div>
        </div>

        <div class="ms-section">
          <h3 class="ms-section-title">责任人</h3>
          <div class="ms-field">
            <el-icon><User /></el-icon>
            <span class="ms-field-label">Owner</span>
            <span class="ms-field-value">
              <template v-if="!editing">
                <template v-if="ownerUser">
                  <el-avatar :size="20" style="vertical-align:middle; margin-right:4px">
                    {{ ownerUser.fullName?.charAt(0) }}
                  </el-avatar>
                  {{ ownerUser.fullName }} <span style="color:#909399; font-size:12px">({{ ownerUser.username }})</span>
                </template>
                <span v-else style="color:#c0c4cc">未指定</span>
              </template>
              <el-select v-else v-model="editForm.ownerUserId" placeholder="选 owner" filterable clearable size="small" style="width:200px">
                <el-option v-for="u in users" :key="u.id" :value="u.id" :label="`${u.fullName} (${u.username})`" />
              </el-select>
            </span>
          </div>
        </div>

        <div class="ms-section">
          <h3 class="ms-section-title">交付物</h3>
          <div class="ms-field" style="flex-direction:column; align-items:flex-start; gap:6px">
            <div style="display:flex; align-items:center; gap:6px">
              <el-icon><Document /></el-icon>
              <span class="ms-field-label">交付物清单</span>
            </div>
            <div v-if="!editing" class="ms-multiline">
              {{ detail.deliverable || '— 尚未填写 —' }}
            </div>
            <el-input
              v-else
              v-model="editForm.deliverable"
              type="textarea"
              :rows="3"
              placeholder="例:《需求规格说明书 V1.0》+《需求追溯矩阵》"
            />
          </div>
        </div>

        <div class="ms-section">
          <h3 class="ms-section-title">备注</h3>
          <div v-if="!editing" class="ms-multiline">
            {{ detail.remark || '— 暂无 —' }}
          </div>
          <el-input
            v-else v-model="editForm.remark" type="textarea" :rows="2"
            placeholder="备注 / 风险说明 / 客户方反馈..."
          />
        </div>

        <div class="ms-section">
          <h3 class="ms-section-title">元信息</h3>
          <el-row :gutter="16">
            <el-col :span="12"><div class="ms-field"><span class="ms-field-label">创建</span><span class="ms-field-value" style="font-size:12px">{{ new Date(detail.createdAt).toLocaleString('zh-CN') }}</span></div></el-col>
            <el-col :span="12"><div class="ms-field"><span class="ms-field-label">更新</span><span class="ms-field-value" style="font-size:12px">{{ new Date(detail.updatedAt).toLocaleString('zh-CN') }}</span></div></el-col>
          </el-row>
        </div>

        <!-- 底部操作栏 -->
        <footer class="ms-footer">
          <template v-if="!editing">
            <el-button-group>
              <el-button
                v-if="detail.status?.code === 'PENDING'"
                :icon="Edit" @click="changeStatus('IN_PROGRESS')"
              >开始</el-button>
              <el-button
                v-if="detail.status?.code === 'IN_PROGRESS'"
                :icon="Check" type="success" @click="changeStatus('COMPLETED')"
              >标记完成</el-button>
              <el-button
                v-if="!detail.status?.terminal"
                type="warning" @click="changeStatus('DELAYED')"
              >标记延期</el-button>
              <el-button
                v-if="detail.status?.code === 'DELAYED'"
                @click="changeStatus('IN_PROGRESS')"
              >恢复进行中</el-button>
            </el-button-group>
            <el-button :icon="Edit" type="primary" plain @click="editing = true">编辑</el-button>
            <el-button :icon="Refresh" @click="loadDetail(detail.id)">刷新</el-button>
          </template>
          <template v-else>
            <el-button @click="editing = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
          </template>
        </footer>
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
.ms-drawer { padding: 0; }
.ms-empty { padding-top: 80px; }
.ms-header {
  padding: 16px 20px 14px;
  border-bottom: 1px solid #ebeef5;
  border-left: 4px solid #909399;
  background: #fafbfc;
}
.ms-title-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.ms-status-dot {
  width: 8px; height: 8px; border-radius: 50%;
  box-shadow: 0 0 0 3px rgba(255,255,255,1), 0 0 0 4px currentColor;
}
.ms-title { margin: 0; font-size: 18px; font-weight: 600; }
.ms-subtitle { font-size: 12px; color: #909399; margin-top: 4px; }
.ms-section {
  padding: 14px 20px;
  border-bottom: 1px solid #f5f5f5;
}
.ms-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin: 0 0 10px 0;
  letter-spacing: 0.5px;
}
.ms-field {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  line-height: 1.8;
}
.ms-field-label { color: #909399; min-width: 60px; }
.ms-field-value { color: #303133; flex: 1; }
.ms-multiline {
  width: 100%;
  background: #fafafa;
  padding: 8px 10px;
  border-radius: 4px;
  color: #303133;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
}
.ms-footer {
  position: sticky;
  bottom: 0;
  background: #fff;
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  flex-wrap: wrap;
}
</style>
