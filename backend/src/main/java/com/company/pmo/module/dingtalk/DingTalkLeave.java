package com.company.pmo.module.dingtalk;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 钉钉请休假记录 (P5)
 * 完整字段同步,支持增量同步 (基于 dingtalk_updated_at)
 */
@Entity
@Table(name = "dingtalk_leave")
@Getter
@Setter
public class DingTalkLeave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 钉钉 leave_id (用于去重) */
    @Column(name = "leave_id", nullable = false, unique = true, length = 64)
    private String leaveId;

    /** 钉钉 userid */
    @Column(name = "userid", nullable = false, length = 64)
    private String userid;

    /** 请假类型 */
    @Column(name = "leave_type", nullable = false, length = 32)
    private String leaveType;

    /** 开始时间 */
    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    /**
     * 结束时间
     * 允许为 null: 部分审批(加班/补卡)只有开始无明确结束, 或表单组件解析失败时,
     * 旧版本 NOT NULL 会导致整批 477 条 rollback。改为可空, 业务展示时按 - 表示。
     */
    @Column(name = "end_time")
    private Instant endTime;

    /** 时长(小时) */
    @Column(name = "duration", nullable = false, precision = 8, scale = 2)
    private BigDecimal duration = BigDecimal.ZERO;

    /** 时长单位 */
    @Column(name = "duration_unit", nullable = false, length = 16)
    private String durationUnit = "HOUR";

    /** 请假原因 */
    @Lob
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    /** 状态: NORMAL/REJECT/REVOKE */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "NORMAL";

    /** 审批人 userid */
    @Column(name = "approver_userid", length = 64)
    private String approverUserid;

    /** 关联 PMO 业务用户 id (避免与 userid 字段冲突,改名) */
    @Column(name = "user_id")
    private Long pmoUserId;

    /** 冗余姓名 */
    @Column(name = "user_name", length = 64)
    private String userName;

    /** 冗余部门 */
    @Column(name = "department_id")
    private Long departmentId;

    /** 钉钉端最后更新时间 (用于增量同步) */
    @Column(name = "dingtalk_updated_at")
    private Instant dingtalkUpdatedAt;

    /** 本次同步时间 */
    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt = Instant.now();

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
