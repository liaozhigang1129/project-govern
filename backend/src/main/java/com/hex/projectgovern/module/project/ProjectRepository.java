package com.hex.projectgovern.module.project;

import com.hex.projectgovern.module.dashboard.dto.ProjectCardDto;
import com.hex.projectgovern.module.project.dto.ProjectQuery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {
    Optional<Project> findByIdAndDeletedFalse(Long id);
    boolean existsByCodeAndDeletedFalse(String code);

    @Query("SELECT p FROM Project p WHERE p.deleted = false ORDER BY p.updatedAt DESC")
    List<Project> findAllActive();

    /** L1-1: 离职交接 — 把指定 PM 的所有项目转交给新 PM */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
        "UPDATE Project p SET p.pmUserId = :newPmId " +
        "WHERE p.pmUserId = :oldPmId AND p.deleted = false")
    int reassignPm(@org.springframework.data.repository.query.Param("oldPmId") Long oldPmId,
                   @org.springframework.data.repository.query.Param("newPmId") Long newPmId);

    /** 仅 ACTIVE 状态、未软删的项目(给健康度跑批用) */
    @Query("""
        SELECT p FROM Project p JOIN FETCH p.status
        WHERE p.deleted = false
          AND p.status.code = 'ACTIVE'
        ORDER BY p.id ASC
        """)
    List<Project> findAllActiveProjects();

    /**
     * 多条件查询 — JPA Specification 动态拼条件
     *  - 字段均为可选,null 视为不过滤
     *  - 项目经理走精确匹配
     *  - BU / PL 走精确匹配
     *  - 计划开始日 走范围
     *  - 关键字 走 name/code 的 LIKE
     */
    static Specification<Project> specOf(ProjectQuery q) {
        return (root, cq, cb) -> {
            cq = cq == null ? cb.createQuery() : cq;
            var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.equal(root.get("deleted"), false));
            if (q.getBuId() != null)        preds.add(cb.equal(root.get("buId"), q.getBuId()));
            if (q.getPlId() != null)        preds.add(cb.equal(root.get("plId"), q.getPlId()));
            if (q.getPmUserId() != null)    preds.add(cb.equal(root.get("pmUserId"), q.getPmUserId()));
            if (q.getPlanStartFrom() != null) preds.add(cb.greaterThanOrEqualTo(root.get("planStartDate"), q.getPlanStartFrom()));
            if (q.getPlanStartTo() != null)   preds.add(cb.lessThanOrEqualTo(root.get("planStartDate"), q.getPlanStartTo()));
            if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
                String like = "%" + q.getKeyword().trim() + "%";
                preds.add(cb.or(
                        cb.like(root.get("name"), like),
                        cb.like(root.get("code"), like)
                ));
            }
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /** P1-里程碑分析: 拉某部门集合下的所有项目 (BU 展开后批量查) */
    @org.springframework.data.jpa.repository.Query(
        "SELECT p FROM Project p WHERE p.deleted = false AND p.departmentId IN :deptIds")
    List<Project> findActiveByDepartmentIds(@org.springframework.data.repository.query.Param("deptIds") java.util.Collection<Long> deptIds);

    /** P1-里程碑分析: 拉某 PM 名下的项目 (scope=pl) */
    List<Project> findByPmUserIdAndDeletedFalse(Long pmUserId);

    /**
     * V4.34: 按 id 集合批量查项目 (自动填报名避免 N+1)
     */
    @Query("SELECT p FROM Project p WHERE p.id IN :ids AND p.deleted = false")
    java.util.List<Project> findByIdInAndDeletedFalse(@Param("ids") java.util.Collection<Long> ids);
}
