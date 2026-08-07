package com.hex.projectgovern.module.cost;

import com.hex.projectgovern.module.cost.dto.CostDtos.CostDimensionResponse;
import com.hex.projectgovern.module.cost.dto.CostDtos.CostDimensionRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * T3 多维成本核算服务 (P0-F2)
 * 底层 3 张视图: v_project_cost / v_phase_cost / v_dept_cost
 *
 * 设计:
 *  - 单端点 /api/cost/dimension?dim=... 切换维度
 *  - dim=ALL 时返回 3 维度并联 (财务驾驶舱用)
 *  - 任何 month 为空时返回所有月份历史 (按 month 排序)
 *  - costPct 在 service 内计算, 不放 view 减少冗余
 *  - SQL 同时兼容 PostgreSQL (生产) 与 MySQL (开发)
 *    * 不使用反引号 `, 列名一律裸写 (lowercase, 视图已建立成小写)
 *    * 不使用 CAST UNSIGNED/SIGNED (MySQL 专属), 改用 NULL::BIGINT 形式
 *    * CONCAT(...) 在 PG 和 MySQL 语法相同
 */
@Service
@RequiredArgsConstructor
public class CostDimensionService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public CostDimensionResponse byProject(String yearMonth) {
        return query(
                "PROJECT", yearMonth,
                """
                SELECT
                    CAST(project_id AS CHAR)     AS key,
                    project_code                AS code,
                    project_name                AS label,
                    NULL                        AS phase_id,
                    NULL                        AS phase_name,
                    NULL                        AS sort_order,
                    year_month                  AS ym,
                    total_hours                 AS hours,
                    total_cost                  AS cost,
                    budget_estimate             AS budget,
                    headcount                   AS headcount
                FROM v_project_cost
                WHERE (? IS NULL OR year_month = ?)
                ORDER BY year_month DESC, total_cost DESC
                """,
                ps -> { ps.setString(1, yearMonth); ps.setString(2, yearMonth); }
        );
    }

    @Transactional(readOnly = true)
    public CostDimensionResponse byPhase(String yearMonth) {
        return query(
                "PHASE", yearMonth,
                """
                SELECT
                    CONCAT(phase_id, '-', project_id) AS key,
                    project_code                    AS code,
                    project_name                    AS label,
                    phase_id                        AS phase_id,
                    phase_name                      AS phase_name,
                    sort_order                      AS sort_order,
                    year_month                      AS ym,
                    total_hours                     AS hours,
                    total_cost                      AS cost,
                    NULL                            AS budget,
                    NULL                            AS headcount
                FROM v_phase_cost
                WHERE phase_id IS NOT NULL
                  AND (? IS NULL OR year_month = ?)
                ORDER BY year_month DESC, sort_order ASC, total_cost DESC
                """,
                ps -> { ps.setString(1, yearMonth); ps.setString(2, yearMonth); }
        );
    }

    @Transactional(readOnly = true)
    public CostDimensionResponse byDept(String yearMonth) {
        return query(
                "DEPT", yearMonth,
                """
                SELECT
                    CAST(department_id AS CHAR)   AS key,
                    dept_code                    AS code,
                    dept_name                    AS label,
                    NULL                          AS phase_id,
                    NULL                          AS phase_name,
                    NULL                          AS sort_order,
                    year_month                   AS ym,
                    total_hours                  AS hours,
                    total_cost                   AS cost,
                    NULL                          AS budget,
                    headcount                    AS headcount
                FROM v_dept_cost
                WHERE (? IS NULL OR year_month = ?)
                ORDER BY year_month DESC, total_cost DESC
                """,
                ps -> { ps.setString(1, yearMonth); ps.setString(2, yearMonth); }
        );
    }

    /**
     * 维度查询统一入口
     *  - dim=PROJECT|PHASE|DEPT
     *  - month=YYYY-MM 或 null
     */
    @Transactional(readOnly = true)
    public CostDimensionResponse query(String dim, String yearMonth) {
        if (dim == null) dim = "PROJECT";
        return switch (dim.toUpperCase()) {
            case "PHASE" -> byPhase(yearMonth);
            case "DEPT"  -> byDept(yearMonth);
            default      -> byProject(yearMonth);
        };
    }

    // ============== 私有查询助手 ==============
    private CostDimensionResponse query(String dim, String yearMonth, String sql, SqlBinder binder) {
        List<CostDimensionRow> raw = jdbc.query(
                (java.sql.Connection c) -> {
                    var ps = c.prepareStatement(sql);
                    binder.bind(ps);
                    return ps;
                },
                (rs, i) -> new CostDimensionRow(
                        dim,
                        rs.getString("key"),
                        rs.getString("code"),
                        rs.getString("label"),
                        rs.getObject("phase_id") == null ? null : rs.getLong("phase_id"),
                        rs.getString("phase_name"),
                        rs.getObject("sort_order") == null ? null : rs.getInt("sort_order"),
                        rs.getString("ym"),
                        rs.getBigDecimal("hours"),
                        rs.getBigDecimal("cost"),
                        rs.getBigDecimal("budget"),
                        null,   // costRate 下面算
                        rs.getObject("headcount") == null ? null : rs.getLong("headcount"),
                        null    // costPct 下面算
                )
        );
        return aggregate(dim, yearMonth, raw);
    }

    /**
     * 聚合: 算 costRate (cost/hours) 和 costPct (单行 cost / 总 cost)
     */
    private CostDimensionResponse aggregate(String dim, String yearMonth, List<CostDimensionRow> rows) {
        if (rows.isEmpty()) {
            return new CostDimensionResponse(dim, yearMonth,
                BigDecimal.ZERO, BigDecimal.ZERO, 0L,
                0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                rows);
        }
        BigDecimal totalCost = rows.stream()
                .map(CostDimensionRow::cost)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalHours = rows.stream()
                .map(CostDimensionRow::hours)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalHead = rows.stream()
                .map(CostDimensionRow::headcount)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue).sum();
        // 派生 4 个 KPI
        long activeProjects = rows.stream()
                .map(CostDimensionRow::key)
                .filter(java.util.Objects::nonNull)
                .distinct().count();
        BigDecimal avgCostPerUser = totalHead > 0
            ? totalCost.divide(BigDecimal.valueOf(totalHead), 2, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        long rowsWithBudget = rows.stream()
                .filter(r -> r.budget() != null && r.budget().signum() > 0)
                .count();
        BigDecimal budgetCoveragePct = rows.size() > 0
            ? BigDecimal.valueOf(rowsWithBudget * 10000L / rows.size()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        BigDecimal avgHourlyRate = totalHours.signum() > 0
            ? totalCost.divide(totalHours, 2, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // 二次映射: 计算派生字段
        List<CostDimensionRow> enriched = new ArrayList<>(rows.size());
        for (CostDimensionRow r : rows) {
            BigDecimal cr = (r.hours() != null && r.hours().signum() > 0 && r.cost() != null)
                    ? r.cost().divide(r.hours(), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal pct = (totalCost.signum() > 0 && r.cost() != null)
                    ? r.cost().multiply(BigDecimal.valueOf(100))
                      .divide(totalCost, 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            enriched.add(new CostDimensionRow(
                    r.dimension(), r.key(), r.code(), r.label(),
                    r.phaseId(), r.phaseName(), r.sortOrder(),
                    r.yearMonth(), r.hours(), r.cost(), r.budget(),
                    cr, r.headcount(), pct
            ));
        }
        return new CostDimensionResponse(dim, yearMonth,
            totalHours, totalCost, totalHead,
            activeProjects, avgCostPerUser, budgetCoveragePct, avgHourlyRate,
            enriched);
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(java.sql.PreparedStatement ps) throws java.sql.SQLException;
    }
}