<template>
  <!-- Step 5 — 风险应对 -->
  <div>
    <el-alert
      type="info" :closable="false"
      title="基于 Step 2 AI 识别 / Step 3 调整后的风险,填报应对方案与成本。"
      style="margin-bottom: 16px"
    />

    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span style="font-weight: 600">⚠️ 风险及应对</span>
          <el-button type="primary" :icon="Plus" size="small" @click="addRow">添加风险</el-button>
        </div>
      </template>

      <el-table :data="rows" size="small" border>
        <el-table-column label="等级" width="120">
          <template #default="{ row }">
            <el-select v-model="row.riskLevel" size="small" style="width: 100%">
              <el-option v-for="l in LEVELS" :key="l.value" :label="l.label" :value="l.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="风险" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.riskTitle" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="AI 建议" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.riskSuggestion" size="small" type="textarea" :rows="1" />
          </template>
        </el-table-column>
        <el-table-column label="应对动作" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.responseAction" size="small" type="textarea" :rows="1" />
          </template>
        </el-table-column>
        <el-table-column label="应对成本" width="140">
          <template #default="{ row }">
            <el-input-number
              v-model="row.responseCost" :min="0" :step="1000" :precision="2"
              size="small" :controls="false" style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-select v-model="row.status" size="small" style="width: 100%">
              <el-option v-for="s in STATUSES" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="" width="60" align="center">
          <template #default="{ $index }">
            <el-button size="small" link type="danger" :icon="Delete" @click="rows.splice($index, 1)" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <div class="footer-bar">
      <span>风险应对总成本: <b style="color: #E6A23C; font-size: 18px">¥ {{ formatCost(total) }}</b></span>
      <el-button type="primary" :icon="Check" :loading="saving" @click="saveAll">保存风险应对</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Plus, Delete, Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api, { type RiskResponse } from '@/api/client'

const props = defineProps<{ initiationId: number }>()
const emit = defineEmits<{ 'update:totalCost': [n: number] }>()

const LEVELS = [
  { value: 'LOW', label: '低' },
  { value: 'MEDIUM', label: '中' },
  { value: 'HIGH', label: '高' },
  { value: 'CRITICAL', label: '极高' },
]
const STATUSES = [
  { value: 'PLANNED', label: '已规划' },
  { value: 'IN_PROGRESS', label: '进行中' },
  { value: 'DONE', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
]

const rows = ref<RiskResponse[]>([])
const saving = ref(false)

const total = computed(() =>
  rows.value.reduce((a, r) => a + (r.responseCost ?? 0), 0)
)
emit('update:totalCost', total.value)

function formatCost(v: number) {
  return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function addRow() {
  rows.value.push({
    riskTitle: '', riskLevel: 'MEDIUM', riskSuggestion: '',
    responseAction: '', responseCost: 0, status: 'PLANNED',
  })
}

async function loadExisting() {
  try {
    rows.value = await api.get<RiskResponse[]>(`/initiations/${props.initiationId}/risks`)
  } catch {/* first time */}
}

async function saveAll() {
  saving.value = true
  try {
    await api.delete(`/initiations/${props.initiationId}/risks`)
    for (const r of rows.value) {
      if (!r.riskTitle?.trim()) continue
      await api.post(`/initiations/${props.initiationId}/risks`, r)
    }
    emit('update:totalCost', total.value)
    ElMessage.success('风险应对已保存')
  } catch (e: any) {
    ElMessage.error('保存失败: ' + e.message)
  } finally {
    saving.value = false
  }
}

defineExpose({ loadExisting, total })
</script>

<style scoped>
.footer-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  padding: 16px;
  background: #fafbfc;
  border-radius: 6px;
}
</style>
