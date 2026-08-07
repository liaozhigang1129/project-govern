package com.hex.projectgovern.module.org.dto;

import com.hex.projectgovern.module.org.Department;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 部门树节点 (V4.14 升级版)
 * - 自身字段
 * - 递归挂 children
 * - memberCount: 该部门下"直属"用户数 (不含子部门)
 * - leaderUserId / leaderUserName / leaderUsername: 部门负责人
 * - V4.14 新增: dingtalkDeptId / dingtalkParentId / treePath / treeLevel
 */
public class DepartmentNode {
    public Long id;
    public String code;
    public String name;
    public Long parentId;
    public String parentName;
    public Integer sortOrder;
    public Boolean enabled;
    public Long memberCount;
    /** V4.18: 含子部门的总人数 (自身 + 所有后代部门用户数), 后端 Service 计算 */
    public Long memberCountTotal;
    public Long leaderUserId;
    public String leaderUsername;     // 负责人登录名
    public String leaderFullName;     // 负责人姓名
    public Instant createdAt;
    public Instant updatedAt;

    // ========== V4.14: 钉钉同步 + 层级 ==========
    public Long dingtalkDeptId;       // 钉钉 dept_id (BIGINT), null=未同步
    public Long dingtalkParentId;     // 钉钉父 dept_id
    public String treePath;           // 层级路径 /1/5/12/
    public Integer treeLevel;         // 0=根, 1=一级部门, ...
    // ============================================

    public List<DepartmentNode> children = new ArrayList<>();

    public static DepartmentNode from(Department d, Long memberCount, String parentName) {
        return from(d, memberCount, parentName, null, null, null);
    }

    /**
     * 全字段构造(带 leader 信息)
     */
    public static DepartmentNode from(Department d, Long memberCount, String parentName,
                                      String leaderUsername, String leaderFullName,
                                      Instant createdAt) {
        DepartmentNode n = new DepartmentNode();
        n.id = d.getId();
        n.code = d.getCode();
        n.name = d.getName();
        n.parentId = d.getParentId();
        n.parentName = parentName;
        n.sortOrder = d.getSortOrder();
        n.enabled = d.isEnabled();
        n.memberCount = memberCount;
        n.leaderUserId = d.getLeaderUserId();
        n.leaderUsername = leaderUsername;
        n.leaderFullName = leaderFullName;
        n.createdAt = createdAt;
        n.updatedAt = d.getUpdatedAt();
        // V4.14
        n.dingtalkDeptId = d.getDingtalkDeptId();
        n.dingtalkParentId = d.getDingtalkParentId();
        n.treePath = d.getTreePath();
        n.treeLevel = d.getTreeLevel();
        return n;
    }
}
