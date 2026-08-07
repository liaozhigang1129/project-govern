package com.hex.projectgovern.module.cost;

import com.hex.projectgovern.common.entity.AuditableEntity;
import com.hex.projectgovern.module.org.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 工时费率 (P0-A.1 — F1 工时→成本引擎)
 *
 * <p>对齐 V4.0 {@code hourly_rate} 表。同一人/同角色在时间轴上允许多段调价:
 * <ul>
 *   <li>{@code userId} 为 null  → 全局角色档 (role_code 维度)</li>
 *   <li>{@code userId} 非 null  → 单人 override, 优先级高于角色档</li>
 * </ul>
 *
 * <p>生效区间: {@code [effectiveMonth, endMonth]}, 任一端为 null = 开放端。
 * 查询算法 (在 {@code HourlyRateRepository} / {@code HourlyRateService} 中实现):
 *   1. user_id 精确命中 → 取该 user 在该月生效的行
 *   2. 否则按 role_code 取最新一份
 *   3. 否则查 role_cost_default 角色档默认
 *   4. 否则用 app_user.default_hourly_rate
 *
 * <p>审计: 操作历史 (新增/调价/关停) 全部由 AuditLog 注解覆盖, 不在本表冗余。
 *
 * @since V4.0 (2026-Q2)
 */
@Entity
@Table(name = "hourly_rate_v4", indexes = {
        @Index(name = "idx_hourly_rate_v4_user_month", columnList = "user_id, effective_month"),
        @Index(name = "idx_hourly_rate_v4_role_month", columnList = "role_code, effective_month")
})
@Getter @Setter @NoArgsConstructor
public class HourlyRate extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 角色编码 (FK→role_cost_default.code), 不允许 null */
    @Column(name = "role_code", nullable = false, length = 32)
    private String roleCode;

    /** 单人 override; null = 角色档默认价 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    /** 时薪 (元/小时), 必填 > 0 */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal rate;

    /** 生效月份 (YYYY-MM-01), 必填 */
    @Column(name = "effective_month", nullable = false)
    private LocalDate effectiveMonth;

    /** 失效月份 (YYYY-MM-01, 含); null = 仍生效 */
    @Column(name = "end_month")
    private LocalDate endMonth;

    /** 备注 / 调价原因 */
    @Column(length = 256)
    private String remark;

    /** 创建人 (FK→app_user.id), 由 service 在写入时回填 */
    @Column(name = "created_by")
    private Long createdBy;

    /** 校验: rate > 0 */
    @PrePersist @PreUpdate
    void validate() {
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalStateException("hourly_rate.rate 必须 > 0, 实得: " + rate);
        }
        if (effectiveMonth == null) {
            throw new IllegalStateException("hourly_rate.effectiveMonth 不能为空");
        }
        if (endMonth != null && endMonth.isBefore(effectiveMonth)) {
            throw new IllegalStateException("endMonth(" + endMonth + ") 必须 >= effectiveMonth(" + effectiveMonth + ")");
        }
    }

    /** 业务方法: 在 month (YYYY-MM-01) 是否生效 */
    public boolean isEffectiveOn(LocalDate month) {
        if (effectiveMonth == null || month == null) return false;
        if (month.isBefore(effectiveMonth)) return false;
        return endMonth == null || !month.isAfter(endMonth);
    }
}