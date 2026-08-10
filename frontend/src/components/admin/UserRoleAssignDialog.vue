<script setup lang="ts">
/**
 * V4.16: 单用户 / 批量用户 角色授权弹窗
 *
 *  - mode = 'single': 给 1 个用户授权 (props.userId 必填)
 *  - mode = 'batch':   给多个用户授权 (props.userIds 必填, ≥1)
 *
 *  弹窗内:
 *    - 已选用户摘要
 *    - 角色多选 (el-transfer / el-table 多选)
 *    - 3 种模式: REPLACE (全量替换) | ADD (追加) | REMOVE (移除)
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/users'

interface AssignableRole {
  id: number
  code: string
  name: string
  builtin: boolean
}

const props = defineProps<{
  visible: boolean
  mode: 'single' | 'batch'
  userId?: number // single 必填
  username?: string // single 显示用
  userIds?: number[] // batch 必填
  userLabels?: string[] // batch 显示用
  userPrimaryRoleCode?: string // 防止自我降级提示
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'saved'): void
}>()

// ============================================================
// 状态
// ============================================================
const allRoles = ref<AssignableRole[]>([])
const selectedRoleIds = ref<number[]>([])
const assignMode = ref<'REPLACE' | 'ADD' | 'REMOVE'>('REPLACE')
const submitting = ref(false)

// 模式
const modeLabel = computed(() => (props.mode === 'batch' ? '批量授权' : '角色授权'))
const title = computed(() => {
  if (props.mode === 'batch') {
    const n = props.userIds?.length ?? 0
    return `批量角色授权 — ${n} 个用户`
  }
  return `角色授权 — ${props.username ?? ''}`
})

// 选中用户摘要
const userSummary = computed(() => {
  if (props.mode === 'batch') {
    const list = props.userLabels ?? []
    if (list.length <= 3) return list.join('、')
    return list.slice(0, 3).join('、') + ` 等 ${list.length} 人`
  }
  return props.username ?? ''
})

// V4.18: 批量模式未选用户标记
const userSummaryEmpty = computed(
  () => props.mode === 'batch' && (!props.userIds || props.userIds.length === 0),
)

// ============================================================
// 加载
// ============================================================
const loading = ref(false)

async function loadAll() {
  loading.value = true
  try {
    allRoles.value = await userApi.listAssignableRoles()
  } finally {
    loading.value = false
  }
}

// 单用户模式: 预加载当前已分配的角色
async function loadCurrentRoles() {
  if (props.mode === 'single' && props.userId) {
    try {
      const data = await userApi.getRoles(props.userId)
      selectedRoleIds.value = (data.roles ?? []).map((r: any) => r.id)
    } catch {
      selectedRoleIds.value = []
    }
  }
}

// V4.17: allRoles 加载完后, 把 selectedRoleIds 对应的行勾上
// V4.20: 用 nextTick 等待 el-table 渲染, 防死循环
const tableRef = ref()
let syncing = false
function onSelectionChange(rows: AssignableRole[]) {
  if (syncing) return
  selectedRoleIds.value = rows.map((r) => r.id)
}
async function applySelection() {
  if (syncing) return
  if (!tableRef.value || !allRoles.value.length) return
  syncing = true
  try {
    await nextTick()
    tableRef.value.clearSelection()
    const want = new Set(selectedRoleIds.value)
    allRoles.value.forEach((row: AssignableRole) => {
      if (want.has(row.id)) {
        tableRef.value.toggleRowSelection(row, true)
      }
    })
  } finally {
    nextTick(() => {
      syncing = false
    })
  }
}
watch([allRoles, selectedRoleIds], applySelection, { flush: 'post' })

watch(
  () => props.visible,
  (v) => {
    if (v) {
      selectedRoleIds.value = []
      assignMode.value = 'REPLACE'
      loadAll()
      loadCurrentRoles()
    }
  },
)

// ============================================================
// 提交
// ============================================================
async function submit() {
  // V4.18: 批量模式必须先选用户, 单用户模式必须选角色
  if (props.mode === 'batch' && (!props.userIds || props.userIds.length === 0)) {
    ElMessage.warning('请先在用户列表勾选要授权的用户 (或在下方目标用户里选人)')
    return
  }
  if (selectedRoleIds.value.length === 0) {
    ElMessage.warning('请至少选择 1 个角色')
    return
  }
  submitting.value = true
  try {
    if (props.mode === 'single' && props.userId) {
      if (assignMode.value === 'REPLACE') {
        await userApi.assignRoles(props.userId, selectedRoleIds.value)
        ElMessage.success(`已更新 ${props.username} 的角色`)
      } else {
        // ADD / REMOVE 用批量端点
        const r = await userApi.batchAssignRoles([props.userId], selectedRoleIds.value, assignMode.value)
        if (r.failed > 0) {
          ElMessage.warning(`失败: ${r.errors[0] ?? '未知'}`)
          return
        }
        ElMessage.success(`已${assignMode.value === 'ADD' ? '追加' : '移除'} ${props.username} 的角色`)
      }
    } else if (props.mode === 'batch' && props.userIds) {
      const r = await userApi.batchAssignRoles(props.userIds, selectedRoleIds.value, assignMode.value)
      const msg = r.failed > 0 ? `成功 ${r.success}, 失败 ${r.failed}` : `成功更新 ${r.success} 个用户的角色`
      if (r.failed > 0) {
        ElMessage.warning(msg + (r.errors[0] ? ` (${r.errors[0]})` : ''))
      } else {
        ElMessage.success(msg)
      }
    }
    emit('saved')
    emit('update:visible', false)
  } catch (e: any) {
    ElMessage.error(e?.message || '授权失败')
  } finally {
    submitting.value = false
  }
}

function cancel() {
  emit('update:visible', false)
}

onMounted(() => {
  if (props.visible) loadAll()
})
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="560px"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
    @close="cancel"
  >
    <el-alert
      v-if="mode === 'batch'"
      :type="userSummaryEmpty ? 'warning' : 'info'"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    >
      <div v-if="userSummaryEmpty" style="color: #e6a23c">
        ⚠️ 目标用户为空: 请先在用户管理列表
        <b>勾选要授权的用户</b>
        , 然后再次打开本弹窗。
      </div>
      <div v-else>
        目标用户:
        <b>{{ userSummary }}</b>
      </div>
    </el-alert>

    <el-form label-width="80px">
      <el-form-item label="操作模式">
        <el-radio-group v-model="assignMode">
          <el-radio value="REPLACE">
            全量替换
            <span style="color: #909399; font-size: 12px">(覆盖现有)</span>
          </el-radio>
          <el-radio value="ADD">
            追加
            <span style="color: #909399; font-size: 12px">(保留现有 + 加新)</span>
          </el-radio>
          <el-radio value="REMOVE">
            移除
            <span style="color: #909399; font-size: 12px">(从现有中删除)</span>
          </el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="选择角色">
        <el-table
          ref="tableRef"
          v-loading="loading"
          :data="allRoles"
          height="320"
          size="small"
          border
          stripe
          :row-key="(row: AssignableRole) => row.id"
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="44" />
          <el-table-column prop="code" label="代码" width="160" />
          <el-table-column prop="name" label="名称" />
          <el-table-column label="内置" width="70" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.builtin" type="warning" size="small">内置</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>

      <el-form-item>
        <span style="color: #909399; font-size: 12px">
          已选
          <b style="color: #409eff">{{ selectedRoleIds.length }}</b>
          / {{ allRoles.length }} 个角色
        </span>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="cancel">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="selectedRoleIds.length === 0 || userSummaryEmpty"
        @click="submit"
      >
        确认授权
      </el-button>
    </template>
  </el-dialog>
</template>
