package com.hex.projectgovern.module.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    @Query("SELECT m FROM ProjectMember m WHERE m.projectId = :pid AND m.deleted = false ORDER BY m.role.sortOrder ASC, m.id ASC")
    List<ProjectMember> findActiveByProject(Long pid);

    Optional<ProjectMember> findByIdAndDeletedFalse(Long id);

    /**
     * V4.34: 找用户在某日仍"在职"的所有项目成员关系
     *  - join_date <= day AND (leave_date IS NULL OR leave_date >= day)
     *  - 自动填报名核心: 圈定候选项目
     */
    @Query("""
        SELECT m FROM ProjectMember m
        WHERE m.userId = :userId
          AND m.deleted = false
          AND m.joinDate <= :day
          AND (m.leaveDate IS NULL OR m.leaveDate >= :day)
        ORDER BY m.id ASC
        """)
    List<ProjectMember> findActiveByUserAndDay(@org.springframework.data.repository.query.Param("userId") Long userId,
                                              @org.springframework.data.repository.query.Param("day") java.time.LocalDate day);

    /** V4.34: 找用户在某区间所有"在职"过的项目成员关系 (按 day 范围) */
    @Query("""
        SELECT m FROM ProjectMember m
        WHERE m.userId = :userId
          AND m.deleted = false
          AND m.joinDate <= :to
          AND (m.leaveDate IS NULL OR m.leaveDate >= :from)
        ORDER BY m.id ASC
        """)
    List<ProjectMember> findActiveByUserAndRange(@org.springframework.data.repository.query.Param("userId") Long userId,
                                                 @org.springframework.data.repository.query.Param("from") java.time.LocalDate from,
                                                 @org.springframework.data.repository.query.Param("to") java.time.LocalDate to);
}
