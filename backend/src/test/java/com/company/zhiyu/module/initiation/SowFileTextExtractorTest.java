package com.company.zhiyu.module.initiation;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V4.23 — {@link SowFileTextExtractor} 单元测试。
 *
 * <p>用 POI 现场构造真实格式的文件(避免放二进制 fixture 进仓库),覆盖:
 * <ul>
 *   <li>.pdf  → 通过 PDFBox 反向路径另写一个最简 PDF(略,留给集成测),先测 plain/md/docx/xlsx/pptx</li>
 *   <li>.md / .txt / .json → 直读</li>
 *   <li>.docx / .xlsx / .pptx → POI 生成 → 抽 → 断言</li>
 * </ul>
 *
 * <p>PDF 用 PDFBox 自带 API 直接造一份, 不依赖外部样本。
 */
class SowFileTextExtractorTest {

    @TempDir
    Path tmp;

    // ===== 纯文本 =====
    @Test
    @DisplayName(".txt 抽取: 保留原 UTF-8 内容")
    void txt_extractsContent() throws IOException {
        Path f = tmp.resolve("sow.txt");
        Files.writeString(f, "智能客服 NLP 升级\n工期 6 个月\n预算 38 万\n", StandardCharsets.UTF_8);
        String out = SowFileTextExtractor.extract(f, "sow.txt");
        assertNotNull(out);
        assertTrue(out.contains("智能客服"));
        assertTrue(out.contains("38 万"));
    }

    @Test
    @DisplayName(".md 抽取: 同 .txt")
    void md_extractsContent() throws IOException {
        Path f = tmp.resolve("sow.md");
        Files.writeString(f, "# 项目概述\n工单分类 / 意图识别 / 情绪分析\n", StandardCharsets.UTF_8);
        String out = SowFileTextExtractor.extract(f, "sow.md");
        assertNotNull(out);
        assertTrue(out.contains("工单分类"));
        assertTrue(out.contains("意图识别"));
    }

    @Test
    @DisplayName(".yaml 抽取: JSON / YAML 也走纯文本路径")
    void yaml_extractsContent() throws IOException {
        Path f = tmp.resolve("config.yaml");
        Files.writeString(f, "industry: AI\nmodules:\n  - 意图识别\n  - 智能派单\n", StandardCharsets.UTF_8);
        String out = SowFileTextExtractor.extract(f, "config.yaml");
        assertNotNull(out);
        assertTrue(out.contains("意图识别"));
    }

    // ===== DOCX =====
    @Test
    @DisplayName(".docx 抽取: 段落文本能完整拿出")
    void docx_extractsParagraphs() throws IOException {
        Path f = tmp.resolve("sow.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            addPara(doc, "银行风控数据中台迁移项目");
            addPara(doc, "工期 12 周, 报价 280 万");
            addPara(doc, "范围: 5 个核心系统 (信贷/征信/支付) 历史数据迁到数据湖仓");
            try (var out = Files.newOutputStream(f)) {
                doc.write(out);
            }
        }
        String out = SowFileTextExtractor.extract(f, "sow.docx");
        assertNotNull(out, "docx must extract");
        assertTrue(out.contains("银行风控"));
        assertTrue(out.contains("信贷"));
        assertTrue(out.contains("数据湖仓"));
    }

    // ===== XLSX =====
    @Test
    @DisplayName(".xlsx 抽取: 表头 + 数据行都能拿出")
    void xlsx_extractsRows() throws IOException {
        Path f = tmp.resolve("sow.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("需求清单");
            addCell(sh, 0, 0, "模块");
            addCell(sh, 0, 1, "工期(周)");
            addCell(sh, 1, 0, "工单分类模型");
            addCell(sh, 1, 1, 8);
            addCell(sh, 2, 0, "意图识别模型");
            addCell(sh, 2, 1, 6);
            try (var out = Files.newOutputStream(f)) {
                wb.write(out);
            }
        }
        String out = SowFileTextExtractor.extract(f, "sow.xlsx");
        assertNotNull(out, "xlsx must extract");
        assertTrue(out.contains("需求清单"));
        assertTrue(out.contains("工单分类模型"));
        assertTrue(out.contains("意图识别"));
        assertTrue(out.contains("8"), "数字 cell 应被字符串化, 实际: " + out);
    }

    // ===== PPTX =====
    @Test
    @DisplayName(".pptx 抽取: 幻灯片文本能拿出")
    void pptx_extractsSlides() throws IOException {
        Path f = tmp.resolve("sow.pptx");
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            XSLFSlide slide1 = ppt.createSlide();
            XSLFTextShape tb1 = slide1.createTextBox();
            tb1.setText("项目背景: 客服工单 NLP 准确率仅 71%");
            XSLFSlide slide2 = ppt.createSlide();
            XSLFTextShape tb2 = slide2.createTextBox();
            tb2.setText("目标: NLP 准确率提升到 92%");
            try (var out = Files.newOutputStream(f)) {
                ppt.write(out);
            }
        }
        String out = SowFileTextExtractor.extract(f, "sow.pptx");
        assertNotNull(out, "pptx must extract");
        assertTrue(out.contains("Slide 1"));
        assertTrue(out.contains("Slide 2"));
        assertTrue(out.contains("71%"));
        assertTrue(out.contains("92%"));
    }

    // ===== PDF =====
    @Test
    @DisplayName(".pdf 抽取: 用 PDFBox 现场造一份, 文本能取出")
    void pdf_extractsContent() throws IOException {
        Path f = tmp.resolve("sow.pdf");
        // 用 PDFBox 直接写一个最简 PDF (Helvetica 内置字体不支持中文, 用英文 + 数字验证抽取链路)
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                         new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Smart Customer Service NLP Upgrade SOW");
                cs.newLineAtOffset(0, -20);
                cs.showText("Timeline 6 months, Budget 380K RMB");
                cs.newLineAtOffset(0, -20);
                cs.showText("Smart Dispatch Coverage 60%");
                cs.endText();
            }
            doc.save(f.toFile());
        }
        String out = SowFileTextExtractor.extract(f, "sow.pdf");
        assertNotNull(out, "pdf must extract");
        assertTrue(out.contains("Smart Customer Service"));
        assertTrue(out.contains("380K"),
                "数字 '380K' 必须在文本里, 实际: " + out);
        assertTrue(out.contains("Smart Dispatch"));
    }

    // ===== 不支持 / 空 / 损坏 =====
    @Test
    @DisplayName("不支持的后缀返回 null (不抛异常)")
    void unsupportedExt_returnsNull() throws IOException {
        Path f = tmp.resolve("weird.bin");
        Files.writeString(f, "anything", StandardCharsets.UTF_8);
        String out = SowFileTextExtractor.extract(f, "weird.bin");
        assertNull(out, "未知扩展名应返回 null, 让上层降级到 placeholder");
    }

    @Test
    @DisplayName("损坏的 PDF 不抛异常, 返回 null")
    void corruptedPdf_returnsNull() throws IOException {
        Path f = tmp.resolve("broken.pdf");
        Files.writeString(f, "not a real pdf content, just text", StandardCharsets.UTF_8);
        String out = SowFileTextExtractor.extract(f, "broken.pdf");
        assertNull(out, "PDF 解析失败应被吞掉返回 null");
    }

    @Test
    @DisplayName("null / 空路径 → null")
    void nullInputs() {
        assertNull(SowFileTextExtractor.extract(null, "x.pdf"));
        assertNull(SowFileTextExtractor.extract(tmp.resolve("anything"), null));
    }

    @Test
    @DisplayName("大文本截断: 200KB 上限生效")
    void largeText_truncated() throws IOException {
        Path f = tmp.resolve("big.txt");
        // 写 300KB 的纯 a
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300 * 1024; i++) sb.append('a');
        Files.writeString(f, sb.toString(), StandardCharsets.UTF_8);
        String out = SowFileTextExtractor.extract(f, "big.txt");
        assertNotNull(out);
        assertEquals(SowFileTextExtractor.MAX_EXTRACTED_CHARS, out.length(),
                "应被截断到 MAX_EXTRACTED_CHARS");
    }

    // ===== helpers =====

    private static void addPara(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setText(text);
    }

    private static void addCell(Sheet sh, int rowIdx, int colIdx, Object value) {
        Row row = sh.getRow(rowIdx);
        if (row == null) row = sh.createRow(rowIdx);
        Cell cell = row.getCell(colIdx);
        if (cell == null) cell = row.createCell(colIdx);
        if (value instanceof String s) {
            cell.setCellValue(s);
        } else if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        }
    }
}