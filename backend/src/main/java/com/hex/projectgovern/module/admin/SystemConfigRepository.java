package com.hex.projectgovern.module.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    Optional<SystemConfig> findByConfigKey(String configKey);

    boolean existsByConfigKey(String configKey);

    @Query("SELECT s FROM SystemConfig s ORDER BY s.configGroup, s.sortOrder, s.configKey")
    List<SystemConfig> findAllOrderByGroupAndSort();

    @Query("SELECT s FROM SystemConfig s WHERE s.configGroup = :grp ORDER BY s.sortOrder, s.configKey")
    List<SystemConfig> findByGroup(@Param("grp") String grp);
}
