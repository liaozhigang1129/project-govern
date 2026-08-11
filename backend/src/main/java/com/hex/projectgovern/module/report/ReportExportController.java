package com.hex.projectgovern.module.report;

import com.hex.projectgovern.common.ratelimit.RateLimit;
import com.hex.projectgovern.module.finance.ReconciliationService;
import com.hex.projectgovern.module.project.ProjectRepository;
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 报表导出 (P2 #29) — PDF / Excel 后端聚合 + 导出
 *
 * <p>3 端点:
 * <ul>
 *   <li>GET /api/reports/project/{id}/monthly.pdf</li>
 *   <li>GET /api/reports/finance/reconciliation-{month}.xlsx</li>
 *   <li>GET /api/reports/risk/summary-{quarter}.pdf</li>
 * </ul>
 *
 * <p>限流: 100 req/min/IP (P2 #29 业务要求)
 *
 * <p>技术栈:
 *  - PDF: openpdf 1.3.43 (兼容 iText 7 API)
 *  - Excel: Apache POI 5.x SXSSF (流式, 大数据量友好)
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportExportController {

    private final ProjectRepository projectRepository;
    private final ReconciliationService reconciliationService;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    // ============================================================
    // 1) 项目月报 PDF
    // ============================================================

    @GetMapping("/project/{id}/monthly.pdf")
    @RateLimit(permitsPerMinute = 100)
    public void projectMonthlyPdf(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month,
            HttpServletResponse response) throws IOException {
        if (month == null) month = LocalDate.now();
        YearMonth ym = YearMonth.from(month);

        var opt = projectRepository.findById(id);
        if (opt.isEmpty() || opt.get().isDeleted()) {
            response.sendError(404, "Project not found: " + id);
            return;
        }
        var p = opt.get();

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=project-" + id + "-" + MONTH_FMT.format(month) + ".pdf");

        try (OutputStream out = response.getOutputStream()) {
            Document doc = new Document(PageSize.A4);
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph("Project Monthly Report"));
            doc.add(new Paragraph("ID: " + id));
            doc.add(new Paragraph("Name: " + (p.getName() == null ? "" : p.getName())));
            doc.add(new Paragraph("Customer: " + (p.getCustomer() == null ? "" : p.getCustomer())));
            doc.add(new Paragraph("Period: " + ym.toString()));
            doc.add(new Paragraph("Budget Estimate: " + (p.getBudgetEstimate() == null ? "N/A" : p.getBudgetEstimate().toPlainString())));
            doc.add(new Paragraph("BAC: " + (p.getBac() == null ? "N/A" : p.getBac().toPlainString())));
            doc.add(new Paragraph(""));
            doc.add(new Paragraph("(Detailed metrics omitted in MVP. Full report includes milestone progress, WBS burn-down, EVM indicators.)"));
            doc.close();
            log.info("[Report] project monthly PDF generated id={} month={}", id, MONTH_FMT.format(month));
        }
    }

    // ============================================================
    // 2) 财务对账 Excel (按月)
    // ============================================================

    @GetMapping("/finance/reconciliation-{month}.xlsx")
    @RateLimit(permitsPerMinute = 100)
    public void financeReconciliationExcel(
            @PathVariable String month,
            HttpServletResponse response) throws IOException {
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (Exception e) {
            response.sendError(400, "Invalid month format (expected yyyy-MM): " + month);
            return;
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=reconciliation-" + month + ".xlsx");

        try (SXSSFWorkbook wb = new SXSSFWorkbook(100);
             OutputStream out = response.getOutputStream()) {
            SXSSFSheet sheet = wb.createSheet("Reconciliation " + month);
            // Header
            Row header = sheet.createRow(0);
            String[] cols = {"ID", "Project", "Contract", "Period", "Total Income", "Total Cost", "Total Diff", "Status"};
            for (int i = 0; i < cols.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cols[i]);
            }
            // Rows (按 project 拉 reconciliation)
            // 当前 MVP 简化: 输出项目列表 + 占位数据
            var projects = projectRepository.findAll();
            int rowIdx = 1;
            for (var p : projects) {
                if (p.isDeleted()) continue;
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(p.getId());
                row.createCell(1).setCellValue(p.getName() == null ? "" : p.getName());
                row.createCell(2).setCellValue(p.getCustomer() == null ? "" : p.getCustomer());
                row.createCell(3).setCellValue(month);
                row.createCell(4).setCellValue(0);
                row.createCell(5).setCellValue(0);
                row.createCell(6).setCellValue(0);
                row.createCell(7).setCellValue("PENDING");
            }
            wb.write(out);
            log.info("[Report] reconciliation Excel generated month={} rows={}", month, rowIdx - 1);
        }
    }

    // ============================================================
    // 3) 风险摘要 PDF (按季度)
    // ============================================================

    @GetMapping("/risk/summary-{quarter}.pdf")
    @RateLimit(permitsPerMinute = 100)
    public void riskSummaryPdf(@PathVariable String quarter, HttpServletResponse response) throws IOException {
        // quarter 格式: 2026-Q3
        if (!quarter.matches("\\d{4}-Q[1-4]")) {
            response.sendError(400, "Invalid quarter format (expected yyyy-Qn): " + quarter);
            return;
        }
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=risk-summary-" + quarter + ".pdf");

        try (OutputStream out = response.getOutputStream()) {
            Document doc = new Document(PageSize.A4);
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph("Risk Summary Report"));
            doc.add(new Paragraph("Quarter: " + quarter));
            doc.add(new Paragraph(""));
            doc.add(new Paragraph("(Detailed risk list omitted in MVP. Full report includes risk count, severity distribution, top 10 risks, response plan.)"));
            doc.close();
            log.info("[Report] risk PDF generated quarter={}", quarter);
        }
    }
}