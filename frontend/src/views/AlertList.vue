<template>
  <div class="alert-list">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span class="title">告警列表</span>
          <el-button :icon="Refresh" @click="load">刷新</el-button>
        </div>
      </template>

      <!-- 筛选 -->
      <el-form inline>
        <el-form-item label="严重度">
          <el-select v-model="filterSeverity" clearable placeholder="全部" style="width: 120px">
            <el-option label="CRITICAL" value="CRITICAL" />
            <el-option label="HIGH" value="HIGH" />
            <el-option label="MEDIUM" value="MEDIUM" />
            <el-option label="LOW" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterStatus" clearable placeholder="全部" style="width: 140px">
            <el-option label="NEW (待处理)" value="NEW" />
            <el-option label="ACKNOWLEDGED" value="ACKNOWLEDGED" />
            <el-option label="RESOLVED" value="RESOLVED" />
            <el-option label="SUPPRESSED" value="SUPPRESSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则类型">
          <el-input v-model="filterTypeCode" placeholder="如 COST_DIFF" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
      </el-form>

      <!-- 列表 -->
      <el-table :data="rows" v-loading="loading" stripe @row-click="openDetail">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="严重度" width="100">
          <template #default="{ row }">
            <el-tag :type="severityType(row.severity)">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="消息" min-width="280" show-overflow-tooltip />
        <el-table-column label="项目" width="100">
          <template #default="{ row }">
            <span v-if="row.projectId">#{{ row.projectId }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="触发时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.triggeredAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'NEW'" link type="primary" @click.stop="ack(row.id)">
              确认
            </el-button>
            <el-button v-if="row.status !== 'RESOLVED'" link type="success" @click.stop="resolve(row.id)">
              解决
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="load"
        style="margin-top: 16px; justify-content: flex-end"
      />
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="告警详情" size="500px">
      <template v-if="selected">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="ID">{{ selected.id }}</el-descriptions-item>
          <el-descriptions-item label="严重度">
            <el-tag :type="severityType(selected.severity)">{{ selected.severity }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(selected.status)">{{ statusLabel(selected.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="规则 ID">{{ selected.ruleId }}</el-descriptions-item>
          <el-descriptions-item label="消息">{{ selected.message }}</el-descriptions-item>
          <el-descriptions-item label="项目">{{ selected.projectId ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="实际值">{{ selected.actualValue ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="阈值">{{ selected.thresholdValue ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="触发时间">{{ fmtTime(selected.triggeredAt) }}</el-descriptions-item>
          <el-descriptions-item label="通知状态">{{ selected.notifyStatus }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { alertApi, type AlertItem, type AlertSeverity, type AlertStatus } from '@/api/alert'

const rows = ref<AlertItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const filterSeverity = ref<AlertSeverity | undefined>(undefined)
const filterStatus = ref<AlertStatus | undefined>(undefined)
const filterTypeCode = ref<string>('')

const drawerVisible = ref(false)
const selected = ref<AlertItem | null>(null)

function severityType(s: AlertSeverity): 'danger' | 'warning' | 'info' | 'success' {
  if (s === 'CRITICAL') return 'danger'
  if (s === 'HIGH') return 'danger'
  if (s === 'MEDIUM') return 'warning'
  return 'info'
}

function statusType(s: AlertStatus): 'danger' | 'warning' | 'info' | 'success' {
  if (s === 'NEW') return 'danger'
  if (s === 'ACKNOWLEDGED') return 'warning'
  if (s === 'RESOLVED') return 'success'
  return 'info'
}

function statusLabel(s: AlertStatus): string {
  if (s === 'NEW') return '待处理'
  if (s === 'ACKNOWLEDGED') return '已确认'
  if (s === 'RESOLVED') return '已解决'
  return '已抑制'
}

function fmtTime(s: string | null): string {
  if (!s) return '—'
  return new Date(s).toLocaleString('zh-CN')
}

async function load() {
  loading.value = true
  try {
    const resp = await alertApi.list({
      severity: filterSeverity.value,
      status: filterStatus.value,
      typeCode: filterTypeCode.value || undefined,
      page: page.value - 1,
      size: size.value,
    })
    rows.value = resp.items
    total.value = resp.total
  } catch (e) {
    ElMessage.error('加载告警列表失败: ' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function openDetail(row: AlertItem) {
  selected.value = row
  drawerVisible.value = true
}

async function ack(id: number) {
  try {
    await alertApi.ack(id)
    ElMessage.success(`告警 #${id} 已确认`)
    await load()
    if (selected.value?.id === id) selected.value = { ...selected.value, status: 'ACKNOWLEDGED' }
  } catch (e) {
    ElMessage.error('确认失败: ' + (e as Error).message)
  }
}

async function resolve(id: number) {
  try {
    await ElMessageBox.confirm(`确认解决告警 #${id}?`, '确认', { type: 'warning' })
    await alertApi.resolve(id)
    ElMessage.success(`告警 #${id} 已解决`)
    await load()
    if (selected.value?.id === id) selected.value = { ...selected.value, status: 'RESOLVED' }
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error('解决失败: ' + (e?.message ?? e))
  }
}

onMounted(load)
</script>

<style scoped>
.alert-list {
  padding: 16px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.title {
  font-weight: 600;
}
.muted {
  color: #909399;
}
</style>
