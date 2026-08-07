<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, View } from '@element-plus/icons-vue'
import api from '@/api/client'

// --- 类型(从 openapi.json 抄) ---
export interface AuditLogItem {
  id: number
  userId: number | null
  resourceType: string
  resourceId: number | null
  action: string
  ipAddress: string | null
  createdAt: string
  result: string
}

export interface AuditLogDetail {
  id: number
  userId: number | null
  resourceType: string
  resourceId: number | null
  action: string
  payload: string  // JSON 字符串
  ipAddress: string | null
  createdAt: string
}

interface PageResp<T> {
  total: number
  page: number
  size: number
  totalPages: number
  start: string
  end: string
  items: T[]
}

// --- 列表 ---
const list = ref<AuditLogItem[]>([])
const total = ref(0)
const totalPages = ref(0)
const loading = ref(false)

// 筛选
const filter = ref({
  resourceType: '',
  userId: null as number | null,
  action: '',
  start: '',  // ISO 字符串,空 = 默认 7 天窗口(后端控制)
  end: '',
})

const pagination = ref({ page: 0, size: 20 })

// 选项(从后端 /initiations 等已枚举的列表里抽)
const resourceTypes = ref<string[]>([
  'AUTH', 'INITIATION', 'MILESTONE', 'PROJECT', 'HEALTH_ADVISOR', 'TIMESHEET',
])
const actions = ref<string[]>([
  'LOGIN', 'REFRESH', 'LOGOUT', 'CREATE', 'UPDATE', 'UPDATE_ENTRIES',
  'UPDATE_STATUS', 'DELETE', 'SUBMIT', 'APPROVE', 'REJECT', 'BATCH_APPROVE',
  'RESUBMIT', 'RUN_BATCH',
])

// 7 天前(后端默认窗口起始),给个参考
const defaultStart = computed(() => {
  const d = new Date()
  d.setDate(d.getDate() - 7)
  return d.toISOString()
})

async function load() {
  loading.value = true
  try {
    const params: Record<string, string | number> = {
      page: pagination.value.page,
      size: pagination.value.size,
    }
    if (filter.value.resourceType) params.resourceType = filter.value.resourceType
    if (filter.value.userId) params.userId = filter.value.userId
    if (filter.value.action) params.action = filter.value.action
    if (filter.value.start) params.start = filter.value.start
    if (filter.value.end) params.end = filter.value.end

    const data = (await api.get('/audit-logs', { params })) as PageResp<AuditLogItem>
    list.value = data.items
    total.value = data.total
    totalPages.value = data.totalPages
  } catch (e) {
    ElMessage.error(`加载失败: ${(e as Error).message}`)
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.value.page = 0
  load()
}

function reset() {
  filter.value = { resourceType: '', userId: null, action: '', start: '', end: '' }
  pagination.value.page = 0
  load()
}

onMounted(load)

// --- 详情抽屉 ---
const detailVisible = ref(false)
const detail = ref<AuditLogDetail | null>(null)
const detailLoading = ref(false)

async function showDetail(id: number) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = (await api.get(`/audit-logs/${id}`)) as AuditLogDetail
  } catch (e) {
    ElMessage.error(`加载详情失败: ${(e as Error).message}`)
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

// 格式化 payload JSON 字符串为带缩进的对象
const prettyPayload = computed(() => {
  if (!detail.value?.payload) return ''
  try {
    return JSON.stringify(JSON.parse(detail.value.payload), null, 2)
  } catch {
    return detail.value.payload
  }
})

// 颜色映射
function resultTagType(r: string) {
  return r === 'SUCCESS' ? 'success' : r === 'FAILURE' ? 'danger' : 'info'
}

function actionColor(a: string) {
  if (a.includes('DELETE') || a.includes('REJECT')) return 'danger'
  if (a.includes('UPDATE') || a.includes('STATUS') || a.includes('RESUBMIT') || a === 'SUBMIT') return 'warning'
  if (a.includes('LOGIN') || a.includes('LOGOUT')) return 'info'
  return 'success'  // CREATE / APPROVE / BATCH_APPROVE / RUN_BATCH
}
</script>

<template>
  <div style="padding: 24px">
    <h2 style="margin: 0 0 16px 0">📋 审计日志(PMO_ADMIN / ADMIN)</h2>

    <!-- 筛选区 -->
    <el-card style="margin-bottom: 16px">
      <el-form :model="filter" inline label-width="auto">
        <el-form-item label="资源类型">
          <el-select v-model="filter.resourceType" clearable placeholder="全部" style="width: 160px">
            <el-option v-for="t in resourceTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作">
          <el-select v-model="filter.action" clearable placeholder="全部" style="width: 160px">
            <el-option v-for="a in actions" :key="a" :label="a" :value="a" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户ID">
          <el-input-number v-model="filter.userId" :min="1" placeholder="任意" style="width: 140px" controls-position="right" />
        </el-form-item>
        <el-form-item label="起始时间">
          <el-date-picker
            v-model="filter.start"
            type="datetime"
            placeholder="默认 7 天前"
            value-format="YYYY-MM-DDTHH:mm:ss[Z]"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="filter.end"
            type="datetime"
            placeholder="现在"
            value-format="YYYY-MM-DDTHH:mm:ss[Z]"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card>
      <el-table :data="list" v-loading="loading" stripe border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="createdAt" label="时间" width="200">
          <template #default="{ row }">
            <span style="font-family: monospace; font-size: 12px">
              {{ new Date(row.createdAt).toLocaleString() }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="用户" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.userId" type="info" size="small">#{{ row.userId }}</el-tag>
            <span v-else style="color: #999">匿名</span>
          </template>
        </el-table-column>
        <el-table-column prop="resourceType" label="资源" width="140">
          <template #default="{ row }">
            <el-tag size="small">{{ row.resourceType }}</el-tag>
            <span v-if="row.resourceId" style="margin-left: 6px; color: #999; font-size: 12px">
              #{{ row.resourceId }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="动作" width="120">
          <template #default="{ row }">
            <el-tag :type="actionColor(row.action) as any" size="small">{{ row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="result" label="结果" width="100">
          <template #default="{ row }">
            <el-tag :type="resultTagType(row.result) as any" size="small">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP" width="160">
          <template #default="{ row }">
            <code style="font-size: 12px">{{ row.ipAddress || '—' }}</code>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button :icon="View" size="small" link @click="showDetail(row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div style="display: flex; justify-content: flex-end; margin-top: 16px">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="(s: number) => { pagination.size = s; pagination.page = 0; load() }"
        />
      </div>
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="detailVisible"
      title="审计日志详情"
      size="60%"
      :destroy-on-close="true"
    >
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="1" border style="margin-bottom: 16px">
            <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
            <el-descriptions-item label="时间">
              {{ new Date(detail.createdAt).toLocaleString() }}
            </el-descriptions-item>
            <el-descriptions-item label="用户ID">
              {{ detail.userId ?? '匿名' }}
            </el-descriptions-item>
            <el-descriptions-item label="资源">
              {{ detail.resourceType }} {{ detail.resourceId ? `#${detail.resourceId}` : '' }}
            </el-descriptions-item>
            <el-descriptions-item label="动作">
              <el-tag :type="actionColor(detail.action) as any" size="small">{{ detail.action }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="IP">
              <code>{{ detail.ipAddress || '—' }}</code>
            </el-descriptions-item>
          </el-descriptions>

          <h4>Payload</h4>
          <pre style="background: #1e1e1e; color: #d4d4d4; padding: 12px;
                       border-radius: 4px; overflow: auto; max-height: 60vh;
                       font-size: 12px; line-height: 1.5"
          ><code>{{ prettyPayload }}</code></pre>
        </template>
      </div>
    </el-drawer>
  </div>
</template>
