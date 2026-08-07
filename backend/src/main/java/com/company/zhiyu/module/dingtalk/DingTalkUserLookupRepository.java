package com.company.zhiyu.module.dingtalk;

import com.company.zhiyu.module.org.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 钉钉同步用 — 仅在 dingtalk 模块内复用, 不污染 org 模块。
 * 如果以后有更多 org 端用法, 可考虑把方法提到 UserRepository。
 */
@Repository
public class DingTalkUserLookupRepository {

    @PersistenceContext
    private EntityManager em;

    public Optional<AppUser> findByDingtalkUserId(String dingtalkUserId) {
        List<AppUser> list = em.createQuery(
                "SELECT u FROM AppUser u WHERE u.dingtalkUserId = :did AND u.deleted = false",
                AppUser.class)
                .setParameter("did", dingtalkUserId)
                .setMaxResults(1)
                .getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * V4.33 批量查: 给定一堆钉钉 userid, 一次性返回 AppUser 列表
     * 用于 runSync 聚合时一次性查完, 减少 N+1
     */
    public java.util.List<AppUser> findByDingtalkUserIdIn(java.util.Collection<String> dingtalkUserIds) {
        if (dingtalkUserIds == null || dingtalkUserIds.isEmpty())
            return java.util.Collections.emptyList();
        return em.createQuery(
                "SELECT u FROM AppUser u WHERE u.dingtalkUserId IN :dids AND u.deleted = false",
                AppUser.class)
                .setParameter("dids", dingtalkUserIds)
                .getResultList();
    }

    public Optional<AppUser> findByPhone(String phone) {
        if (phone == null || phone.isBlank()) return Optional.empty();
        List<AppUser> list = em.createQuery(
                "SELECT u FROM AppUser u WHERE u.phone = :phone AND u.deleted = false",
                AppUser.class)
                .setParameter("phone", phone)
                .setMaxResults(1)
                .getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** PMO 里有 dingtalk_user_id 但本次同步没出现 → 视为离职 */
    public List<AppUser> findDingtalkBoundActive() {
        return em.createQuery(
                "SELECT u FROM AppUser u WHERE u.dingtalkUserId IS NOT NULL AND u.deleted = false",
                AppUser.class)
                .getResultList();
    }

    /** 拉取所有 PMO 已绑定钉钉的 userid 列表(用于考勤同步确定查询范围) */
    public List<String> findAllDingtalkUserIds() {
        return em.createQuery(
                "SELECT u.dingtalkUserId FROM AppUser u WHERE u.dingtalkUserId IS NOT NULL AND u.deleted = false",
                String.class)
                .getResultList();
    }

    /**
     * 批量查 (pmoUserId, workDate) 当天填写的工时项目 (考勤聚合用)
     * 一次 JOIN 出来, 减少 N+1
     * 返回 [pmoUserId, workDate, projectId] 多行 (projectName 单独从 ProjectRepository 查)
     * 用 nativeQuery 是因为 TimesheetEntry.projectId 是裸 Long, JPQL 不能直接 join
     */
    public List<Object[]> findTimesheetProjects(java.util.Collection<Long> pmoUserIds,
                                                java.time.LocalDate from,
                                                java.time.LocalDate to) {
        if (pmoUserIds == null || pmoUserIds.isEmpty()) return java.util.Collections.emptyList();
        return em.createNativeQuery(
                "SELECT ts.user_id, te.work_date, te.project_id " +
                "FROM timesheet_entry te " +
                "JOIN timesheet_week ts ON ts.id = te.timesheet_id " +
                "WHERE ts.user_id IN (:uids) " +
                "  AND te.work_date BETWEEN :f AND :t " +
                "  AND te.deleted = 0 " +
                "  AND ts.deleted = 0 " +
                "ORDER BY ts.user_id, te.work_date, te.project_id")
                .setParameter("uids", pmoUserIds)
                .setParameter("f", from)
                .setParameter("t", to)
                .getResultList();
    }
}
