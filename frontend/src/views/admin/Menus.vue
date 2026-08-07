<script setup lang="ts">
/**
 * L1-3 菜单管理 — 树形层级结构
 * 仅 PMO_ADMIN / ADMIN 可见
 *
 *  设计:
 *   - el-table 的 tree-data 模式 (row-key + treeProps.children)
 *   - 默认展开全部 (一级菜单 + 二级子菜单)
 *   - 操作列含: 编辑 / 新建子菜单 / 启停 / 删除
 *   - 父菜单下拉只允许选 DIR 类型
 *   - 内置菜单: 不可删 / 不可改 code
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { menuApi, type SysMenuItem, type SysMenuCreateBody, type SysMenuUpdateBody } from '@/api/menus'

// ============================================================
// 状态
// ============================================================
const loading = ref(false)
const includeDisabled = ref(false)
const flatItems = ref<SysMenuItem[]>([])       // 后端原始扁平数据
const parentOptions = ref<SysMenuItem[]>([])

const dlg = ref({
  visible: false,
  mode: 'create' as 'create' | 'edit',
  submitting: false,
  form: {
    id: 0,
    code: '',
    name: '',
    parentId: null as number | null,
    path: '',
    icon: '',
    sortOrder: 100,
    menuType: 'PAGE' as 'DIR' | 'PAGE',
    enabled: true,
    description: '',
  },
})

// ============================================================
// 加载
// ============================================================
async function load() {
  loading.value = true
  try {
    flatItems.value = await menuApi.list(includeDisabled.value)
    parentOptions.value = await menuApi.parentOptions()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '加载菜单失败')
  } finally {
    loading.value = false
  }
}

// ============================================================
// 把扁平数据组装成树 (children)
// ============================================================
interface TreeNode extends SysMenuItem {
  children?: TreeNode[]
}
const treeData = computed<TreeNode[]>(() => {
  const map = new Map<number, TreeNode>()
  flatItems.value.forEach(m => map.set(m.id, { ...m, children: [] }))
  const roots: TreeNode[] = []
  flatItems.value.forEach(m => {
    const node = map.get(m.id)!
    if (m.parentId && map.has(m.parentId)) {
      map.get(m.parentId)!.children!.push(node)
    } else {
      roots.push(node)
    }
  })
  // 同级排序: sortOrder asc, 然后 id asc
  const sortFn = (a: TreeNode, b: TreeNode) =>
    a.sortOrder - b.sortOrder || a.id - b.id
  const deepSort = (arr: TreeNode[]) => {
    arr.sort(sortFn)
    arr.forEach(n => n.children && deepSort(n.children))
  }
  deepSort(roots)
  return roots
})

// 统计: 顶层 + 子项
const totalCount = computed(() => flatItems.value.length)

// 父菜单下拉候选: 仅 DIR/PAGE 都可, 但 PAGE 不常见 — 这里允许所有非自身节点
const dirParentOptions = computed(() =>
  parentOptions.value
    .filter(m => m.menuType === 'DIR' || m.menuType === 'PAGE')
    .sort((a, b) => (a.parentName ?? '').localeCompare(b.parentName ?? '') || a.sortOrder - b.sortOrder)
)

// ============================================================
// 新建 / 编辑
// ============================================================
function openCreate(parentId: number | null = null) {
  dlg.value = {
    visible: true,
    mode: 'create',
    submitting: false,
    form: {
      id: 0, code: '', name: '',
      parentId, path: '', icon: '',
      sortOrder: 100, menuType: 'PAGE',
      enabled: true, description: '',
    },
  }
}

function openEdit(row: SysMenuItem) {
  dlg.value = {
    visible: true,
    mode: 'edit',
    submitting: false,
    form: {
      id: row.id,
      code: row.code,
      name: row.name,
      parentId: row.parentId,
      path: row.path ?? '',
      icon: row.icon ?? '',
      sortOrder: row.sortOrder,
      menuType: row.menuType,
      enabled: row.enabled,
      description: row.description ?? '',
    },
  }
}

async function submit() {
  const f = dlg.value.form
  if (!f.code.trim()) { ElMessage.warning('请填写菜单 code'); return }
  if (!f.name.trim()) { ElMessage.warning('请填写菜单名'); return }
  dlg.value.submitting = true
  try {
    if (dlg.value.mode === 'create') {
      const body: SysMenuCreateBody = {
        code: f.code.trim().toUpperCase(),
        name: f.name.trim(),
        parentId: f.parentId,
        path: f.path.trim() || undefined,
        icon: f.icon.trim() || undefined,
        sortOrder: f.sortOrder,
        menuType: f.menuType,
        enabled: f.enabled,
        description: f.description.trim() || undefined,
      }
      await menuApi.create(body)
      ElMessage.success(`菜单 [${body.code}] 创建成功`)
    } else {
      const body: SysMenuUpdateBody = {
        name: f.name.trim(),
        parentId: f.parentId,
        path: f.path.trim() || undefined,
        icon: f.icon.trim() || undefined,
        sortOrder: f.sortOrder,
        menuType: f.menuType,
        enabled: f.enabled,
        description: f.description.trim() || undefined,
      }
      await menuApi.update(f.id, body)
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
async function toggleEnabled(row: SysMenuItem) {
  const op = row.enabled ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确认${op}菜单 "${row.name}" (${row.code})?`,
      `${op}确认`,
      { type: 'warning' }
    )
  } catch { return }
  try {
    await menuApi.setEnabled(row.id, !row.enabled)
    ElMessage.success(`${op}成功`)
    load()
  } catch (e: any) {
    ElMessage.error(e?.message ?? `${op}失败`)
  }
}

/** el-switch 直接切换 (内置菜单禁用, 不可点) */
async function onToggleFromSwitch(row: SysMenuItem, v: boolean) {
  if (row.builtin) {
    ElMessage.warning('内置菜单不可停用')
    return
  }
  try {
    await menuApi.setEnabled(row.id, v)
    ElMessage.success(v ? '已启用' : '已停用')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '操作失败')
  }
}

async function onDelete(row: SysMenuItem) {
  if (row.builtin) {
    ElMessage.warning('内置菜单不可删除')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认删除菜单 "${row.name}" (${row.code})? 此操作不可恢复!`,
      '删除确认',
      { type: 'error' }
    )
  } catch { return }
  try {
    await menuApi.delete(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '删除失败')
  }
}

// 新建子菜单 (从行级按钮)
function addChild(row: SysMenuItem) {
  openCreate(row.id)
}

// 切换展开/折叠
const expandAll = ref(true)
function toggleExpand() {
  expandAll.value = !expandAll.value
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>🧭 菜单管理 (L1-3) — 系统菜单 CRUD & 树形维护</span>
          <div style="display: flex; gap: 12px; align-items: center">
            <el-tag size="small" effect="plain">共 {{ totalCount }} 个菜单</el-tag>
            <el-checkbox v-model="includeDisabled" @change="load">包含已停用</el-checkbox>
            <el-button size="small" @click="toggleExpand">
              {{ expandAll ? '收起全部' : '展开全部' }}
            </el-button>
            <el-button type="primary" @click="openCreate()">+ 新建顶层菜单</el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="treeData"
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        :default-expand-all="expandAll"
        border
        stripe
        style="width: 100%"
        empty-text="无菜单"
        :row-class-name="(data: any) => !data.row.enabled ? 'menu-disabled' : ''"
      >
        <el-table-column label="菜单名 / code" min-width="340">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 8px">
              <el-icon v-if="row.icon" style="color: #409eff">
                <component :is="iconName(row.icon)" />
              </el-icon>
              <strong :style="{ color: row.menuType === 'DIR' ? '#e6a23c' : '#303133' }">
                {{ row.name }}
              </strong>
              <el-tag size="small" effect="plain" type="info">{{ row.code }}</el-tag>
              <el-tag v-if="row.menuType === 'DIR'" size="small" type="warning">📂 目录</el-tag>
              <el-tag v-else size="small" type="success">📄 页面</el-tag>
              <el-tag v-if="row.builtin" size="small" type="danger">内置</el-tag>
              <el-tag v-if="!row.enabled" size="small" type="info">停用</el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="路由路径" min-width="180">
          <template #default="{ row }">
            <code v-if="row.path">{{ row.path }}</code>
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>

        <el-table-column label="图标" width="80">
          <template #default="{ row }">
            <code v-if="row.icon" style="font-size: 11px">{{ row.icon }}</code>
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>

        <el-table-column label="子菜单" width="80" align="center">
          <template #default="{ row }">
            <el-badge
              v-if="row.children && row.children.length > 0"
              :value="row.children.length"
              type="primary"
            />
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>

        <el-table-column prop="sortOrder" label="排序" width="70" align="center" />

        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              :disabled="row.builtin"
              @change="(v: boolean) => onToggleFromSwitch(row, v)"
              inline-prompt
              active-text="启"
              inactive-text="停"
            />
          </template>
        </el-table-column>

        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" link type="primary" @click="addChild(row)">
              新建子菜单
            </el-button>
            <el-button
              v-if="row.builtin"
              size="small"
              link
              :type="row.enabled ? 'danger' : 'success'"
              @click="toggleEnabled(row)"
            >
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
            <el-button
              size="small"
              link type="danger"
              :disabled="row.builtin"
              @click="onDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建 / 编辑 对话框 -->
    <el-dialog
      v-model="dlg.visible"
      :title="dlg.mode === 'create' ? '新建菜单' : `编辑菜单 — ${dlg.form.code}`"
      width="600px"
    >
      <el-form label-width="100px">
        <el-form-item label="菜单 code" required>
          <el-input
            v-model="dlg.form.code"
            :disabled="dlg.mode === 'edit'"
            placeholder="大���字母/数字/下划线, 以字母开头, 2-64 字符"
            maxlength="64" show-word-limit
          />
        </el-form-item>
        <el-form-item label="菜单名" required>
          <el-input
            v-model="dlg.form.name"
            placeholder="中文/英文均可"
            maxlength="64"
          />
        </el-form-item>
        <el-form-item label="菜单类型" required>
          <el-radio-group v-model="dlg.form.menuType">
            <el-radio-button value="DIR">目录</el-radio-button>
            <el-radio-button value="PAGE">页面</el-radio-button>
          </el-radio-group>
          <div style="color: #909399; font-size: 12px; line-height: 1.4; margin-top: 4px">
            目录 = 无 path, 仅作分组. 页面 = 有 path, 真实跳转.
          </div>
        </el-form-item>
        <el-form-item label="父菜单">
          <el-select
            v-model="dlg.form.parentId" placeholder="顶层 (无父)" clearable filterable
            style="width: 100%"
          >
            <el-option label="— 顶层 (无父) —" :value="null" />
            <el-option
              v-for="m in dirParentOptions" :key="m.id"
              :label="`${m.parentName ? m.parentName + ' / ' : ''}${m.name} (${m.code})`"
              :value="m.id"
              :disabled="dlg.mode === 'edit' && m.id === dlg.form.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="路由路径">
          <el-input
            v-model="dlg.form.path"
            placeholder="例 /admin/menus (目录可空)"
            maxlength="128"
          />
        </el-form-item>
        <el-form-item label="图标">
          <el-input
            v-model="dlg.form.icon"
            placeholder="Element Plus icon 英文名, 例 House / Calendar / User"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number
            v-model="dlg.form.sortOrder" :min="0" :max="9999" :step="10"
          />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">数字越小越靠前</span>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="dlg.form.enabled" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">
            停用后, 角色授权中该菜单自动失效 (前端隐藏, 后端保留)
          </span>
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="dlg.form.description" type="textarea" :rows="2"
            placeholder="选填" maxlength="256" show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="dlg.submitting" @click="submit">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
// icon 名 → 组件 映射 (常用 30 个, 其他未匹配时显示默认 Menu)
import {
  Bell, Box, Calendar, Check, ChatDotRound, DataBoard, DataLine,
  Document, Flag, Histogram, House, List, MagicStick, Menu, Money, Moon,
  OfficeBuilding, Setting, Tools, TrendCharts, User, UserFilled, Warning,
} from '@element-plus/icons-vue'
const iconMap: Record<string, any> = {
  Bell, Box, Calendar, Check, ChatDotRound, DataBoard, DataLine,
  Document, Flag, Histogram, House, List, MagicStick, Menu, Money, Moon,
  OfficeBuilding, Setting, Tools, TrendCharts, User, UserFilled, Warning,
}
export function iconName(name: string) {
  return iconMap[name] ?? Menu
}
</script>

<style scoped>
.page { padding: 16px; }
code {
  background: #f5f7fa; padding: 1px 6px; border-radius: 3px;
  font-size: 12px; color: #606266;
}
:deep(.menu-disabled) {
  background: #f5f7fa !important;
  color: #909399;
}
:deep(.el-table__row--level-1) {
  background: #fafbfc;
}
</style>