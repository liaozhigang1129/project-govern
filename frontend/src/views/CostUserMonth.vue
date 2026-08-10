<script setup lang="ts">
/**
 * P0-A.1 工时 → 成本 验收页 (F1 主验收)
 *
 * 入口: /cost/user-month
 *  - 输入 userId + month (YYYY-MM), 点击"查询"
 *  - 显示 totalHours / totalCost 卡片
 *  - 显示 5 个 RateSource 的工时分账柱状条
 *  - 显示 items 明细(项目 + 里程碑 + 工时 × 时薪 = 小计)
 *  - 双击某条可下钻到当日成本
 *
 * 验证用例:
 *  - 上传 cost_rates.csv (6 行 DEV 600)
 *  - 录入 6 月某周报 (张三 40h)
 *  - 审批 APPROVED
 *  - 在本页查 userId=张三, month=2026-06
 *  - 期望 totalCost = 24000
 */
import { onMounted, ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, DataAnalysis, Money, Document } from '@element-plus/icons-vue'
import {
  costApi,
  type UserMonthCostResponse,
  type UserDayCostResponse,
  type CostBreakdownItem,
} from '@/api/cost'

const YUAN = '¥'

// ===== 输入 =====
const userId = ref<number | null>(null)
const month = ref('')
const loading = ref(false)
const data = ref<UserMonthCostResponse | null>(null)

// 初始化默认月份 = 当前月
function initMonth() {
  const now = new Date()
  month.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

async function query() {
  if (!userId.value) {
    ElMessage.warning('请填 userId')
    return
  }
  if (!month.value) {
    ElMessage.warning('请选月份')
    return
  }
  loading.value = true
  try {
    data.value = await costApi.userMonthCost(userId.value, month.value)
  } catch (e: any) {
    ElMessage.error(e?.message ?? '查询失败')
    data.value = null
  } finally {
    loading.value = false
  }
}

// ===== 5 个 RateSource 分账柱状条 =====
const BREAKDOWN_META: {
  key: keyof UserMonthCostResponse['rateSourceBreakdown']
  label: string
  color: string
}[] = [
  { key: 'userOverrideHours', label: 'USER_OVERRIDE', color: '#f56c6c' },
  { key: 'roleOverrideHours', label: 'ROLE_OVERRIDE', color: '#e6a23c' },
  { key: 'roleDefaultHours', label: 'ROLE_COST_DEFAULT', color: '#67c23a' },
  { key: 'userDefaultHours', label: 'USER_DEFAULT', color: '#409eff' },
  { key: 'noneHours', label: 'NONE (rate=0)', color: '#909399' },
]

const totalBreakdownHours = computed(() => {
  if (!data.value) return 0
  const b = data.value.rateSourceBreakdown
  return b.userOverrideHours + b.roleOverrideHours + b.roleDefaultHours + b.userDefaultHours + b.noneHours
})

function pctOfBreakdown(n: number): string {
  const t = totalBreakdownHours.value
  if (!t) return '0%'
  return `${((n / t) * 100).toFixed(1)}%`
}

// ===== 单日下钻 =====
const dayDialog = ref({
  visible: false,
  date: '',
  data: null as UserDayCostResponse | null,
  loading: false,
})

async function openDay(row: CostBreakdownItem) {
  if (!userId.value) return
  // 没有 entry.workDate 字段(响应没带), 用 row 没有日期 — 简化: 弹窗让用户填日期
  // 实际更好的做法: 后端在 CostBreakdownItem 加 workDate 字段,这里先用 dialog 让用户选日期
  dayDialog.value.date = ''
  dayDialog.value.data = null
  dayDialog.value.visible = true
}

async function queryDay() {
  if (!userId.value || !dayDialog.value.date) {
    ElMessage.warning('请填日期')
    return
  }
  dayDialog.value.loading = true
  try {
    dayDialog.value.data = await costApi.userDayCost(userId.value, dayDialog.value.date)
  } catch (e: any) {
    ElMessage.error(e?.message ?? '查询失败')
    dayDialog.value.data = null
  } finally {
    dayDialog.value.loading = false
  }
}

function fmtMoney(n: number): string {
  return `${YUAN}${n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

// 按 projectId 聚合显示(后端 items 是按 (work_date, project, milestone) 拆的,
// 这里前端简化合并: 同 project 的 cost 累加)
const projectGroups = computed(() => {
  if (!data.value) return []
  const map = new Map<number, { projectId: number; hours: number; cost: number; rateSources: Set<string> }>()
  for (const it of data.value.items) {
    const e = map.get(it.projectId) ?? {
      projectId: it.projectId,
      hours: 0,
      cost: 0,
      rateSources: new Set<string>(),
    }
    e.hours += it.hours
    e.cost += it.cost
    e.rateSources.add(it.rateSource)
    map.set(it.projectId, e)
  }
  return Array.from(map.values()).sort((a, b) => b.cost - a.cost)
})

// 当 totalCost 改变时, 如果和验收值匹配打个绿色高亮
const isAcceptanceMatch = computed(() => {
  if (!data.value) return false
  // F1 主验收: 24000
  return Math.abs(data.value.totalCost - 24000) < 0.01
})

onMounted(() => {
  initMonth()
  // 试一下: 从 URL ?userId= 自动填
  const params = new URLSearchParams(window.location.search)
  const uid = params.get('userId')
  if (uid) userId.value = Number(uid)
})
</script>
<template>
  <div style="padding: 16px">
    <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 16px">
      <h2 style="margin: 0">工时 → 成本 月度核算</h2>
      <el-tag size="small" type="info">F1 主验收</el-tag>
    </div>

    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>
        <div style="display: flex; align-items: center; gap: 12px">
          <span>查询条件</span>
        </div>
      </template>
      <div style="display: flex; gap: 12px; align-items: center">
        <span>用户 ID:</span>
        <el-input-number
          v-model="userId"
          :min="1"
          controls-position="right"
          placeholder="如 1 (张三)"
          style="width: 180px"
        />
        <span>月份:</span>
        <el-input v-model="month" placeholder="YYYY-MM" style="width: 160px" />
        <el-button type="primary" :icon="Search" :loading="loading" @click="query">查询</el-button>
        <span style="margin-left: auto; color: #909399; font-size: 12px">
          只统计
          <b>APPROVED</b>
          周报 · DRAFT/SUBMITTED 不计入
        </span>
      </div>
    </el-card>

    <template v-if="data">
      <!-- 总览卡片 -->
      <div style="display: flex; gap: 16px; margin-bottom: 16px; flex-wrap: wrap">
        <el-card shadow="never" style="flex: 1; min-width: 240px">
          <div style="display: flex; align-items: center; gap: 8px; color: #909399">
            <el-icon><component :is="DataAnalysis" /></el-icon>
            <span>工时合计</span>
          </div>
          <div style="font-size: 28px; font-weight: 600; margin-top: 8px">
            {{ data.totalHours.toFixed(2) }}
            <span style="font-size: 14px; color: #909399">小时</span>
          </div>
        </el-card>

        <el-card shadow="never" style="flex: 1; min-width: 240px">
          <div style="display: flex; align-items: center; gap: 8px; color: #909399">
            <el-icon><component :is="Money" /></el-icon>
            <span>成本合计</span>
          </div>
          <div
            :style="{
              fontSize: '28px',
              fontWeight: 600,
              marginTop: '8px',
              color: isAcceptanceMatch ? '#67c23a' : '#f56c6c',
            }"
          >
            {{ fmtMoney(data.totalCost) }}
          </div>
          <div v-if="isAcceptanceMatch" style="font-size: 12px; color: #67c23a; margin-top: 4px">
            ✅ F1 主验收通过 (¥24,000)
          </div>
        </el-card>

        <el-card shadow="never" style="flex: 1; min-width: 240px">
          <div style="display: flex; align-items: center; gap: 8px; color: #909399">
            <el-icon><component :is="Document" /></el-icon>
            <span>用户 / 主角色 / 月份</span>
          </div>
          <div style="font-size: 16px; margin-top: 8px">
            <div>{{ data.userName }} (#{{ data.userId }})</div>
            <div style="color: #909399; font-size: 13px; margin-top: 2px">
              {{ data.primaryRoleCode || '—' }} · {{ data.month }}
            </div>
          </div>
        </el-card>
      </div>

      <!-- 5 个 RateSource 分账 -->
      <el-card shadow="never" style="margin-bottom: 16px">
        <template #header><span style="font-weight: 600">费率来源分账</span></template>
        <div v-for="b in BREAKDOWN_META" :key="b.key" style="margin-bottom: 10px">
          <div style="display: flex; justify-content: space-between; font-size: 13px; margin-bottom: 4px">
            <span>
              <code style="background: #f0f4f8; padding: 1px 6px; border-radius: 4px; font-size: 11px">
                {{ b.label }}
              </code>
            </span>
            <span style="color: #606266">
              <b>{{ data.rateSourceBreakdown[b.key] }}h</b>
              <span style="color: #909399; margin-left: 6px">
                ({{ pctOfBreakdown(data.rateSourceBreakdown[b.key]) }})
              </span>
            </span>
          </div>
          <el-progress
            :percentage="
              totalBreakdownHours === 0 ? 0 : (data.rateSourceBreakdown[b.key] / totalBreakdownHours) * 100
            "
            :color="b.color"
            :show-text="false"
            :stroke-width="14"
          />
        </div>
      </el-card>

      <!-- 项目分账 -->
      <el-card shadow="never" style="margin-bottom: 16px">
        <template #header><span style="font-weight: 600">项目分账 (按 projectId 聚合)</span></template>
        <el-table :data="projectGroups" border stripe style="width: 100%">
          <el-table-column prop="projectId" label="项目 ID" width="120" />
          <el-table-column label="工时">
            <template #default="{ row }">
              <span style="color: #409eff; font-weight: 600">{{ row.hours.toFixed(2) }} h</span>
            </template>
          </el-table-column>
          <el-table-column label="成本">
            <template #default="{ row }">
              <span style="color: #67c23a; font-weight: 600">{{ fmtMoney(row.cost) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="费率来源">
            <template #default="{ row }">
              <el-tag
                v-for="s in row.rateSources"
                :key="s"
                size="small"
                type="info"
                style="margin-right: 4px"
              >
                {{ s }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 详细 items -->
      <el-card shadow="never">
        <template #header><span style="font-weight: 600">明细 (project × milestone)</span></template>
        <el-table :data="data.items" border stripe style="width: 100%" empty-text="该月无成本数据">
          <el-table-column prop="projectId" label="项目" width="120" />
          <el-table-column prop="milestoneId" label="里程碑" width="120">
            <template #default="{ row }">{{ row.milestoneId ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="工时">
            <template #default="{ row }">{{ row.hours.toFixed(2) }} h</template>
          </el-table-column>
          <el-table-column label="时薪">
            <template #default="{ row }">
              <span style="color: #67c23a">{{ YUAN }}{{ row.rate.toFixed(2) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="160">
            <template #default="{ row }">
              <el-tag size="small">{{ row.rateSource }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="小计">
            <template #default="{ row }">
              <span style="font-weight: 600">{{ fmtMoney(row.cost) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <el-empty v-else description="输入 userId + 月份后点击查询" />

    <!-- 单日下钻对话框 -->
    <el-dialog v-model="dayDialog.visible" title="单日成本下钻" width="640px">
      <div style="display: flex; gap: 8px; align-items: center; margin-bottom: 12px">
        <span>日期:</span>
        <el-input v-model="dayDialog.date" placeholder="YYYY-MM-DD" style="width: 200px" />
        <el-button type="primary" :icon="Search" :loading="dayDialog.loading" @click="queryDay">
          查询
        </el-button>
      </div>
      <template v-if="dayDialog.data">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="日期">{{ dayDialog.data.date }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ dayDialog.data.userName }}</el-descriptions-item>
          <el-descriptions-item label="工时">{{ dayDialog.data.hours.toFixed(2) }} h</el-descriptions-item>
          <el-descriptions-item label="成本">{{ fmtMoney(dayDialog.data.cost) }}</el-descriptions-item>
          <el-descriptions-item label="时薪">
            {{ YUAN }}{{ dayDialog.data.rate.toFixed(2) }}
          </el-descriptions-item>
          <el-descriptions-item label="来源">{{ dayDialog.data.rateSource }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="dayDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
code {
  font-family: 'SF Mono', Menlo, Consolas, monospace;
}
</style>
