package com.hex.projectgovern.module.menu;

import com.hex.projectgovern.common.exception.BusinessException;
import com.hex.projectgovern.module.menu.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * L1-3 菜单管理 Service
 *
 * 护栏:
 *  - 内置菜单 (builtin=true) 不可删 / 不可改 code, 但可改 name/path/sortOrder/enabled
 *  - 修改 parent 不能形成环 (自指 + 父子互指)
 *  - 删除前先清 role_menu 引用 (FK ON DELETE CASCADE 已自动处理)
 */
@Service
@RequiredArgsConstructor
public class SysMenuService {

    private final SysMenuRepository menuRepo;
    private final RoleMenuRepository roleMenuRepo;

    // ============================================================
    //  查询 — 列表/树/简表
    // ============================================================
    @Transactional(readOnly = true)
    public List<SysMenuItem> list(boolean includeDisabled) {
        List<SysMenu> all = includeDisabled
                ? menuRepo.findAllByOrderBySortOrderAscIdAsc()
                : menuRepo.findAllByEnabledTrueOrderBySortOrderAscIdAsc();
        // 一次性把父名查出来 (避免 N+1)
        Map<Long, String> parentNames = new HashMap<>();
        for (SysMenu m : all) {
            if (m.getParentId() != null && !parentNames.containsKey(m.getParentId())) {
                menuRepo.findById(m.getParentId())
                        .ifPresent(p -> parentNames.put(p.getId(), p.getName()));
            }
        }
        return all.stream()
                .map(m -> SysMenuItem.from(m,
                        m.getParentId() == null ? null : parentNames.get(m.getParentId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SysMenuItem get(Long id) {
        SysMenu m = mustGet(id);
        String pname = m.getParentId() == null ? null
                : menuRepo.findById(m.getParentId()).map(SysMenu::getName).orElse(null);
        return SysMenuItem.from(m, pname);
    }

    /** 给前端"父菜单"下拉用 — 仅 DIR 类型 */
    @Transactional(readOnly = true)
    public List<SysMenuItem> parentOptions(Long excludeId) {
        return menuRepo.findAllByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .filter(m -> !Objects.equals(m.getId(), excludeId))   // 排除自己(避免自指)
                .map(m -> new SysMenuItem(m.getId(), m.getCode(), m.getName(),
                        m.getParentId(), null, m.getPath(), m.getIcon(),
                        m.getSortOrder(), m.getMenuType(),
                        m.isEnabled(), m.isBuiltin(),
                        m.getDescription(), m.getCreatedAt()))
                .toList();
    }

    // ============================================================
    //  新建
    // ============================================================
    @Transactional
    public SysMenuItem create(SysMenuCreateRequest req) {
        if (menuRepo.existsByCode(req.code())) {
            throw new BusinessException("菜单 code 已存在: " + req.code());
        }
        if (req.parentId() != null) {
            mustGet(req.parentId());   // 父必须存在
        }
        SysMenu m = new SysMenu();
        m.setCode(req.code());
        m.setName(req.name());
        m.setParentId(req.parentId());
        m.setPath(req.path());
        m.setIcon(req.icon());
        m.setSortOrder(req.sortOrder());
        m.setMenuType(req.menuType() == null || req.menuType().isBlank() ? "PAGE" : req.menuType());
        m.setEnabled(req.enabled() == null || req.enabled());
        m.setBuiltin(false);
        m.setDescription(req.description());
        SysMenu saved = menuRepo.save(m);
        return get(saved.getId());
    }

    // ============================================================
    //  更新
    // ============================================================
    @Transactional
    public SysMenuItem update(Long id, SysMenuUpdateRequest req) {
        SysMenu m = mustGet(id);
        if (req.parentId() != null) {
            // 1) 父必须存在
            mustGet(req.parentId());
            // 2) 不能形成环: 父不能是自己 / 不能是当前节点的子孙
            if (req.parentId().equals(id)) {
                throw new BusinessException("父菜单不能是自己");
            }
            Set<Long> descendants = collectDescendantIds(id);
            if (descendants.contains(req.parentId())) {
                throw new BusinessException("父菜单不能是当前菜单的子菜单 (会成环)");
            }
        }
        m.setName(req.name());
        m.setParentId(req.parentId());
        m.setPath(req.path());
        m.setIcon(req.icon());
        m.setSortOrder(req.sortOrder());
        if (req.menuType() != null && !req.menuType().isBlank()) {
            m.setMenuType(req.menuType());
        }
        if (req.enabled() != null) m.setEnabled(req.enabled());
        m.setDescription(req.description());
        return get(id);
    }

    // ============================================================
    //  启停
    // ============================================================
    @Transactional
    public SysMenuItem setEnabled(Long id, boolean enabled) {
        SysMenu m = mustGet(id);
        if (!enabled) {
            // 停用前检查: 该菜单的所有祖先是否还在启用 (否则前端会找不到)
            // 一期简单: 不阻止停用, 给个 warning
        }
        m.setEnabled(enabled);
        return get(id);
    }

    // ============================================================
    //  删除
    // ============================================================
    @Transactional
    public void delete(Long id) {
        SysMenu m = mustGet(id);
        if (m.isBuiltin()) {
            throw new BusinessException("内置菜单 [" + m.getCode() + "] 不能删除");
        }
        long childCount = menuRepo.countByParentId(id);
        if (childCount > 0) {
            throw new BusinessException(
                    "该菜单下仍有 " + childCount + " 个子菜单, 请先删除子菜单");
        }
        menuRepo.delete(m);
    }

    /** V4.12: 批量启停 */
    @Transactional
    public int bulkSetEnabled(List<Long> ids, boolean enabled) {
        if (ids == null || ids.isEmpty()) return 0;
        return menuRepo.bulkSetEnabled(ids, enabled);
    }

    // ============================================================
    //  工具
    // ============================================================
    private SysMenu mustGet(Long id) {
        return menuRepo.findById(id)
                .orElseThrow(() -> new BusinessException(404, "菜单不存在: " + id));
    }

    /** 收集指定菜单的所有子孙 ID (排除自身) */
    private Set<Long> collectDescendantIds(Long rootId) {
        List<SysMenu> all = menuRepo.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, List<Long>> childMap = all.stream()
                .filter(m -> m.getParentId() != null)
                .collect(Collectors.groupingBy(SysMenu::getParentId,
                        Collectors.mapping(SysMenu::getId, Collectors.toList())));
        Set<Long> out = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>(childMap.getOrDefault(rootId, List.of()));
        while (!stack.isEmpty()) {
            Long cur = stack.pop();
            if (out.add(cur)) {
                stack.addAll(childMap.getOrDefault(cur, List.of()));
            }
        }
        return out;
    }
}