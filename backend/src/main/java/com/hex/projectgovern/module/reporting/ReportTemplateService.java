package com.hex.projectgovern.module.reporting;

import com.hex.projectgovern.common.exception.BusinessException;
import com.hex.projectgovern.module.reporting.dto.ReportingDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportTemplateService {
    private final ReportTemplateRepository repo;
    private final DatasetRepository datasetRepo;

    @Transactional
    public ReportTemplate create(ReportTemplateRequest req, Long userId) {
        if (repo.findByCode(req.code()).isPresent()) {
            throw new BusinessException("Template code exists: " + req.code());
        }
        if (req.datasetId() != null && !datasetRepo.existsById(req.datasetId())) {
            throw new BusinessException("Dataset not found: " + req.datasetId());
        }
        ReportTemplate t = new ReportTemplate();
        t.setCode(req.code());
        t.setCategory(req.category());
        t.setName(req.name());
        t.setDatasetId(req.datasetId());
        if (req.format() != null) t.setFormat(req.format());
        t.setDefaultFilters(req.defaultFilters());
        t.setLayout(req.layout());
        t.setScheduleCron(req.scheduleCron());
        t.setDescription(req.description());
        t.setCreatedBy(userId);
        t.setStatus("DRAFT");
        return repo.save(t);
    }

    @Transactional(readOnly = true)
    public ReportTemplate get(Long id) {
        return repo.findById(id).orElseThrow(() -> new BusinessException("Template not found"));
    }

    @Transactional(readOnly = true)
    public List<ReportTemplate> list(String category) {
        if (category != null) return repo.findByCategory(category);
        return repo.findAll();
    }

    @Transactional
    public ReportTemplate publish(Long id) {
        ReportTemplate t = get(id);
        t.setStatus("ACTIVE");
        return t;
    }

    /** 渲染报表: MVP 返回模板元信息 (实际数据查询后续接 dataset) */
    @Transactional(readOnly = true)
    public Object render(Long templateId) {
        ReportTemplate t = get(templateId);
        return java.util.Map.of(
            "templateId", t.getId(),
            "code", t.getCode(),
            "name", t.getName(),
            "format", t.getFormat(),
            "data", java.util.List.of()  // 空数据, 后续接 DatasetService
        );
    }
}
