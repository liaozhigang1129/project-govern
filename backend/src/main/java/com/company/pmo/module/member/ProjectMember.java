package com.company.pmo.module.member;

import com.company.pmo.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 项目组成员
 * <p>
 * 一个项目可有多人,每人对应一条记录 + 一个角色。
 * <ul>
 *   <li>user_id 可空(外部专家/客户方,只填姓名)</li>
 *   <li>member_name 冗余字段(内部 user 取 fullName,外部手填)</li>
 *   <li>join_date / leave_date 参与时间段,留空=仍在项目中</li>
 *   <li>allocation_pct 投入比例(0-100),为后续工时/资源模块预留</li>
 * </ul>
 *
 * <p>DB 约束:</p>
 * <ul>
 *   <li>FK project_id → project(id) ON DELETE CASCADE</li>
 *   <li>FK role_id → member_role(id)</li>
 *   <li>FK user_id → app_user(id) (可空)</li>
 *   <li>CHECK leave_date >= join_date</li>
 *   <li>CHECK allocation_pct BETWEEN 0 AND 100</li>
 * </ul>
 */
@Entity
@Table(name = "project_member")
@Getter @Setter @NoArgsConstructor
public class ProjectMember extends SoftDeletableEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private MemberRole role;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "member_name", nullable = false, length = 64)
    private String memberName;

    @Column(name = "is_external", nullable = false)
    private boolean external = false;

    @Column(name = "join_date", nullable = false)
    private LocalDate joinDate;

    @Column(name = "leave_date")
    private LocalDate leaveDate;

    @Column(name = "allocation_pct", nullable = false)
    private int allocationPct = 100;

    @Column(length = 256)
    private String remark;
}
