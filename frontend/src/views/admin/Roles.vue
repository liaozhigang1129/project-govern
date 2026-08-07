<script setup lang="ts">
/**
 * L1-2 角色管理 — 列表页
 * 7 端点全部接入: 列表 / 新建 / 编辑 / 启停 / 删除 / 简表下拉
 * 仅 PMO_ADMIN / ADMIN 可见
 * 新增 (L1-3 配套): "菜单授权" 按钮, 弹出 RoleMenuAssignDialog
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  roleApi,
  type RoleListItem,
  type RoleCreateBody,
  type RoleUpdateBody,
} from '@/api/roles'
import RoleMenuAssignDialog from '@/components/admin/RoleMenuAssignDialog.vue'

// ============================================================
// 状态
// ============================================================
const loading = ref(false)
const includeDisabled = ref(false)
const items = ref<RoleListItem[]>([])

// 菜单授权弹窗状态 (L1-3)
const menuDlg = ref({
  visible: false,
  roleId: 0,
  roleName: '',
})
function openMenuAssign(row: RoleListItem) {
  menuDlg.value = { visible: true, roleId: row.id, roleName: row.name }
}

// 对话框 — 新建/编辑 (复用同一个)
const dlg = ref({
  visible: false,
  mode: 'create' as 'create' | 'edit',
  submitting: false,
  form: {
    id: 0,
    code: '',
    name: '',
    description: '',
    enabled: true,
    sortOrder: 100,
  } as {
    id: number
    code: string
    name: string
    description: string
    enabled: boolean
    sortOrder: number
  },
})

// ============================================================
// 加载
// ============================================================
async function load() {
  loading.value = true
  try {
    items.value = await roleApi.list(includeDisabled.value)
  } catch (e: any) {
    ElMessage.error(e?.message ?? '加载角色列表失败')
  } finally {
    loading.value = false
  }
}

// ============================================================
// 新建 / 编辑
// ============================================================
function openCreate() {
  dlg.value = {
    visible: true,
    mode: 'create',
    submitting: false,
    form: { id: 0, code: '', name: '', description: '', enabled: true, sortOrder: 100 },
  }
}

function openEdit(row: RoleListItem) {
  dlg.value = {
    visible: true,
    mode: 'edit',
    submitting: false,
    form: {
      id: row.id,
      code: row.code,
      name: row.name,
      description: row.description ?? '',
      enabled: row.enabled,
      sortOrder: row.sortOrder,
    },
  }
}

async function submit() {
  const f = dlg.value.form
  if (!f.name.trim()) { ElMessage.warning('请填写角色名'); return }
  if (dlg.value.mode === 'create' && !f.code.trim()) {
    ElMessage.warning('请填写角色 code'); return
  }
  dlg.value.submitting = true
  try {
    if (dlg.value.mode === 'create') {
      const body: RoleCreateBody = {
        code: f.code.trim(),
        name: f.name.trim(),
        description: f.description.trim() || undefined,
        enabled: f.enabled,
        sortOrder: f.sortOrder,
      }
      await roleApi.create(body)
      ElMessage.success(`角色 [${body.code}] 创建成功`)
    } else {
      const body: RoleUpdateBody = {
        name: f.name.trim(),
        description: f.description.trim() || undefined,
        enabled: f.enabled,
        sortOrder: f.sortOrder,
      }
      await roleApi.update(f.id, body)
      ElMessage.success('已更新')
    }
    dlg.value.visible = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '保存失败')
  } finally {
    dlg.value.submitting = false
  }
}

// ============================================================
// 启停 / 删除
// ============================================================
async function toggleEnabled(row: RoleListItem) {
  const op = row.enabled ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确认${op}角色 "${row.name}" (${row.code})?`,
      `${op}确认`,
      { type: 'warning' }
    )
  } catch { return }
  try {
    await roleApi.setEnabled(row.id, !row.enabled)
    ElMessage.success(`${op}成功`)
    load()
  } catch (e: any) {
    ElMessage.error(e?.message ?? `${op}失败`)
  }
}

async function onDelete(row: RoleListItem) {
  if (row.builtIn) {
    ElMessage.warning('内置角色不可删除')
    return
  }
  if (row.primaryUserCount > 0) {
    ElMessage.warning(
      `该角色仍有 ${row.primaryUserCount} 个用户作为主角色, 请先转移再删除`
    )
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认删除角色 "${row.name}" (${row.code})? 此操作不可恢复!`,
      '删除确认',
      { type: 'error' }
    )
  } catch { return }
  try {
    await roleApi.delete(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>🛡 角色管理 (L1-2)</span>
          <div style="display: flex; gap: 12px; align-items: center">
            <el-checkbox v-model="includeDisabled" @change="load">
              包含已停用
            </el-checkbox>
            <el-button type="primary" @click="openCreate">+ 新建角色</el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="items"
        border
        stripe
        style="width: 100%"
        empty-text="无角色"
      >
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 6px">
              <strong>{{ row.name }}</strong>
              <el-tag size="small" effect="plain" type="info">{{ row.code }}</el-tag>
              <el-tag v-if="row.builtIn" size="small" type="warning">内置</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          prop="description"
          label="说明"
          min-width="220"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span v-if="row.description">{{ row.description }}</span>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.enabled" type="success" size="small">启用</el-tag>
            <el-tag v-else type="info" size="small">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column
          prop="primaryUserCount"
          label="主角色用户数"
          width="120"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.primaryUserCount > 0 ? 'primary' : 'info'"
              effect="plain"
              size="small"
            >
              {{ row.primaryUserCount }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button size="small" link type="success" @click="openMenuAssign(row)">
              🧭 菜单授权
            </el-button>
            <el-button
              size="small"
              link
              :type="row.enabled ? 'danger' : 'success'"
              :disabled="row.builtIn && row.primaryUserCount > 0 && row.enabled"
              @click="toggleEnabled(row)"
            >
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
            <el-button
              size="small"
              link
              type="danger"
              :disabled="row.builtIn"
              @click="onDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑 对话框 -->
    <el-dialog
      v-model="dlg.visible"
      :title="dlg.mode === 'create' ? '新建角色' : `编辑角色 — ${dlg.form.code}`"
      width="520px"
    >
      <el-form label-width="100px">
        <el-form-item label="角色 code" required>
          <el-input
            v-model="dlg.form.code"
            :disabled="dlg.mode === 'edit'"
            placeholder="大写字母/数字/下划线, 2-32 字符"
            maxlength="32"
            show-word-limit
          />
          <div style="color: #909399; font-size: 12px; line-height: 1.4">
            建议: PMO_OPS / FINANCE_BP / QA_REVIEWER ...<br>
            内置角色: <code>PM</code> / <code>DEPT_LEAD</code> /
            <code>PMO_ADMIN</code> / <code>EXEC</code> / <code>VIEWER</code>
          </div>
        </el-form-item>
        <el-form-item label="角色名" required>
          <el-input
            v-model="dlg.form.name"
            placeholder="中文/英文均可, 给用户看的"
            maxlength="64"
          />
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="dlg.form.description"
            type="textarea"
            :rows="2"
            placeholder="选填, 该角色能干什么"
            maxlength="256"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="dlg.form.enabled" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">
            停用后, 该角色不可被分配给新用户 (已分配的不受影响)
          </span>
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number
            v-model="dlg.form.sortOrder"
            :min="0"
            :max="9999"
            :step="10"
          />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">
            数字越小越靠前
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="dlg.submitting"
          @click="submit"
        >
          确认
        </el-button>
      </template>
    </el-dialog>

    <!-- L1-3: 菜单授权弹窗 -->
    <RoleMenuAssignDialog
      v-model:visible="menuDlg.visible"
      :role-id="menuDlg.roleId"
      :role-name="menuDlg.roleName"
      @saved="load"
    />
  </div>
</template>

<style scoped>
.page { padding: 16px; }
code { background: #f5f7fa; padding: 1px 6px; border-radius: 3px; font-size: 12px; }
</style>
