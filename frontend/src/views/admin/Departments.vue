<script setup lang="ts">
/**
 * L1-3 部门管理 — 树状表格 + CRUD
 * 7 端点全部接入
 * 仅 PMO_ADMIN / ADMIN 可见
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  departmentApi,
  type DepartmentNode,
  type DepartmentCreateBody,
  type DepartmentUpdateBody,
} from '@/api/departments'

// ============================================================
// 状态
// ============================================================
const loading = ref(false)
const tree = ref<DepartmentNode[]>([])

// 平铺节点 (用于按 id 查 code/name)
const flatMap = ref<Map<number, DepartmentNode>>(new Map())

function flatten(list: DepartmentNode[], into: Map<number, DepartmentNode>) {
  for (const n of list) {
    into.set(n.id, n)
    if (n.children?.length) flatten(n.children, into)
  }
}

async function load() {
  loading.value = true
  try {
    tree.value = await departmentApi.tree()
    flatMap.value = new Map()
    flatten(tree.value, flatMap.value)
  } catch (e: any) {
    ElMessage.error(e?.message ?? '加载部门树失败')
  } finally {
    loading.value = false
  }
}

// ============================================================
// 对话框 (新建/编辑 复用)
// ============================================================
interface DialogForm {
  id: number
  mode: 'create' | 'edit'
  code: string
  name: string
  parentId: number | null
  sortOrder: number
  enabled: boolean
  submitting: boolean
}

const dlg = ref<{
  visible: boolean
  form: DialogForm
}>({
  visible: false,
  form: emptyForm(),
})

function emptyForm(): DialogForm {
  return {
    id: 0,
    mode: 'create',
    code: '',
    name: '',
    parentId: null,
    sortOrder: 0,
    enabled: true,
    submitting: false,
  }
}

function openCreate(parentId: number | null = null) {
  dlg.value = {
    visible: true,
    form: { ...emptyForm(), parentId, sortOrder: nextSortOrderFor(parentId) },
  }
}

function openEdit(node: DepartmentNode) {
  dlg.value = {
    visible: true,
    form: {
      id: node.id,
      mode: 'edit',
      code: node.code,
      name: node.name,
      parentId: node.parentId,
      sortOrder: node.sortOrder,
      enabled: node.enabled,
      submitting: false,
    },
  }
}

function nextSortOrderFor(parentId: number | null): number {
  // 找同 parent 下的最大 sortOrder + 10
  const siblings = [...flatMap.value.values()].filter((n) => n.parentId === parentId)
  if (!siblings.length) return 0
  return Math.max(...siblings.map((s) => s.sortOrder)) + 10
}

async function submit() {
  const f = dlg.value.form
  if (!f.name.trim()) {
    ElMessage.warning('请填写部门名')
    return
  }
  if (f.mode === 'create' && !f.code.trim()) {
    ElMessage.warning('请填写部门 code')
    return
  }
  if (f.mode === 'edit' && f.parentId === f.id) {
    ElMessage.warning('父级不能是自己')
    return
  }
  f.submitting = true
  try {
    if (f.mode === 'create') {
      const body: DepartmentCreateBody = {
        code: f.code.trim(),
        name: f.name.trim(),
        parentId: f.parentId,
        sortOrder: f.sortOrder,
        enabled: f.enabled,
      }
      await departmentApi.create(body)
      ElMessage.success('已新建')
    } else {
      const body: DepartmentUpdateBody = {
        name: f.name.trim(),
        parentId: f.parentId,
        sortOrder: f.sortOrder,
        enabled: f.enabled,
      }
      await departmentApi.update(f.id, body)
      ElMessage.success('已更新')
    }
    dlg.value.visible = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '保存失败')
  } finally {
    f.submitting = false
  }
}

// ============================================================
// 启停 / 删除
// ============================================================
async function toggleEnabled(node: DepartmentNode) {
  const op = node.enabled ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(`确认${op}部门 "${node.name}"?`, `${op}确认`, { type: 'warning' })
  } catch {
    return
  }
  try {
    await departmentApi.setEnabled(node.id, !node.enabled)
    ElMessage.success(`${op}成功`)
    load()
  } catch (e: any) {
    ElMessage.error(e?.message ?? `${op}失败`)
  }
}

async function onDelete(node: DepartmentNode) {
  if (node.children.length > 0) {
    ElMessage.warning(`该部门下还有 ${node.children.length} 个子部门, 请先删除子部门`)
    return
  }
  if (node.memberCount > 0) {
    ElMessage.warning(`该部门下还有 ${node.memberCount} 个用户, 请先转移用户`)
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除部门 "${node.name}" (${node.code})?`, '删除确认', { type: 'error' })
  } catch {
    return
  }
  try {
    await departmentApi.delete(node.id)
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
          <span>🏢 部门管理 (L1-3)</span>
          <el-button type="primary" @click="openCreate(null)">+ 新建根部门</el-button>
        </div>
      </template>

      <el-alert v-if="!loading && tree.length === 0" type="info" :closable="false" show-icon>
        暂无部门, 点击右上角 "新建根部门" 开始
      </el-alert>

      <el-table
        v-loading="loading"
        :data="tree"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        row-key="id"
        border
        default-expand-all
        style="width: 100%"
        empty-text="无部门"
      >
        <el-table-column prop="name" label="部门名" min-width="200">
          <template #default="{ row }">
            <strong>{{ row.name }}</strong>
            <el-tag size="small" effect="plain" type="info" style="margin-left: 6px">
              {{ row.code }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="直属成员" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.memberCount > 0 ? 'primary' : 'info'" effect="plain" size="small">
              {{ row.memberCount }} 人
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.enabled" type="success" size="small">启用</el-tag>
            <el-tag v-else type="info" size="small">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openCreate(row.id)">+ 子部门</el-button>
            <el-button size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              size="small"
              link
              :type="row.enabled ? 'danger' : 'success'"
              @click="toggleEnabled(row)"
            >
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
            <el-button
              size="small"
              link
              type="danger"
              :disabled="row.children.length > 0 || row.memberCount > 0"
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
      :title="dlg.form.mode === 'create' ? '新建部门' : `编辑部门 — ${dlg.form.code}`"
      width="520px"
    >
      <el-form label-width="100px">
        <el-form-item label="部门名" required>
          <el-input v-model="dlg.form.name" placeholder="中文/英文均可" maxlength="64" />
        </el-form-item>
        <el-form-item label="部门 code" :required="dlg.form.mode === 'create'">
          <el-input
            v-model="dlg.form.code"
            :disabled="dlg.form.mode === 'edit'"
            placeholder="字母/数字/下划线, 2-32 字符"
            maxlength="32"
            show-word-limit
          />
          <div style="color: #909399; font-size: 12px">
            内置:
            <code>ROOT</code>
            /
            <code>RD</code>
            /
            <code>PD</code>
            /
            <code>DL</code>
          </div>
        </el-form-item>
        <el-form-item label="父级部门">
          <el-tree-select
            v-model="dlg.form.parentId"
            :data="tree"
            :props="{ value: 'id', label: 'name', children: 'children' }"
            check-strictly
            clearable
            placeholder="不选 = 根部门"
            style="width: 100%"
            node-key="id"
            :default-expand-all="true"
          />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="dlg.form.sortOrder" :min="0" :max="9999" :step="10" />
        </el-form-item>
        <el-form-item v-if="dlg.form.mode === 'edit'" label="启用">
          <el-switch v-model="dlg.form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="dlg.form.submitting" @click="submit">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
code {
  background: #f5f7fa;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 12px;
}
</style>
