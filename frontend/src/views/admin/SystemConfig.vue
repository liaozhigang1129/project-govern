<script setup lang="ts">
/**
 * P1-4 系统参数 — Tab 分组 + 表格 + 编辑 + 复位 + 批量保存
 * 仅 PMO_ADMIN / ADMIN 可见
 */
import { onMounted, ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, Edit, RefreshLeft, Check, Close } from '@element-plus/icons-vue'
import { systemConfigApi, type SystemConfigItem, type SystemConfigValueType } from '@/api/systemConfig'

const items = ref<SystemConfigItem[]>([])
const loading = ref(false)
const activeTab = ref('all')
const searchKw = ref('')

// 编辑对话框
const editDialog = ref({
  visible: false,
  item: null as SystemConfigItem | null,
  newValue: '',
  submitting: false,
  isJsonError: false,
})

const groupOrder = ['security', 'business', 'integration']
const groupLabel: Record<string, string> = {
  security: '🔒 安全',
  business: '💼 业务',
  integration: '🔌 集成',
}

const filtered = computed(() => {
  let list = items.value
  if (activeTab.value !== 'all') list = list.filter(i => i.configGroup === activeTab.value)
  const kw = searchKw.value.trim().toLowerCase()
  if (kw) list = list.filter(i => i.configKey.toLowerCase().includes(kw) || (i.description || '').toLowerCase().includes(kw))
  return list
})

const dirtyCount = computed(() => filtered.value.filter(i => !i.isDefault).length)

async function load() {
  loading.value = true
  try {
    items.value = await systemConfigApi.list()
  } finally { loading.value = false }
}

function startEdit(item: SystemConfigItem) {
  editDialog.value.item = item
  editDialog.value.newValue = item.configValue ?? ''
  editDialog.value.isJsonError = false
  editDialog.value.visible = true
}

async function save() {
  const it = editDialog.value.item
  if (!it) return
  editDialog.value.submitting = true
  try {
    const updated = await systemConfigApi.update(it.configKey, { configValue: editDialog.value.newValue })
    // 替换
    const i = items.value.findIndex(x => x.id === it.id)
    if (i >= 0) items.value[i] = updated
    ElMessage.success('已保存,缓存已自动失效')
    editDialog.value.visible = false
  } catch (e: any) {
    ElMessage.error(e?.message ?? '保存失败')
  } finally {
    editDialog.value.submitting = false
  }
}

async function reset(item: SystemConfigItem) {
  try {
    await ElMessageBox.confirm(`把 [${item.configKey}] 复位到默认值 [${item.defaultValue}] ?`, '复位', { type: 'warning' })
    const updated = await systemConfigApi.reset(item.configKey)
    const i = items.value.findIndex(x => x.id === item.id)
    if (i >= 0) items.value[i] = updated
    ElMessage.success('已复位')
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function resetAll() {
  const dirty = items.value.filter(i => !i.isDefault)
  if (dirty.length === 0) { ElMessage.info('没有修改过'); return }
  try {
    await ElMessageBox.confirm(`当前有 ${dirty.length} 项被改过,确认全部复位?`, '全部复位', { type: 'warning' })
    for (const it of dirty) await systemConfigApi.reset(it.configKey)
    await load()
    ElMessage.success(`已复位 ${dirty.length} 项`)
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function evictCache() {
  try {
    await systemConfigApi.evictCache()
    ElMessage.success('缓存已清空,下次读取将重新从 DB 加载')
  } catch (e: any) { ElMessage.error(e?.message ?? '失败') }
}

function typeTag(t: SystemConfigValueType): '' | 'success' | 'warning' | 'info' | 'primary' {
  return ({ STRING: 'info', NUMBER: 'primary', BOOLEAN: 'success', JSON: 'warning', ENUM: 'info' } as const)[t]
}

function isMultiline(item: SystemConfigItem): boolean {
  const v = item.configValue ?? ''
  return v.length > 60 || v.includes('\n')
}

onMounted(load)
</script>

<template>
  <div style="padding: 16px">
    <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px">
      <h2 style="margin: 0">⚙️ 系统参数</h2>
      <div style="display: flex; gap: 8px; align-items: center">
        <el-input v-model="searchKw" placeholder="搜索 key 或说明" :prefix-icon="Search" clearable style="width: 240px" />
        <el-button :icon="Refresh" @click="load" :loading="loading">刷新</el-button>
        <el-button :icon="RefreshLeft" type="warning" @click="resetAll" :disabled="dirtyCount === 0">
          全部复位 ({{ dirtyCount }})
        </el-button>
        <el-button @click="evictCache" plain>清缓存</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" style="margin-bottom: 12px">
      改完保存即生效(后台 60s 兜底刷新),标 🟡 表示当前值已偏离默认。重启服务不影响 — 配置存在 DB。
    </el-alert>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane v-for="g in groupOrder" :key="g" :name="g">
        <template #label>
          <span>{{ groupLabel[g] }}</span>
          <el-badge
            :value="items.filter(i => i.configGroup === g && !i.isDefault).length"
            :hidden="items.filter(i => i.configGroup === g && !i.isDefault).length === 0"
            type="warning"
            style="margin-left: 4px"
          />
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-table
      v-loading="loading"
      :data="filtered"
      border
      stripe
      style="width: 100%"
      empty-text="无配置项"
      :default-sort="{ prop: 'sortOrder', order: 'ascending' }"
    >
      <el-table-column prop="sortOrder" label="序" width="60" sortable />
      <el-table-column prop="configKey" label="Key" min-width="280">
        <template #default="{ row }">
          <code style="background: #f0f4f8; padding: 2px 6px; border-radius: 4px; font-size: 12px">{{ row.configKey }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="valueType" label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="typeTag(row.valueType)" size="small">{{ row.valueType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前值" min-width="240">
        <template #default="{ row }">
          <span v-if="!row.isDefault" style="color: #e6a23c; margin-right: 4px">🟡</span>
          <el-tooltip v-if="isMultiline(row)" :content="row.configValue || '(空)'" placement="top">
            <code style="font-size: 12px">{{ (row.configValue || '(空)').slice(0, 60) }}{{ row.configValue && row.configValue.length > 60 ? '...' : '' }}</code>
          </el-tooltip>
          <code v-else style="font-size: 12px">{{ row.configValue || '(空)' }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="defaultValue" label="默认值" min-width="180">
        <template #default="{ row }">
          <code style="font-size: 12px; color: #909399">{{ row.defaultValue || '(空)' }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button :icon="Edit" size="small" type="primary" link @click="startEdit(row)">编辑</el-button>
          <el-button :icon="RefreshLeft" size="small" type="warning" link :disabled="row.isDefault" @click="reset(row)">复位</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <!-- 编辑对话框 -->
  <el-dialog
    v-model="editDialog.visible"
    :title="'编辑 ' + (editDialog.item?.configKey || '')"
    width="640px"
    :close-on-click-modal="false"
  >
    <el-form v-if="editDialog.item" label-width="100px">
      <el-form-item label="Key">
        <code style="background: #f0f4f8; padding: 4px 8px; border-radius: 4px">{{ editDialog.item.configKey }}</code>
      </el-form-item>
      <el-form-item label="类型">
        <el-tag :type="typeTag(editDialog.item.valueType)" size="small">{{ editDialog.item.valueType }}</el-tag>
      </el-form-item>
      <el-form-item label="说明">{{ editDialog.item.description }}</el-form-item>
      <el-form-item :label="'当前值 (' + editDialog.item.valueType + ')'">
        <!-- BOOLEAN -->
        <el-switch
          v-if="editDialog.item.valueType === 'BOOLEAN'"
          v-model="editDialog.newValue"
          active-value="true"
          inactive-value="false"
          active-text="true"
          inactive-text="false"
        />
        <!-- ENUM -->
        <el-select
          v-else-if="editDialog.item.valueType === 'ENUM' && editDialog.item.options"
          v-model="editDialog.newValue"
          style="width: 100%"
        >
          <el-option v-for="o in editDialog.item.options.split(',')" :key="o.trim()" :label="o.trim()" :value="o.trim()" />
        </el-select>
        <!-- JSON (textarea + 校验) -->
        <div v-else-if="editDialog.item.valueType === 'JSON'" style="width: 100%">
          <el-input
            v-model="editDialog.newValue"
            type="textarea"
            :rows="6"
            placeholder='["email"]'
          />
          <div :style="{ color: editDialog.isJsonError ? '#f56c6c' : '#67c23a', fontSize: '12px', marginTop: '4px' }">
            <el-icon><component :is="editDialog.isJsonError ? Close : Check" /></el-icon>
            {{ editDialog.isJsonError ? 'JSON 格式错误,保存时会被拒绝' : 'JSON 格式 OK' }}
          </div>
        </div>
        <!-- STRING / NUMBER (一行) -->
        <el-input
          v-else
          v-model="editDialog.newValue"
          :placeholder="'默认值: ' + (editDialog.item.defaultValue || '(空)')"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="editDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="editDialog.submitting" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.code, code { font-family: 'SF Mono', Menlo, Consolas, monospace; }
</style>
