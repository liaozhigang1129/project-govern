<script setup lang="ts">
/**
 * IM 绑定管理 — V2 前端自助 UI(P2-A 已知限制 #5 修复)
 *
 * 两种视图:
 *  1. 自我服务(默认): 当前用户管理自己的 binding — 创建/编辑/启用暂停/删除
 *  2. 管理员视图: PMO_ADMIN/ADMIN 可切换到任意用户,为他人建 binding
 *
 * 字段说明(显示给用户):
 *  - channel: 平台 (wechat_work / dingtalk / feishu)
 *  - externalUserId: 用户在 IM 平台内的标识
 *    - 企业微信: userid(应用可见范围内的 userid)
 *    - 钉钉: 群机器人 @ 时用不到,这里取手机号或 staff_id
 *    - 飞书: open_id / user_id / email
 *  - enabled: 是否启用推送(暂停 = 离职/换号)
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Connection, Delete, Edit, Plus, Refresh, User } from '@element-plus/icons-vue'
import {
  CHANNEL_LABEL,
  type ImBinding,
  type ImBindingUpdateReq,
  type ImChannel,
  type UserLite,
  imBindingApi,
} from '@/api/im-binding'
import api from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const isAdmin = computed(() => {
  const r = (auth.user as any)?.role
  return r === 'PMO_ADMIN' || r === 'ADMIN'
})

// ---------- 列表 ----------
const list = ref<ImBinding[]>([])
const loading = ref(false)
async function loadList() {
  loading.value = true
  try {
    const params = isAdmin.value && filterUserId.value ? { userId: filterUserId.value } : undefined
    list.value = (await imBindingApi.list(params)) ?? []
  } catch (e: any) {
    ElMessage.error(e.message ?? '加载失败')
  } finally {
    loading.value = false
  }
}

// ---------- 管理员:切换被管理用户 ----------
const filterUserId = ref<number | null>(null)
const users = ref<UserLite[]>([])
async function loadUsers() {
  if (!isAdmin.value) return
  try {
    users.value = await api.get<UserLite[]>('/users/options')
  } catch {
    /* ignore */
  }
}

const currentUserLabel = computed(() => {
  if (isAdmin.value && filterUserId.value) {
    const u = users.value.find((x) => x.id === filterUserId.value)
    return u ? `${u.fullName} (${u.username})` : `userId=${filterUserId.value}`
  }
  return `${auth.user?.fullName ?? ''} (我自己)`
})

// ---------- 弹窗:创建 / 编辑 ----------
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const form = reactive<{
  id: number | null
  userId: number
  channel: ImChannel
  externalUserId: string
  enabled: boolean
}>({
  id: null,
  userId: auth.user?.id ?? 0,
  channel: 'wechat_work',
  externalUserId: '',
  enabled: true,
})

function resetForm() {
  form.id = null
  form.userId = isAdmin.value && filterUserId.value ? filterUserId.value : (auth.user?.id ?? 0)
  form.channel = 'wechat_work'
  form.externalUserId = ''
  form.enabled = true
  formRef.value?.clearValidate()
}

function openCreate() {
  resetForm()
  dialogMode.value = 'create'
  dialogVisible.value = true
}

function openEdit(row: ImBinding) {
  form.id = row.id
  form.userId = row.userId
  form.channel = row.channel
  form.externalUserId = row.externalUserId
  form.enabled = row.enabled
  dialogMode.value = 'edit'
  dialogVisible.value = true
}

const formRef = ref()
const rules = {
  userId: [{ required: true, type: 'number' as const, message: '请选择用户', trigger: 'change' }],
  channel: [{ required: true, message: '请选择平台', trigger: 'change' }],
  externalUserId: [
    { required: true, message: '请填写 IM 标识', trigger: 'blur' },
    { max: 128, message: '最长 128 字符', trigger: 'blur' },
  ],
}

async function submitForm() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    if (dialogMode.value === 'create') {
      await imBindingApi.create({
        userId: form.userId,
        channel: form.channel,
        externalUserId: form.externalUserId,
      })
      ElMessage.success('已创建')
    } else {
      const req: ImBindingUpdateReq = {
        externalUserId: form.externalUserId,
        enabled: form.enabled,
      }
      await imBindingApi.update(form.id!, req)
      ElMessage.success('已保存')
    }
    dialogVisible.value = false
    await loadList()
  } catch (e: any) {
    ElMessage.error(e.message ?? '保存失败')
  } finally {
    saving.value = false
  }
}

// ---------- 启停 / 删除 ----------
const saving = ref(false)
async function toggleEnabled(row: ImBinding) {
  saving.value = true
  try {
    await imBindingApi.update(row.id, { enabled: !row.enabled })
    ElMessage.success(row.enabled ? '已暂停' : '已启用')
    await loadList()
  } catch (e: any) {
    ElMessage.error(e.message ?? '操作失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: ImBinding) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${CHANNEL_LABEL[row.channel]}」绑定吗?删除后将不再推送。`,
      '确认删除',
      { type: 'warning' },
    )
  } catch {
    return
  }
  saving.value = true
  try {
    await imBindingApi.remove(row.id)
    ElMessage.success('已删除')
    await loadList()
  } catch (e: any) {
    ElMessage.error(e.message ?? '删除失败')
  } finally {
    saving.value = false
  }
}

// ---------- 通道说明(给用户的引导) ----------
const channelHint: Record<ImChannel, string> = {
  wechat_work: '填写 企业微信 userid(应用可见范围内)',
  dingtalk: '填写 手机号 或 staff_id(群机器人 @ 时使用)',
  feishu: '填写 open_id / user_id / email 三选一',
}

onMounted(async () => {
  await loadUsers()
  await loadList()
})
</script>

<template>
  <div style="padding: 16px">
    <el-card>
      <template #header>
        <div
          style="
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 12px;
          "
        >
          <span style="display: flex; align-items: center; gap: 6px">
            <el-icon><ChatDotRound /></el-icon>
            IM 绑定管理
            <el-tag type="info" size="small" style="margin-left: 6px">当前: {{ currentUserLabel }}</el-tag>
          </span>
          <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
            <el-select
              v-if="isAdmin"
              v-model="filterUserId"
              placeholder="切换为:全部(自己)"
              clearable
              style="width: 240px"
              @change="loadList"
            >
              <el-option
                v-for="u in users"
                :key="u.id"
                :value="u.id"
                :label="`${u.fullName} (${u.username})`"
              />
            </el-select>
            <el-button :icon="Refresh" @click="loadList">刷新</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新建绑定</el-button>
          </div>
        </div>
      </template>

      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
        <template #title>
          绑定后,审批通知将通过对应 IM 平台实时推送给您。 暂停(
          <el-tag type="warning" size="small">已暂停</el-tag>
          )后仍保留记录,只是不再推送;删除后无法恢复。
        </template>
      </el-alert>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="channel" label="平台" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ CHANNEL_LABEL[row.channel as ImChannel] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="externalUserId" label="IM 标识" min-width="200">
          <template #default="{ row }">
            <code style="background: #f5f7fa; padding: 2px 6px; border-radius: 3px">
              {{ row.externalUserId }}
            </code>
          </template>
        </el-table-column>
        <el-table-column v-if="isAdmin" prop="userId" label="所属用户" width="140">
          <template #default="{ row }">
            <span style="display: flex; align-items: center; gap: 4px">
              <el-icon><User /></el-icon>
              {{ users.find((u) => u.id === row.userId)?.fullName ?? row.userId }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.enabled" type="success" size="small">启用中</el-tag>
            <el-tag v-else type="warning" size="small">已暂停</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180">
          <template #default="{ row }">
            <span style="color: #909399">{{ row.updatedAt?.slice(0, 19).replace('T', ' ') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button :icon="Edit" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              :icon="Connection"
              link
              :type="row.enabled ? 'warning' : 'success'"
              :loading="saving"
              @click="toggleEnabled(row)"
            >
              {{ row.enabled ? '暂停' : '启用' }}
            </el-button>
            <el-button :icon="Delete" link type="danger" :loading="saving" @click="remove(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无绑定。点击右上角「新建绑定」开始。">
            <el-button type="primary" :icon="Plus" @click="openCreate">新建绑定</el-button>
          </el-empty>
        </template>
      </el-table>
    </el-card>

    <!-- 创建/编辑 弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建 IM 绑定' : '编辑 IM 绑定'"
      width="520px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item v-if="isAdmin" label="所属用户" prop="userId">
          <el-select v-model="form.userId" placeholder="选择用户" style="width: 100%">
            <el-option
              v-for="u in users"
              :key="u.id"
              :value="u.id"
              :label="`${u.fullName} (${u.username})`"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="所属用户">
          <el-input :value="auth.user?.fullName" disabled />
        </el-form-item>
        <el-form-item label="平台" prop="channel">
          <el-radio-group v-model="form.channel" :disabled="dialogMode === 'edit'">
            <el-radio-button value="wechat_work">企业微信</el-radio-button>
            <el-radio-button value="dingtalk">钉钉</el-radio-button>
            <el-radio-button value="feishu">飞书</el-radio-button>
          </el-radio-group>
          <div style="color: #909399; font-size: 12px; margin-top: 4px">
            {{ channelHint[form.channel] }}
          </div>
        </el-form-item>
        <el-form-item label="IM 标识" prop="externalUserId">
          <el-input
            v-model="form.externalUserId"
            placeholder="例如:zhangsan / 13800138000 / ou_xxx"
            maxlength="128"
            show-word-limit
          />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'edit'" label="启用">
          <el-switch v-model="form.enabled" />
          <span style="color: #909399; font-size: 12px; margin-left: 8px">关闭后不再推送通知,但保留记录</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
:deep(.el-card__header) {
  padding: 12px 16px;
}
:deep(.el-card__body) {
  padding: 16px;
}
</style>
