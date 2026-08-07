package com.company.pmo.module.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RoleMenuRepository extends JpaRepository<RoleMenu, RoleMenu.PK> {

    List<RoleMenu> findAllByRoleId(Long roleId);

    List<RoleMenu> findAllByMenuId(Long menuId);

    /** 给前端查"某角色有哪些菜单 code"用 (用于登录后的前端过滤) */
    @Query("""
        SELECT m.code FROM RoleMenu rm
          JOIN SysMenu m ON m.id = rm.menuId
         WHERE rm.roleId = :roleId AND m.enabled = true
        """)
    List<String> findEnabledMenuCodesByRoleId(@Param("roleId") Long roleId);

    /** 给前端查"多角色合集"用 (用户可能有多个角色, 取并集) */
    @Query("""
        SELECT DISTINCT m.code FROM RoleMenu rm
          JOIN SysMenu m ON m.id = rm.menuId
         WHERE rm.roleId IN :roleIds AND m.enabled = true
        """)
    List<String> findEnabledMenuCodesByRoleIds(@Param("roleIds") List<Long> roleIds);

    @Modifying
    @Query("DELETE FROM RoleMenu rm WHERE rm.roleId = :roleId")
    void deleteAllByRoleId(@Param("roleId") Long roleId);
}