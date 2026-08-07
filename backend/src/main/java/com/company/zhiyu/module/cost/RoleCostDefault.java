package com.company.zhiyu.module.cost;

import com.company.zhiyu.common.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 角色档默认时薪字典 (P0-A.1 — F1 工时→成本引擎)
 *
 * <p>对齐 V4.0 {@code role_cost_default} 表。财务可调。
 * 当前 seed 6 角色档: ARCH / DEV / TEST / PM / BA / OPS。
 *
 * <p>本表是 {@link HourlyRate} (user_id IS NULL 角色档行) 的"出厂价",
 * 也是成本计算的第三级兜底:
 * <ol>
 *   <li>user_id 精确命中 hourly_rate</li>
 *   <li>否则 hourly_rate.role_code 命中</li>
 *   <li>否则本表 role_cost_default</li>
 *   <li>否则 app_user.default_hourly_rate</li>
 *   <li>否则 0 (兜底兜底)</li>
 * </ol>
 *
 * <p>改本表的 rate 字段 → 立即生效 (按 effectiveMonth 自然分摊, 历史工时取当月行)。
 *
 * @since V4.0 (2026-Q2)
 */
@Entity
@Table(name = "role_cost_default")
@Getter @Setter @NoArgsConstructor
public class RoleCostDefault extends AuditableEntity {

    /** 角色编码 (主键, 例如 ARCH / DEV / PM) */
    @Id
    @Column(length = 32)
    private String code;

    /** 显示名 (例如 架构师 / 开发 / PM) */
    @Column(nullable = false, length = 64)
    private String name;

    /** 默认时薪 (元/h), > 0 */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal rate;

    /** 列表展示顺序 */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;
}