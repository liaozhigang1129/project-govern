<script setup lang="ts">
/**
 * P6-资源管道大盘
 *  4 KPI + 人员×周热力图 + 部门产能 + 技能矩阵 + 加班预警
 */
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getResourceKpis,
  getCapacityMatrix,
  getSkillMatrix,
  getOverloadAlerts,
  getDepartmentCapacity,
  type ResourceKpis,
  type CapacityMatrix,
  type SkillStat,
  type OverloadAlert,
  type DeptCapacity
} from '@/api/resourcePipeline'

const kpis = ref<ResourceKpis | null>(null)
const matrix = ref<CapacityMatrix | null>(null)
const skills = ref<SkillStat[]>([])
const alerts = ref<OverloadAlert[]>([])
const depts = ref<DeptCapacity[]>([])
const loading = ref(false)
const from = ref(new Date(Date.now() - 7 * 86400000).toISOString().slice(0, 10))
const to = ref(new Date(Date.now() + 28 * 86400000).toISOString().slice(0, 10))

async function load() {
  loading.value = true
  try {
    const [k, m, s, a, d] = await Promise.all([
      getResourceKpis(),
      getCapacityMatrix(from.value, to.value),
      getSkillMatrix(),
      getOverloadAlerts(),
      getDepartmentCapacity()
    ])
    kpis.value = k
    matrix.value = m
    skills.value = s
    alerts.value = a
    depts.value = d
  } catch (e) {
    ElMessage.error('加载失败: ' + (e as any).message)
  } finally {
    loading.value = false
  }
}

function colorByPct(pct: number) {
  if (pct >= 100) return '#f56c6c'
  if (pct >= 80) return '#e6a23c'
  if (pct >= 50) return '#67c23a'
  if (pct >= 20) return '#409eff'
  return '#ebeef5'
}

const maxAlloc = computed(() => 200)

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <h2 class="page-title">📊 资源管道 · 资源管理协同</h2>

    <el-row :gutter="16" class="kpi-row">
      <el-col :span="4">
        <el-card shadow="hover" class="kpi-card kpi-blue">
          <div class="kpi-label">总资源</div>
          <div class="kpi-value">{{ kpis?.totalResources ?? 0 }}</div>
          <div class="kpi-sub">已登记 {{ kpis?.totalSkills ?? 0 }} 项技能</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="kpi-card kpi-green">
          <div class="kpi-label">已分配</div>
          <div class="kpi-value">{{ kpis?.allocated ?? 0 }}</div>
          <div class="kpi-sub">在岗 {{ kpis?.activeProjects ?? 0 }} 个项目</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="kpi-card kpi-orange">
          <div class="kpi-label">空闲</div>
          <div class="kpi-value">{{ kpis?.idle ?? 0 }}</div>
          <div class="kpi-sub">平均分配率 {{ kpis?.avgAllocation ?? 0 }}%</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="kpi-card" :class="(kpis?.overloaded ?? 0) > 0 ? 'kpi-red' : 'kpi-gray'">
          <div class="kpi-label">加班预警</div>
          <div class="kpi-value">{{ kpis?.overloaded ?? 0 }}</div>
          <div class="kpi-sub">分配率 &gt; 100%</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="kpi-card kpi-purple">
          <div class="kpi-label">利用率</div>
          <div class="kpi-value">{{ Math.round(kpis?.utilization ?? 0) }}%</div>
          <div class="kpi-sub">已分配 / (已分配+空闲)</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="util-card" shadow="hover">
      <div class="util-title">整体利用率</div>
      <el-progress
        :percentage="Math.round(kpis?.utilization ?? 0)"
        :color="(kpis?.utilization ?? 0) > 85 ? '#f56c6c' : (kpis?.utilization ?? 0) > 70 ? '#e6a23c' : '#67c23a'"
        :stroke-width="18"
        text-inside
      />
    </el-card>

    <div class="filter-bar">
      <span>区间:</span>
      <el-date-picker v-model="from" type="date" value-format="YYYY-MM-DD" />
      <span>~</span>
      <el-date-picker v-model="to" type="date" value-format="YYYY-MM-DD" />
      <el-button type="primary" @click="load">刷新</el-button>
    </div>

    <el-card shadow="hover">
      <h4 class="section">🌡 人员 × 周 产能热力图</h4>
      <div v-if="!matrix || !matrix.users.length" class="empty">暂无数据</div>
      <div v-else class="heatmap-wrapper">
        <table class="heatmap">
          <thead>
            <tr>
              <th class="user-col">人员</th>
              <th v-for="w in matrix.weeks" :key="w" class="week-col">
                {{ w.slice(5) }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in matrix.users" :key="u.userId">
              <td class="user-col">U#{{ u.userId }}</td>
              <td
                v-for="w in matrix.weeks"
                :key="w"
                class="week-cell"
                :style="{ background: colorByPct(u.weeks[w]?.allocPct ?? 0) }"
                :title="`W${w}: 分配 ${u.weeks[w]?.allocPct ?? 0}%`"
              >
                {{ u.weeks[w]?.allocPct ? Math.round(u.weeks[w]?.allocPct) : '' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </el-card>

    <el-row :gutter="16" style="margin-top: 16px;">
      <el-col :span="12">
        <el-card shadow="hover">
          <h4 class="section">🧠 技能矩阵 Top-20</h4>
          <el-table :data="skills" stripe size="small" max-height="380">
            <el-table-column prop="skillCode" label="技能" width="120" />
            <el-table-column prop="count" label="人数" width="80" align="right" />
            <el-table-column label="平均等级" width="180">
              <template #default="{ row }">
                <el-rate v-model="row.avgLevel" :max="5" disabled show-score :colors="['#67c23a','#409eff','#e6a23c']" />
              </template>
            </el-table-column>
            <el-table-column prop="certified" label="已认证" width="80" align="right">
              <template #default="{ row }">
                <el-tag v-if="row.certified > 0" type="success" size="small">{{ row.certified }}</el-tag>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="hover">
          <h4 class="section">🏢 部门产能分布</h4>
          <el-table :data="depts" stripe size="small" max-height="380">
            <el-table-column prop="departmentName" label="部门" />
            <el-table-column prop="headCount" label="人数" width="80" align="right" />
            <el-table-column label="平均分配率" width="180">
              <template #default="{ row }">
                <el-progress
                  :percentage="Math.min(100, Math.round(row.totalAllocation))"
                  :color="row.totalAllocation > 100 ? '#f56c6c' : '#409eff'"
                />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card v-if="alerts.length" shadow="hover" class="alert-card">
      <h4 class="section">🚨 加班预警 (分配率 &gt; 100%)</h4>
      <el-table :data="alerts" stripe size="small">
        <el-table-column prop="userName" label="人员" width="120" />
        <el-table-column prop="departmentName" label="部门" width="120" />
        <el-table-column label="总分配率" width="160">
          <template #default="{ row }">
            <el-tag :type="row.allocSum > 150 ? 'danger' : 'warning'" effect="dark">
              {{ row.allocSum }}%
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="projectCount" label="项目数" width="100" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.page { padding: 16px; }
.page-title { margin: 0 0 16px; color: #303133; }
.kpi-row { margin-bottom: 16px; }
.kpi-card {
  text-align: center;
  .kpi-label { font-size: 12px; color: #909399; }
  .kpi-value { font-size: 32px; font-weight: 600; margin: 8px 0; }
  .kpi-sub { font-size: 11px; color: #c0c4cc; }
}
.kpi-blue .kpi-value { color: #409eff; }
.kpi-green .kpi-value { color: #67c23a; }
.kpi-orange .kpi-value { color: #e6a23c; }
.kpi-red .kpi-value { color: #f56c6c; }
.kpi-gray .kpi-value { color: #606266; }
.kpi-purple .kpi-value { color: #9c27b0; }
.util-card { margin-bottom: 16px; }
.util-title { font-size: 13px; color: #606266; margin-bottom: 8px; }
.filter-bar {
  display: flex; align-items: center; gap: 8px; margin-bottom: 16px;
  font-size: 13px; color: #606266;
}
.section { margin: 0 0 12px; color: #303133; font-size: 14px; }
.heatmap-wrapper { overflow-x: auto; }
.heatmap {
  border-collapse: separate; border-spacing: 2px; font-size: 12px;
  .user-col {
    position: sticky; left: 0; background: #fafbfc; z-index: 1;
    padding: 4px 8px; min-width: 80px; text-align: left;
  }
  .week-col { padding: 4px 8px; min-width: 60px; color: #909399; }
  .week-cell {
    padding: 4px 8px; min-width: 60px; text-align: center;
    color: #fff; font-weight: 500; border-radius: 3px;
  }
}
.alert-card { margin-top: 16px; border: 1px solid #fbc4c4; }
.empty { text-align: center; color: #c0c4cc; padding: 30px; }
.muted { color: #c0c4cc; }
</style>
