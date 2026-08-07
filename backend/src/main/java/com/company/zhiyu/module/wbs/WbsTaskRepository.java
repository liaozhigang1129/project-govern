package com.company.zhiyu.module.wbs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WbsTaskRepository extends JpaRepository<WbsTask, Long> {

    /**
     * 拉一个项目全树(平铺,由前端/Service 拼成 children 树)。
     * 排序: wbs_code 字典序, 等同于深度优先遍历。
     */
    @Query(value = """
        SELECT * FROM wbs_task
        WHERE project_id = :projectId AND deleted = false
        ORDER BY wbs_code ASC
    """, nativeQuery = true)
    List<WbsTask> findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(@Param("projectId") Long projectId);

    /**
     * 拉一个项目所有"根"任务(parentId IS NULL), 用于在创建子任务时取顶层编码参考。
     */
    @Query(value = """
        SELECT * FROM wbs_task
        WHERE project_id = :projectId AND parent_id IS NULL AND deleted = false
        ORDER BY wbs_code ASC
    """, nativeQuery = true)
    List<WbsTask> findByProjectIdAndParentIdIsNullAndDeletedFalseOrderByWbsCodeAsc(@Param("projectId") Long projectId);

    /**
     * 同项目内 wbs_code 唯一性预检(项目 id + 编码)。
     * 用 exists 而非 find, 性能更好。
     */
    @Query(value = """
        SELECT COUNT(*) FROM wbs_task
        WHERE project_id = :projectId AND wbs_code = :wbsCode AND deleted = false
    """, nativeQuery = true)
    long countByProjectIdAndWbsCodeAndDeletedFalse(@Param("projectId") Long projectId, @Param("wbsCode") String wbsCode);

    /**
     * 项目级加权进度(对齐 v_wbs_progress_summary)。
     * 注意: weight/progressPct 在 DB 是 Integer(weight 1-10, progressPct 0-100),
     * 计算时先转 double, 避免乘法溢出与精度丢失。
     * <p>修复: 原 PG cast 语法 (weight::bigint * progress_pct) 在 MySQL 报语法错,
     * 改为统一 CAST(... AS UNSIGNED) 与 CAST(... AS DECIMAL) 兼容 MySQL/PG。
     */
    @Query(value = """
        SELECT COALESCE(
            CAST(ROUND(
                100.0 * SUM(CAST(weight AS UNSIGNED) * CAST(progress_pct AS UNSIGNED)) /
                NULLIF(SUM(weight), 0)
            ) AS SIGNED), 0)
        FROM wbs_task
        WHERE project_id = :projectId AND deleted = false
    """, nativeQuery = true)
    Integer computeWeightedProgressPct(@Param("projectId") Long projectId);

    // ============================================================
    // P4-WBS 拖拽重排 (a 步) —— moveTask 用
    // ============================================================

    /**
     * 拉一个父任务下的所有子任务 (按 id 升序, 决定 wbsCode 序号)。
     * 排除软删。给 1B 算 nextWbsCode 用。
     */
    @Query(value = """
        SELECT * FROM wbs_task
        WHERE parent_id = :parentId AND deleted = false
        ORDER BY id ASC
    """, nativeQuery = true)
    List<WbsTask> findByParentIdAndDeletedFalseOrderByIdAsc(@Param("parentId") Long parentId);

    /**
     * 算子任务数 (含所有后代) — 给 1B 级联重编号 / 拖拽确认时"影响 N 个子任务"提示用。
     * 走递归 CTE (PG 8.4+/MySQL 8.0+ 支持), 一次查询搞定整棵子树。
     * <p>H2 测试库不保证支持递归 CTE, 1B 走 repository 调用两次 (先拿直接子, 再逐层) 兜底。
     */
    @Query(value = """
        WITH RECURSIVE sub AS (
            SELECT id, parent_id, wbs_code FROM wbs_task
            WHERE parent_id = :rootId AND deleted = false
            UNION ALL
            SELECT t.id, t.parent_id, t.wbs_code
            FROM wbs_task t JOIN sub s ON t.parent_id = s.id
            WHERE t.deleted = false
        )
        SELECT COUNT(*) FROM sub
    """, nativeQuery = true)
    long countDescendants(@Param("rootId") Long rootId);

    // ============================================================
    //  L1-1 用户管理: 离职交接 — 把指定 owner 的所有 WBS 任务转交
    // ============================================================
    @Modifying
    @Query(value = """
        UPDATE wbs_task
        SET owner_user_id = :newOwnerId
        WHERE owner_user_id = :oldOwnerId AND deleted = false
    """, nativeQuery = true)
    int reassignOwner(@Param("oldOwnerId") Long oldOwnerId,
                      @Param("newOwnerId") Long newOwnerId);
}
