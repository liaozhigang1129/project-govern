package com.hex.projectgovern.module.opportunityfunnel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface OpportunityRepository extends JpaRepository<Opportunity, Long> {

    long countByStatusAndDeletedFalse(String status);
    long countByStatus(String status);  // legacy alias

    long countByStageAndDeletedFalse(String stage);
    long countByStage(String stage);    // legacy alias

    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM opportunity " +
                   "WHERE status = :s AND deleted = false", nativeQuery = true)
    BigDecimal sumAmountByStatus(@Param("s") String status);

    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM opportunity " +
                   "WHERE stage = :s AND deleted = false", nativeQuery = true)
    BigDecimal sumAmountByStage(@Param("s") String stage);

    @Query(value = "SELECT COALESCE(SUM(amount * probability), 0) FROM opportunity " +
                   "WHERE status = 'OPEN' AND deleted = false", nativeQuery = true)
    BigDecimal sumWeightedAmount();

    @Query(value = "SELECT COUNT(DISTINCT opportunity_id) FROM opportunity_stage_history " +
                   "WHERE to_stage = :s", nativeQuery = true)
    long countByStageEver(@Param("s") String stage);

    /**
     * PG: DATE_FORMAT(d, '%Y-%m') → to_char(d, 'YYYY-MM')
     */
    @Query(value = "SELECT COUNT(*) FROM opportunity WHERE deleted = false", nativeQuery = true)
    long countAll();

    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM opportunity " +
                   "WHERE stage = 'WON' AND deleted = false", nativeQuery = true)
    BigDecimal sumAmountWon();

    @Query(value = """
        SELECT COUNT(DISTINCT bu_id) FROM opportunity WHERE deleted = false AND bu_id IS NOT NULL
    """, nativeQuery = true)
    long countDistinctBu();

    @Query(value = """
        SELECT to_char(actual_close, 'YYYY-MM') AS month,
               COUNT(*), COALESCE(SUM(amount), 0)
        FROM opportunity
        WHERE stage = 'WON' AND actual_close IS NOT NULL AND deleted = false
        GROUP BY to_char(actual_close, 'YYYY-MM')
        ORDER BY month
    """, nativeQuery = true)
    List<Object[]> aggregateMonthlyWon();

    @Query(value = """
        SELECT o.owner_user_id, u.full_name,
               SUM(CASE WHEN o.status = 'OPEN' THEN 1 ELSE 0 END),
               SUM(CASE WHEN o.stage  = 'WON'  THEN 1 ELSE 0 END),
               COALESCE(SUM(CASE WHEN o.stage = 'WON' THEN o.amount ELSE 0 END), 0)
        FROM opportunity o
        JOIN app_user u ON u.id = o.owner_user_id
        WHERE o.deleted = false AND u.deleted = false
        GROUP BY o.owner_user_id, u.full_name
        ORDER BY 5 DESC
        LIMIT 20
    """, nativeQuery = true)
    List<Object[]> aggregateByOwner();

    @Query(value = """
        SELECT o.bu_id, b.name, o.pl_id, p.name,
               COALESCE(SUM(CASE WHEN o.status = 'OPEN' THEN o.amount ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN o.stage  = 'WON'  THEN o.amount ELSE 0 END), 0),
               SUM(CASE WHEN o.stage = 'WON' THEN 1 ELSE 0 END)
        FROM opportunity o
        LEFT JOIN business_unit b ON b.id = o.bu_id
        LEFT JOIN product_line  p ON p.id = o.pl_id
        WHERE o.deleted = false
        GROUP BY o.bu_id, b.name, o.pl_id, p.name
        ORDER BY 5 DESC
    """, nativeQuery = true)
    List<Object[]> aggregateByBuPl();
}