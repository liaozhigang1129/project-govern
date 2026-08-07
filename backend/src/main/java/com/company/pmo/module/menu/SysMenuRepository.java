package com.company.pmo.module.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SysMenuRepository extends JpaRepository<SysMenu, Long> {

    Optional<SysMenu> findByCode(String code);
    boolean existsByCode(String code);

    List<SysMenu> findAllByOrderBySortOrderAscIdAsc();
    List<SysMenu> findAllByEnabledTrueOrderBySortOrderAscIdAsc();
    long countByParentId(Long parentId);

    /** V4.12: 批量启停 (单 SQL, 跳过内置菜单) */
    @Modifying
    @Query("UPDATE SysMenu m SET m.enabled = :enabled WHERE m.id IN :ids AND m.builtin = false")
    int bulkSetEnabled(@Param("ids") List<Long> ids, @Param("enabled") boolean enabled);
}