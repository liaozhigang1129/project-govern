<script setup lang="ts">
/**
 * 风险矩阵视图 (P3-风险管理)
 * 包装 components/RiskMatrixView.vue, 加项目选择器
 * - 路由 /risks/matrix?projectId=3
 * 修过: 1) onMounted 默认选第一个项目
 *       2) watch route.query.projectId (切换时重渲染)
 */
import { onMounted, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/client'
import { ElMessage } from 'element-plus'
import { Histogram, Refresh } from '@element-plus/icons-vue'
import RiskMatrixView from '@/components/RiskMatrixView.vue'

const route = useRoute()
const router = useRouter()

const projectId = computed(() => {
  const q = route.query.projectId
  return q ? Number(q) : null
})

const projectList = ref<any[]>([])

async function loadProjects() {
  try {
    const r: any = await api.get('/projects?page=0&size=200')
    // 后端 /api/projects 直接返回 array, 不是 Page 包装
    projectList.value = Array.isArray(r) ? r : r.content || r.data?.content || r.data || []
    console.log('[RiskMatrix] loaded projects:', projectList.value.length)
  } catch (e) {
    ElMessage.error('加载项目列表失败: ' + (e as any).message)
  }
}

function onProjectChange(v: number | undefined) {
  if (!v) return
  router.push({ path: '/risks/matrix', query: { projectId: v } })
}

onMounted(async () => {
  await loadProjects()
  // 默认选第一个项目
  if (!projectId.value && projectList.value.length > 0) {
    const first = projectList.value[0]
    router.replace({ path: '/risks/matrix', query: { projectId: first.id } })
  }
})
</script>

<template>
  <div class="rm-page" style="padding: 16px">
    <el-page-header :icon="null" style="margin-bottom: 12px">
      <template #content>
        <div style="display: flex; align-items: center; gap: 8px">
          <el-icon><Histogram /></el-icon>
          <span style="font-size: 18px; font-weight: 600">风险矩阵 (5×5 热力图)</span>
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
        <el-button :icon="Refresh" :disabled="!projectId" style="margin-left: 8px">刷新</el-button>
      </template>
    </el-page-header>

    <el-card v-loading="false" shadow="never">
      <div v-if="!projectId" class="rm-empty">
        <el-empty description="请从上方下拉选择项目" />
      </div>
      <div v-else>
        <RiskMatrixView :project-id="projectId" :key="projectId" />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.rm-empty {
  padding: 40px;
  background: #fafbfc;
  border-radius: 6px;
}
</style>
