package com.company.zhiyu.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * operation_log 表的 Repository。
 *
 * <p>支持多条件分页查询;主键查询;最新 N 条查询。
 * 表字段:{@link OperationLog}。
 *
 * @since 2026-Q1 P1.5-d
 */
public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    /**
     * 多条件分页查询。
     *
     * @param resourceType 资源类型(可选)
     * @param userId       操作用户 ID(可选)
     * @param action       操作动作(可选)
     * @param start        起始时间(包含)
     * @param end          结束时间(不包含)
     * @param pageable     分页参数
     */
    @Query("""
            SELECT o FROM OperationLog o
            WHERE (:resourceType IS NULL OR o.resourceType = :resourceType)
              AND (:userId IS NULL OR o.userId = :userId)
              AND (:action IS NULL OR o.action = :action)
              AND (:start IS NULL OR o.createdAt >= :start)
              AND (:end IS NULL OR o.createdAt < :end)
            ORDER BY o.createdAt DESC
            """)
    Page<OperationLog> search(@Param("resourceType") String resourceType,
                              @Param("userId") Long userId,
                              @Param("action") String action,
                              @Param("start") Instant start,
                              @Param("end") Instant end,
                              Pageable pageable);

    /**
     * 查找某用户最新的 N 条记录(用于用户画像等场景)。
     */
    List<OperationLog> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 统计某资源类型在某时间段内的次数。
     */
    long countByResourceTypeAndCreatedAtBetween(String resourceType, Instant start, Instant end);

    /** 通过 ID 查找(覆盖默认,显式声明便于阅读) */
    @Override
    Optional<OperationLog> findById(Long id);
}
