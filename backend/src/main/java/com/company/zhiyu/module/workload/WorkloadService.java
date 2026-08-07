package com.company.zhiyu.module.workload;

import com.company.zhiyu.module.workload.dto.WorkloadDtos.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * P2.B 人员负载查询服务。
 *
 * <p>三个查询:
 * <ol>
 *   <li>{@link #userLoadMatrix(Long, Long, LocalDate, LocalDate)} — 人 × 周 矩阵
 *   <li>{@link #projectLoad(Long, LocalDate, LocalDate)} — 单项目汇总 + 分人 + 分日
 * </ol>
 *
 * <p>纯 SQL 聚合(走 EntityManager.createNativeQuery)以避免 JPQL 多表 join
 * 在 PG/MySQL 方言差异;COUNT/SUM/GROUP BY 用最基本语法。
 */
@Service
@RequiredArgsConstructor
public class WorkloadService {

    @PersistenceContext
    private EntityManager em;

    // ---------------------------------------------------------------
    // 1) 人 × 周 矩阵
    // ---------------------------------------------------------------

    /**
     * 列出 [from, to] 区间内每位用户每周的总工时。
     * <p>无工时的(用户 × 周)也补一行,status=NO_DATA — 这样前端画矩阵不缺格。
     *
     * @param departmentId 部门过滤(null = 全员;非 null = 仅该部门)
     * @param scopeUserId   仅看某人(null = 全员;PM 自己查自己时用)
     */
    @Transactional(readOnly = true)
    public UserLoadMatrix userLoadMatrix(Long departmentId, Long scopeUserId,
                                         LocalDate from, LocalDate to) {
        // 0) 找区间内所有活跃用户(可能没工时也要返回)
        String userSql = scopeUserId != null
                ? "SELECT id, username, full_name, department_id, department_name " +
                  "FROM v_active_user WHERE id = :uid ORDER BY id"
                : "SELECT id, username, full_name, department_id, department_name " +
                  "FROM v_active_user " +
                  (departmentId != null ? "WHERE department_id = :did " : "") +
                  "ORDER BY id";
        var userQ = em.createNativeQuery(userSql);
        if (scopeUserId != null) {
            userQ.setParameter("uid", scopeUserId);
        } else if (departmentId != null) {
            userQ.setParameter("did", departmentId);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> userRows = userQ.getResultList();

        // 1) 拉取所有工时明细
        StringBuilder sql = new StringBuilder(
                "SELECT u.id, ts.week_start, ts.week_end, ts.status, " +
                "       COALESCE(SUM(te.hours), 0), " +
                "       COUNT(DISTINCT te.project_id), " +
                "       COUNT(te.id) " +
                "FROM app_user u " +
                "JOIN timesheet_week ts  ON ts.user_id = u.id AND ts.deleted = FALSE " +
                "LEFT JOIN timesheet_entry te ON te.timesheet_id = ts.id " +
                "WHERE u.deleted = FALSE ");
        if (departmentId != null) {
            sql.append("AND u.department_id = :did ");
        }
        if (scopeUserId != null) {
            sql.append("AND u.id = :uid ");
        }
        sql.append("AND ts.week_start >= :from AND ts.week_start <= :to " +
                "GROUP BY u.id, ts.week_start, ts.week_end, ts.status");

        var query = em.createNativeQuery(sql.toString());
        if (departmentId != null) query.setParameter("did", departmentId);
        if (scopeUserId != null)  query.setParameter("uid", scopeUserId);
        query.setParameter("from", Date.valueOf(from));
        query.setParameter("to",   Date.valueOf(to));

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        // P2.5: 拉该用户集合下, [from, from+14d) 窗口内里程碑数
        // 直接聚合到 userId, 不按周切, 后续按 userId 全局合并 (同一人多周显示同一组里程碑)
        // 但前端要"周维度", 改成"该人该周内里程碑数":
        //   用 LEFT JOIN te 顺带关联 m, 但 m 属于 project 不属于 te, 复杂
        // 简化为: 先拉 (userId, milestoneId) 全集, 在 Java 层按 week 分类
        //  思路: 收集 userIds, 查他们参与项目下的所有 m.plan_date BETWEEN from AND to+14d
        Set<Long> uidsInScope = rows.stream()
                .map(r -> ((Number) r[0]).longValue())
                .collect(Collectors.toSet());
        // 再补: userRows 里没工时但有用户的
        if (scopeUserId != null) uidsInScope.add(scopeUserId);
        for (Object[] u : userRows) uidsInScope.add(((Number) u[0]).longValue());

        //  (userId, milestoneId)  →  planDate, statusCode
        // 从 userId 找到他参与的项目 → 这些项目下的里程碑 (in window)
        @SuppressWarnings("unchecked")
        List<Object[]> msRows = uidsInScope.isEmpty() ? List.of() : em.createNativeQuery(
                "SELECT ts.user_id, m.id, m.plan_date, ms.code " +
                "FROM timesheet_entry te " +
                "JOIN timesheet_week ts ON ts.id = te.timesheet_id AND ts.deleted = FALSE " +
                "JOIN app_user u        ON u.id = ts.user_id AND u.deleted = FALSE " +
                "JOIN project p         ON p.id = te.project_id AND p.deleted = FALSE " +
                "JOIN milestone m       ON m.project_id = p.id " +
                "                          AND m.deleted = FALSE " +
                "                          AND m.plan_date >= :mfrom " +
                "                          AND m.plan_date < :mto " +
                "JOIN milestone_status ms ON ms.id = m.status_id " +
                "WHERE te.deleted = FALSE " +
                "  AND ts.user_id IN (:uids) " +
                "GROUP BY ts.user_id, m.id, m.plan_date, ms.code"
        )
                .setParameter("mfrom", Date.valueOf(from))
                .setParameter("mto", Date.valueOf(to.plusDays(14)))
                .setParameter("uids", uidsInScope)
                .getResultList();

        // 按 userId → milestones 索引 (去重 planDate)
        // SQL 列顺序: ts.user_id(Long), m.id(Long), m.plan_date(Date), ms.code(String)
        Map<Long, List<LocalDate>> userMilestones = new HashMap<>();
        for (Object[] r : msRows) {
            Long uid = ((Number) r[0]).longValue();
            Long mid = ((Number) r[1]).longValue();  // 跳过 m.id
            LocalDate pd = ((Date) r[2]).toLocalDate();
            userMilestones.computeIfAbsent(uid, k -> new ArrayList<>()).add(pd);
        }

        // 2) 索引: (userId, weekStart) → row
        Map<String, UserWeekRow> indexed = new LinkedHashMap<>();
        for (Object[] r : rows) {
            Long uid    = ((Number) r[0]).longValue();
            LocalDate w = ((Date) r[1]).toLocalDate();
            LocalDate e = ((Date) r[2]).toLocalDate();
            String st   = (String) r[3];
            BigDecimal hrs = new BigDecimal(r[4].toString());
            int projCnt = ((Number) r[5]).intValue();
            int entCnt  = ((Number) r[6]).intValue();
            // 阶段 3.5: 该人所有里程碑, 落在 [w, w+6d] 内的计入该周
            List<LocalDate> allDates = userMilestones.getOrDefault(uid, List.of());
            int mc = 0, uc = 0;
            for (LocalDate pd : allDates) {
                if (!pd.isBefore(w) && !pd.isAfter(e)) {
                    mc++;
                    // upcoming = 14 天内到期, 相对于 from 不是 weekStart
                    if (!pd.isBefore(from) && pd.isBefore(from.plusDays(14))) {
                        uc++;
                    }
                }
            }
            indexed.put(uid + ":" + w,
                    new UserWeekRow(uid, null, null, null, null, w, e, st, hrs, projCnt, entCnt, mc, uc));
        }

        // 3) 把 userRows 信息补齐 + 给无数据的(用户,周)补 NO_DATA
        List<UserWeekRow> result = new ArrayList<>();
        for (Object[] u : userRows) {
            Long uid    = ((Number) u[0]).longValue();
            String uname = (String) u[1];
            String fname = (String) u[2];
            Long did    = u[3] == null ? null : ((Number) u[3]).longValue();
            String dname = (String) u[4];
            // 遍历区间内每个周一
            LocalDate cursor = mondayOf(from);
            while (!cursor.isAfter(to)) {
                String key = uid + ":" + cursor;
                UserWeekRow existing = indexed.get(key);
                if (existing != null) {
                    result.add(new UserWeekRow(uid, uname, fname, did, dname,
                            existing.weekStart(), existing.weekEnd(),
                            existing.status(), existing.totalHours(),
                            existing.projectCount(), existing.entryCount(),
                            existing.milestoneCount(), existing.upcomingCount()));
                } else {
                    result.add(new UserWeekRow(uid, uname, fname, did, dname,
                            cursor, cursor.plusDays(6), "NO_DATA",
                            BigDecimal.ZERO, 0, 0, 0, 0));
                }
                cursor = cursor.plusWeeks(1);
            }
        }

        int weekCount = (int) (java.time.temporal.ChronoUnit.WEEKS.between(mondayOf(from), to) + 1);
        return new UserLoadMatrix(result, from, to, weekCount);
    }

    // ---------------------------------------------------------------
    // 2) 单项目工时汇总
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public ProjectLoad projectLoad(Long projectId, LocalDate from, LocalDate to) {
        // 0) 校验项目存在
        Object[] projRow = (Object[]) em.createNativeQuery(
                "SELECT id, name FROM project WHERE id = :pid AND deleted = FALSE")
                .setParameter("pid", projectId)
                .getResultList()
                .stream().findFirst().orElse(null);
        if (projRow == null) {
            throw new com.company.zhiyu.common.exception.BusinessException(404, "项目不存在: " + projectId);
        }
        String projName = (String) projRow[1];

        // 1) byMember
        @SuppressWarnings("unchecked")
        List<Object[]> byM = em.createNativeQuery(
                "SELECT u.id, u.username, u.full_name, " +
                        "       COALESCE(SUM(te.hours), 0), " +
                        "       COUNT(DISTINCT te.work_date) " +
                        "FROM timesheet_entry te " +
                        "JOIN timesheet_week ts  ON ts.id = te.timesheet_id " +
                        "JOIN app_user u         ON u.id = ts.user_id " +
                        "WHERE te.project_id = :pid AND te.work_date BETWEEN :from AND :to " +
                        "  AND te.deleted = FALSE AND ts.deleted = FALSE AND u.deleted = FALSE " +
                        "GROUP BY u.id, u.username, u.full_name " +
                        "ORDER BY SUM(te.hours) DESC")
                .setParameter("pid", projectId)
                .setParameter("from", Date.valueOf(from))
                .setParameter("to", Date.valueOf(to))
                .getResultList();

        List<ProjectMemberHours> memberList = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int dayCount = 0;
        for (Object[] r : byM) {
            Long uid   = ((Number) r[0]).longValue();
            String uname = (String) r[1];
            String fname = (String) r[2];
            BigDecimal hrs = new BigDecimal(r[3].toString());
            int dc = ((Number) r[4]).intValue();
            total = total.add(hrs);
            dayCount = Math.max(dayCount, dc);
            memberList.add(new ProjectMemberHours(uid, uname, fname, hrs, dc));
        }

        // 2) byDay
        @SuppressWarnings("unchecked")
        List<Object[]> byD = em.createNativeQuery(
                "SELECT te.work_date, COALESCE(SUM(te.hours), 0), COUNT(DISTINCT ts.user_id) " +
                        "FROM timesheet_entry te " +
                        "JOIN timesheet_week ts ON ts.id = te.timesheet_id " +
                        "WHERE te.project_id = :pid AND te.work_date BETWEEN :from AND :to " +
                        "  AND te.deleted = FALSE AND ts.deleted = FALSE " +
                        "GROUP BY te.work_date ORDER BY te.work_date")
                .setParameter("pid", projectId)
                .setParameter("from", Date.valueOf(from))
                .setParameter("to", Date.valueOf(to))
                .getResultList();

        List<ProjectDayHours> dayList = new ArrayList<>();
        for (Object[] r : byD) {
            LocalDate d = ((Date) r[0]).toLocalDate();
            BigDecimal hrs = new BigDecimal(r[1].toString());
            int mc = ((Number) r[2]).intValue();
            dayList.add(new ProjectDayHours(d, hrs, mc));
        }

        return new ProjectLoad(projectId, projName, total, memberList.size(), dayList.size(),
                memberList, dayList);
    }

    // ---------------------------------------------------------------


    /**
     * P2.5: 单人某周里程碑列表(单元格点击下钻)
     * - 走 ts→te→p→m 全链
     * - 限定 planDate 在 [weekStart, weekEnd] 内
     */
    @Transactional(readOnly = true)
    public UserMilestoneList userMilestones(Long userId, LocalDate weekStart, LocalDate from, LocalDate to) {
        // 0) 取用户信息
        Object[] uRow = (Object[]) em.createNativeQuery(
                "SELECT id, full_name FROM app_user WHERE id = :uid AND deleted = FALSE")
                .setParameter("uid", userId)
                .getResultList().stream().findFirst().orElse(null);
        if (uRow == null) {
            throw new com.company.zhiyu.common.exception.BusinessException(404, "用户不存在: " + userId);
        }
        String fullName = (String) uRow[1];
        LocalDate weekEnd = weekStart.plusDays(6);

        // 1) 拉里程碑
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT m.id, m.name, m.project_id, p.code, p.name, " +
                "       ph.id, ph.name, ms.code, ms.name, " +
                "       m.plan_date, m.actual_date, m.weight " +
                "FROM timesheet_entry te " +
                "JOIN timesheet_week ts ON ts.id = te.timesheet_id AND ts.deleted = FALSE " +
                "JOIN app_user u        ON u.id = ts.user_id AND u.deleted = FALSE " +
                "JOIN project p         ON p.id = te.project_id AND p.deleted = FALSE " +
                "JOIN milestone m       ON m.project_id = p.id " +
                "                          AND m.deleted = FALSE " +
                "                          AND m.plan_date >= :ws " +
                "                          AND m.plan_date <= :we " +
                "LEFT JOIN milestone_phase ph ON ph.id = m.phase_id " +
                "JOIN milestone_status ms     ON ms.id = m.status_id " +
                "WHERE te.deleted = FALSE " +
                "  AND ts.user_id = :uid " +
                "GROUP BY m.id, m.name, m.project_id, p.code, p.name, " +
                "         ph.id, ph.name, ms.code, ms.name, " +
                "         m.plan_date, m.actual_date, m.weight " +
                "ORDER BY m.plan_date ASC, m.id ASC"
        )
                .setParameter("uid", userId)
                .setParameter("ws", Date.valueOf(weekStart))
                .setParameter("we", Date.valueOf(weekEnd))
                .getResultList();

        List<UserMilestoneRow> items = new ArrayList<>();
        for (Object[] r : rows) {
            Long mid   = ((Number) r[0]).longValue();
            String mn  = (String) r[1];
            Long pid   = ((Number) r[2]).longValue();
            String pc  = (String) r[3];
            String pnm = (String) r[4];
            Long phId  = r[5] == null ? null : ((Number) r[5]).longValue();
            String phn = (String) r[6];
            String stc = (String) r[7];
            String stn = (String) r[8];
            LocalDate pd = ((Date) r[9]).toLocalDate();
            LocalDate ad = r[10] == null ? null : ((Date) r[10]).toLocalDate();
            int w = ((Number) r[11]).intValue();
            items.add(new UserMilestoneRow(mid, mn, pid, pc, pnm,
                    phId, phn, stc, stn, pd, ad, w));
        }

        return new UserMilestoneList(userId, fullName,
                weekStart, weekEnd, from, to, items.size(), items);
    }

    /** 取 d 所在周的周一 */
    public static LocalDate mondayOf(LocalDate d) {
        return d.minusDays(d.getDayOfWeek().getValue() - java.time.DayOfWeek.MONDAY.getValue());
    }
}

