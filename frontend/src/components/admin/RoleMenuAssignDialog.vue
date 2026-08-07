<script setup lang="ts">
/**
 * 角色 × 菜单 授权 — 弹窗内表单
 * 被 Roles.vue / Menus.vue 共用
 *
 *  设计:
 *   - 树形 el-tree, 数据来自 /api/menus (含父子)
 *   - check-strictly = false (默认): 父选 = 自动选子; 后端拿 menuIds 即可
 *   - 显示每条菜单的 type / 状态, 停用的不显示在树里
 *   - 顶部: "全选 / 清空 / 展开 / 收起" 4 个快捷按钮
 *   - 底部: "已选 N 项" 统计 + 取消/确认
 */
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { menuApi, type SysMenuItem } from '@/api/menus'
import { roleMenuApi } from '@/api/roleMenus'

const props = defineProps<{
  visible: boolean
  roleId: number
  roleName: string
}>()
const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'saved'): void
}>()

// ============================================================
// 数据
// ============================================================
const treeRef = ref<any>()
const allMenus = ref<SysMenuItem[]>([])         // 原始
const checkedKeys = ref<number[]>([])            // 已选 menuId
const loading = ref(false)
const submitting = ref(false)

// ============================================================
// 把扁平列表 → 树结构 (按 parentId 拼)
// ============================================================
type TreeNode = SysMenuItem & { children?: TreeNode[] }

function buildTree(list: SysMenuItem[]): TreeNode[] {
  const map = new Map<number, TreeNode>()
  list.forEach(m => map.set(m.id, { ...m }))
  const roots: TreeNode[] = []
  map.forEach(n => {
    if (n.parentId && map.has(n.parentId)) {
      const p = map.get(n.parentId)!
      p.children = p.children ?? []
      p.children.push(n)
    } else {
      roots.push(n)
    }
  })
  // 按 sortOrder 排序
  const sortRec = (arr: TreeNode[]) => {
    arr.sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
    arr.forEach(n => n.children && sortRec(n.children))
  }
  sortRec(roots)
  return roots
}

const treeData = computed<TreeNode[]>(() => buildTree(allMenus.value))

// ============================================================
// 加载
// ============================================================
async function load() {
  loading.value = true
  try {
    allMenus.value = await menuApi.list(true)
    const assigned = await roleMenuApi.listByRole(props.roleId)
    // 仅保留启用的菜单 (停用的不进授权)
    checkedKeys.value = assigned.filter(id =>
      allMenus.value.find(m => m.id === id && m.enabled)
    )
  } catch (e: any) {
    ElMessage.error(e?.message ?? '加载菜单授权失败')
  } finally {
    loading.value = false
  }
}

// ============================================================
// 操作
// ============================================================
function checkAll() {
  treeRef.value?.setCheckedKeys(allMenus.value.map(m => m.id))
}
function clearAll() {
  treeRef.value?.setCheckedKeys([])
}
function expandAll() {
  // el-tree 没 API 一次性展开全部, 用节点 ref 递归
  const nodes = treeRef.value?.store?.nodesMap || {}
  Object.values(nodes).forEach((n: any) => { n.expanded = true })
}
function collapseAll() {
  const nodes = treeRef.value?.store?.nodesMap || {}
  Object.values(nodes).forEach((n: any) => { n.expanded = false })
}

async function submit() {
  const keys: number[] = treeRef.value?.getCheckedKeys() ?? []
  const half: number[] = treeRef.value?.getHalfCheckedKeys() ?? []
  // 半选 = 父没勾但子有勾; 一期: 半选当成"父也勾上", 让菜单完整可见
  const finalIds = Array.from(new Set([...keys, ...half])).sort((a, b) => a - b)

  submitting.value = true
  try {
    await roleMenuApi.assign(props.roleId, {
      roleId: props.roleId,
      menuIds: finalIds,
    })
    ElMessage.success(`已更新角色 [${props.roleName}] 的菜单授权 (${finalIds.length} 项)`)
    emit('saved')
    emit('update:visible', false)
  } catch (e: any) {
    ElMessage.error(e?.message ?? '保存失败')
  } finally {
    submitting.value = false
  }
}

function close() { emit('update:visible', false) }

// ============================================================
// 监听 visible 打开
// ============================================================
watch(() => props.visible, (v) => {
  if (v && props.roleId) load()
})
onMounted(() => {
  if (props.visible && props.roleId) load()
})
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    :title="`菜单授权 — ${roleName}`"
    width="640px"
    top="6vh"
    :close-on-click-modal="false"
  >
    <div v-loading="loading">
      <!-- 工具栏 -->
      <div style="display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap">
        <el-button size="small" @click="checkAll">☑ 全选</el-button>
        <el-button size="small" @click="clearAll">☐ 清空</el-button>
        <el-button size="small" @click="expandAll">⊞ 展开全部</el-button>
        <el-button size="small" @click="collapseAll">⊟ 收起全部</el-button>
        <div style="margin-left: auto; color: #909399; font-size: 12px; align-self: center">
          已选 {{ (treeRef?.getCheckedKeys() ?? []).length }} 项
        </div>
      </div>

      <!-- 树 -->
      <div style="max-height: 56vh; overflow: auto; border: 1px solid #ebeef5; border-radius: 4px; padding: 8px">
        <el-tree
          ref="treeRef"
          :data="treeData"
          node-key="id"
          show-checkbox
          :default-checked-keys="checkedKeys"
          :props="{ label: 'name', children: 'children' }"
        >
          <template #default="{ data }">
            <div style="display: flex; align-items: center; gap: 6px; width: 100%">
              <strong>{{ data.name }}</strong>
              <el-tag size="small" effect="plain" type="info">{{ data.code }}</el-tag>
              <el-tag v-if="data.menuType === 'DIR'" size="small" type="warning">目录</el-tag>
              <span v-if="data.path" style="color: #909399; font-size: 12px">{{ data.path }}</span>
              <el-tag v-if="!data.enabled" size="small" type="info">已停用</el-tag>
            </div>
          </template>
        </el-tree>
      </div>
    </div>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认授权</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
code {
  background: #f5f7fa; padding: 1px 6px; border-radius: 3px;
  font-size: 12px; color: #606266;
}
</style>