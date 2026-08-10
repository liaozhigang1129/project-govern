<script setup lang="ts">
/**
 * L1-1 用户管理 — 列表页
 * 11 个端点全部接入, 行内操作: 启停/解锁/重置密码/离职
 * 仅 PMO_ADMIN / ADMIN 可见
 */
import { onMounted, reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  userApi,
  type UserListItem,
  type UserQuery,
  type PageResult,
  type RoleRef,
  type DepartmentRef,
} from '@/api/users'
import { roleApi, type RoleOption } from '@/api/roles'
import { departmentApi, type DepartmentOption } from '@/api/departments'
import { Connection, Download } from '@element-plus/icons-vue'
import api from '@/api/client'
import UserOrgView from './UserOrgView.vue'
import UserRoleAssignDialog from '@/components/admin/UserRoleAssignDialog.vue'

// ============================================================
// 状态
// ============================================================
const loading = ref(false)
const syncLoading = ref(false)
const router = useRouter()
const syncResult = ref<{
  type: 'success' | 'warning' | 'error' | 'info'
  title: string
  desc: string
  logId?: number
  viewUrl?: string
  viewLabel?: string
} | null>(null)
let syncPollId: number | null = null
const data = ref<PageResult<UserListItem> | null>(null)
const query = reactive<UserQuery>({
  keyword: undefined,
  roleCode: undefined,
  departmentId: undefined,
  enabled: undefined,
  locked: undefined,
  page: 0,
  size: 20,
  sort: 'createdAt,desc',
})

// 视图模式: 'list' (默认按筛选) | 'org' (按组织, 双栏)
const viewMode = ref<'list' | 'org'>('list')

// 字典
const roleList = ref<RoleRef[]>([])
const deptList = ref<DepartmentRef[]>([])

// 选中的行 (V4.19: 用于批量授权, 跨页保留)
// items.value 包含当前页 20 条
// selectedIds 含所有已勾选用户 id (跨页累计)
const selectedIds = ref<number[]>([])
const tableRef = ref() // el-table ref, 用于跨页勾选同步

// 切换整页勾选 (工具栏"全选当前页" / "全选全部" / "清空")
async function toggleAllOnPage(on: boolean) {
  if (!tableRef.value) return
  items.value.forEach((row: any) => {
    if (on) {
      if (!selectedIds.value.includes(row.id)) {
        tableRef.value.toggleRowSelection(row, true)
        selectedIds.value.push(row.id)
      }
    } else {
      tableRef.value.toggleRowSelection(row, false)
    }
  })
  if (!on) {
    selectedIds.value = selectedIds.value.filter((id) => !items.value.some((u: any) => u.id === id))
  }
}

async function selectAllMatched() {
  // 1) 拉满 1000 条 (后端 size 上限)
  try {
    loading.value = true
    const big = await userApi.list({ ...query, page: 0, size: 1000 })
    const all = (big?.content ?? []) as any[]
    // 2) 写 selectedIds
    const existing = new Set(selectedIds.value)
    all.forEach((u) => existing.add(u.id))
    selectedIds.value = Array.from(existing)
    // 3) 让 el-table 同步显示 (当前页)
    await toggleAllOnPage(true)
    ElMessage.success(`已选中 ${all.length} 个匹配用户 (累计 ${selectedIds.value.length})`)
  } catch (e: any) {
    ElMessage.error(e?.message ?? '全选失败')
  } finally {
    loading.value = false
  }
}

function clearSelection() {
  selectedIds.value = []
  tableRef.value?.clearSelection()
}

// 对话框 — 重置密码
const pwdDialog = ref({
  visible: false,
  userId: 0 as number,
  username: '',
  newPassword: '',
  mustChange: true,
  notifyEmail: true,
  submitting: false,
})
// 生成合规随机密码 (10 位, 包含大小写+数字)
function generatePassword() {
  const up = 'ABCDEFGHJKLMNPQRSTUVWXYZ'
  const lo = 'abcdefghjkmnpqrstuvwxyz'
  const di = '23456789'
  const all = up + lo + di
  let s =
    up[Math.floor(Math.random() * up.length)] +
    lo[Math.floor(Math.random() * lo.length)] +
    di[Math.floor(Math.random() * di.length)]
  for (let i = 3; i < 12; i++) s += all[Math.floor(Math.random() * all.length)]
  return s
    .split('')
    .sort(() => Math.random() - 0.5)
    .join('')
}

// 对话框 — 角色授权 (V4.16)
const roleDialog = ref({
  visible: false,
  mode: 'single' as 'single' | 'batch',
  userId: 0 as number,
  username: '',
  userIds: [] as number[],
  userLabels: [] as string[],
})
function openAssignRole(row: UserListItem) {
  roleDialog.value = {
    visible: true,
    mode: 'single',
    userId: row.id,
    username: row.username,
    userIds: [],
    userLabels: [],
  }
}
function openBatchAssignRole() {
  // V4.18: 允许不预选用户直接打开批量授权弹窗
  // 0 人时弹窗仍可打开, 但在弹窗内禁用确认按钮 + 提示 (由 UserRoleAssignDialog 处理)
  const sel = items.value.filter((u: any) => selectedIds.value.includes(u.id))
  roleDialog.value = {
    visible: true,
    mode: 'batch',
    userId: 0,
    username: '',
    userIds: sel.map((u: any) => u.id),
    userLabels: sel.map((u: any) => `${u.fullName} (${u.username})`),
  }
  if (sel.length === 0) {
    // 静默打开, 不再 ElMessage.warning 弹窗阻挡
  }
}
function onRoleSaved() {
  load() // 重新加载列表
}

// 对话框 — 离职
const offboardDialog = ref({
  visible: false,
  userId: 0 as number,
  username: '',
  transferTo: undefined as number | undefined,
  reason: '',
  submitting: false,
})
const transferOptions = ref<{ id: number; fullName: string; username: string }[]>([])

// 角色选项: 真正用 /roles 接口
const roleOptions = ref<RoleOption[]>([])

// ============================================================
// 加载
// ============================================================
async function loadDicts() {
  try {
    const [roles, depts] = await Promise.all([
      // 真正用 /roles 接口拿 (L1-2 角色管理新增)
      roleApi
        .list(true)
        .then((list) => list.map((r) => ({ id: r.id, code: r.code, name: r.name }) as RoleRef)),
      // 真正用 /departments 接口拿 (L1-3 部门管理新增)
      departmentApi.options().then((list) => list.map((d) => ({ id: d.id, name: d.name }) as DepartmentRef)),
    ])
    roleList.value = roles ?? []
    deptList.value = depts ?? []
  } catch {
    // 静默
  }
}

async function load() {
  loading.value = true
  try {
    data.value = await userApi.list(query)
    selectedIds.value = []
  } catch (e: any) {
    ElMessage.error(e?.message ?? '加载用户列表失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 0
  load()
}

function onReset() {
  query.keyword = undefined
  query.roleCode = undefined
  query.departmentId = undefined
  query.enabled = undefined
  query.locked = undefined
  query.page = 0
  load()
}
// ============================================================
// 同步钉钉通讯录
// ============================================================
async function onSyncDingTalk() {
  if (syncLoading.value) return
  syncLoading.value = true
  syncResult.value = { type: 'info', title: '同步中…', desc: '正在从钉钉拉取通讯录,请稍候(预计 5-30s)' }
  try {
    const r: any = await api.post('/admin/dingtalk/sync/trigger')
    const log = r.data || r
    // 后端 trigger 已改为异步, 立即返回 RUNNING. 需要轮询 logs 拿到最终结果
    const finalLog = await pollSyncResult(log?.id)
    showSyncResult(finalLog)
    await load()
  } catch (e: any) {
    syncResult.value = {
      type: 'error',
      title: '同步失败',
      desc: e?.message || '未知错误,请查看后端日志',
    }
  } finally {
    syncLoading.value = false
  }
}

// ============================================================
// 导出 Excel (V4.36) — 与当前筛选条件一致
//   走 /users/export.xlsx, 后端返回二进制流, 浏览器直接保存
// ============================================================
const exportLoading = ref(false)

async function onExportExcel() {
  if (exportLoading.value) return
  exportLoading.value = true
  try {
    // 1) 携带当前 query (keyword/roleCode/departmentId/enabled)
    const params: Record<string, string> = {}
    if (query.keyword) params.keyword = query.keyword
    if (query.departmentId) params.departmentId = String(query.departmentId)
    if (query.roleCode) params.roleCode = query.roleCode
    params.enabled = query.enabled === undefined ? 'true' : String(query.enabled)
    // 2) 后端 axios 拦截器会自动加 Authorization + 解包 ApiResponse;
    //    但 xlsx 是二进制流 (无 ApiResponse 包装), 所以这里用 fetch 拿原始 blob
    const tok = localStorage.getItem('token')
    const qs = new URLSearchParams(params).toString()
    const res = await fetch('/api/users/export.xlsx?' + qs, {
      headers: tok ? { Authorization: `Bearer ${tok}` } : {},
    })
    if (!res.ok) {
      const txt = await res.text().catch(() => '')
      throw new Error(`HTTP ${res.status}: ${txt.slice(0, 200)}`)
    }
    // 3) 解析文件名 (优先后端 Content-Disposition, 否则按时间生成)
    const cd = res.headers.get('Content-Disposition') || ''
    let filename = 'users.xlsx'
    const m = cd.match(/filename="?([^"]+)"?/)
    if (m) filename = m[1]
    // 4) 下载
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${Math.round(blob.size / 1024)} KB, 共 ${blob.size > 0 ? 'N/A' : '0'} 行`)
  } catch (e: any) {
    ElMessage.error(e?.message ?? '导出失败')
  } finally {
    exportLoading.value = false
  }
}

/**
 * 轮询同步日志,直到 RUNNING 状态结束
 * @param logId 触发后返回的日志 ID
 * @param maxAttempts 最大轮询次数 (默认 30 次 × 1s = 30s)
 */
async function pollSyncResult(logId: number | undefined, maxAttempts = 30): Promise<any> {
  if (!logId) return null
  // 30 次 × 1s = 30s 内拿不到结果, 给用户一个直达链接而不是原文案死胡同
  syncResult.value = {
    type: 'info',
    title: '同步中…',
    desc: `正在拉取钉钉通讯录(0/${maxAttempts}s)`,
  }
  for (let i = 0; i < maxAttempts; i++) {
    await new Promise((r) => setTimeout(r, 1000))
    syncResult.value = {
      type: 'info',
      title: '同步中…',
      desc: `正在拉取钉钉通讯录(${i + 1}/${maxAttempts}s)`,
    }
    try {
      const res: any = await api.get('/admin/dingtalk/sync/logs', { params: { page: 0, size: 5 } })
      const logs: any[] = res.data?.content ?? res.data ?? res ?? []
      const log = Array.isArray(logs) ? logs.find((l: any) => l.id === logId) : null
      if (log && log.status !== 'RUNNING') {
        return log
      }
    } catch (e) {
      // 网络错误继续重试
    }
  }
  // 轮询超时: 把 logId 透传给同步日志页, 用户可直接跳过去看最终状态
  return {
    id: logId,
    status: 'TIMEOUT',
    errorMessage: '轮询超时, 后台同步可能仍在执行, 请到 "系统管理 → 同步日志" 查看最终结果',
  }
}

function showSyncResult(log: any) {
  if (!log) return
  const ok = log.status === 'SUCCESS'
  const isMock = log.totalDepts === 0 && log.totalUsers === 0
  if (log.status === 'TIMEOUT') {
    syncResult.value = {
      type: 'warning',
      title: '同步进行中,请稍候',
      desc: log.errorMessage || '后台同步仍在执行,请到 "系统管理 → 同步日志" 查看最终结果',
      logId: log.id,
      viewUrl: `/admin/dingtalk-sync-log?logId=${log.id}`,
      viewLabel: '打开同步日志',
    }
    return
  }
  if (!ok) {
    syncResult.value = {
      type: 'error',
      title: `同步失败: ${log.status}`,
      desc: log.errorMessage || '未知错误',
      logId: log.id,
      viewUrl: `/admin/dingtalk-sync-log?logId=${log.id}`,
      viewLabel: '打开同步日志',
    }
    return
  }
  // 检测"部门成功但用户为 0"的情形 — 通常是钉钉应用权限不足
  const noUser = log.totalDepts > 0 && log.totalUsers === 0
  if (noUser) {
    syncResult.value = {
      type: 'warning',
      title: `同步完成: ${log.totalDepts ?? 0} 部门,但未同步用户 (0)`,
      desc: '可能原因: 钉钉应用未开通 [qyapi_get_department_member] 权限。\n请到钉钉开放平台 → 应用权限 → 申请该权限,审批通过后再次同步。',
      logId: log.id,
      viewUrl: `/admin/dingtalk-sync-log?logId=${log.id}`,
      viewLabel: '打开同步日志',
    }
    return
  }
  syncResult.value = {
    type: isMock ? 'warning' : 'success',
    title: isMock
      ? '同步完成(空数据,未配置钉钉 app_key/secret)'
      : `同步成功: ${log.totalDepts ?? 0} 部门,${log.totalUsers ?? 0} 用户,${log.disabledUsers ?? 0} 离职禁用`,
    desc: `ID=${log.id}  类型=${log.syncType}  耗时 ${log.durationMs ?? 0}ms  启动时间 ${new Date(log.startedAt).toLocaleString()}`,
    logId: log.id,
    viewUrl: `/admin/dingtalk-sync-log?logId=${log.id}`,
    viewLabel: '打开同步日志',
  }
}

/**
 * 跳到同步日志页(自动带上 logId, 在 DingTalkSyncLog.vue 内高亮该行)
 */
function openSyncLog(s: any) {
  if (!s?.viewUrl) return
  router.push(s.viewUrl)
}

function onPageChange(p: number) {
  query.page = p - 1
  load()
}

function onSizeChange(s: number) {
  query.size = s
  query.page = 0
  load()
}

const total = computed(() => data.value?.totalElements ?? 0)
const items = computed(() => data.value?.content ?? [])

// ============================================================
// 行内操作
// ============================================================
async function toggleEnabled(row: UserListItem) {
  const op = row.enabled ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(`确认${op}用户 “${row.fullName}” (${row.username})?`, `${op}确认`, {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await userApi.setEnabled(row.id, !row.enabled)
    ElMessage.success(`${op}成功`)
    load()
  } catch (e: any) {
    ElMessage.error(e?.message ?? `${op}失败`)
  }
}

async function onUnlock(row: UserListItem) {
  try {
    await userApi.unlock(row.id)
    ElMessage.success(`已解锁 ${row.fullName}`)
    load()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '解锁失败')
  }
}

function openResetPassword(row: UserListItem) {
  pwdDialog.value = {
    visible: true,
    userId: row.id,
    username: row.username,
    newPassword: generatePassword(),
    mustChange: true,
    notifyEmail: true,
    submitting: false,
  }
}

async function submitResetPassword() {
  if (pwdDialog.value.newPassword.length < 10) {
    ElMessage.warning('新密码至少 10 位')
    return
  }
  pwdDialog.value.submitting = true
  try {
    await userApi.resetPassword(pwdDialog.value.userId, pwdDialog.value.newPassword, {
      mustChangeOnNextLogin: pwdDialog.value.mustChange,
      notifyByEmail: pwdDialog.value.notifyEmail,
    })
    ElMessage.success(`密码已重置 (新密码已复制到剪贴板): ${pwdDialog.value.newPassword}`)
    try {
      await navigator.clipboard.writeText(pwdDialog.value.newPassword)
    } catch {
      /* 用户取消授权,降级为手动复制 */
    }
    pwdDialog.value.visible = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '重置失败')
  } finally {
    pwdDialog.value.submitting = false
  }
}

async function copyPwd() {
  try {
    await navigator.clipboard.writeText(pwdDialog.value.newPassword)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败,请手动选中')
  }
}

async function openOffboard(row: UserListItem) {
  offboardDialog.value = {
    visible: true,
    userId: row.id,
    username: row.username,
    transferTo: undefined,
    reason: '',
    submitting: false,
  }
  try {
    transferOptions.value = await userApi.options()
  } catch {
    /* 用户取消授权,降级为手动复制 */
  }
}

async function submitOffboard() {
  if (!offboardDialog.value.reason.trim()) {
    ElMessage.warning('请填写离职原因')
    return
  }
  offboardDialog.value.submitting = true
  try {
    await userApi.offboard(
      offboardDialog.value.userId,
      offboardDialog.value.transferTo ?? null,
      offboardDialog.value.reason,
    )
    ElMessage.success('已离职')
    offboardDialog.value.visible = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '离职失败')
  } finally {
    offboardDialog.value.submitting = false
  }
}

// ============================================================
// 工具
// ============================================================
function fmtTime(s: string | null) {
  if (!s) return '-'
  return new Date(s).toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  loadDicts()
  load()
})
</script>

<template>
  <div class="page">
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>👥 用户管理 (L1-1)</span>
          <div style="display: flex; align-items: center; gap: 16px">
            <el-radio-group v-model="viewMode" size="small">
              <el-radio-button label="list">📋 按筛选</el-radio-button>
              <el-radio-button label="org">🏢 按组织</el-radio-button>
            </el-radio-group>
            <span style="color: #909399; font-size: 12px">共 {{ total }} 个用户</span>
          </div>
        </div>
      </template>

      <!-- ============= 视图 1: 按筛选 ============= -->
      <template v-if="viewMode === 'list'">
        <!-- 搜索栏 -->
        <el-form :inline="true" :model="query" @submit.prevent>
          <el-form-item label="关键字">
            <el-input
              v-model="query.keyword"
              placeholder="账号/姓名/手机"
              clearable
              style="width: 180px"
              @keyup.enter="onSearch"
            />
          </el-form-item>
          <el-form-item label="主角色">
            <el-select v-model="query.roleCode" placeholder="全部" clearable style="width: 140px">
              <el-option v-for="r in roleList" :key="r.code" :label="r.name" :value="r.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="部门">
            <el-select v-model="query.departmentId" placeholder="全部" clearable style="width: 160px">
              <el-option v-for="d in deptList" :key="d.id" :label="d.name" :value="d.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.enabled" placeholder="全部" clearable style="width: 110px">
              <el-option :value="true" label="启用" />
              <el-option :value="false" label="停用" />
            </el-select>
          </el-form-item>
          <el-form-item label="锁定">
            <el-select v-model="query.locked" placeholder="全部" clearable style="width: 110px">
              <el-option :value="true" label="已锁定" />
              <el-option :value="false" label="未锁定" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="'Search'" @click="onSearch">查询</el-button>
            <el-button @click="onReset">重置</el-button>
            <el-button
              type="success"
              :loading="syncLoading"
              :icon="'Connection'"
              @click="onSyncDingTalk"
              style="margin-left: 8px"
            >
              同步钉钉
            </el-button>
            <el-button
              type="primary"
              :loading="exportLoading"
              :icon="'Download'"
              @click="onExportExcel"
              style="margin-left: 8px"
              data-testid="users-export-btn"
            >
              导出 Excel
            </el-button>
            <el-button type="warning" style="margin-left: 8px" @click="openBatchAssignRole">
              批量授权角色
              <el-badge v-if="selectedIds.length > 0" :value="selectedIds.length" style="margin-left: 4px" />
            </el-button>
            <!-- V4.19: 批量勾选工具 (按当前搜索条件全选) -->
            <el-dropdown
              style="margin-left: 8px"
              @command="
                (c: string) => {
                  if (c === 'page') toggleAllOnPage(true)
                  else if (c === 'clear') clearSelection()
                  else if (c === 'all') selectAllMatched()
                }
              "
            >
              <el-button>
                批量勾选
                <el-icon class="el-icon--right"><CaretBottom /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="page">全选当前页 ({{ items.length }})</el-dropdown-item>
                  <el-dropdown-item command="all" :disabled="loading">
                    全选所有匹配 (按当前筛选, 最多 1000)
                  </el-dropdown-item>
                  <el-dropdown-item command="clear" divided :disabled="selectedIds.length === 0">
                    清空勾选 ({{ selectedIds.length }})
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </el-form-item>
        </el-form>

        <!-- 同步进度 / 最近一次结果 -->
        <el-alert
          v-if="syncResult"
          :type="syncResult.type"
          :title="syncResult.title"
          :description="syncResult.desc"
          show-icon
          :closable="true"
          @close="syncResult = null"
          style="margin-bottom: 12px"
        >
          <template #default v-if="syncResult.viewUrl">
            <el-button size="small" type="primary" link @click="openSyncLog(syncResult)">
              {{ syncResult.viewLabel || '打开同步日志' }}
            </el-button>
          </template>
        </el-alert>

        <!-- 表格 (V4.19: 复选框列 + 跨页累计) -->
        <el-table
          ref="tableRef"
          v-loading="loading"
          :data="items"
          border
          stripe
          row-key="id"
          style="width: 100%"
          empty-text="无用户"
          @selection-change="(rows: any[]) => (selectedIds = rows.map((r) => r.id))"
        >
          <el-table-column type="selection" width="44" :reserve-selection="true" />
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="username" label="账号" min-width="100" />
          <el-table-column prop="fullName" label="姓名" min-width="100" />
          <el-table-column label="角色" min-width="180">
            <template #default="{ row }">
              <el-tooltip
                v-if="row.roleCodes?.length"
                :content="`已分配 ${row.roleCodes.length} 个角色: ${row.roleCodes.join(', ')}`"
                placement="top"
              >
                <el-tag size="small" type="primary">
                  {{ row.primaryRoleName || row.primaryRoleCode || '—' }}
                </el-tag>
                <el-tag
                  v-for="code in (row.roleCodes || []).filter((c: string) => c !== row.primaryRoleCode)"
                  :key="code"
                  size="small"
                  type="info"
                  effect="plain"
                  style="margin-left: 4px"
                >
                  {{ code }}
                </el-tag>
              </el-tooltip>
              <el-tag v-else size="small" type="info" effect="plain">未分配</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="部门" min-width="200">
            <template #default="{ row }">
              <div v-if="row.departmentName" style="line-height: 1.3">
                <div style="font-weight: 500">{{ row.departmentName }}</div>
                <div
                  v-if="row.departmentPath && row.departmentPath !== row.departmentName"
                  style="font-size: 11px; color: #909399; margin-top: 2px"
                  :title="row.departmentPath"
                >
                  {{ row.departmentPath }}
                </div>
              </div>
              <span v-else style="color: #c0c4cc">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="phone" label="手机" min-width="120" />
          <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.enabled" type="success" size="small">启用</el-tag>
              <el-tag v-else type="info" size="small">停用</el-tag>
              <el-tag v-if="row.locked" type="danger" size="small" style="margin-left: 4px">
                🔒 {{ row.loginFailCount }}次
              </el-tag>
              <el-tag v-if="row.mustChangePassword" type="warning" size="small" style="margin-left: 4px">
                需改密
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最后登录" min-width="150">
            <template #default="{ row }">
              <div style="font-size: 12px">{{ fmtTime(row.lastLoginAt) }}</div>
              <div style="font-size: 11px; color: #909399">{{ row.lastLoginIp ?? '' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="320" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="openAssignRole(row)">授权角色</el-button>
              <el-button v-if="row.locked" size="small" link type="warning" @click="onUnlock(row)">
                解锁
              </el-button>
              <el-button
                size="small"
                link
                :type="row.enabled ? 'danger' : 'success'"
                @click="toggleEnabled(row)"
              >
                {{ row.enabled ? '停用' : '启用' }}
              </el-button>
              <el-button size="small" link type="primary" @click="openResetPassword(row)">重置密码</el-button>
              <el-button v-if="row.enabled" size="small" link type="danger" @click="openOffboard(row)">
                离职
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div style="display: flex; justify-content: flex-end; margin-top: 16px">
          <el-pagination
            :current-page="(query.page ?? 0) + 1"
            :page-size="query.size ?? 20"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="onPageChange"
            @size-change="onSizeChange"
          />
        </div>
      </template>

      <!-- 视图: 按组织 (双栏, 拖拽分配) -->
      <UserOrgView v-else />
    </el-card>

    <!-- 重置密码对话框 -->
    <el-dialog v-model="pwdDialog.visible" :title="`重置密码 — ${pwdDialog.username}`" width="480px">
      <el-form label-width="100px">
        <el-form-item label="新密码">
          <el-input v-model="pwdDialog.newPassword" show-password />
        </el-form-item>
        <el-form-item>
          <el-button size="small" @click="pwdDialog.newPassword = generatePassword()">🎲 重新生成</el-button>
          <el-button size="small" @click="copyPwd">📋 复制</el-button>
        </el-form-item>
        <el-form-item label="下次登录">
          <el-switch v-model="pwdDialog.mustChange" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">强制用户下次登录后必须改密</span>
        </el-form-item>
        <el-form-item label="邮件通知">
          <el-switch v-model="pwdDialog.notifyEmail" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">把新密码发到用户邮箱</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="pwdDialog.submitting" @click="submitResetPassword">
          确认重置
        </el-button>
      </template>
    </el-dialog>

    <!-- 离职对话框 -->
    <el-dialog v-model="offboardDialog.visible" :title="`离职 — ${offboardDialog.username}`" width="520px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px">
        离职后账号将立即被停用并踢下线。其名下的项目 / WBS 任务可交接给其他用户。
      </el-alert>
      <el-form label-width="100px">
        <el-form-item label="交接给">
          <el-select
            v-model="offboardDialog.transferTo"
            placeholder="不交接,清空引用"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="u in transferOptions.filter((o) => o.id !== offboardDialog.userId)"
              :key="u.id"
              :label="`${u.fullName} (${u.username})`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="离职原因" required>
          <el-input
            v-model="offboardDialog.reason"
            type="textarea"
            :rows="3"
            placeholder="例: 个人原因离职 / 调岗 / 合同到期..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="offboardDialog.visible = false">取消</el-button>
        <el-button type="danger" :loading="offboardDialog.submitting" @click="submitOffboard">
          确认离职
        </el-button>
      </template>
    </el-dialog>

    <!-- V4.16: 角色授权弹窗 (单 / 批量通用) -->
    <UserRoleAssignDialog
      v-model:visible="roleDialog.visible"
      :mode="roleDialog.mode"
      :user-id="roleDialog.userId"
      :username="roleDialog.username"
      :user-ids="roleDialog.userIds"
      :user-labels="roleDialog.userLabels"
      @saved="onRoleSaved"
    />
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
</style>
