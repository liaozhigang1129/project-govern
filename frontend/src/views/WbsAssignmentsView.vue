<script setup lang="ts">
/**
 * WbsAssignmentsView — 项目资源分配页 (P3.2)
 *
 * 入口: /projects/:id/assignments
 *
 *  - 顶部: 项目上下文 (id + 跳转 WBS)
 *  - 主体: WbsAssignmentMatrix
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import WbsAssignmentMatrix from '@/components/WbsAssignmentMatrix.vue'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.id))
</script>

<template>
  <div class="asn-page">
    <el-page-header :icon="null" style="margin-bottom: 12px">
      <template #content>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span style="font-size: 18px; font-weight: 600">
            👥 资源分配矩阵
            <el-tag size="small" type="info" effect="plain" style="margin-left: 8px">
              项目 #{{ projectId }}
            </el-tag>
          </span>
          <div>
            <el-button @click="router.push(`/projects/${projectId}`)">
              返回项目详情
            </el-button>
            <el-button type="primary" @click="router.push(`/projects/${projectId}/wbs`)">
              打开 WBS 树
            </el-button>
          </div>
        </div>
      </template>
    </el-page-header>

    <WbsAssignmentMatrix :project-id="projectId" />
  </div>
</template>

<style scoped>
.asn-page { display: flex; flex-direction: column; }
</style>
