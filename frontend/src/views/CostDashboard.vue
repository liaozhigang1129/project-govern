<template>
  <div class="cost-dashboard">
    <!-- ===== 顶部 KPI 卡 (8 卡) ===== -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card kpi-total">
          <div class="kpi-label">总成本</div>
          <div class="kpi-value">¥{{ format(totalCost) }}</div>
          <div class="kpi-sub">{{ totalHeadcount }} 人 · {{ totalHours }} h</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">活跃项目</div>
          <div class="kpi-value">{{ activeProjects }} 个</div>
          <div class="kpi-sub">预算覆盖 {{ budgetCoveragePct.toFixed(0) }}%</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">人均成本</div>
          <div class="kpi-value">¥{{ format(avgCostPerUser) }}</div>
          <div class="kpi-sub">{{ totalHeadcount }} 人 · {{ totalHours }} h</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">平均时薪</div>
          <div class="kpi-value">¥{{ avgHourlyRate.toFixed(0) }}/h</div>
          <div class="kpi-sub">cost / hours</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">维度</div>
          <div class="kpi-value">{{ dimLabel }}</div>
          <div class="kpi-sub">{{ yearMonthLabel }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">分组数</div>
          <div class="kpi-value">{{ rowCount }}</div>
          <div class="kpi-sub">{{ dimLabel }} 维度</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">总工时</div>
          <div class="kpi-value">{{ totalHours }} h</div>
          <div class="kpi-sub">人均 {{ totalHours > 0 ? (totalHours / Math.max(totalHeadcount, 1)).toFixed(1) : 0 }} h</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">总人头</div>
          <div class="kpi-value">{{ totalHeadcount }} 人</div>
          <div class="kpi-sub">人均成本 ¥{{ format(avgCostPerUser) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ===== 筛选 + 刷新 ===== -->
    <el-card class="filter-card" shadow="never">
      <el-form inline>
        <el-form-item label="维度">
          <el-radio-group v-model="dim" @change="load">
            <el-radio-button value="PROJECT">项目</el-radio-button>
            <el-radio-button value="PHASE">阶段</el-radio-button>
            <el-radio-button value="DEPT">部门</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="月份">
          <el-date-picker
            v-model="monthDate"
            type="month"
            value-format="YYYY-MM"
            placeholder="不选则全历史"
            clearable
            @change="load"
          />
        </el-form-item>
        <el-form-item>
          <el-button :icon="Refresh" @click="load">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ===== 表格 ===== -->
    <el-card class="table-card" shadow="never" v-loading="loading">
      <el-table :data="rows" stripe :max-height="600" :empty-text="loading ? '加载中' : '无数据'">
        <el-table-column prop="code" label="编码" width="160" />
        <el-table-column label="名称" min-width="200">
          <template #default="{ row }">
            <span class="row-label">{{ row.label }}</span>
            <el-tag v-if="dim === 'PHASE' && row.phaseName" size="small" effect="plain" class="ml-1">
              {{ row.phaseName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="yearMonth" label="月份" width="100" v-if="!monthDate" />
        <el-table-column label="工时" width="100" align="right">
          <template #default="{ row }">
            <span class="num">{{ row.hours.toFixed(1) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="成本" width="140" align="right" sortable :sort-by="(r: any) => r.cost">
          <template #default="{ row }">
            <span class="num strong">¥{{ format(row.cost) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时薪" width="100" align="right">
          <template #default="{ row }">
            <span class="num-sm">¥{{ row.costRate.toFixed(0) }}/h</span>
          </template>
        </el-table-column>
        <el-table-column v-if="dim === 'PROJECT'" label="预算" width="140" align="right">
          <template #default="{ row }">
            <span v-if="row.budget" class="num-sm">¥{{ format(row.budget) }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column v-if="dim === 'PROJECT'" label="预算占比" width="120" align="right">
          <template #default="{ row }">
            <span v-if="row.budget && row.cost" :class="costPctOfBudgetClass(row)">
              {{ ((row.cost / row.budget) * 100).toFixed(1) }}%
            </span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="人数" width="80" align="right" prop="headcount" />
        <el-table-column label="占比" width="180">
          <template #default="{ row }">
            <el-progress
              :percentage="Math.min(100, row.costPct)"
              :stroke-width="14"
              :show-text="false"
              :color="barColor(row.costPct)"
            />
            <span class="pct-text">{{ row.costPct.toFixed(1) }}%</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from "vue"
import { ElMessage } from "element-plus"
import { Refresh } from "@element-plus/icons-vue"
import { costApi, type CostDimensionRow, type CostDimensionResponse } from "@/api/cost"

const dim = ref<"PROJECT" | "PHASE" | "DEPT">("PROJECT")
const monthDate = ref<string | null>("2026-06")
const loading = ref(false)
const response = ref<CostDimensionResponse | null>(null)

const dimLabel = computed(() =>
  dim.value === "PROJECT" ? "项目" : dim.value === "PHASE" ? "阶段" : "部门",
)

const yearMonthLabel = computed(() => monthDate.value || "全历史")

const rows = computed<CostDimensionRow[]>(() => response.value?.rows ?? [])
const totalCost = computed(() => response.value?.totalCost ?? 0)
const totalHours = computed(() => response.value?.totalHours ?? 0)
const totalHeadcount = computed(() => response.value?.totalHeadcount ?? 0)
const activeProjects = computed(() => response.value?.activeProjects ?? 0)
const avgCostPerUser = computed(() => response.value?.avgCostPerUser ?? 0)
const budgetCoveragePct = computed(() => response.value?.budgetCoveragePct ?? 0)
const avgHourlyRate = computed(() => response.value?.avgHourlyRate ?? 0)
const rowCount = computed(() => rows.value.length)
const avgRate = computed(() =>
  totalHours.value > 0 ? (totalCost.value / totalHours.value).toFixed(0) : "0",
)

function format(n: number) {
  return n.toLocaleString("zh-CN", { maximumFractionDigits: 0 })
}

function barColor(pct: number) {
  if (pct >= 50) return "#f56c6c"
  if (pct >= 25) return "#e6a23c"
  return "#67c23a"
}

function costPctOfBudgetClass(row: CostDimensionRow) {
  if (!row.budget) return "muted"
  const pct = (row.cost / row.budget) * 100
  if (pct > 90) return "warn-bad"
  if (pct > 70) return "warn-mid"
  return "num-sm"
}

async function load() {
  loading.value = true
  try {
    const resp = await costApi.dimension({
      dim: dim.value,
      month: monthDate.value || undefined,
    })
    response.value = resp
  } catch (e: any) {
    ElMessage.error(e?.message || "加载失败")
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.cost-dashboard { padding: 16px; }
.kpi-row { margin-bottom: 16px; }
.kpi-card { text-align: left; }
.kpi-card.kpi-total { background: linear-gradient(135deg, #409eff 0%, #67c23a 100%); color: #fff; }
.kpi-card.kpi-total .kpi-label, .kpi-card.kpi-total .kpi-sub { color: rgba(255,255,255,0.85); }
.kpi-label { font-size: 12px; color: #909399; }
.kpi-value { font-size: 28px; font-weight: 700; color: #303133; margin: 6px 0; }
.kpi-sub { font-size: 12px; color: #909399; }
.filter-card, .table-card { margin-bottom: 16px; }
.num { font-variant-numeric: tabular-nums; }
.num.strong { font-weight: 600; color: #303133; }
.num-sm { font-variant-numeric: tabular-nums; color: #606266; font-size: 13px; }
.muted { color: #c0c4cc; }
.row-label { font-weight: 500; }
.ml-1 { margin-left: 6px; }
.pct-text { margin-left: 8px; font-size: 12px; color: #606266; }
.warn-bad { color: #f56c6c; font-weight: 600; }
.warn-mid { color: #e6a23c; font-weight: 600; }
</style>
