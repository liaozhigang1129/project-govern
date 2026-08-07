<script setup lang="ts">
/**
 * RiskView — 项目风险管理容器页 (P4)
 *
 * 风格跟 WbsView 一致:
 *  - 顶: <el-page-header> 标题 + 项目 #id 标签 + 操作按钮
 *  - 中: 健康度 KPI 卡片条 (activeCount / critical / high / maxScore)
 *  - 主体: 3 个 tab 切换 — 列表 / 矩阵 / (健康度详情)
 *  - 抽屉: RiskDetailDrawer (点行 / 选中弹)
 *  - 弹窗: RiskFormDialog (新建 / 编辑)
 *
 * 顶栏入口:
 *  - "全部 / 活跃" 切换 (走 list 组件的 scope prop)
 *  - "新建风险" 按钮 (走 store.save, 不开 form 也行, 这里保留 form 给填写)
 *
 * 数据源: useRiskStore (Pinia)
 *  - 列表 tab: loadList(projectId, scope)
 *  - 矩阵 tab: loadMatrix(projectId)
 *  - KPI tab:   loadHealth(projectId) + RiskHealthSummary.byCategory / byLevel
 */
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Warning, Histogram, DataLine, Plus, Refresh } from '@element-plus/icons-vue'
import RiskList from '@/components/RiskList.vue'
import RiskMatrixView from '@/components/RiskMatrixView.vue'
import RiskDetailDrawer from '@/components/RiskDetailDrawer.vue'
import RiskFormDialog from '@/components/RiskFormDialog.vue'
import { useRiskStore } from '@/stores/risk'
import api from '@/api/client'
import type { RiskItem } from '@/api/risk'

const route = useRoute()
const router = useRouter()
// PD-3-12: 支持 projectId=0 (无项目, 全局入口) — 0 = 让用户选项目
const projectId = computed(() => {
  const v = Number(route.params.id ?? 0)
  return Number.isFinite(v) && v > 0 ? v : 0
})
const store = useRiskStore()

// PD-3-12: 全局 /risks 入口用 — 让用户选项目, 然后跳到 /risks/{id}
const chosenProjectId = ref<number | null>(null)
const projectOptions = ref<Array<{ id: number; code: string; name: string }>>([])
async function loadProjects() {
  try {
    const list = await api.get('/projects?page=0&size=200')
    projectOptions.value = (list as any).content ?? list
  } catch (e: any) {
    ElMessage.error('加载项目列表失败: ' + (e?.message ?? ''))
  }
}
watch(chosenProjectId, (v) => {
  if (v) router.push(`/risks/${v}`)
})

/** 视图模式: 列表 / 矩阵 / 健康度 */
const viewMode = ref<'list' | 'matrix' | 'health'>('list')

/** 列表 scope: active 默认, "全部" 切到 all */
const listScope = ref<'active' | 'all'>('active')

/** 选中行 (供 drawer) */
const selected = ref<RiskItem | null>(null)

/** 编辑/新建弹窗 */
const formDialog = ref({
  visible: false,
  risk: null as RiskItem | null,
})

/** 详情抽屉 */
const detailDrawer = ref({
  visible: false,
  risk: null as RiskItem | null,
})

/** KPI 派生 */
const health = computed(() => store.healthByProject.get(projectId.value))
const kpiLoading = computed(() => store.isLoading(`health:${projectId.value}`))

function levelColor(n: number) {
  if (n >= 16) return '#f56c6c'
  if (n >= 10) return '#e6a23c'
  return '#67c23a'
}

// ============================================================
// 事件处理
// ============================================================

function onSelect(risk: RiskItem) {
  selected.value = risk
  detailDrawer.value = { visible: true, risk }
}
function onEdit(risk: RiskItem) {
  formDialog.value = { visible: true, risk }
}
function onCreate() {
  formDialog.value = { visible: true, risk: null }
}
function onFormSaved(_saved: RiskItem) {
  // 列表 store.save 已同步缓存, 矩阵/健康度让它自己失效重拉
  if (viewMode.value === 'matrix') store.loadMatrix(projectId.value)
  if (viewMode.value === 'health') store.loadHealth(projectId.value)
  // drawer 选中的也刷一下
  if (detailDrawer.value.risk?.id === _saved.id) {
    detailDrawer.value = { visible: true, risk: _saved }
  }
}
function onDetailEdit(risk: RiskItem) {
  // drawer 内点编辑 → 关 drawer 开 form
  detailDrawer.value.visible = false
  formDialog.value = { visible: true, risk }
}
function refreshAll() {
  store.loadList(projectId.value, listScope.value !== 'all')
  if (viewMode.value === 'matrix') store.loadMatrix(projectId.value)
  if (viewMode.value === 'health') store.loadHealth(projectId.value)
  if (detailDrawer.value.risk?.id) {
    store.loadResponses(detailDrawer.value.risk.id)
    store.loadHistory(detailDrawer.value.risk.id)
  }
}

// ============================================================
// 生命周期: 进入页面 + 切 tab + 切 scope 都刷
// ============================================================
onMounted(() => {
  if (projectId.value > 0) refreshAll()
  else if (projectOptions.value.length === 0) loadProjects()
})
watch(() => projectId.value, (v) => {
  if (v > 0) refreshAll()
})
watch(() => viewMode.value, (v) => {
  if (projectId.value > 0) {
    if (v === 'matrix') store.loadMatrix(projectId.value)
    if (v === 'health') store.loadHealth(projectId.value)
  }
})
watch(() => listScope.value, () => {
  if (projectId.value > 0) store.loadList(projectId.value, listScope.value !== 'all')
})
</script>

<template>
  <div class="risk-page">
    <!-- 顶栏 -->
    <el-page-header :icon="null" style="margin-bottom: 12px">
      <template #content>
        <div style="display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap">
          <span style="font-size: 18px; font-weight: 600">
            <el-icon><Warning /></el-icon>
            风险管理
            <el-tag v-if="projectId > 0" size="small" type="info" effect="plain" style="margin-left: 8px">
              项目 #{{ projectId }}
            </el-tag>
          </span>
          <div style="display: flex; gap: 8px; align-items: center">
            <!-- PD-3-12: 无 projectId (来自 /risks 全局入口) 时让用户先选项目 -->
            <el-select
              v-if="projectId <= 0"
              v-model="chosenProjectId"
              placeholder="选择项目..."
              size="default"
              style="width: 240px"
              filterable
            >
              <el-option
                v-for="p in projectOptions"
                :key="p.id"
                :label="`${p.code} ${p.name}`"
                :value="p.id"
              />
            </el-select>
            <el-radio-group v-model="listScope" size="default" v-if="viewMode === 'list' && projectId > 0">
              <el-radio-button value="active">活跃</el-radio-button>
              <el-radio-button value="all">全部</el-radio-button>
            </el-radio-group>
            <el-button v-if="projectId > 0" type="primary" @click="onCreate">
              <el-icon><Plus /></el-icon> 新建风险
            </el-button>
            <el-button v-if="projectId > 0" text @click="refreshAll">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>
        </div>
      </template>
    </el-page-header>

    <!-- 没选项目时 (全局入口) 提示用户选项目 -->
    <el-empty
      v-if="projectId <= 0"
      description="请先选择一个项目"
    >
      <el-button v-if="projectOptions.length === 0" type="primary" @click="loadProjects">加载项目列表</el-button>
    </el-empty>

    <!-- 健康度 KPI 卡片条 (顶部常驻, 给 PMO 一眼看态势) -->
    <div v-if="health" class="kpi-bar">
      <div class="kpi-cell">
        <div class="kpi-label">总风险</div>
        <div class="kpi-value">{{ health.totalCount }}</div>
      </div>
      <div class="kpi-cell">
        <div class="kpi-label">活跃</div>
        <div class="kpi-value" style="color: #409eff">{{ health.activeCount }}</div>
      </div>
      <div class="kpi-cell kpi-warn">
        <div class="kpi-label">🔴 严重 (CRITICAL)</div>
        <div class="kpi-value" style="color: #f56c6c">{{ health.criticalActive }}</div>
      </div>
      <div class="kpi-cell kpi-warn">
        <div class="kpi-label">🟠 高 (HIGH)</div>
        <div class="kpi-value" style="color: #e6a23c">{{ health.highActive }}</div>
      </div>
      <div class="kpi-cell">
        <div class="kpi-label">已发生</div>
        <div class="kpi-value" style="color: #909399">{{ health.occurredCount }}</div>
      </div>
      <div class="kpi-cell">
        <div class="kpi-label">最高分</div>
        <div class="kpi-value" :style="{ color: levelColor(health.maxActiveScore) }">
          {{ health.maxActiveScore || 0 }}
        </div>
      </div>
    </div>

    <!-- 主体: 视图切换 (projectId > 0 才渲染) -->
    <el-card v-if="projectId > 0" shadow="never">
      <template #header>
        <el-radio-group v-model="viewMode" size="default">
          <el-radio-button value="list">📋 列表</el-radio-button>
          <el-radio-button value="matrix">
            <el-icon><Histogram /></el-icon> 5x5 矩阵
          </el-radio-button>
          <el-radio-button value="health">
            <el-icon><DataLine /></el-icon> 健康度
          </el-radio-button>
        </el-radio-group>
      </template>

      <!-- 列表 tab -->
      <RiskList
        v-if="viewMode === 'list'"
        :project-id="projectId"
        :scope="listScope"
        :can-delete="true"
        @select="onSelect"
        @edit="onEdit"
        @create="onCreate"
      />

      <!-- 矩阵 tab -->
      <RiskMatrixView
        v-else-if="viewMode === 'matrix'"
        :project-id="projectId"
      />

      <!-- 健康度 tab (KPI 分类详情) -->
      <div v-else-if="viewMode === 'health'" v-loading="kpiLoading">
        <el-empty v-if="!health" description="暂无风险数据" />
        <template v-else>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-card shadow="never" header="按分类 (category)">
                <div v-if="Object.keys(health.byCategory).length === 0" class="muted">无活跃风险</div>
                <div v-else class="bar-list">
                  <div v-for="(n, k) in health.byCategory" :key="k" class="bar-row">
                    <span class="bar-label">{{ k }}</span>
                    <el-progress
                      :percentage="n / health.activeCount * 100"
                      :format="() => n"
                      :stroke-width="14"
                    />
                  </div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card shadow="never" header="按等级 (level)">
                <div v-if="Object.keys(health.byLevel).length === 0" class="muted">无活跃风险</div>
                <div v-else class="bar-list">
                  <div v-for="(n, k) in health.byLevel" :key="k" class="bar-row">
                    <span class="bar-label">
                      <el-tag
                        :type="k === 'CRITICAL' ? 'danger' : k === 'HIGH' ? 'warning' : k === 'MEDIUM' ? '' : 'success'"
                        effect="dark" size="small"
                      >{{ k }}</el-tag>
                    </span>
                    <el-progress
                      :percentage="n / health.activeCount * 100"
                      :format="() => n"
                      :stroke-width="14"
                      :color="k === 'CRITICAL' ? '#f56c6c' : k === 'HIGH' ? '#e6a23c' : k === 'MEDIUM' ? '#909399' : '#67c23a'"
                    />
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </template>
      </div>
    </el-card>

    <!-- 抽屉 + 弹窗 -->
    <RiskDetailDrawer
      v-model="detailDrawer.visible"
      :risk="detailDrawer.risk"
      @edit="onDetailEdit"
    />
    <RiskFormDialog
      v-model="formDialog.visible"
      :project-id="projectId"
      :risk="formDialog.risk"
      @saved="onFormSaved"
    />
  </div>
</template>

<style scoped>
.risk-page { display: flex; flex-direction: column; gap: 12px; }

/* KPI 卡片条 */
.kpi-bar {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 14px;
}
.kpi-cell {
  text-align: center;
  padding: 6px 0;
  border-right: 1px solid #ebeef5;
}
.kpi-cell:last-child { border-right: none; }
.kpi-warn { background: #fef0f0; border-radius: 4px; border-right: none; }
.kpi-label { font-size: 12px; color: #909399; margin-bottom: 4px; }
.kpi-value { font-size: 22px; font-weight: 700; color: #303133; }

/* 健康度 tab */
.muted { color: #c0c4cc; font-size: 13px; text-align: center; padding: 12px; }
.bar-list { display: flex; flex-direction: column; gap: 8px; }
.bar-row { display: flex; align-items: center; gap: 12px; }
.bar-label { width: 100px; font-size: 12px; color: #606266; }
.bar-row :deep(.el-progress) { flex: 1; }
</style>
