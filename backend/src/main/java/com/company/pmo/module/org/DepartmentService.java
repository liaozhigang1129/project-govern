package com.company.pmo.module.org;

import com.company.pmo.common.exception.BusinessException;
import com.company.pmo.module.org.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * L1-3 部门管理 Service (V4.12 升级)
 *
 * 护栏:
 *  - code 唯一
 *  - 修改 parentId 时: 不能把自己设成自己的后代 (避免环)
 *  - 删除: 无子部门 & 无直属用户
 *  - parentId 设到不存在的部门 → 404
 *  - V4.12: leader 信息批量取(避免 N+1)
 *  - V4.12: 新增批量启停 (事务 + 单 SQL)
 *
 * 性能:
 *  - tree() 一次性查所有 leader info,不再 N+1
 *  - 批量操作走 @Transactional + 单 SQL (UPDATE ... WHERE id IN (?))
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository deptRepo;
    private final UserRepository userRepo;

    // ============================================================
    //  树 — V4.12: 批量取 leader info (避免 N+1)
    // ============================================================
    @Transactional(readOnly = true)
    public List<DepartmentNode> tree() {
        List<Department> all = deptRepo.findAllByDeletedFalseOrderByParentIdAscSortOrderAscIdAsc();
        return buildTree(all);
    }

    @Transactional(readOnly = true)
    public List<DepartmentNode> treeIncludingDisabled() {
        List<Department> all = deptRepo.findAllByOrderByParentIdAscSortOrderAscIdAsc();
        return buildTree(all);
    }

    private List<DepartmentNode> buildTree(List<Department> all) {
        // 1) 批量取所有 leader 的基本信息 (1 次 SQL)
        Set<Long> leaderIds = all.stream()
                .map(Department::getLeaderUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String[]> leaderMap = new HashMap<>(); // id -> [username, fullName]
        if (!leaderIds.isEmpty()) {
            for (Object[] row : userRepo.findBasicInfoByIds(leaderIds)) {
                leaderMap.put((Long) row[0], new String[]{(String) row[1], (String) row[2]});
            }
        }

        // 2) 建节点 + 挂 children
        Map<Long, DepartmentNode> map = new HashMap<>();
        List<DepartmentNode> roots = new ArrayList<>();
        for (Department d : all) {
            String parentName = null;
            if (d.getParentId() != null && map.containsKey(d.getParentId())) {
                parentName = map.get(d.getParentId()).name;
            }
            String[] leaderInfo = d.getLeaderUserId() == null ? null : leaderMap.get(d.getLeaderUserId());
            DepartmentNode n = DepartmentNode.from(
                    d,
                    deptRepo.countUsers(d.getId()),
                    parentName,
                    leaderInfo == null ? null : leaderInfo[0],
                    leaderInfo == null ? null : leaderInfo[1],
                    null);
            map.put(d.getId(), n);
        }
        for (Department d : all) {
            DepartmentNode n = map.get(d.getId());
            if (d.getParentId() == null) {
                roots.add(n);
            } else {
                DepartmentNode p = map.get(d.getParentId());
                if (p != null) p.children.add(n);
                else roots.add(n);
            }
        }

        // 3) 计算 memberCountTotal = 自身 + 所有后代用户的总数
        Map<Long, Long> subtreeCounts = new HashMap<>();
        computeSubtreeMemberCounts(roots, subtreeCounts);
        for (DepartmentNode n : map.values()) {
            n.memberCountTotal = subtreeCounts.getOrDefault(n.id, n.memberCount);
        }
        return roots;
    }

    /**
     * 递归计算每个节点的"含子部门用户总数"
     * @return map: deptId -> 该节点及其所有后代部门的直属用户数之和
     */
    private void computeSubtreeMemberCounts(List<DepartmentNode> nodes, Map<Long, Long> out) {
        if (nodes == null) return;
        for (DepartmentNode n : nodes) {
            long self = n.memberCount == null ? 0L : n.memberCount;
            long childrenSum = 0L;
            if (n.children != null && !n.children.isEmpty()) {
                computeSubtreeMemberCounts(n.children, out);
                for (DepartmentNode c : n.children) {
                    childrenSum += out.getOrDefault(c.id, 0L);
                }
            }
            out.put(n.id, self + childrenSum);
        }
    }

    @Transactional(readOnly = true)
    public DepartmentNode get(Long id) {
        Department d = mustGet(id);
        String parentName = d.getParentId() == null ? null
                : deptRepo.findByIdAndDeletedFalse(d.getParentId()).map(Department::getName).orElse(null);
        return buildNodeWithLeader(d, parentName);
    }

    private DepartmentNode buildNodeWithLeader(Department d, String parentName) {
        String username = null, fullName = null;
        if (d.getLeaderUserId() != null) {
            var arr = userRepo.findBasicInfoByIds(List.of(d.getLeaderUserId()));
            if (!arr.isEmpty()) {
                username = (String) arr.get(0)[1];
                fullName = (String) arr.get(0)[2];
            }
        }
        return DepartmentNode.from(d, deptRepo.countUsers(d.getId()), parentName, username, fullName, null);
    }

    // ============================================================
    //  简表
    // ============================================================
    @Transactional(readOnly = true)
    public List<DepartmentOption> options() {
        return deptRepo.findAllByDeletedFalseOrderByParentIdAscSortOrderAscIdAsc().stream()
                .filter(Department::isEnabled)
                .map(DepartmentOption::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentOption> optionsForMove(Long excludeId) {
        return deptRepo.findAllByDeletedFalseOrderByParentIdAscSortOrderAscIdAsc().stream()
                .map(DepartmentOption::from)
                .filter(d -> excludeId == null || !d.id().equals(excludeId))
                .toList();
    }

    // ============================================================
    //  新建
    // ============================================================
    @Transactional
    public DepartmentNode create(DepartmentCreateRequest req) {
        if (deptRepo.existsByCodeAndDeletedFalse(req.code())) {
            throw new BusinessException("部门 code 已存在: " + req.code());
        }
        if (req.parentId() != null && !deptRepo.existsByIdAndDeletedFalse(req.parentId())) {
            throw new BusinessException("父级部门不存在: " + req.parentId());
        }
        validateLeader(req.leaderUserId());
        Department d = new Department();
        d.setCode(req.code());
        d.setName(req.name());
        d.setParentId(req.parentId());
        d.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        d.setEnabled(req.enabled() == null || req.enabled());
        d.setLeaderUserId(req.leaderUserId());
        Department saved = deptRepo.save(d);
        return get(saved.getId());
    }

    // ============================================================
    //  更新
    // ============================================================
    @Transactional
    public DepartmentNode update(Long id, DepartmentUpdateRequest req) {
        Department d = mustGet(id);
        d.setName(req.name());
        if (req.parentId() != null) {
            if (req.parentId().equals(id)) throw new BusinessException("不能把自己设为父级");
            if (!deptRepo.existsByIdAndDeletedFalse(req.parentId())) {
                throw new BusinessException("父级部门不存在: " + req.parentId());
            }
            if (isDescendant(req.parentId(), id)) {
                throw new BusinessException("不能把部门移到自己的子部门下");
            }
            d.setParentId(req.parentId());
        } else {
            d.setParentId(null);
        }
        if (req.sortOrder() != null) d.setSortOrder(req.sortOrder());
        if (req.enabled() != null) d.setEnabled(req.enabled());
        if (req.leaderUserId() != null) {
            validateLeader(req.leaderUserId());
            d.setLeaderUserId(req.leaderUserId());
        }
        return get(id);
    }

    // ============================================================
    //  启停
    // ============================================================
    @Transactional
    public DepartmentNode setEnabled(Long id, boolean enabled) {
        Department d = mustGet(id);
        d.setEnabled(enabled);
        return get(id);
    }

    /** V4.12: 批量启停 — 走单 SQL 性能更好 */
    @Transactional
    public int bulkSetEnabled(List<Long> ids, boolean enabled) {
        if (ids == null || ids.isEmpty()) return 0;
        return deptRepo.bulkSetEnabled(ids, enabled);
    }

    // ============================================================
    //  删除 (软删)
    // ============================================================
    @Transactional
    public void delete(Long id) {
        Department d = mustGet(id);
        long children = deptRepo.countChildren(id);
        if (children > 0) throw new BusinessException("该部门下还有 " + children + " 个子部门, 请先转移或删除子部门");
        long users = deptRepo.countUsers(id);
        if (users > 0) throw new BusinessException("该部门下还有 " + users + " 个用户, 请先转移用户");
        d.setDeleted(true);
        deptRepo.save(d);
    }

    // ============================================================
    //  V4.14: 层级 / 子部门查询
    // ============================================================
    /**
     * 查某部门及所有子部门 (基于 tree_path 前缀)
     * @return flat list, 按 tree_level 升序
     */
    @Transactional(readOnly = true)
    public List<Department> findDescendants(Long deptId) {
        Department root = mustGet(deptId);
        String path = root.getTreePath();
        if (path == null || path.isEmpty()) {
            // 防御: 旧数据没填 tree_path
            path = "/" + root.getId() + "/";
        }
        return deptRepo.findDescendants(path);
    }

    /**
     * 查某部门及所有子部门的 ID 列表
     */
    @Transactional(readOnly = true)
    public List<Long> findDescendantIds(Long deptId) {
        Department root = mustGet(deptId);
        String path = root.getTreePath();
        if (path == null || path.isEmpty()) {
            path = "/" + root.getId() + "/";
        }
        return deptRepo.findDescendantIds(path);
    }

    // ============================================================
    //  工具
    // ============================================================
    private Department mustGet(Long id) {
        return deptRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(404, "部门不存在: " + id));
    }

    private void validateLeader(Long userId) {
        if (userId == null) return;
        if (!userRepo.findByIdAndDeletedFalse(userId).map(u -> u.isEnabled() && !u.isDeleted()).orElse(false)) {
            throw new BusinessException(422, "部门负责人无效或已停用: " + userId);
        }
    }

    private boolean isDescendant(Long candidateAncestor, Long startId) {
        Long cur = candidateAncestor;
        while (cur != null) {
            if (cur.equals(startId)) return true;
            Department p = deptRepo.findByIdAndDeletedFalse(cur).orElse(null);
            if (p == null) return false;
            cur = p.getParentId();
        }
        return false;
    }
}