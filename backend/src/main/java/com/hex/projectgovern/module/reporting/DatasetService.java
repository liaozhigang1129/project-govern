package com.hex.projectgovern.module.reporting;

import com.hex.projectgovern.common.exception.BusinessException;
import com.hex.projectgovern.module.reporting.dto.ReportingDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DatasetService {
    private final DatasetRepository datasetRepo;
    private final DatasetFieldRepository fieldRepo;

    @Transactional
    public Dataset create(DatasetRequest req, Long userId) {
        if (datasetRepo.findByCode(req.code()).isPresent()) {
            throw new BusinessException("Dataset code exists: " + req.code());
        }
        Dataset d = new Dataset();
        d.setCode(req.code());
        d.setName(req.name());
        d.setDomain(req.domain());
        d.setSourceTable(req.sourceTable());
        d.setSqlTemplate(req.sqlTemplate());
        if (req.refreshPolicy() != null) d.setRefreshPolicy(req.refreshPolicy());
        d.setDescription(req.description());
        d.setCreatedBy(userId);
        d.setStatus("DRAFT");
        return datasetRepo.save(d);
    }

    @Transactional
    public Dataset update(Long id, DatasetRequest req) {
        Dataset d = datasetRepo.findById(id).orElseThrow(() -> new BusinessException("Dataset not found"));
        d.setName(req.name());
        d.setDomain(req.domain());
        d.setSourceTable(req.sourceTable());
        d.setSqlTemplate(req.sqlTemplate());
        if (req.refreshPolicy() != null) d.setRefreshPolicy(req.refreshPolicy());
        d.setDescription(req.description());
        return d;
    }

    @Transactional(readOnly = true)
    public Dataset get(Long id) {
        return datasetRepo.findById(id).orElseThrow(() -> new BusinessException("Dataset not found"));
    }

    @Transactional(readOnly = true)
    public List<Dataset> list(String domain, String status) {
        if (domain != null) return datasetRepo.findByDomain(domain);
        if (status != null) return datasetRepo.findByStatus(status);
        return datasetRepo.findAll();
    }

    @Transactional(readOnly = true)
    public List<DatasetField> listFields(Long datasetId) {
        return fieldRepo.findByDatasetIdOrderBySortOrder(datasetId);
    }

    @Transactional
    public DatasetField addField(DatasetFieldRequest req) {
        DatasetField f = new DatasetField();
        f.setDatasetId(req.datasetId());
        f.setFieldName(req.fieldName());
        f.setDisplayName(req.displayName());
        f.setFieldType(req.fieldType());
        f.setDataType(req.dataType());
        f.setAggFunc(req.aggFunc());
        f.setFormula(req.formula());
        f.setDimRole(req.dimRole());
        f.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        return fieldRepo.save(f);
    }

    @Transactional(readOnly = true)
    public String testSql(Long id) {
        Dataset d = get(id);
        return "OK: " + (d.getSqlTemplate() == null ? "(no sql template)" : d.getSqlTemplate().substring(0, Math.min(80, d.getSqlTemplate().length())));
    }
}
