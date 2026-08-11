<template>
  <div class="role-dashboard">
    <header class="role-dashboard__header">
      <h1>角色仪表盘 — {{ roleCode }}</h1>
      <nav role="navigation" aria-label="role-tabs">
        <button
          v-for="r in roles"
          :key="r"
          :class="['role-tab', { active: r === roleCode }]"
          :aria-current="r === roleCode ? 'page' : undefined"
          @click="goTo(r)"
        >
          {{ r }}
        </button>
      </nav>
    </header>
    <main v-if="data" class="role-dashboard__content">
      <section class="kpi-grid" :aria-label="`${roleCode} KPI`">
        <article v-for="(value, key) in data.kpis" :key="key" class="kpi-card">
          <h2>{{ key }}</h2>
          <p class="kpi-value">{{ value }}</p>
        </article>
      </section>
      <section v-if="data.dataQuality" class="quality-panel" aria-label="数据质量">
        <h2>数据质量指标</h2>
        <ul>
          <li v-for="(v, k) in (data.dataQuality.indicators || {})" :key="k">
            <strong>{{ k }}:</strong> {{ ((v as number) * 100).toFixed(2) }}%
          </li>
        </ul>
      </section>
    </main>
    <p v-else>加载中…</p>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/client'

const route = useRoute()
const router = useRouter()
const roleCode = ref(String(route.params.roleCode || 'PM'))
const data = ref<Record<string, any> | null>(null)
const roles = ['PM', 'PMO_ADMIN', 'EXEC', 'DEPT_LEAD', 'VIEWER', 'FINANCE', 'HR', 'AI_ADVISOR']

async function load() {
  try {
    const res = await api.get<Record<string, any>>(`/api/dashboards/role/${roleCode.value}`)
    data.value = res
  } catch (e) {
    console.error('[RoleDashboard] load failed:', e)
  }
}

function goTo(r: string) {
  roleCode.value = r
  router.push({ name: 'RoleDashboard', params: { roleCode: r } })
}

onMounted(load)
watch(() => route.params.roleCode, (v) => { if (v) { roleCode.value = String(v); load() } })
</script>

<style scoped>
.role-dashboard { padding: 16px; }
.role-dashboard__header { margin-bottom: 16px; }
.role-dashboard__header h1 { margin: 0 0 8px; font-size: 18px; }
nav[role="navigation"] { display: flex; gap: 4px; flex-wrap: wrap; }
.role-tab {
  padding: 6px 12px;
  border: 1px solid var(--pmo-border, #e4e7ed);
  background: var(--pmo-card, #fff);
  cursor: pointer;
  font-size: 13px;
  min-height: 32px;
}
.role-tab.active { background: #409eff; color: #fff; border-color: #409eff; }
.kpi-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 8px; }
.kpi-card { padding: 12px; border: 1px solid var(--pmo-border, #e4e7ed); border-radius: 4px; background: var(--pmo-card, #fff); }
.kpi-card h2 { font-size: 12px; color: #909399; margin: 0 0 4px; text-transform: uppercase; }
.kpi-value { font-size: 24px; margin: 0; font-weight: 600; }
.quality-panel { margin-top: 16px; padding: 12px; border: 1px solid var(--pmo-border, #e4e7ed); border-radius: 4px; }
.quality-panel ul { margin: 8px 0 0; padding-left: 20px; }
</style>
