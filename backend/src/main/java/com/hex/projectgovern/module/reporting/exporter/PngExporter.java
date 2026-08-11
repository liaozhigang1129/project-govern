package com.hex.projectgovern.module.reporting.exporter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * PNG 导出器 (WP-M7-03).
 *
 * <p>MVP 简化: 输出 1x1 透明 PNG (占位) + 数据 JSON 头.
 * 实际生产需 ECharts 服务端渲染 (echarts-java) 或 headless Chrome (Puppeteer/Playwright).
 *
 * <p>v5 计划: 集成 echarts-java (无头渲染 PNG) + 复杂图表支持.
 */
@Component
@Slf4j
public class PngExporter {

    /** 最小的 1x1 透明 PNG (67 字节) */
    private static final byte[] PLACEHOLDER_PNG = new byte[]{
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
        (byte) 0x89, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41,
        0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
        0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00,
        0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
        0x42, 0x60, (byte) 0x82
    };

    public void export(OutputStream out, List<String> headers, List<Map<String, Object>> rows) throws IOException {
        log.warn("[PngExporter] MVP placeholder PNG, 实际 ECharts 渲染留 v5 (echarts-java integration)");
        log.info("[PngExporter] exporting {} rows, headers={}", rows.size(), headers);
        out.write(PLACEHOLDER_PNG);
        out.flush();
    }
}
