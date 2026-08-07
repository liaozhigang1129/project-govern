<template>
  <!-- Step 6 — 成本预算与项目毛利 (Step 6 是「冻结视图」,数据全部来自后端) -->
  <el-row :gutter="16">
    <el-col :span="14">
      <el-card shadow="never">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <span style="font-weight: 600">📊 预算明细 (实时联动)</span>
            <el-button size="small" :icon="RefreshRight" @click="loadAll" :loading="loading">
              刷新
            </el-button>
          </div>
        </template>

        <el-descriptions :column="1" border size="default">
          <el-descriptions-item label="📄 合同金额 (联动立项)">
            <span style="font-weight: 600; font-size: 16px; color: #409EFF">
              ¥ {{ fmt(contractAmount) }}
            </span>
            <el-tag
              v-if="contractAmount > 0" type="success" size="small" effect="plain" style="margin-left: 8px"
            >来自立项表</el-tag>
            <span v-else style="color: #F56C6C; font-size: 12px; margin-left: 8px">
              ⚠️ 立项未填合同金额,请回 Step 1 填写
            </span>
          </el-descriptions-item>

          <el-descriptions-item label="👥 资源派遣小计 (Step 4)">
            <span style="color: #E6A23C; font-weight: 600">¥ {{ fmt(resourceCost) }}</span>
            <span style="color: #909399; font-size: 12px; margin-left: 8px">
              后端汇总: {{ resourceCount }} 条派遣 · 只读
            </span>
          </el-descriptions-item>

          <el-descriptions-item label="⚠️ 风险应对小计 (Step 5)">
            <span style="color: #E6A23C; font-weight: 600">¥ {{ fmt(riskCost) }}</span>
            <span style="color: #909399; font-size: 12px; margin-left: 8px">
              后端汇总: {{ riskCount }} 项应对 · 只读
            </span>
          </el-descriptions-item>

          <el-descriptions-item label="📦 其他成本 (差旅/采购)">
            <el-input-number
              v-model="otherCost"
              :min="0" :step="1000" :precision="2" :controls="false"
              size="default" style="width: 220px"
            />
            <span style="color: #909399; font-size: 12px; margin-left: 8px">
              本页可调,冻结时锁定
            </span>
          </el-descriptions-item>

          <el-descriptions-item label="💸 总成本">
            <span style="color: #F56C6C; font-weight: 700; font-size: 18px">
              ¥ {{ fmt(totalCost) }}
            </span>
          </el-descriptions-item>

          <el-descriptions-item label="📐 计算公式">
            <code style="color: #606266">
              毛利 = 合同金额 - 总成本 = 合同 - (资源 + 风险 + 其他)
            </code>
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="!validationOk" type="error" :closable="false" style="margin-top: 12px"
          :title="validationMsg"
        />

        <!-- ⚠️ 浮动预览:点了「冻结」后才真正生效 -->
        <el-card
          shadow="never" style="margin-top: 12px; background: #fafbfc"
          header-class-name="preview-header"
        >
          <template #header>
            <span style="font-weight: 600">🔍 冻结预览 (点击下方按钮前请确认)</span>
          </template>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="合同金额">¥ {{ fmt(contractAmount) }}</el-descriptions-item>
            <el-descriptions-item label="总成本">¥ {{ fmt(totalCost) }}</el-descriptions-item>
            <el-descriptions-item label="毛利 / 毛利率">
              <span :style="{ color: previewMargin >= 0 ? '#67C23A' : '#F56C6C', fontWeight: 600 }">
                ¥ {{ fmt(previewMargin) }} ({{ previewMarginPct.toFixed(2) }}%)
              </span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <div style="margin-top: 16px; text-align: right">
          <el-button type="primary" :icon="Coin" :loading="freezing" @click="freeze">
            冻结预算 & 计算毛利
          </el-button>
          <el-button
            v-if="frozen" type="warning" :icon="RefreshRight"
            plain style="margin-left: 8px" @click="loadAll"
          >重新载入</el-button>
        </div>
      </el-card>
    </el-col>

    <el-col :span="10">
      <el-card shadow="never" class="margin-card">
        <template #header>
          <span style="font-weight: 600">💎 项目毛利 ({{ frozen ? '已冻结' : '预览' }})</span>
        </template>

        <div class="big-row">
          <span class="label">合同金额</span>
          <span class="val contract">¥ {{ fmt(displayContract) }}</span>
        </div>
        <div class="big-row">
          <span class="label">总成本</span>
          <span class="val cost">¥ {{ fmt(displayTotal) }}</span>
        </div>
        <el-divider />
        <div class="big-row hero">
          <span class="label">毛利</span>
          <span class="val margin" :style="{ color: displayMargin >= 0 ? '#67C23A' : '#F56C6C' }">
            ¥ {{ fmt(displayMargin) }}
          </span>
        </div>
        <div class="big-row hero">
          <span class="label">毛利率</span>
          <span class="val margin" :style="{ color: displayMargin >= 0 ? '#67C23A' : '#F56C6C' }">
            {{ displayMarginPct.toFixed(2) }}%
          </span>
        </div>

        <el-progress
          :percentage="Math.max(0, Math.min(100, displayMarginPct))"
          :stroke-width="14"
          :color="displayMargin >= 0 ? '#67C23A' : '#F56C6C'"
          style="margin-top: 12px"
        />

        <div v-if="frozen" class="frozen-tag">
          <el-icon><Lock /></el-icon>
          预算已冻结于 {{ fmtDt(frozenAt) }} · 快照 ID #{{ frozenId }}
        </div>
        <div v-else class="frozen-tag" style="color: #E6A23C; background: #fdf6ec">
          <el-icon><Warning /></el-icon>
          当前是预览值,点击「冻结预算」才会正式生效并写入 budget_freeze
        </div>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Coin, Lock, RefreshRight, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api, { type BudgetFreeze } from '@/api/client'

const props = defineProps<{
  initiationId: number
}>()

// ==================== 后端实时拉 ====================
const loading = ref(false)
const freezing = ref(false)

// 立项表
const initiationContract = ref(0)
// 资源派遣 (后端聚合)
const resourceCost = ref(0)
const resourceCount = ref(0)
// 风险应对 (后端聚合)
const riskCost = ref(0)
const riskCount = ref(0)
// 其他成本(只本组件可改)
const otherCost = ref(0)
// 已冻结的快照
const frozen = ref(false)
const frozenAt = ref('')
const frozenId = ref<number | null>(null)
const snapshotContract = ref(0)

async function loadAll() {
  loading.value = true
  try {
    // 1) 立项表(拿合同金额)
    const init = await api.get<any>(`/initiations/${props.initiationId}`)
    initiationContract.value = Number(init.contractAmount ?? 0)

    // 2) 资源派遣:列表 + 汇总
    const resources = await api.get<any[]>(`/initiations/${props.initiationId}/resource-plans`)
      .catch(() => [])
    resourceCount.value = (resources ?? []).length
    resourceCost.value = (resources ?? []).reduce(
      (a: number, r: any) => a + Number(r.costAmount ?? 0), 0
    )

    // 3) 风险应对:列表 + 汇总
    const risks = await api.get<any[]>(`/initiations/${props.initiationId}/risks`)
      .catch(() => [])
    riskCount.value = (risks ?? []).length
    riskCost.value = (risks ?? []).reduce(
      (a: number, r: any) => a + Number(r.responseCost ?? 0), 0
    )

    // 4) 已冻结快照
    const f = await api.get<BudgetFreeze>(`/initiations/${props.initiationId}/budget-freeze/latest`)
      .catch(() => null) as any
    if (f && f.id) {
      frozen.value = true
      frozenAt.value = f.frozenAt ?? ''
      frozenId.value = f.id
      snapshotContract.value = Number(f.contractAmount ?? 0)
      otherCost.value = Number(f.otherCost ?? 0)
      // 资源/风险成本以快照为准(冻结后不可变)
      resourceCost.value = Number(f.resourceCost ?? resourceCost.value)
      riskCost.value = Number(f.riskCost ?? riskCost.value)
    } else {
      frozen.value = false
      frozenAt.value = ''
      frozenId.value = null
    }
  } catch (e: any) {
    ElMessage.error('预算数据加载失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

// ==================== 派生量 ====================
const contractAmount = computed(() => initiationContract.value)
const totalCost = computed(() =>
  (resourceCost.value ?? 0) + (riskCost.value ?? 0) + (otherCost.value ?? 0)
)
const previewMargin = computed(() => contractAmount.value - totalCost.value)
const previewMarginPct = computed(() =>
  contractAmount.value ? (previewMargin.value / contractAmount.value) * 100 : 0
)

// 已冻结视图显示快照值,否则显示预览值
const displayContract = computed(() =>
  frozen.value ? snapshotContract.value : contractAmount.value
)
const displayTotal = computed(() =>
  frozen.value
    ? ((resourceCost.value ?? 0) + (riskCost.value ?? 0) + (otherCost.value ?? 0))
    : totalCost.value
)
const displayMargin = computed(() => displayContract.value - displayTotal.value)
const displayMarginPct = computed(() =>
  displayContract.value ? (displayMargin.value / displayContract.value) * 100 : 0
)

const validationOk = computed(() => contractAmount.value > 0)
const validationMsg = computed(() =>
  contractAmount.value <= 0 ? '请先在 Step 1「基础信息」中填写合同金额' : ''
)

function fmt(v: number | undefined) {
  return (v ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function fmtDt(dt: string) {
  return dt ? dt.replace('T', ' ').slice(0, 19) : ''
}

async function freeze() {
  if (!validationOk.value) {
    ElMessage.warning(validationMsg.value)
    return
  }
  freezing.value = true
  try {
    const r = await api.post<BudgetFreeze>(`/initiations/${props.initiationId}/budget-freeze`, {
      otherCost: otherCost.value,
      contractAmountOverride: null,   // 使用立项表的合同金额
    }) as any
    frozen.value = true
    frozenAt.value = r.frozenAt ?? ''
    frozenId.value = r.id ?? null
    snapshotContract.value = Number(r.contractAmount ?? contractAmount.value)
    ElMessage.success(`预算已冻结,毛利 ¥${fmt(r.margin)} 已写入快照 #${r.id}`)
    // 冻结后重新拉一次,确保 resourceCost/riskCost 与后端一致
    await loadAll()
  } catch (e: any) {
    ElMessage.error('冻结失败: ' + e.message)
  } finally {
    freezing.value = false
  }
}

watch(() => props.initiationId, () => {
  if (props.initiationId) loadAll()
}, { immediate: true })
onMounted(() => { if (props.initiationId) loadAll() })

defineExpose({ loadAll })
</script>

<style scoped>
.margin-card {
  background: linear-gradient(135deg, #f8fbff 0%, #ffffff 100%);
  border: 1px solid #e1f0ff;
}
.big-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 8px 0;
}
.big-row .label {
  color: #606266;
  font-size: 13px;
}
.big-row .val {
  font-family: 'Roboto Mono', monospace;
  font-weight: 600;
  font-size: 16px;
}
.big-row.hero .val {
  font-size: 24px;
}
.frozen-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 8px 12px;
  background: #f0f9ff;
  border-radius: 4px;
  color: #1890ff;
  font-size: 12px;
}
:deep(.preview-header) {
  background: #fafbfc !important;
}
</style>