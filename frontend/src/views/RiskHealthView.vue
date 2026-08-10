<script setup lang="ts">
/**
 * 风险健康度看板 (P3-风险管理)
 * 数据: GET /api/risks/health/by-project/{id} → RiskHealthSummary
 * 字段: projectId, totalCount, activeCount, criticalActive, highActive, occurredCount,
 *       maxActiveScore, byCategory, byLevel
 * 修过: 1) /risk → /risks
 *       2) onMounted 默认选第一个项目 (不再空状态)
 *       3) watch route.query.projectId (切换项目立刻 load, 不再 setTimeout 100ms)
 */
import { onMounted, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/client'
import { ElMessage } from 'element-plus'
import { DataLine, Warning, Bell, Aim, Histogram, Refresh } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

// URL ?projectId=X, 缺省时由 onMounted 选第一个
const projectId = computed(() => {
  const q = route.query.projectId
  if (q) return Number(q)
  return null
})

const projectList = ref<any[]>([])
const data = ref<any>(null)
const loading = ref(false)

async function loadProjects() {
  try {
    const r: any = await api.get('/projects?page=0&size=200')
    // 后端 /api/projects 直接返回 array, 不是 Page 包装
    projectList.value = Array.isArray(r) ? r : r.content || r.data?.content || r.data || []
    console.log('[RiskHealth] loaded projects:', projectList.value.length, projectList.value[0])
  } catch (e) {
    ElMessage.error('加载项目列表失败: ' + (e as any).message)
  }
}

async function load() {
  if (!projectId.value) return
  loading.value = true
  try {
    // 真实端点: /api/risks/health/by-project/{id} (复数)
    data.value = await api.get(`/risks/health/by-project/${projectId.value}`)
  } catch (e) {
    ElMessage.error('加载健康度失败: ' + (e as any).message)
    data.value = null
  } finally {
    loading.value = false
  }
}

function onProjectChange(v: number | undefined) {
  if (!v) return
  router.push({ path: '/risks/health', query: { projectId: v } })
  // 改用 watch route.query.projectId, 不再 setTimeout
}

// 切换 projectId 自动 reload (修复: 删 setTimeout 100ms)
watch(
  () => route.query.projectId,
  async (newQ) => {
    if (newQ) {
      await load()
    } else {
      data.value = null
    }
  },
)

onMounted(async () => {
  await loadProjects()
  // 默认选第一个项目 (用户点菜单进来直接看到数据, 不再空状态)
  if (!projectId.value && projectList.value.length > 0) {
    const first = projectList.value[0]
    router.replace({ path: '/risks/health', query: { projectId: first.id } })
    // watch 会触发 load
  }
})

function colorByScore(s: number) {
  if (s >= 15) return '#f56c6c'
  if (s >= 10) return '#e6a23c'
  return '#67c23a'
}

function colorByLevel(level: string) {
  if (level === 'CRITICAL') return '#f56c6c'
  if (level === 'HIGH') return '#e6a23c'
  if (level === 'MEDIUM') return '#67c23a'
  return '#909399'
}

const healthStatus = computed(() => {
  if (!data.value) return { label: '未评估', type: 'info' }
  if (data.value.criticalActive > 0) return { label: '🔴 需立即关注', type: 'danger' }
  if (data.value.highActive > 0) return { label: '🟡 需关注', type: 'warning' }
  if (data.value.occurredCount > 0) return { label: '🟠 风险已发生', type: 'warning' }
  if (data.value.activeCount === 0) return { label: '🟢 健康', type: 'success' }
  return { label: '🟢 平稳', type: 'success' }
})

const sortedCategories = computed(() => {
  if (!data.value?.byCategory) return []
  return Object.entries(data.value.byCategory)
    .map(([k, v]) => ({ k, v: v as number }))
    .sort((a, b) => b.v - a.v)
})

const sortedLevels = computed(() => {
  if (!data.value?.byLevel) return []
  const order = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
  return order
    .filter((l) => data.value.byLevel[l] !== undefined)
    .map((l) => ({ k: l, v: data.value.byLevel[l] }))
})
</script>

<template>
  <div class="rh-page" style="padding: 16px">
    <el-page-header :icon="null" style="margin-bottom: 12px">
      <template #content>
        <div style="display: flex; align-items: center; gap: 8px">
          <el-icon><Histogram /></el-icon>
          <span style="font-size: 18px; font-weight: 600">风险健康度看板</span>
        </div>
      </template>
      <template #extra>
        <el-select
          :model-value="projectId"
          placeholder="选择项目"
          style="width: 280px"
          @change="onProjectChange"
        >
          <el-option
            v-for="p in projectList"
            :key="p.id"
            :label="`${p.code ?? ''} ${p.name ?? ''}`"
            :value="p.id"
          />
        </el-select>
        <el-button :icon="Refresh" :disabled="!projectId" @click="load" style="margin-left: 8px">
          刷新
        </el-button>
      </template>
    </el-page-header>

    <div v-if="!projectId" class="rh-empty">
      <el-empty description="请从上方下拉选择项目" />
    </div>

    <div v-else>
      <el-tag :type="healthStatus.type as any" effect="dark" size="large" style="margin-bottom: 16px">
        整体健康度: {{ healthStatus.label }}
      </el-tag>

      <el-card v-loading="loading" shadow="never">
        <div v-if="data" class="rh-grid">
          <!-- 4 KPI 卡 -->
          <div class="rh-kpi" :style="{ borderTop: '4px solid #909399' }">
            <div class="rh-kpi-icon"><Aim /></div>
            <div class="rh-kpi-num">{{ data.totalCount }}</div>
            <div class="rh-kpi-label">风险总数</div>
          </div>
          <div class="rh-kpi" :style="{ borderTop: '4px solid #409eff' }">
            <div class="rh-kpi-icon"><DataLine /></div>
            <div class="rh-kpi-num">{{ data.activeCount }}</div>
            <div class="rh-kpi-label">活跃风险</div>
          </div>
          <div class="rh-kpi" :style="{ borderTop: '4px solid #f56c6c' }">
            <div class="rh-kpi-icon"><Warning /></div>
            <div class="rh-kpi-num" style="color: #f56c6c">{{ data.criticalActive }}</div>
            <div class="rh-kpi-label">CRITICAL 活跃</div>
          </div>
          <div class="rh-kpi" :style="{ borderTop: '4px solid #e6a23c' }">
            <div class="rh-kpi-icon"><Bell /></div>
            <div class="rh-kpi-num" style="color: #e6a23c">
              {{ data.occurredCount }}
              <span v-if="data.maxActiveScore" style="font-size: 12px; color: #909399">
                / 最高分 {{ data.maxActiveScore }}
              </span>
            </div>
            <div class="rh-kpi-label">已发生 / 最高分</div>
          </div>

          <!-- 等级分布 -->
          <div class="rh-panel" style="grid-column: 1 / span 2">
            <h4>📊 按风险等级</h4>
            <div v-if="sortedLevels.length === 0" class="rh-muted">无数据</div>
            <div v-for="lv in sortedLevels" :key="lv.k" class="rh-bar-row">
              <span :style="{ color: colorByLevel(lv.k), width: '80px', fontWeight: 600 }">
                {{ lv.k }}
              </span>
              <el-progress
                :percentage="data.activeCount ? Math.round((lv.v / data.activeCount) * 100) : 0"
                :color="colorByLevel(lv.k)"
                :stroke-width="16"
                style="flex: 1"
              />
              <span style="width: 40px; text-align: right">{{ lv.v }}</span>
            </div>
          </div>

          <!-- 类别分布 -->
          <div class="rh-panel" style="grid-column: 3 / span 2">
            <h4>📂 按风险类别</h4>
            <div v-if="sortedCategories.length === 0" class="rh-muted">无数据</div>
            <div v-for="cat in sortedCategories" :key="cat.k" class="rh-bar-row">
              <span style="width: '120px'">{{ cat.k }}</span>
              <el-progress
                :percentage="data.activeCount ? Math.round((cat.v / data.activeCount) * 100) : 0"
                :stroke-width="16"
                style="flex: 1"
              />
              <span style="width: 40px; text-align: right">{{ cat.v }}</span>
            </div>
          </div>
        </div>
        <div v-else class="rh-muted">未获取到数据</div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.rh-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.rh-kpi {
  background: #fafbfc;
  border-radius: 6px;
  padding: 16px;
  text-align: center;
  border: 1px solid #ebeef5;
}
.rh-kpi-icon {
  font-size: 24px;
  color: #909399;
  margin-bottom: 4px;
}
.rh-kpi-num {
  font-size: 32px;
  font-weight: 700;
  color: #303133;
}
.rh-kpi-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.rh-panel {
  background: #fafbfc;
  border-radius: 6px;
  padding: 16px;
  border: 1px solid #ebeef5;
}
.rh-panel h4 {
  margin: 0 0 12px;
  font-size: 14px;
  color: #303133;
}
.rh-bar-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.rh-muted {
  color: #c0c4cc;
  text-align: center;
  padding: 24px;
}
.rh-empty {
  background: #fff;
  padding: 40px;
  border-radius: 6px;
}
</style>
