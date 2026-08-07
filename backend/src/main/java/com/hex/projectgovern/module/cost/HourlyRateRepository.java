package com.hex.projectgovern.module.cost;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HourlyRateRepository extends JpaRepository<HourlyRate, Long> {

    /**
     * 取某用户在该月生效的"个人 override"行 (user_id = :uid)
     * <p>规则 B: 调档仅当月生效 (effectiveMonth = :month)。下月自动 fallback 到 ROLE_COST_DEFAULT。
     * 这样可防止"调一次档影响所有未来月份"的危险语义。\n     * 如需覆盖多个月,财务必须显式建多行。\n     */
    @Query("""
        SELECT h FROM HourlyRate h
        WHERE h.user.id = :userId
          AND h.effectiveMonth = :month
        ORDER BY h.effectiveMonth DESC
        """)
    List<HourlyRate> findActiveUserRate(@Param("userId") Long userId,
                                        @Param("month") LocalDate month);

    /**
     * 取某角色在该月生效的"全局角色档"行 (user_id IS NULL)
     * <p>规则 B: 同上,仅当月生效。\n     */
    @Query("""
        SELECT h FROM HourlyRate h
        WHERE h.roleCode = :roleCode
          AND h.user IS NULL
          AND h.effectiveMonth = :month
        ORDER BY h.effectiveMonth DESC
        """)
    List<HourlyRate> findActiveRoleRate(@Param("roleCode") String roleCode,
                                        @Param("month") LocalDate month);

    /**
     * 冲突检测: 给定 (userId, roleCode) 在区间 [from, to] 内是否已有其他生效行
     * - excludeId: 编辑时排除自身
     * - userId=null 时仅检 user_id IS NULL 的角色档
     */
    @Query("""
        SELECT h FROM HourlyRate h
        WHERE (:userId IS NULL AND h.user IS NULL OR :userId IS NOT NULL AND h.user.id = :userId)
          AND h.roleCode = :roleCode
          AND (:excludeId IS NULL OR h.id <> :excludeId)
          AND h.effectiveMonth <= :to
          AND (h.endMonth IS NULL OR h.endMonth >= :from)
        """)
    List<HourlyRate> findOverlap(@Param("userId") Long userId,
                                 @Param("roleCode") String roleCode,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to,
                                 @Param("excludeId") Long excludeId);

    /**
     * 管理后台表格: 全部按 effectiveMonth 倒序
     */
    List<HourlyRate> findAllByOrderByEffectiveMonthDescIdDesc();

    /**
     * 按 user 列表过滤 (管理后台 / 单人历史)
     */
    List<HourlyRate> findAllByUser_IdOrderByEffectiveMonthDesc(Long userId);

    /** 单一主键 (带 user 关联加载) */
    @Query("SELECT h FROM HourlyRate h LEFT JOIN FETCH h.user WHERE h.id = :id")
    Optional<HourlyRate> findByIdWithUser(@Param("id") Long id);
}