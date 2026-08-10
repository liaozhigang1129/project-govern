<template>
  <div class="reconciliation-list">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span class="title">财务-成本 3-way match 对账</span>
          <div>
            <el-button :icon="Refresh" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <!-- 健康度概览 -->
      <el-row :gutter="16" class="kpi-row" v-if="health">
        <el-col :span="6">
          <el-statistic title="对账健康度" :value="greenRatePct" suffix="%" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="MISMATCH 条数" :value="health.mismatch" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="待处理 (PARTIAL+PENDING)" :value="pendingTotal" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="差异总额 (¥)" :value="health.totalDiff" :precision="2" />
        </el-col>
      </el-row>

      <!-- 筛选 -->
      <el-form inline>
        <el-form-item label="项目">
          <el-input-number v-model="filterProjectId" :min="0" placeholder="可选" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterStatus" clearable placeholder="全部">
            <el-option label="MATCHED" value="MATCHED" />
            <el-option label="PARTIAL" value="PARTIAL" />
            <el-option label="MISMATCH" value="MISMATCH" />
            <el-option label="PENDING" value="PENDING" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
      </el-form>

      <!-- 列表 -->
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="projectId" label="项目" width="100" />
        <el-table-column prop="period" label="期间" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.matchStatus)">{{ row.matchStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="合同 / 开票 / 实付 / 入账" min-width="280">
          <template #default="{ row }">
            <span>
              ¥{{ fmt(row.contractAmount) }} / ¥{{ fmt(row.invoiceAmount) }} / ¥{{ fmt(row.paymentAmount) }} /
              ¥{{ fmt(row.costAmount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="差异" width="120">
          <template #default="{ row }">
            <span :class="{ 'diff-warn': row.diffAmount > 100 }">¥{{ fmt(row.diffAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="diffReason" label="原因" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="retry(row.id)">重跑</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  financeApi,
  type ReconciliationItem,
  type ReconciliationHealth,
  type MatchStatus,
} from '@/api/finance'

const rows = ref<ReconciliationItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const filterProjectId = ref<number | undefined>(undefined)
const filterStatus = ref<MatchStatus | undefined>(undefined)
const health = ref<ReconciliationHealth | null>(null)

const greenRatePct = computed(() => Math.round((health.value?.greenRate ?? 1) * 100))
const pendingTotal = computed(() => (health.value?.partial ?? 0) + (health.value?.pending ?? 0))

function statusTagType(s: MatchStatus): 'success' | 'warning' | 'danger' | 'info' {
  if (s === 'MATCHED') return 'success'
  if (s === 'MISMATCH') return 'danger'
  if (s === 'PARTIAL') return 'warning'
  return 'info'
}

function fmt(n: number) {
  return (n ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function load() {
  loading.value = true
  try {
    const resp = await financeApi.reconciliationList({
      projectId: filterProjectId.value,
      status: filterStatus.value,
      page: page.value - 1,
      size: size.value,
    })
    rows.value = resp.items
    total.value = resp.total
  } catch (e) {
    ElMessage.error('加载对账列表失败: ' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

async function loadHealth() {
  try {
    health.value = await financeApi.reconciliationHealth()
  } catch (e) {
    health.value = null
  }
}

async function retry(id: number) {
  try {
    await ElMessageBox.confirm(`重跑对账记录 #${id}?`, '确认', { type: 'warning' })
    await financeApi.reconciliationRetry(id)
    ElMessage.success(`对账 #${id} 已重跑`)
    await load()
    await loadHealth()
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error('重跑失败: ' + (e?.message ?? e))
  }
}

onMounted(() => {
  load()
  loadHealth()
})
</script>

<style scoped>
.reconciliation-list {
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
.kpi-row {
  margin-bottom: 16px;
}
.diff-warn {
  color: #f56c6c;
  font-weight: 600;
}
</style>
