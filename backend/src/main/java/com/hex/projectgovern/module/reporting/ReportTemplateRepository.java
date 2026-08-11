package com.hex.projectgovern.module.reporting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, Long> {
    Optional<ReportTemplate> findByCode(String code);
    List<ReportTemplate> findByCategory(String category);
    List<ReportTemplate> findByStatus(String status);
}
