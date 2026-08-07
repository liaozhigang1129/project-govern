package com.company.pmo.module.wbs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WbsAssignmentRepository extends JpaRepository<WbsAssignment, Long> {

    List<WbsAssignment> findByWbsTaskIdAndDeletedFalse(Long wbsTaskId);

    Optional<WbsAssignment> findByWbsTaskIdAndUserIdAndDeletedFalse(Long wbsTaskId, Long userId);

    /** 拉一个用户在多个项目下的所有分配 — 资源模块查"我下个月多少投入"用 */
    @Query("""
        SELECT a FROM WbsAssignment a
        WHERE a.userId = :userId AND a.deleted = false
        ORDER BY a.startDate ASC
    """)
    List<WbsAssignment> findByUserId(@Param("userId") Long userId);

    /** 项目级资源清点(给仪表盘/分配页用) */
    @Query("""
        SELECT a FROM WbsAssignment a
        JOIN WbsTask t ON t.id = a.wbsTaskId
        WHERE t.projectId = :projectId AND a.deleted = false
    """)
    List<WbsAssignment> findByProjectId(@Param("projectId") Long projectId);

    /**
     * V4.34: 找用户某日命中的 WBS 分配 (assignment.startDate <= day <= assignment.endDate)
     *  用于自动填报名"按 WBS 任务定位 project + milestone"
     *  返回可能多行 — 取 priority first
     */
    @Query("""
        SELECT a FROM WbsAssignment a
        WHERE a.userId = :userId
          AND a.deleted = false
          AND (a.startDate IS NULL OR a.startDate <= :day)
          AND (a.endDate   IS NULL OR a.endDate   >= :day)
        ORDER BY a.id ASC
        """)
    List<WbsAssignment> findActiveByUserAndDay(@Param("userId") Long userId,
                                               @Param("day") java.time.LocalDate day);
}
