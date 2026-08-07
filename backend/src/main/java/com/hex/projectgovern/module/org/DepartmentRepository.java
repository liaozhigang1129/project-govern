package com.hex.projectgovern.module.org;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findAllByDeletedFalseOrderBySortOrderAscIdAsc();

    List<Department> findAllByDeletedFalseOrderByParentIdAscSortOrderAscIdAsc();

    /** V4.12: 包含已停用, 用于全树 */
    List<Department> findAllByOrderByParentIdAscSortOrderAscIdAsc();

    Optional<Department> findByIdAndDeletedFalse(Long id);

    boolean existsByCodeAndDeletedFalse(String code);

    boolean existsByIdAndDeletedFalse(Long id);

    @Query("SELECT COUNT(d) FROM Department d WHERE d.parentId = :pid AND d.deleted = false")
    long countChildren(@Param("pid") Long pid);

    @Query("SELECT COUNT(u) FROM AppUser u WHERE u.departmentId = :did AND u.deleted = false")
    long countUsers(@Param("did") Long did);

    /**
     * V4.12: 批量启停 (单 SQL)
     */
    @Modifying
    @Query("UPDATE Department d SET d.enabled = :enabled WHERE d.id IN :ids AND d.deleted = false")
    int bulkSetEnabled(@Param("ids") List<Long> ids, @Param("enabled") boolean enabled);

    /**
     * V4.12: 批量软删
     */
    @Modifying
    @Query("UPDATE Department d SET d.deleted = true, d.enabled = false WHERE d.id IN :ids AND d.deleted = false")
    int bulkSoftDelete(@Param("ids") List<Long> ids);

    // ============================================================
    //  V4.14: 层级路径查询 (V4.14 新增)
    // ============================================================
    /** 查 path 前缀 = 该部门及所有子部门 (含自身) */
    @Query("SELECT d FROM Department d WHERE d.deleted = false AND d.treePath LIKE :path% ORDER BY d.treeLevel ASC, d.sortOrder ASC")
    List<Department> findDescendants(@Param("path") String treePathPrefix);

    /** 查 path 前缀 = 该部门及所有子部门 (含自身) 的 ID 列表 */
    @Query("SELECT d.id FROM Department d WHERE d.deleted = false AND d.treePath LIKE :path%")
    List<Long> findDescendantIds(@Param("path") String treePathPrefix);
}
