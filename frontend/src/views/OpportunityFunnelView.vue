<script setup lang="ts">
/**
 * P6-商机配置大盘 (漏斗)
 *  5 KPI + 6 阶段漏斗 + 转化率 + 月度趋势 + 销售排行 + BU×PL 分布
 */
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getOpportunityKpis, getFunnel, getConversionRates,
  getMonthlyTrend, getSalesRank, getAmountByBuPl,
  type OpportunityKpis, type FunnelStage, type ConversionRate,
  type MonthlyTrend, type SalesRank, type BuPlAmount
} from '@/api/opportunityFunnel'

const kpis = ref<OpportunityKpis | null>(null)
const funnel = ref<FunnelStage[]>([])
const rates = ref<ConversionRate[]>([])
const trend = ref<MonthlyTrend[]>([])
const rank = ref<SalesRank[]>([])
const bupl = ref<BuPlAmount[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [k, f, r, t, sr, bp] = await Promise.all([
      getOpportunityKpis(), getFunnel(), getConversionRates(),
      getMonthlyTrend(), getSalesRank(), getAmountByBuPl()
    ])
    kpis.value = k
    funnel.value = f
    rates.value = r
    trend.value = t
    rank.value = sr
    bupl.value = bp
  } catch (e) {
    ElMessage.error('加载失败: ' + (e as any).message)
  } finally {
    loading.value = false
  }
}

function fmtMoney(v: number) {
  if (v >= 1e8) return (v / 1e8).toFixed(2) + ' 亿'
  if (v >= 1e4) return (v / 1e4).toFixed(2) + ' 万'
  return (v ?? 0).toFixed(0)
}

const funnelMax = computed(() => {
  if (!funnel.value.length) return 1
  return Math.max(1, ...funnel.value.map(s => s.count))
})

const trendMax = computed(() => {
  if (!trend.value.length) return 1
  return Math.max(1, ...trend.value.map(t => t.wonAmount))
})

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <h2 class="page-title">💰 商机配置 · 漏斗大盘</h2>

    <el-row :gutter="16" class="kpi-row">
      <el-col :span="4">
        <el-card shadow="hover" class="kpi-card kpi-blue">
          <div class="kpi-label">总商机</div>
          <div class="kpi-value">{{ kpis?.totalOpportunities ?? 0 }}</div>
          <div class="kpi-sub">含 {{ kpis?.wonCount ?? 0 }} 成交 / {{ kpis?.lostCount ?? 0 }} 流失</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="kpi-card kpi-green">
          <div class="kpi-label">进行中</div>
          <div class="kpi-value">{{ kpis?.openCount ?? 0 }}</div>
          <div class="kpi-sub">{{ fmtMoney(kpis?.openAmount ?? 0) }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="kpi-card kpi-orange">
          <div class="kpi-label">加权管道</div>
          <div class="kpi-value">{{ fmtMoney(kpis?.weightedPipeline ?? 0) }}</div>
          <div class="kpi-sub">金额 × 概率</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="kpi-card kpi-purple">
          <div class="kpi-label">赢率</div>
          <div class="kpi-value">{{ kpis?.winRate ?? 0 }}%</div>
          <div class="kpi-sub">WON / (WON+LOST)</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="kpi-card kpi-gray">
          <div class="kpi-label">平均单额</div>
          <div class="kpi-value">{{ (kpis?.avgDealSize ?? 0).toFixed(2) }} 万</div>
          <div class="kpi-sub">{{ kpis?.buCount ?? 0 }} 个 BU 在推</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="hover">
          <h4 class="section">🔻 漏斗 (6 阶段)</h4>
          <div class="funnel">
            <div
              v-for="s in funnel"
              :key="s.stage"
              class="funnel-stage"
              :style="{
                width: (Math.max(s.count, 1) / funnelMax * 100) + '%',
                background: s.color
              }"
            >
              <div class="stage-name">{{ s.stage }}</div>
              <div class="stage-count">{{ s.count }}</div>
              <div class="stage-amount">{{ fmtMoney(s.amount) }}</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card shadow="hover">
          <h4 class="section">📈 阶段转化率</h4>
          <div
            v-for="r in rates"
            :key="r.from + r.to"
            class="conv-row"
          >
            <span class="conv-from">{{ r.from }}</span>
            <span class="conv-arrow">→</span>
            <span class="conv-to">{{ r.to }}</span>
            <el-progress
              :percentage="r.rate"
              :color="r.rate >= 30 ? '#67c23a' : r.rate >= 15 ? '#e6a23c' : '#f56c6c'"
              :stroke-width="10"
              style="flex: 1; margin: 0 12px;"
            />
            <span class="conv-rate">{{ r.rate }}%</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover" style="margin-top: 16px;">
      <h4 class="section">📊 月度成交趋势</h4>
      <el-table v-if="trend.length" :data="trend" stripe size="small" max-height="280">
        <el-table-column prop="month" label="月份" width="120" />
        <el-table-column prop="wonCount" label="成交单数" width="120" align="right" />
        <el-table-column label="成交金额" align="right">
          <template #default="{ row }">{{ fmtMoney(row.wonAmount) }}</template>
        </el-table-column>
        <el-table-column label="可视化" min-width="200">
          <template #default="{ row }">
            <el-progress
              :percentage="Math.min(100, (row.wonAmount / trendMax) * 100)"
              :color="'#67c23a'"
            />
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty">暂无成交数据</div>
    </el-card>

    <el-row :gutter="16" style="margin-top: 16px;">
      <el-col :span="12">
        <el-card shadow="hover">
          <h4 class="section">🏆 销售排行 Top-20</h4>
          <el-table :data="rank" stripe size="small" max-height="380">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="userName" label="销售" />
            <el-table-column prop="openCount" label="进行中" width="80" align="right" />
            <el-table-column prop="wonCount" label="已成交" width="80" align="right" />
            <el-table-column label="成交金额" align="right">
              <template #default="{ row }">{{ fmtMoney(row.wonAmount) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="hover">
          <h4 class="section">🏢 BU × PL 金额分布</h4>
          <el-table :data="bupl" stripe size="small" max-height="380">
            <el-table-column prop="buName" label="BU" width="100" />
            <el-table-column prop="plName" label="PL" width="100" />
            <el-table-column label="管道金额" align="right">
              <template #default="{ row }">{{ fmtMoney(row.openAmount) }}</template>
            </el-table-column>
            <el-table-column label="已成交" align="right">
              <template #default="{ row }">{{ fmtMoney(row.wonAmount) }}</template>
            </el-table-column>
            <el-table-column prop="wonCount" label="单数" width="60" align="right" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.page { padding: 16px; }
.page-title { margin: 0 0 16px; color: #303133; }
.kpi-row { margin-bottom: 16px; }
.kpi-card {
  text-align: center;
  .kpi-label { font-size: 12px; color: #909399; }
  .kpi-value { font-size: 28px; font-weight: 600; margin: 6px 0; }
  .kpi-sub { font-size: 11px; color: #c0c4cc; }
}
.kpi-blue .kpi-value { color: #409eff; }
.kpi-green .kpi-value { color: #67c23a; }
.kpi-orange .kpi-value { color: #e6a23c; }
.kpi-purple .kpi-value { color: #9c27b0; }
.kpi-gray .kpi-value { color: #606266; }
.section { margin: 0 0 12px; color: #303133; font-size: 14px; }

.funnel {
  display: flex; flex-direction: column; align-items: stretch; gap: 4px;
  padding: 16px 0;
}
.funnel-stage {
  color: #fff; padding: 12px 16px; border-radius: 4px;
  display: flex; justify-content: space-between; align-items: center;
  font-size: 13px; min-width: 200px;
  transition: all 0.3s;
  &:hover { transform: scale(1.02); }
  .stage-name { font-weight: 600; min-width: 90px; }
  .stage-count { font-size: 18px; font-weight: 700; }
  .stage-amount { font-size: 11px; opacity: 0.9; }
}

.conv-row {
  display: flex; align-items: center; margin-bottom: 12px;
  font-size: 12px;
  .conv-from, .conv-to { width: 90px; font-weight: 500; }
  .conv-arrow { color: #909399; margin: 0 4px; }
  .conv-rate { width: 50px; text-align: right; font-weight: 600; }
}
.empty { text-align: center; color: #c0c4cc; padding: 30px; }
</style>
