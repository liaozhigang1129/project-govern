package com.company.zhiyu.module.org;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsernameAndDeletedFalse(String username);
    Optional<AppUser> findByIdAndDeletedFalse(Long id);
    boolean existsByUsername(String username);

    /**
     * V4.12: 批量查 id→username+fullName (N+1 杀手)
     */
    @Query("SELECT u.id, u.username, u.fullName FROM AppUser u WHERE u.id IN :ids AND u.deleted = false")
    List<Object[]> findBasicInfoByIds(@Param("ids") java.util.Collection<Long> ids);

    /** 找某部门第一任部门负责人(给通知收件人用) */
    Optional<AppUser> findFirstByDepartmentIdAndPrimaryRoleCodeAndDeletedFalse(
            Long departmentId, String primaryRoleCode);

    Optional<AppUser> findFirstByPrimaryRoleCodeAndDeletedFalse(String primaryRoleCode);

    /** P3: 取所有 roleCode 命中的、未删除、启用的用户(通知多收件人用) */
    List<AppUser> findAllByPrimaryRoleCodeInAndEnabledAndDeletedFalse(
            List<String> primaryRoleCodes, boolean enabled);

    // ============================================================
    //  L1-1 用户管理: 分页 + 规格
    //  - keyword: 模糊匹配 username/fullName/email/phone (任一命中即返)
    //  - departmentId: 精确
    //  - roleCode: 主角色 OR 兼任角色匹配其一即返 (多角色)
    //  - enabled: 默认 true; false 时取已停用
    // ============================================================
    @Query("""
        SELECT DISTINCT u FROM AppUser u
        LEFT JOIN u.primaryRole r
        WHERE u.deleted = false
          AND (:keyword IS NULL OR :keyword = ''
               OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(u.fullName)  LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR COALESCE(u.phone, '') LIKE CONCAT('%', :keyword, '%'))
          AND (:departmentId IS NULL OR u.departmentId = :departmentId)
          AND (:roleCode IS NULL OR r.code = :roleCode
               OR EXISTS (SELECT 1 FROM UserRole ur WHERE ur.userId = u.id
                           AND ur.roleId = (SELECT rr.id FROM Role rr WHERE rr.code = :roleCode)))
          AND u.enabled = :enabled
        """)
    Page<AppUser> search(@Param("keyword") String keyword,
                         @Param("departmentId") Long departmentId,
                         @Param("roleCode") String roleCode,
                         @Param("enabled") boolean enabled,
                         Pageable pageable);

    boolean existsByEmailAndDeletedFalse(String email);

    long countByPrimaryRoleCodeAndEnabledAndDeletedFalse(String code, boolean enabled);

    /** L1-1: 离职 — 把所有 backup_user_id 指向该用户的引用清空 */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
        "UPDATE AppUser u SET u.backupUserId = NULL WHERE u.backupUserId = :userId AND u.deleted = false")
    int clearBackupUserId(@org.springframework.data.repository.query.Param("userId") Long userId);

    // ============================================================
    //  V4.12: 批量操作
    // ============================================================
    /** 批量启停 (内置角色护栏在 service 层) */
    @Modifying
    @Query("UPDATE AppUser u SET u.enabled = :enabled WHERE u.id IN :ids AND u.deleted = false")
    int bulkSetEnabled(@Param("ids") java.util.List<Long> ids, @Param("enabled") boolean enabled);

    /** 批量调整部门 */
    @Modifying
    @Query("UPDATE AppUser u SET u.departmentId = :deptId WHERE u.id IN :ids AND u.deleted = false")
    int bulkSetDepartment(@Param("ids") java.util.List<Long> ids, @Param("deptId") Long deptId);

    /** 批量解锁 */
    @Modifying
    @Query("UPDATE AppUser u SET u.lockedUntil = NULL, u.loginFailCount = 0 WHERE u.id IN :ids AND u.deleted = false")
    int bulkUnlock(@Param("ids") java.util.List<Long> ids);

    /** V4.14: 按部门 ID 列表查用户 (用于"按组织"展示) */
    @Query("SELECT u FROM AppUser u WHERE u.departmentId IN :deptIds AND u.deleted = false")
    Page<AppUser> findByDepartmentIdInAndDeletedFalse(@Param("deptIds") java.util.List<Long> deptIds, Pageable pageable);

    /** V4.14: 未分配部门的用户 (departmentId IS NULL AND deleted = false) */
    @Query("SELECT u FROM AppUser u WHERE u.departmentId IS NULL AND u.deleted = false")
    Page<AppUser> findByDepartmentIdIsNullAndDeletedFalse(Pageable pageable);

    /** 批量软删 */

    }

