package com.company.zhiyu.module.dingtalk;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 钉钉考勤(每日打卡)记录 (V4.30)
 *
 * 数据源: 钉钉 attendance API, 一条记录 = 一个用户某天的一次打卡(上班 OR 下班)
 *   - OnDuty 上班打卡
 *   - OffDuty 下班打卡
 *
 * 字段定义: 钉钉 attendance record 原始 JSON 平铺
 *   - timeResult: Normal / Tardy(迟到) / Early(早退) / SeriousTardy(严重迟到) / NotSigned(缺卡)
 *   - locationResult: Normal / Outside(外勤) / Invalid(无效)
 */
@Entity
@Table(name = "dingtalk_attendance")
@Getter
@Setter
public class DingTalkAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 钉钉 recordId (用于去重) */
    @Column(name = "record_id", nullable = false, unique = true, length = 96)
    private String recordId;

    @Column(name = "userid", nullable = false, length = 64)
    private String userid;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /** OnDuty / OffDuty */
    @Column(name = "check_type", nullable = false, length = 16)
    private String checkType = "";

    /** USER(手动打卡) / BT(蓝牙) / FACE(人脸) / SYSTEM(自动) / APPROVED(补卡) */
    @Column(name = "source", nullable = false, length = 16)
    private String source = "";

    /** Normal / Tardy / Early / SeriousTardy / NotSigned */
    @Column(name = "time_result", nullable = false, length = 16)
    private String timeResult = "";

    @Column(name = "location_method", nullable = false, length = 16)
    private String locationMethod = "";

    @Column(name = "location_result", nullable = false, length = 16)
    private String locationResult = "";

    @Column(name = "plan_time")
    private Instant planTime;

    @Column(name = "actual_time")
    private Instant actualTime;

    @Column(name = "base_check_time")
    private Instant baseCheckTime;

    /** 关联 PMO 业务用户 */
    @Column(name = "user_id")
    private Long pmoUserId;

    @Column(name = "user_name", length = 64)
    private String userName;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "dingtalk_updated_at")
    private Instant dingtalkUpdatedAt;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt = Instant.now();

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
