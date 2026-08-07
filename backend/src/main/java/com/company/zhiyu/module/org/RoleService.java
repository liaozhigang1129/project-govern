package com.company.zhiyu.module.org;

import com.company.zhiyu.common.exception.BusinessException;
import com.company.zhiyu.module.org.dto.RoleCreateRequest;
import com.company.zhiyu.module.org.dto.RoleListItem;
import com.company.zhiyu.module.org.dto.RoleUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * L1-2 角色管理 Service (V4.12 升级)
 *
 * 护栏:
 *  - 内置角色 (built_in = true) 不可删除, code 不可改
 *  - 删除前必须无人以它为主角色, 否则 409
 *  - code 全大写, 校验在 DTO 里 (Pattern)
 *  - V4.12 新增: 批量启停、复制授权、按用户数排序
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepo;
    private final UserRepository userRepo;
    private final UserRoleRepository userRoleRepo;

    // ============================================================
    //  查询
    // ============================================================
    @Transactional(readOnly = true)
    public List<RoleListItem> list(boolean includeDisabled) {
        List<Role> all = includeDisabled
                ? roleRepo.findAllByOrderBySortOrderAscIdAsc()
                : roleRepo.findAllByEnabledTrueOrderBySortOrderAscIdAsc();
        return all.stream()
                .map(r -> RoleListItem.from(r,
                        userRepo.countByPrimaryRoleCodeAndEnabledAndDeletedFalse(r.getCode(), true)))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleListItem get(Long id) {
        Role r = mustGet(id);
        return RoleListItem.from(r,
                userRepo.countByPrimaryRoleCodeAndEnabledAndDeletedFalse(r.getCode(), true));
    }

    @Transactional(readOnly = true)
    public List<RoleOption> options() {
        return roleRepo.findAllByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(r -> new RoleOption(r.getId(), r.getCode(), r.getName(), r.isBuiltIn()))
                .toList();
    }

    // ============================================================
    //  新建
    // ============================================================
    @Transactional
    public RoleListItem create(RoleCreateRequest req) {
        if (roleRepo.existsByCode(req.code())) {
            throw new BusinessException("角色 code 已存在: " + req.code());
        }
        Role r = new Role();
        r.setCode(req.code());
        r.setName(req.name());
        r.setDescription(req.description());
        r.setEnabled(req.enabled() == null || req.enabled());
        r.setSortOrder(req.sortOrder() == null ? 100 : req.sortOrder());
        r.setBuiltIn(false);
        Role saved = roleRepo.save(r);
        return RoleListItem.from(saved, 0L);
    }

    // ============================================================
    //  更新
    // ============================================================
    @Transactional
    public RoleListItem update(Long id, RoleUpdateRequest req) {
        Role r = mustGet(id);
        r.setName(req.name());
        r.setDescription(req.description());
        if (req.enabled() != null) r.setEnabled(req.enabled());
        if (req.sortOrder() != null) r.setSortOrder(req.sortOrder());
        return RoleListItem.from(r,
                userRepo.countByPrimaryRoleCodeAndEnabledAndDeletedFalse(r.getCode(), true));
    }

    // ============================================================
    //  启停
    // ============================================================
    @Transactional
    public RoleListItem setEnabled(Long id, boolean enabled) {
        Role r = mustGet(id);
        if (r.isBuiltIn() && !enabled) {
            long inUse = userRepo.countByPrimaryRoleCodeAndEnabledAndDeletedFalse(r.getCode(), true);
            if (inUse > 0) {
                throw new BusinessException(
                        "内置角色 " + r.getCode() + " 仍有 " + inUse + " 个用户作为主角色, 不能停用");
            }
        }
        r.setEnabled(enabled);
        return RoleListItem.from(r,
                userRepo.countByPrimaryRoleCodeAndEnabledAndDeletedFalse(r.getCode(), true));
    }

    /** V4.12: 批量启停 — 跳过有在用用户的内置角色(单 SQL) */
    @Transactional
    public Map<String, Object> bulkSetEnabled(List<Long> ids, boolean enabled) {
        if (ids == null || ids.isEmpty()) {
            return Map.of("affected", 0, "skipped", List.of());
        }
        // 先校验: 如果是停用, 收集有在用用户的内置角色
        Set<Long> blocked = new HashSet<>();
        if (!enabled) {
            List<Role> roles = roleRepo.findAllById(ids);
            for (Role r : roles) {
                if (r.isBuiltIn()) {
                    long inUse = userRepo.countByPrimaryRoleCodeAndEnabledAndDeletedFalse(r.getCode(), true);
                    if (inUse > 0) blocked.add(r.getId());
                }
            }
            ids = ids.stream().filter(id -> !blocked.contains(id)).toList();
        }
        if (ids.isEmpty()) {
            return Map.of("affected", 0, "skipped", blocked.stream().sorted().toList());
        }
        int n = roleRepo.bulkSetEnabled(ids, enabled);
        return Map.of("affected", n, "skipped", blocked.stream().sorted().toList());
    }

    // ============================================================
    //  删除
    // ============================================================
    @Transactional
    public void delete(Long id) {
        Role r = mustGet(id);
        if (r.isBuiltIn()) {
            throw new BusinessException("内置角色 [" + r.getCode() + "] 不能删除");
        }
        long inUse = userRepo.countByPrimaryRoleCodeAndEnabledAndDeletedFalse(r.getCode(), true);
        if (inUse > 0) {
            throw new BusinessException(
                    "该角色仍有 " + inUse + " 个用户作为主角色, 请先转移给其他角色再删除");
        }
        userRoleRepo.findAllByRoleId(r.getId()).forEach(userRoleRepo::delete);
        roleRepo.delete(r);
    }

    // ============================================================
    //  工具
    // ============================================================
    private Role mustGet(Long id) {
        return roleRepo.findById(id)
                .orElseThrow(() -> new BusinessException(404, "角色不存在: " + id));
    }

    /** 简表 DTO */
    public record RoleOption(Long id, String code, String name, boolean builtIn) {}
}
