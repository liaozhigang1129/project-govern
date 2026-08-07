package com.company.pmo.module.org;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByCode(String code);
    boolean existsByCode(String code);
    List<Role> findAllByEnabledTrueOrderBySortOrderAscIdAsc();
    List<Role> findAllByOrderBySortOrderAscIdAsc();

    /** V4.12: 批量启停 (单 SQL, 但有内置角色护栏) */
    @Modifying
    @Query("UPDATE Role r SET r.enabled = :enabled WHERE r.id IN :ids AND r.builtIn = false")
    int bulkSetEnabled(@Param("ids") List<Long> ids, @Param("enabled") boolean enabled);
}
