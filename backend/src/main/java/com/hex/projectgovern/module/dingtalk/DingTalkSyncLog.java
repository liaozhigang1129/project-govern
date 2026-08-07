package com.hex.projectgovern.module.dingtalk;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;

import java.time.Instant;

/**
 * 钉钉同步审计日志 (V2.13 Phase 1)
 *  - 每次全量同步一行, 含起止时间 / 触发类型 / 影响行数 / 错误详情
 *  - 用作 admin 后台"同步历史"显示
 */
@Entity
@Table(name = "dingtalk_sync_log")
@Getter @Setter @NoArgsConstructor
public class DingTalkSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** CRON / MANUAL */
    @Column(name = "trigger_type", nullable = false, length = 16)
    private String triggerType;

    /** CRON 时 = "SYSTEM", MANUAL 时 = 当前用户 username */
    @Column(name = "triggered_by", length = 64)
    private String triggeredBy;

    /** RUNNING / SUCCESS / PARTIAL / FAILED */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "total_users", nullable = false) private int totalUsers;
    @Column(name = "created_count", nullable = false) private int createdCount;
    @Column(name = "updated_count", nullable = false) private int updatedCount;
    @Column(name = "disabled_count", nullable = false) private int disabledCount;
    @Column(name = "total_depts", nullable = false) private int totalDepts;
    @Column(name = "created_dept_count", nullable = false) private int createdDeptCount;
    @Column(name = "updated_dept_count", nullable = false) private int updatedDeptCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;
}
