<template>
  <div class="alert-dashboard">
    <!-- 顶部 4 个 KPI 卡 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card kpi-total">
          <div class="kpi-label">告警总数 (NEW)</div>
          <div class="kpi-value">{{ totalNew }}</div>
          <div class="kpi-sub">未处理告警</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">高危 (HIGH+CRITICAL)</div>
          <div class="kpi-value" :class="{ 'text-danger': highSeverity > 0 }">
            {{ highSeverity }}
          </div>
          <div class="kpi-sub">需要立即处理</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">中危 (MEDIUM)</div>
          <div class="kpi-value">{{ mediumSeverity }}</div>
          <div class="kpi-sub">关注跟进</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">规则类型</div>
          <div class="kpi-value">{{ typeCodeCount }}</div>
          <div class="kpi-sub">正在产生告警</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 按 typeCode 分布 + 操作入口 -->
    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header><span class="title">按规则类型分布 (NEW 状态)</span></template>
          <div v-if="!typeCodeEntries.length" class="empty">
            <el-icon class="ok"><CircleCheck /></el-icon>
            <span>暂无活跃告警,系统运行健康</span>
          </div>
          <div v-else class="type-list">
            <div v-for="[code, count] in typeCodeEntries" :key="code" class="type-row">
              <el-tag class="code-tag">{{ code }}</el-tag>
              <el-progress :percentage="pctOfType(count)" :format="() => `${count} 条`" />
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><span class="title">快速操作</span></template>
          <div class="actions">
            <el-button type="primary" :icon="ArrowRight" @click="$router.push('/alerts')">
              查看告警列表
            </el-button>
            <el-button :icon="Notification" @click="seedCostDiff">播种 COST_DIFF 规则</el-button>
            <el-button :icon="Refresh" @click="load">刷新统计</el-button>
          </div>
          <el-alert title="告警调度" type="info" :closable="false" style="margin-top: 16px">
            <template #default>
              每 5 分钟扫描 6 类规则 (BUDGET_EXCEED / PROJECT_STALE / ROLE_DEFAULT / CONTRACT_BALANCE /
              HOURS_OVER / PAYMENT_OVERDUE), 命中即入库并通知 PMO/财务。
            </template>
          </el-alert>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, ArrowRight, Notification, Refresh } from '@element-plus/icons-vue'
import { alertApi } from '@/api/alert'

const stats = ref<{ bySeverity: Record<string, number>; byTypeCode: Record<string, number> }>({
  bySeverity: {},
  byTypeCode: {},
})

const totalNew = computed(() => Object.values(stats.value.bySeverity).reduce((a, b) => a + b, 0))
const highSeverity = computed(
  () => (stats.value.bySeverity.HIGH ?? 0) + (stats.value.bySeverity.CRITICAL ?? 0),
)
const mediumSeverity = computed(() => stats.value.bySeverity.MEDIUM ?? 0)
const typeCodeCount = computed(() => Object.keys(stats.value.byTypeCode).length)
const typeCodeEntries = computed(() => Object.entries(stats.value.byTypeCode).sort((a, b) => b[1] - a[1]))
const maxTypeCount = computed(() => typeCodeEntries.value.reduce((m, [, c]) => Math.max(m, c), 1))
function pctOfType(count: number) {
  return Math.round((count / maxTypeCount.value) * 100)
}

async function load() {
  try {
    stats.value = await alertApi.stats()
  } catch (e) {
    ElMessage.error('加载告警统计失败: ' + (e as Error).message)
    stats.value = { bySeverity: {}, byTypeCode: {} }
  }
}

async function seedCostDiff() {
  try {
    const r = await alertApi.seedCostDiffRule()
    ElMessage.success(r.created ? `规则已创建 (id=${r.ruleId})` : `规则已存在 (id=${r.ruleId})`)
  } catch (e) {
    ElMessage.error('播种失败: ' + (e as Error).message)
  }
}

onMounted(load)
</script>

<style scoped>
.alert-dashboard {
  padding: 16px;
}
.kpi-row {
  margin-bottom: 16px;
}
.kpi-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.kpi-value {
  font-size: 28px;
  font-weight: 600;
}
.kpi-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.kpi-total .kpi-value {
  color: #f56c6c;
}
.text-danger {
  color: #f56c6c;
}
.title {
  font-weight: 600;
}
.empty {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #67c23a;
  padding: 24px;
  justify-content: center;
}
.empty .ok {
  font-size: 24px;
}
.type-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.type-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.code-tag {
  width: 160px;
  text-align: center;
  font-family: monospace;
}
.actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
