package com.company.zhiyu.module.wbs.evm;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * EVM (Earned Value Management) 挣值分析 — 纯 Java 工具类。
 * <p>无 Spring / 无 JPA 依赖, 纯计算, 单测易覆盖。
 * <p>所有方法都是 <b>静态</b>, 类不可实例化。
 *
 * <h2>6 大公式 (PMI PMBOK 第 6/7 版标准)</h2>
 * <pre>
 *   1. CV  (Cost Variance)      = EV - AC
 *   2. SV  (Schedule Variance)   = EV - PV
 *   3. CPI (Cost Performance)    = EV / AC        (AC=0 → 1.000)
 *   4. SPI (Schedule Performance)= EV / PV        (PV=0 → 1.000)
 *   5. EAC (Estimate At Complete)= BAC / CPI      (CPI=0 → BAC)
 *   6. ETC (Estimate To Complete)= EAC - AC
 *   7. VAC (Variance At Complete)= BAC - EAC
 * </pre>
 *
 * <h2>数值约定</h2>
 *  - 金额字段 (BAC/PV/EV/AC/CV/SV/EAC/ETC/VAC) 保留 2 位小数 (NUMERIC(14,2))
 *  - 指数字段 (CPI/SPI) 保留 3 位小数 (NUMERIC(6,3))
 *  - 所有除法用 {@link RoundingMode#HALF_UP} (PMBOK 推荐的财务舍入)
 *  - 输入 null 或负数时抛 {@link IllegalArgumentException}
 *  - 0 除法返回安全值 (CPI/SPI=1.0, EAC=BAC), 避免 NPE / Infinity
 *
 * <h2>对应 SQL 函数</h2>
 * PG 端 {@code pmo.fn_snapshot_evm(projectId, source, operatorId)} 调用
 * 本工具类相同口径, 文档化对照如下:
 * <pre>
 *   CV  = ev - ac
 *   SV  = ev - pv
 *   CPI = ev / NULLIF(ac, 0)   :: NUMERIC(6,3)  ROUND HALF_UP
 *   SPI = ev / NULLIF(pv, 0)   :: NUMERIC(6,3)  ROUND HALF_UP
 *   EAC = bac / NULLIF(cpi, 0) :: NUMERIC(14,2) ROUND HALF_UP
 *   ETC = eac - ac
 *   VAC = bac - eac
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>
 *   EvmResult r = EvmCalculator.compute(
 *       new BigDecimal("100000"),  // BAC
 *       new BigDecimal("50000"),   // PV
 *       new BigDecimal("40000"),   // EV
 *       new BigDecimal("50000"));  // AC
 *
 *   r.cpi();   // 0.800  (超支)
 *   r.spi();   // 0.800  (滞后)
 *   r.cv();    // -10000
 *   r.sv();    // -10000
 *   r.eac();   // 125000
 *   r.health();// BAD
 * </pre>
 *
 * @author PMO
 * @since P3.1
 */
public final class EvmCalculator {

    /** 金额保留位数 — 对齐 budget_snapshot.pv/ev/ac/bac 字段 NUMERIC(14,2) */
    private static final int MONEY_SCALE = 2;
    /** 指数保留位数 — 对齐 budget_snapshot.cpi/spi 字段 NUMERIC(6,3) */
    private static final int INDEX_SCALE = 3;
    /** 财务舍入 (PMBOK 推荐) */
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    /** 安全默认值 (项目未开始 / 0 除法时) */
    private static final BigDecimal SAFE_CPI = BigDecimal.ONE;
    private static final BigDecimal SAFE_SPI = BigDecimal.ONE;

    private EvmCalculator() {
        throw new AssertionError("工具类禁止实例化");
    }

    /**
     * 一次性计算所有 11 字段 (4 输入 + 7 派生) — 公共 API。
     *
     * @param bac 完工预算 (≥ 0)
     * @param pv  计划值   (≥ 0)
     * @param ev  挣值     (≥ 0)
     * @param ac  实际成本 (≥ 0)
     * @return 11 字段结果
     * @throws IllegalArgumentException 任一输入为 null 或负数
     */
    public static EvmResult compute(BigDecimal bac, BigDecimal pv, BigDecimal ev, BigDecimal ac) {
        validate(bac, pv, ev, ac);

        BigDecimal cv = ev.subtract(ac).setScale(MONEY_SCALE, RM);
        BigDecimal sv = ev.subtract(pv).setScale(MONEY_SCALE, RM);
        BigDecimal cpi = divideSafe(ev, ac, SAFE_CPI).setScale(INDEX_SCALE, RM);
        BigDecimal spi = divideSafe(ev, pv, SAFE_SPI).setScale(INDEX_SCALE, RM);
        BigDecimal eac = divideSafe(bac, cpi, bac).setScale(MONEY_SCALE, RM);
        BigDecimal etc = eac.subtract(ac).setScale(MONEY_SCALE, RM);
        BigDecimal vac = bac.subtract(eac).setScale(MONEY_SCALE, RM);

        return new EvmResult(bac, pv, ev, ac, cv, sv, cpi, spi, eac, etc, vac);
    }

    // ============================================================
    // 单独公式 (供单测细粒度覆盖, 也供 service 层按需调用)
    // ============================================================

    /** CV = EV - AC */
    public static BigDecimal cv(BigDecimal ev, BigDecimal ac) {
        requireNonNull(ev, "ev"); requireNonNull(ac, "ac");
        return ev.subtract(ac).setScale(MONEY_SCALE, RM);
    }

    /** SV = EV - PV */
    public static BigDecimal sv(BigDecimal ev, BigDecimal pv) {
        requireNonNull(ev, "ev"); requireNonNull(pv, "pv");
        return ev.subtract(pv).setScale(MONEY_SCALE, RM);
    }

    /** CPI = EV / AC, AC=0 时返回 1.000 (避免 0 除) */
    public static BigDecimal cpi(BigDecimal ev, BigDecimal ac) {
        requireNonNull(ev, "ev"); requireNonNull(ac, "ac");
        return divideSafe(ev, ac, SAFE_CPI).setScale(INDEX_SCALE, RM);
    }

    /** SPI = EV / PV, PV=0 时返回 1.000 (避免 0 除) */
    public static BigDecimal spi(BigDecimal ev, BigDecimal pv) {
        requireNonNull(ev, "ev"); requireNonNull(pv, "pv");
        return divideSafe(ev, pv, SAFE_SPI).setScale(INDEX_SCALE, RM);
    }

    /** EAC = BAC / CPI, CPI=0 时返回 BAC (避免 0 除) */
    public static BigDecimal eac(BigDecimal bac, BigDecimal cpi) {
        requireNonNull(bac, "bac"); requireNonNull(cpi, "cpi");
        return divideSafe(bac, cpi, bac).setScale(MONEY_SCALE, RM);
    }

    /** ETC = EAC - AC */
    public static BigDecimal etc(BigDecimal eac, BigDecimal ac) {
        requireNonNull(eac, "eac"); requireNonNull(ac, "ac");
        return eac.subtract(ac).setScale(MONEY_SCALE, RM);
    }

    /** VAC = BAC - EAC */
    public static BigDecimal vac(BigDecimal bac, BigDecimal eac) {
        requireNonNull(bac, "bac"); requireNonNull(eac, "eac");
        return bac.subtract(eac).setScale(MONEY_SCALE, RM);
    }

    // ============================================================
    // 工具方法
    // ============================================================

    /**
     * 安全除法 — 分母为 0 / null 时返回 fallback, 否则做除法。
     * <p>不修改 scale, 由调用方根据字段类型 setScale。
     */
    private static BigDecimal divideSafe(BigDecimal numerator, BigDecimal denominator, BigDecimal fallback) {
        if (denominator == null || denominator.signum() == 0) {
            return fallback;
        }
        return numerator.divide(denominator, 10, RM);  // 高精度中间值, 外层 setScale 收口
    }

    /**
     * 4 输入参数全量校验 — 拒绝 null / 负数。
     * <p>0 是允许的 (项目刚启动, EV/PV/AC 可能全 0)。
     */
    private static void validate(BigDecimal bac, BigDecimal pv, BigDecimal ev, BigDecimal ac) {
        requireNonNull(bac, "bac");
        requireNonNull(pv,  "pv");
        requireNonNull(ev,  "ev");
        requireNonNull(ac,  "ac");
        if (bac.signum() < 0) throw new IllegalArgumentException("bac 不能为负: " + bac);
        if (pv.signum()  < 0) throw new IllegalArgumentException("pv 不能为负: "  + pv);
        if (ev.signum()  < 0) throw new IllegalArgumentException("ev 不能为负: "  + ev);
        if (ac.signum()  < 0) throw new IllegalArgumentException("ac 不能为负: "  + ac);
    }

    private static void requireNonNull(BigDecimal v, String name) {
        if (v == null) throw new IllegalArgumentException(name + " 不能为 null");
    }
}
