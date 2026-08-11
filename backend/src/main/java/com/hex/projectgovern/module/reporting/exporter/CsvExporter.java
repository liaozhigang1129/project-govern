package com.hex.projectgovern.module.reporting.exporter;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * CSV 导出器 (WP-M7-03).
 * 纯文本, 通用 Excel/Numbers 都可打开. UTF-8 BOM (避免 Excel 乱码).
 */
@Component
public class CsvExporter {

    public void export(OutputStream out, List<String> headers, List<List<Object>> rows) throws IOException {
        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        // BOM
        w.write('\ufeff');
        // Header
        if (headers != null) {
            w.write(String.join(",", headers.stream().map(this::escape).toList()));
            w.write("\r\n");
        }
        // Rows
        for (List<Object> row : rows) {
            w.write(String.join(",", row.stream().map(o -> escape(String.valueOf(o == null ? "" : o))).toList()));
            w.write("\r\n");
        }
        w.flush();
    }

    /** 简化版: List<Map> 形式 (列由 headers 决定) */
    public void exportFromMaps(OutputStream out, List<String> headers, List<Map<String, Object>> rows) throws IOException {
        List<List<String>> mapped = rows.stream()
            .map(m -> headers.stream().map(h -> String.valueOf(m.getOrDefault(h, ""))).toList())
            .toList();
        List<List<Object>> mappedObj = mapped.stream().map(r -> (List<Object>)(List<?>) r).toList();
        export(out, headers, mappedObj);
    }

    private String escape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
