package com.hex.projectgovern.module.org.service;

import com.hex.projectgovern.module.org.Department;
import com.hex.projectgovern.module.org.DepartmentRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 部门路径解析 (V4.15)
 *
 * 给定 dept_id, 沿 parent_id 链一路向上到 root, 用 " / " 拼接所有 name.
 * 性能: 1 次 SQL 拉所有相关 dept (id+name+parent_id), 内存里 walk 链.
 *
 * 用法:
 *   Map<Long, String> paths = pathResolver.batchResolve(deptIds);
 */
@Component
public class DeptPathResolver {

    private final DepartmentRepository deptRepo;

    public DeptPathResolver(DepartmentRepository deptRepo) {
        this.deptRepo = deptRepo;
    }

    /**
     * 批量解析多个 dept 的完整路径
     * @param deptIds 部门 ID 集合 (允许 null/重复)
     * @return Map<deptId, "总公司 / 一级 / 二级 / 三级">
     */
    public Map<Long, String> batchResolve(Set<Long> deptIds) {
        Map<Long, String> result = new HashMap<>();
        if (deptIds == null || deptIds.isEmpty()) {
            return result;
        }
        Set<Long> valid = deptIds.stream().filter(id -> id != null).collect(Collectors.toSet());
        if (valid.isEmpty()) {
            return result;
        }
        // 1) 查所有直接涉及 dept
        Map<Long, Department> byId = deptRepo.findAllById(valid).stream()
                .collect(Collectors.toMap(Department::getId, d -> d));
        if (byId.isEmpty()) {
            return result;
        }
        // 2) 沿 parent 链向上, 收集所有未在 byId 里的祖先
        Set<Long> toLoad = new java.util.HashSet<>();
        for (Department d : byId.values()) {
            Long parent = d.getParentId();
            while (parent != null && !byId.containsKey(parent) && !toLoad.contains(parent)) {
                toLoad.add(parent);
                // 防止死循环 (软删的脏数据)
                Department p = byId.get(parent);
                parent = p == null ? null : p.getParentId();
            }
        }
        if (!toLoad.isEmpty()) {
            deptRepo.findAllById(toLoad).forEach(d -> byId.put(d.getId(), d));
        }
        // 3) 计算每个 dept 的 path
        for (Long id : valid) {
            result.put(id, walkPath(id, byId));
        }
        return result;
    }

    private String walkPath(Long leafId, Map<Long, Department> byId) {
        Department d = byId.get(leafId);
        if (d == null) return "";
        java.util.List<String> names = new java.util.ArrayList<>();
        java.util.Set<Long> visited = new java.util.HashSet<>();
        Long cur = leafId;
        while (cur != null && !visited.contains(cur)) {
            visited.add(cur);
            Department curDept = byId.get(cur);
            if (curDept == null) break;
            names.add(0, curDept.getName());
            cur = curDept.getParentId();
        }
        return String.join(" / ", names);
    }
}
