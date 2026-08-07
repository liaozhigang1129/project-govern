package com.hex.projectgovern.module.dingtalk;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 钉钉考勤每日聚合 (V4.33)
 *
 * 一行 = 一个 userid 一个 work_date, 把同一天所有 OnDuty/OffDuty 打卡聚合成"上下班各一次"
 *
 * 数据源: 钉钉 /attendance/listRecord 原始多条记录, 同步 service 在 runSync() 内聚合写入
 *   - 上班取 earliest OnDuty/Before
 *   - 下班取 latest  OffDuty/After
 *   - check_type 兼容: OnDuty/OffDuty (老钉钉) 和 Before/After (新钉钉)
 *
 * 字段语义:
 *   - timeResult: Normal / Late(迟到) / Early(早退) / SeriousLate / NotSigned(缺卡)
 *   - locationResult: Normal / Outside(外勤) / Invalid(无效)
 *   - source: MAP/ATM/WIFI/OTHER (钉钉实测, 不一定是 USER/SYSTEM/BT/FACE)
 *
 * 关联老表 dingtalk_attendance (V4.30, 冻结只读):
 *   raw_record_ids 存当天原始 recordId JSON 数组
 *   详情抽屉: SELECT * FROM dingtalk_attendance WHERE record_id IN (raw_record_ids)
 */
@Entity
@Table(name = "dingtalk_attendance_daily",
        uniqueConstraints = @UniqueConstraint(name = "uq_daily_user_date", columnNames = {"userid", "work_date"}))
@Getter
@Setter
public class DingTalkAttendanceDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ============== 聚合维度 ==============

    /** 钉钉 userid (聚合唯一键 1/2) */
    @Column(name = "userid", nullable = false, length = 64)
    private String userid;

    /** 工作日 本地时区 (聚合唯一键 2/2) */
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    // ============== 上班 (兼容 check_type ∈ {OnDuty, Before}) ==============

    @Column(name = "on_duty_plan")
    private Instant onDutyPlan;

    @Column(name = "on_duty_actual")
    private Instant onDutyActual;

    /** Normal / Late / Early / SeriousLate / NotSigned */
    @Column(name = "on_duty_result", nullable = false, length = 16)
    private String onDutyResult = "";

    /** MAP/ATM/WIFI/OTHER */
    @Column(name = "on_duty_source", nullable = false, length = 16)
    private String onDutySource = "";

    @Column(name = "on_duty_location", nullable = false, length = 256)
    private String onDutyLocation = "";

    @Column(name = "on_duty_location_method", nullable = false, length = 16)
    private String onDutyLocationMethod = "";

    @Column(name = "on_duty_location_result", nullable = false, length = 16)
    private String onDutyLocationResult = "";

    // ============== 下班 (兼容 check_type ∈ {OffDuty, After}) ==============

    @Column(name = "off_duty_plan")
    private Instant offDutyPlan;

    @Column(name = "off_duty_actual")
    private Instant offDutyActual;

    @Column(name = "off_duty_result", nullable = false, length = 16)
    private String offDutyResult = "";

    @Column(name = "off_duty_source", nullable = false, length = 16)
    private String offDutySource = "";

    @Column(name = "off_duty_location", nullable = false, length = 256)
    private String offDutyLocation = "";

    @Column(name = "off_duty_location_method", nullable = false, length = 16)
    private String offDutyLocationMethod = "";

    @Column(name = "off_duty_location_result", nullable = false, length = 16)
    private String offDutyLocationResult = "";

    // ============== 汇总标记 ==============

    /** 当天原始打卡次数 (OnDuty+OffDuty 合计, 审计用) */
    @Column(name = "check_count", nullable = false)
    private Integer checkCount = 0;

    /** V4.34: 工作时长 (分钟) = offDutyActual - onDutyActual; 缺一为 null; 前端展示用 */
    @Column(name = "work_duration")
    private Integer workDuration;

    /** 1=当天有补卡 (钉钉 sourceType=MAKEUP, 老数据回填=0) */
    @Column(name = "is_makeup", nullable = false)
    private Boolean isMakeup = false;

    /** 1=任一打卡 timeResult ∈ {Late,Early,SeriousLate,NotSigned,SeriousEarly} */
    @Column(name = "is_abnormal", nullable = false)
    private Boolean isAbnormal = false;

    /** 异常类型汇总 "迟到;早退" */
    @Column(name = "abnormal_types", nullable = false, length = 128)
    private String abnormalTypes = "";

    // ============== 项目 (从 timesheet_entry JOIN, 同步时填) ==============

    @Column(name = "project_ids", nullable = false, length = 128)
    private String projectIds = "";

    @Column(name = "project_names", nullable = false, length = 512)
    private String projectNames = "";

    // ============== 关联 PMO ==============

    /** app_user.id */
    @Column(name = "pmo_user_id")
    private Long pmoUserId;

    @Column(name = "user_name", length = 64)
    private String userName;

    @Column(name = "department_id")
    private Long departmentId;

    // ============== 同步 / 审计 ==============

    /**
     * 当天原始打卡 record_id 数组 JSON: ["biz1","biz2"]
     * 用于详情抽屉反查老表 dingtalk_attendance
     * 用 @Lob (TEXT 类型) 存储, service 层用 Jackson 序列化
     */
    @Lob
    @Column(name = "raw_record_ids", columnDefinition = "TEXT")
    private String rawRecordIds;

    @Column(name = "dingtalk_updated_at")
    private Instant dingtalkUpdatedAt;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt = Instant.now();

    // ============== 通用 ==============

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
