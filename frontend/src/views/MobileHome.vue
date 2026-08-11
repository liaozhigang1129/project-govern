<template>
  <div class="mobile-home">
    <header class="mobile-home__header">
      <h1>project-govern 移动端</h1>
    </header>
    <nav role="navigation" aria-label="mobile-tabs" class="mobile-tabs">
      <button
        v-for="t in tabs"
        :key="t.code"
        :class="['tab', { active: active === t.code }]"
        :aria-current="active === t.code ? 'page' : undefined"
        @click="active = t.code"
      >
        <span class="tab-icon" aria-hidden="true">{{ t.icon }}</span>
        <span class="tab-label">{{ t.label }}</span>
      </button>
    </nav>
    <main class="mobile-home__content">
      <section v-if="active === 'approval'" aria-label="审批">
        <h2>待办审批</h2>
        <p>(接入 WP-M7-04 阶段 2: 审批 Tab 列表 + 扫码)</p>
      </section>
      <section v-else-if="active === 'timesheet'" aria-label="工时">
        <h2>工时周报</h2>
        <p>(接入 WP-M7-04 阶段 2: 工时 Tab 提交)</p>
      </section>
      <section v-else-if="active === 'notification'" aria-label="通知">
        <h2>通知中心</h2>
        <p>(接入 WP-M7-04 阶段 2: 通知 Tab 铃铛)</p>
      </section>
      <section v-else-if="active === 'project'" aria-label="项目">
        <h2>项目列表</h2>
        <p>(接入 WP-M7-04 阶段 2: 项目 Tab 详情)</p>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const tabs = [
  { code: 'approval', label: '审批', icon: '✓' },
  { code: 'timesheet', label: '工时', icon: '⏱' },
  { code: 'notification', label: '通知', icon: '🔔' },
  { code: 'project', label: '项目', icon: '📋' }
]
const active = ref('approval')
</script>

<style scoped>
.mobile-home { padding: 0; }
.mobile-home__header { padding: 12px 16px; background: var(--pmo-card, #fff); border-bottom: 1px solid var(--pmo-border, #e4e7ed); }
.mobile-home__header h1 { margin: 0; font-size: 16px; }
.mobile-tabs {
  display: flex;
  position: sticky;
  top: 0;
  background: var(--pmo-card, #fff);
  border-bottom: 1px solid var(--pmo-border, #e4e7ed);
  z-index: 10;
}
.tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 0;
  border: none;
  background: none;
  cursor: pointer;
  min-height: 56px;
  font-size: 11px;
  color: #606266;
}
.tab.active { color: #409eff; border-bottom: 2px solid #409eff; }
.tab-icon { font-size: 20px; }
.mobile-home__content { padding: 16px; }
section h2 { font-size: 16px; margin: 0 0 8px; }
section p { color: #909399; font-size: 13px; }
</style>
