<script setup lang="ts">
/**
 * DND 勿扰时段管理 — V2 前端自助 UI(P2 #2)。
 *
 * 业务:
 *  - 每用户多窗口(午餐 + 深夜)
 *  - HH:mm 24h;end < start 自动视为跨午夜(例 22:00 ~ 08:00)
 *  - 命中启用窗口 → IM 通道跳过(邮件仍走)
 *  - 暂停窗口 = 临时关闭(保留时段定义)
 *  - 删除 = 硬删(不可恢复)
 *
 * 与 P2-D ImBindings 风格保持一致;但 DND 只有"自己的",不暴露 admin 切换
 *  —— admin 想代配也支持(本版本开放 list?userId=)
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, BellFilled, Clock, Delete, Edit, Moon, Plus, Refresh } from '@element-plus/icons-vue'
import {
  type ImQuietHours,
  type ImQuietHoursUpdateReq,
  imQuietHoursApi,
} from '@/api/im-quiet-hours'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

// ---------- 列表 ----------
const list = ref<ImQuietHours[]>([])
const loading = ref(false)
async function loadList() {
  loading.value = true
  try {
    const myId = auth.user?.id
    if (!myId) {
      list.value = []
      return
    }
    list.value = (await imQuietHoursApi.list(myId)) ?? []
  } catch (e: any) {
    ElMessage.error(e.message ?? '加载失败')
  } finally {
    loading.value = false
  }
}

// ---------- 状态总览(顶部卡片) ----------
const now = ref(new Date())
let ticker: number | null = null
onMounted(() => {
  // 每 30s 刷新一次顶部状态卡
  ticker = window.setInterval(() => (now.value = new Date()), 30_000)
})

/** 当前是否在任一启用窗口内(本地估算 — 不影响后端权威) */
const isInQuietNow = computed(() => {
  const hhmm = now.value.toTimeString().slice(0, 5) // "HH:mm:ss" → "HH:mm"
  return list.value.some((w) => w.enabled && inWindow(hhmm, w.startTime, w.endTime))
})

/** 估算下一个窗口起止 */
const nextWindow = computed(() => {
  const enabled = list.value.filter((w) => w.enabled)
  if (enabled.length === 0) return null
  const hhmm = now.value.toTimeString().slice(0, 5)
  const sorted = [...enabled].sort((a, b) => a.startTime.localeCompare(b.startTime))
  const cur = sorted.find((w) => inWindow(hhmm, w.startTime, w.endTime))
  if (cur) return { state: 'active' as const, label: `${cur.startTime} ~ ${cur.endTime}` }
  const future = sorted.find((w) => w.startTime > hhmm)
  return { state: 'idle' as const, label: future ? `下一个 ${future.startTime} ~ ${future.endTime}` : '今日无' }
})

/** 单点判定,跨午夜支持 */
function inWindow(nowT: string, start: string, end: string): boolean {
  if (start === end) return false
  if (start < end) return nowT >= start && nowT <= end
  return nowT >= start || nowT <= end // 跨午夜
}

// ---------- 弹窗:创建 / 编辑 ----------
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const form = reactive<{
  id: number | null
  startTime: string
  endTime: string
  enabled: boolean
}>({
  id: null,
  startTime: '22:00',
  endTime: '08:00',
  enabled: true,
})

function resetForm() {
  form.id = null
  form.startTime = '22:00'
  form.endTime = '08:00'
  form.enabled = true
  formRef.value?.clearValidate()
}

function openCreate() {
  resetForm()
  dialogMode.value = 'create'
  dialogVisible.value = true
}

function openEdit(row: ImQuietHours) {
  form.id = row.id
  form.startTime = row.startTime
  form.endTime = row.endTime
  form.enabled = row.enabled
  dialogMode.value = 'edit'
  dialogVisible.value = true
}

const formRef = ref()
const rules = {
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
}

const saving = ref(false)
async function submitForm() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const myId = auth.user?.id
    if (!myId) {
      ElMessage.error('未登录')
      return
    }
    if (dialogMode.value === 'create') {
      await imQuietHoursApi.create({
        userId: myId,
        startTime: form.startTime,
        endTime: form.endTime,
      })
      ElMessage.success('已创建')
    } else {
      const req: ImQuietHoursUpdateReq = {
        startTime: form.startTime,
        endTime: form.endTime,
        enabled: form.enabled,
      }
      await imQuietHoursApi.update(form.id!, req)
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
async function toggleEnabled(row: ImQuietHours) {
  saving.value = true
  try {
    await imQuietHoursApi.update(row.id, { enabled: !row.enabled })
    ElMessage.success(row.enabled ? '已暂停' : '已启用')
    await loadList()
  } catch (e: any) {
    ElMessage.error(e.message ?? '操作失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: ImQuietHours) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.startTime} ~ ${row.endTime}」时段吗?删除后该窗口不再生效。`,
      '确认删除',
      { type: 'warning' }
    )
  } catch {
    return
  }
  saving.value = true
  try {
    await imQuietHoursApi.remove(row.id)
    ElMessage.success('已删除')
    await loadList()
  } catch (e: any) {
    ElMessage.error(e.message ?? '删除失败')
  } finally {
    saving.value = false
  }
}

// ---------- 渲染辅助 ----------
function describeWindow(start: string, end: string): string {
  if (start === end) return '无效(起止相同)'
  if (start < end) return `当日 ${start} → ${end}`
  return `跨午夜 ${start} → 次日 ${end}`
}

function isCrossNight(start: string, end: string): boolean {
  return start >= end
}
</script>

<template>
  <div style="padding: 16px">
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px">
          <span style="display: flex; align-items: center; gap: 6px">
            <el-icon><Moon /></el-icon>
            勿扰时段管理
            <el-tag type="info" size="small" style="margin-left: 6px">
              仅影响 IM 推送(邮件仍走)
            </el-tag>
          </span>
          <div style="display: flex; gap: 8px; align-items: center">
            <el-button :icon="Refresh" @click="loadList">刷新</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新建时段</el-button>
          </div>
        </div>
      </template>

      <!-- 状态总览 -->
      <el-row :gutter="12" style="margin-bottom: 16px">
        <el-col :span="12">
          <el-card shadow="never" :body-style="{ padding: '14px 18px' }">
            <div style="display: flex; align-items: center; gap: 12px">
              <el-icon :size="28" :color="isInQuietNow ? '#e6a23c' : '#67c23a'">
                <component :is="isInQuietNow ? BellFilled : Bell" />
              </el-icon>
              <div>
                <div style="font-size: 13px; color: #909399">当前状态</div>
                <div style="font-size: 18px; font-weight: 600">
                  {{ isInQuietNow ? '勿扰中' : '可推送' }}
                </div>
                <div style="font-size: 12px; color: #909399; margin-top: 2px">
                  {{ nextWindow?.label ?? '未配置时段' }}
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" :body-style="{ padding: '14px 18px' }">
            <div style="display: flex; align-items: center; gap: 12px">
              <el-icon :size="28" color="#909399"><Clock /></el-icon>
              <div>
                <div style="font-size: 13px; color: #909399">本地时间</div>
                <div style="font-size: 18px; font-weight: 600">
                  {{ now.toTimeString().slice(0, 8) }}
                </div>
                <div style="font-size: 12px; color: #909399; margin-top: 2px">
                  Asia/Shanghai
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      >
        <template #title>
          命中任一<strong>启用中</strong>时段, IM 通道将<strong>不推送</strong>通知(邮件、站内信仍照常)— 防止深夜/午休被打扰。
        </template>
      </el-alert>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column label="时段" min-width="220">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 8px">
              <el-tag size="small" :type="isCrossNight(row.startTime, row.endTime) ? 'warning' : 'info'">
                {{ row.startTime }} ~ {{ row.endTime }}
              </el-tag>
              <span style="color: #909399; font-size: 12px">
                {{ describeWindow(row.startTime, row.endTime) }}
              </span>
            </div>
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
              :icon="row.enabled ? 'BellFilled' : 'Bell'"
              link
              :type="row.enabled ? 'warning' : 'success'"
              :loading="saving"
              @click="toggleEnabled(row)"
            >
              {{ row.enabled ? '暂停' : '启用' }}
            </el-button>
            <el-button :icon="Delete" link type="danger" :loading="saving" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="尚未配置勿扰时段,IM 通知将 7×24 推送。">
            <el-button type="primary" :icon="Plus" @click="openCreate">新建时段</el-button>
          </el-empty>
        </template>
      </el-table>
    </el-card>

    <!-- 创建/编辑 弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建勿扰时段' : '编辑勿扰时段'"
      width="480px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="开始时间" prop="startTime">
          <el-time-picker
            v-model="form.startTime"
            format="HH:mm"
            value-format="HH:mm"
            placeholder="选择开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-time-picker
            v-model="form.endTime"
            format="HH:mm"
            value-format="HH:mm"
            placeholder="选择结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-alert
          v-if="form.startTime && form.endTime"
          :type="isCrossNight(form.startTime, form.endTime) ? 'warning' : 'info'"
          :closable="false"
          show-icon
          style="margin-bottom: 8px"
        >
          <template #title>
            {{ describeWindow(form.startTime, form.endTime) }}
          </template>
        </el-alert>
        <el-form-item v-if="dialogMode === 'edit'" label="启用">
          <el-switch v-model="form.enabled" />
          <span style="color: #909399; font-size: 12px; margin-left: 8px">
            关闭后该窗口暂不生效(可随时启用)
          </span>
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
:deep(.el-card__header) { padding: 12px 16px; }
:deep(.el-card__body) { padding: 16px; }
</style>
