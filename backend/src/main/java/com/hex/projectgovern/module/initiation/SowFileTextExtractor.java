package com.hex.projectgovern.module.initiation;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * V4.23 — SOW 文件文本抽取器。
 *
 * <p>背景: 在此之前, {@code InitiationAiWbsService.readSowFileAsText} 只读 .md/.txt 后缀,
 * PDF / Word / Excel / PPT 直接返回 null 并在聚合文本里塞 placeholder, 导致:
 *   - 上传 .pdf SOW 后调用 /ai-wbs/generate 看似"没反应"
 *   - WBS / 风险完全不反映 SOW 真实内容
 *
 * <p>本类提供基于 Apache PDFBox + Apache POI 的纯 Java 抽取, 无外部命令依赖,
 * 任何格式失败都返回 null + 详细 log (由调用方决定是否降级到 placeholder)。
 *
 * <p>支持的格式:
 * <ul>
 *   <li>.pdf  → PDFBox PDFTextStripper</li>
 *   <li>.doc  → POI HWPF (旧 Word)</li>
 *   <li>.docx → POI XWPF (新 Word)</li>
 *   <li>.xls  → POI HSSF (旧 Excel)</li>
 *   <li>.xlsx → POI XSSF (新 Excel)</li>
 *   <li>.ppt  → POI HSLF (旧 PPT)</li>
 *   <li>.pptx → POI XSLF (新 PPT)</li>
 *   <li>.md / .txt / .log / .csv / .json / .yaml / .yml → 原样 UTF-8 读</li>
 * </ul>
 *
 * <p>所有抽取方法都有最大字符数上限 (默认 200KB, 跟上游 SOW 截断保持一致),
 * 避免一个 50MB PDF 把 JVM 内存吃光。
 */
@Slf4j
public final class SowFileTextExtractor {

    /** 单文件抽取最大字符数。超过这个长度会被截断, 防止内存爆炸。 */
    public static final int MAX_EXTRACTED_CHARS = 200 * 1024;

    /** 单 sheet / 单 slide 最多读多少行/张, 防止恶意超长 doc。 */
    private static final int MAX_SHEET_ROWS = 5000;
    private static final int MAX_SLIDES = 500;

    private SowFileTextExtractor() {}

    /**
     * 统一入口: 根据文件后缀分发到对应抽取器。
     *
     * @param filePath  SOW 文件绝对路径
     * @param fileName  原始文件名 (用于判断后缀, 兼容大小写)
     * @return 抽取出的纯文本; 返回 null = 无法抽取 (二进制 / 损坏 / 不支持)
     */
    public static String extract(Path filePath, String fileName) {
        if (filePath == null || fileName == null) return null;
        String name = fileName.toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".pdf"))        return extractPdf(filePath);
            if (name.endsWith(".docx"))       return extractDocx(filePath);
            if (name.endsWith(".doc"))        return extractDoc(filePath);
            if (name.endsWith(".xlsx"))       return extractXlsx(filePath);
            if (name.endsWith(".xls"))        return extractXls(filePath);
            if (name.endsWith(".pptx"))       return extractPptx(filePath);
            if (name.endsWith(".ppt"))        return extractPpt(filePath);
            if (name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".log")
                    || name.endsWith(".csv") || name.endsWith(".json")
                    || name.endsWith(".yaml") || name.endsWith(".yml")) {
                return extractPlainText(filePath);
            }
            log.info("[SowExtract] no extractor for name='{}'", fileName);
            return null;
        } catch (Exception e) {
            log.warn("[SowExtract] extract failed for {} : {}", fileName, e.getMessage(), e);
            return null;
        }
    }

    // ===== PDF =====

    static String extractPdf(Path p) throws IOException {
        try (PDDocument doc = Loader.loadPDF(p.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            return truncate(text);
        }
    }

    // ===== Word =====

    static String extractDocx(Path p) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(p));
             InputStream in = Files.newInputStream(p)) {
            for (XWPFParagraph para : doc.getParagraphs()) {
                sb.append(para.getText()).append('\n');
                if (sb.length() > MAX_EXTRACTED_CHARS) break;
            }
            // 也读表格里的段落 (常见于 SOW 的需求清单 / 交付物清单)
            doc.getTables().forEach(t -> {
                t.getRows().forEach(r -> {
                    r.getTableCells().forEach(c -> {
                        c.getParagraphs().forEach(pp -> sb.append(pp.getText()).append('\t'));
                        sb.append('\n');
                    });
                });
            });
        }
        return truncate(sb.toString());
    }

    static String extractDoc(Path p) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (HWPFDocument doc = new HWPFDocument(Files.newInputStream(p));
             WordExtractor extractor = new WordExtractor(doc)) {
            for (String para : extractor.getParagraphText()) {
                sb.append(para).append('\n');
                if (sb.length() > MAX_EXTRACTED_CHARS) break;
            }
        }
        return truncate(sb.toString());
    }

    // ===== Excel =====

    static String extractXlsx(Path p) throws IOException {
        return extractWorkbook(p, true);
    }

    static String extractXls(Path p) throws IOException {
        return extractWorkbook(p, false);
    }

    private static String extractWorkbook(Path p, boolean xssf) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = Files.newInputStream(p);
             Workbook wb = xssf ? new XSSFWorkbook(in) : new HSSFWorkbook(in)) {
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                sb.append("# ").append(sheet.getSheetName()).append('\n');
                int rowCount = 0;
                for (Row row : sheet) {
                    if (rowCount++ > MAX_SHEET_ROWS) break;
                    for (Cell cell : row) {
                        sb.append(cellToString(cell)).append('\t');
                    }
                    sb.append('\n');
                    if (sb.length() > MAX_EXTRACTED_CHARS) break;
                }
                if (sb.length() > MAX_EXTRACTED_CHARS) break;
            }
        }
        return truncate(sb.toString());
    }

    private static String cellToString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double n = cell.getNumericCellValue();
                // 整数别带 .0
                yield (n == Math.floor(n) && !Double.isInfinite(n))
                        ? Long.toString((long) n)
                        : Double.toString(n);
            }
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue(); }
                catch (Exception e) { yield cell.toString(); }
            }
            default -> "";
        };
    }

    // ===== PowerPoint =====

    static String extractPptx(Path p) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (XMLSlideShow ppt = new XMLSlideShow(Files.newInputStream(p))) {
            int n = 0;
            for (XSLFSlide slide : ppt.getSlides()) {
                if (n++ > MAX_SLIDES) break;
                sb.append("# Slide ").append(n).append('\n');
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape ts) {
                        String t = ts.getText();
                        if (t != null && !t.isBlank()) sb.append(t).append('\n');
                    }
                }
                if (sb.length() > MAX_EXTRACTED_CHARS) break;
            }
        }
        return truncate(sb.toString());
    }

    static String extractPpt(Path p) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (HSLFSlideShow ppt = new HSLFSlideShow(Files.newInputStream(p))) {
            int n = 0;
            var slides = ppt.getSlides();
            for (var slide : slides) {
                if (n++ > MAX_SLIDES) break;
                sb.append("# Slide ").append(n).append('\n');
                for (HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof org.apache.poi.hslf.usermodel.HSLFTextShape ts) {
                        // HSLFTextShape#getText() 直接拼好所有 paragraph 的文本
                        String t = ts.getText();
                        if (t != null && !t.isBlank()) sb.append(t).append('\n');
                    }
                }
                if (sb.length() > MAX_EXTRACTED_CHARS) break;
            }
        }
        return truncate(sb.toString());
    }

    // ===== 纯文本 =====

    static String extractPlainText(Path p) throws IOException {
        long size = Files.size(p);
        if (size <= MAX_EXTRACTED_CHARS) {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        }
        // 大文件: 只读前 200KB
        byte[] buf = new byte[MAX_EXTRACTED_CHARS];
        try (InputStream in = Files.newInputStream(p)) {
            int read = 0;
            while (read < buf.length) {
                int n = in.read(buf, read, buf.length - read);
                if (n < 0) break;
                read += n;
            }
            return new String(buf, 0, read, StandardCharsets.UTF_8);
        }
    }

    // ===== helpers =====

    private static String truncate(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > MAX_EXTRACTED_CHARS) {
            return trimmed.substring(0, MAX_EXTRACTED_CHARS);
        }
        return trimmed;
    }
}