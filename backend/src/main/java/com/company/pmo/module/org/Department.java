package com.company.pmo.module.org;

import com.company.pmo.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "department")
@Getter @Setter @NoArgsConstructor
public class Department extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;


    /**
     * 钉钉 dept_id (V2.13 同步用) — 唯一对应钉钉通讯录部门节点。
     * 根部门钉钉 = 1, 同步时按此字段匹配;未匹配时按 code/name 反查或新建。
     */
    @Column(name = "dingtalk_dept_id")
    private Long dingtalkDeptId;

    /**
     * V4.14: 钉钉父部门 ID (同步时暂存, 用于建立 self-ref parent_id)
     */
    @Column(name = "dingtalk_parent_id")
    private Long dingtalkParentId;

    /**
     * V4.14: 部门层级路径 /1/5/12/ (根→叶), 用于快速查祖先/子部门
     */
    @Column(name = "tree_path", length = 255)
    private String treePath;

    /**
     * V4.14: 部门层级 (0=根, 1=一级部门, ...)
     */
    @Column(name = "tree_level", nullable = false)
    private int treeLevel = 0;

    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * V4.12: 部门负责人 user_id
     *  - 负责人离职/调岗后, FK ON DELETE SET NULL 自动清空
     *  - 通知/审批流用此字段找收件人,优先级高于 app_user.department_id 的派生计算
     */
    @Column(name = "leader_user_id")
    private Long leaderUserId;

    @OneToMany(mappedBy = "parentId")
    @Transient
    private List<Department> children = new ArrayList<>();
}
