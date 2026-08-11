package com.hex.projectgovern.module.report;

import com.hex.projectgovern.common.ratelimit.RateLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 报表导出基础测试 (P2 #29).
 */
class ReportExportControllerTest {

    @Test
    @DisplayName("项目月报 PDF 端点: 路径 + @RateLimit(100/min)")
    void projectMonthlyPdf() throws NoSuchMethodException {
        Method m = ReportExportController.class.getMethod("projectMonthlyPdf", Long.class, java.time.LocalDate.class, jakarta.servlet.http.HttpServletResponse.class);
        RateLimit rl = m.getAnnotation(RateLimit.class);
        assertThat(rl).isNotNull();
        assertThat(rl.permitsPerMinute()).isEqualTo(100);
    }

    @Test
    @DisplayName("财务对账 Excel 端点: 路径 + @RateLimit(100/min)")
    void financeReconciliationExcel() throws NoSuchMethodException {
        Method m = ReportExportController.class.getMethod("financeReconciliationExcel", String.class, jakarta.servlet.http.HttpServletResponse.class);
        RateLimit rl = m.getAnnotation(RateLimit.class);
        assertThat(rl).isNotNull();
        assertThat(rl.permitsPerMinute()).isEqualTo(100);
    }

    @Test
    @DisplayName("风险摘要 PDF 端点: 路径 + @RateLimit(100/min)")
    void riskSummaryPdf() throws NoSuchMethodException {
        Method m = ReportExportController.class.getMethod("riskSummaryPdf", String.class, jakarta.servlet.http.HttpServletResponse.class);
        RateLimit rl = m.getAnnotation(RateLimit.class);
        assertThat(rl).isNotNull();
        assertThat(rl.permitsPerMinute()).isEqualTo(100);
    }
}
