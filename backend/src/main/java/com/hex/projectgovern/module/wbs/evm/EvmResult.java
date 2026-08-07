package com.hex.projectgovern.module.wbs.evm;

import java.math.BigDecimal;

/**
 * EVM 一次计算的结果 — 不可变 record, 11 字段。
 * <p>对齐 {@code budget_snapshot} 表的 13 字段 (id/createdAt/snapshotDate 略)。
 * <p>所有派生指标 (CPI/SPI/EAC/ETC/VAC/CV/SV) 均为 6 位有效数字 / 14 位整数位。
 *
 * <h2>公式来源</h2>
 *  - 4 输入:  BAC / PV / EV / AC   (用户/后端传入)
 *  - 7 派生:  CV / SV / CPI / SPI / EAC / ETC / VAC
 *  - 全部在 {@link EvmCalculator} 计算, 本 record 只承载
 *
 * <h2>对应表 schema</h2>
 * <pre>
 *   budget_snapshot:
 *     bac  NUMERIC(14,2)  完工预算
 *     pv   NUMERIC(14,2)  计划值
 *     ev   NUMERIC(14,2)  挣值
 *     ac   NUMERIC(14,2)  实际成本
 *     cpi  NUMERIC(6,3)   成本绩效指数
 *     spi  NUMERIC(6,3)   进度绩效指数
 *     eac  NUMERIC(14,2)  完工估算
 *     etc  NUMERIC(14,2)  完工尚需
 *     vac  NUMERIC(14,2)  完工偏差
 * </pre>
 *
 * @param bac  完工预算 (Budget At Completion)
 * @param pv   计划值   (Planned Value)
 * @param ev   挣值     (Earned Value)
 * @param ac   实际成本 (Actual Cost)
 * @param cv   成本偏差 EV - AC
 * @param sv   进度偏差 EV - PV
 * @param cpi  成本绩效 EV / AC, AC=0 时为 1.000
 * @param spi  进度绩效 EV / PV, PV=0 时为 1.000
 * @param eac  完工估算 BAC / CPI, CPI=0 时为 BAC
 * @param etc  完工尚需 EAC - AC
 * @param vac  完工偏差 BAC - EAC
 */
public record EvmResult(
        BigDecimal bac,
        BigDecimal pv,
        BigDecimal ev,
        BigDecimal ac,
        BigDecimal cv,
        BigDecimal sv,
        BigDecimal cpi,
        BigDecimal spi,
        BigDecimal eac,
        BigDecimal etc,
        BigDecimal vac
) {
    /**
     * 健康度等级 — 给前端 (EVM 卡片) 用。
     * <pre>
     *   GOOD  CPI≥0.95 且 SPI≥0.95
     *   BAD   CPI<0.85  或 SPI<0.85
     *   WARN  其他
     * </pre>
     */
    public enum Health { GOOD, WARN, BAD }

    /**
     * @return 健康度 (基于 CPI 与 SPI)
     */
    public Health health() {
        double c = cpi != null ? cpi.doubleValue() : 1.0;
        double s = spi != null ? spi.doubleValue() : 1.0;
        if (c >= 0.95 && s >= 0.95) return Health.GOOD;
        if (c < 0.85  || s < 0.85)  return Health.BAD;
        return Health.WARN;
    }
}
