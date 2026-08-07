package com.hex.projectgovern.module.org;

import com.hex.projectgovern.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "app_user")
@Getter @Setter @NoArgsConstructor
public class AppUser extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 256)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 64)
    private String fullName;

    @Column(unique = true, length = 128)
    private String email;

    @Column(length = 32)
    private String phone;

    @Column(name = "department_id")
    private Long departmentId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "primary_role_id", nullable = false)
    private Role primaryRole;

    @Column(name = "job_title", length = 64)
    private String jobTitle;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** V4.0 cost-control: 兜底时薪 (元/h), 优先级最低 (被 hourly_rate 覆盖) */
    @Column(name = "default_hourly_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal defaultHourlyRate = BigDecimal.ZERO;

    /** L1-1 用户管理: 登录失败累计次数 (达到阈值自动锁定) */
    @Column(name = "login_fail_count", nullable = false)
    private int loginFailCount = 0;

    /** L1-1 用户管理: 锁定到期时间 (null = 未锁定) */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** L1-1 用户管理: 最后登录 IP */
    @Column(name = "last_login_ip", length = 64)
    private String lastLoginIp;

    /** L1-1 用户管理: 密码最后修改时间 */
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    /** L1-1 用户管理: 是否强制下次登录改密 (重置密码后置 true) */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    /** 业务方法: 当前是否被锁定 */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    /**
     * 备选审批人(主审批人 disabled/deleted 时,自动 fallback 到此用户代审)。
     * 仅当 roleCode 属于审批流(DEPT_LEAD/PMO_ADMIN/EXEC)时有意义。
     */

    /**
     * 钉钉 userid (V2.13 同步用) — 与钉钉通讯录一一对应。
     * 钉钉是 source-of-truth:同步时按此字段匹配,覆盖 PMO 中的姓名/手机/邮箱/部门/职位。
     */
    @Column(name = "dingtalk_user_id", length = 64)
    private String dingtalkUserId;
    @Column(name = "backup_user_id")
    private Long backupUserId;
}
