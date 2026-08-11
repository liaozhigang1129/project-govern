package com.hex.projectgovern.module.reporting;

import com.hex.projectgovern.common.exception.BusinessException;
import com.hex.projectgovern.module.reporting.dto.ReportingDtos.*;
import com.hex.projectgovern.module.reporting.exporter.CsvExporter;
import com.hex.projectgovern.module.reporting.exporter.PngExporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 报表模块冒烟测试 (WP-M7-03 / P2 #32).
 */
class ReportingSmokeTest {

    @Test
    @DisplayName("DatasetService.create: 重名 code 抛错")
    void dataset_create_duplicate() {
        DatasetRepository repo = mock(DatasetRepository.class);
        when(repo.findByCode("dup")).thenReturn(java.util.Optional.of(new Dataset()));
        DatasetService svc = new DatasetService(repo, null);
        var req = new DatasetRequest("dup", "n", "d", null, null, null, null);
        assertThatThrownBy(() -> svc.create(req, 1L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Dataset code exists");
    }

    @Test
    @DisplayName("ReportTemplateService.create: dataset 不存在抛错")
    void report_create_datasetNotFound() {
        ReportTemplateRepository repo = mock(ReportTemplateRepository.class);
        DatasetRepository dsRepo = mock(DatasetRepository.class);
        when(repo.findByCode("r1")).thenReturn(java.util.Optional.empty());
        when(dsRepo.existsById(99L)).thenReturn(false);
        ReportTemplateService svc = new ReportTemplateService(repo, dsRepo);
        var req = new ReportTemplateRequest("r1", "PROJECT", "name", 99L, "PDF", null, null, null, null);
        assertThatThrownBy(() -> svc.create(req, 1L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Dataset not found");
    }

    @Test
    @DisplayName("ReportSubscriptionService.create: 默认立即触发 (nextRunAt=now)")
    void sub_create_defaultImmediate() {
        ReportSubscriptionRepository repo = mock(ReportSubscriptionRepository.class);
        when(repo.findByCode("s1")).thenReturn(java.util.Optional.empty());
        when(repo.save(any(ReportSubscription.class))).thenAnswer(inv -> inv.getArgument(0));
        ReportTemplateRepository templateRepo = mock(ReportTemplateRepository.class);
        when(templateRepo.existsById(1L)).thenReturn(true);
        ReportSubscriptionService svc = new ReportSubscriptionService(repo, templateRepo);
        var req = new SubscriptionRequest("s1", 1L, 1L, null, "email", "0 0 9 * * ?", null, null);
        var s = svc.create(req);
        assertThat(s.getStatus()).isEqualTo("ACTIVE");
        assertThat(s.getNextRunAt()).isNotNull();
    }

    @Test
    @DisplayName("CsvExporter.export: 写出 BOM + header + row")
    void csv_exporter_basic() throws Exception {
        CsvExporter ex = new CsvExporter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ex.export(out, List.of("a", "b"), List.of(List.of("1", "2"), List.of("3", "4")));
        String s = out.toString("UTF-8");
        assertThat(s).startsWith("\uFEFF");
        assertThat(s).contains("a,b\r\n");
        assertThat(s).contains("1,2\r\n");
        assertThat(s).contains("3,4\r\n");
    }

    @Test
    @DisplayName("CsvExporter.export: 包含逗号的字段加引号")
    void csv_exporter_escape() throws Exception {
        CsvExporter ex = new CsvExporter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ex.export(out, List.of("name"), List.of(List.of("foo,bar")));
        String s = out.toString("UTF-8");
        assertThat(s).contains("\"foo,bar\"");
    }

    @Test
    @DisplayName("PngExporter.export: 写出占位 PNG (MVP)")
    void png_exporter_placeholder() throws Exception {
        PngExporter ex = new PngExporter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ex.export(out, List.of("a"), List.of(Map.of("a", 1)));
        byte[] bytes = out.toByteArray();
        assertThat(bytes.length).isGreaterThan(50);
        // PNG magic: 89 50 4E 47
        assertThat(bytes[0]).isEqualTo((byte) 0x89);
        assertThat(bytes[1]).isEqualTo((byte) 0x50);
    }
}
