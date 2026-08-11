package com.hex.projectgovern.module.reporting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {
    Optional<Dataset> findByCode(String code);
    List<Dataset> findByDomain(String domain);
    List<Dataset> findByStatus(String status);
    @Query("SELECT d FROM Dataset d WHERE d.domain = :domain AND d.status = 'ACTIVE'")
    List<Dataset> findActiveByDomain(@Param("domain") String domain);
}
