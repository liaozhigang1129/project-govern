package com.hex.projectgovern.module.reporting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DatasetFieldRepository extends JpaRepository<DatasetField, Long> {
    List<DatasetField> findByDatasetIdOrderBySortOrder(Long datasetId);
}
