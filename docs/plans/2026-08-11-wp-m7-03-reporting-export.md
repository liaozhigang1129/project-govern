---
status: active
created: 2026-08-11
updated: 2026-08-11
summary: WP-M7-03 v5 核心功能(报表后端 + 4 格式导出)— 8 子任务 / 5 控制器 / 5 服务 / 4 导出器
---

# Plan · WP-M7-03 v5 报表后端 + 4 格式导出

> 对应 WBS 工作包:[`WP-M7-03 v5 核心功能`](../WBS.md#wp-m7-03-v5-核心功能ai-预测--智能推荐--异常--多租户--移动)
> 对应里程碑:**M7**(v5 立项:AI·移动·治理)
> 对应 ADR:[ADR-005 v5 立项范围与关键决策](../decisions/005-m7-v5-scope.md) D4(导出)/D5(数据集)/D6(订阅)/D7(安全)
> 对应 spec:
> - [`reporting.md`](../specs/reporting.md) — 报表/BI/导出业务范围
> - [`reporting-api.md`](../specs/reporting-api.md) — 报表域 API 契约
> 对应前置 plan:
> - [`WP-M7-02 v5 数据模型增量`](../plans/2026-08-11-wp-m7-02-v5-data-model.md)(依赖 V7.0 schema)
> 当前状态:**active**(2026-08-11 plan 落地,等 V7.0 Flyway 迁移完成后正式实施)
> 阻塞项:WP-M7-02 V7.0 Flyway 迁移 + WP-M7-01 D+7 整合会议拍板

---

## 1. 目标与范围

### 1.1 一句话

实现 **报表后端 API 全套 + 4 格式导出服务(PDF/Excel/CSV/PNG) + 订阅分发 + 异步任务调度**,
支撑 WP-M7-04 数据质量看板 + 前端 ECharts 仪表盘的 **数据后端**。
**不实现**:前端 ECharts 渲染(留给 WP-M7-04 / W4-5)、AI 智能报告(本期**不**做)、NLP 报告(Out of Scope)。

### 1.2 范围内

- **5 个 REST 控制器**:`DashboardController` / `DatasetController` / `ReportController` / `ReportExportController` / `ReportSubscriptionController`
- **5 个核心服务**:`DashboardService` / `DatasetService` / `ReportService` / `ReportExportService` / `ReportSubscriptionService`
- **4 个导出器**(策略模式):`PdfExporter` / `ExcelExporter` / `CsvExporter` / `PngExporter`
- **1 个异步任务引擎**:`@Async` + `TaskExecutor` + 进度轮询/SSE
- **1 个调度框架**:`@Scheduled` 扫描 `report_subscription.next_run_at` + `report_snapshot` 物化
- **3 个 Event 桥接**:订阅触发 → 异步任务 → 邮件/IM/链接分享
- **12 个 API 端点**(从 [reporting-api.md §1-§5](../specs/reporting-api.md)):

| # | 方法 | 路径 | 控制器 |
|:--:|---|---|---|
| 01 | GET | `/api/dashboards` | DashboardController |
| 02 | POST | `/api/dashboards` | DashboardController |
| 03 | GET | `/api/dashboards/{id}` | DashboardController |
| 04 | PATCH | `/api/dashboards/{id}` | DashboardController |
| 05 | POST | `/api/dashboards/{id}/clone` | DashboardController |
| 06 | POST | `/api/dashboards/{id}/share` | DashboardController |
| 07 | GET | `/api/dashboards/role/{roleCode}` | DashboardController |
| 08 | GET | `/api/dashboards/{id}/data` | DashboardController(聚合拉取) |
| 09 | POST | `/api/datasets/{id}/query` | DatasetController |
| 10 | GET | `/api/reports/{id}/export?format=pdf\|xlsx\|csv\|png` | ReportExportController(同步/异步) |
| 11 | GET | `/api/exports/{exportId}` | ReportExportController(状态轮询) |
| 12 | POST | `/api/reports/{id}/subscribe` | ReportSubscriptionController |

### 1.3 出范围

- **前端 ECharts 渲染**(W3-W5,见 [WP-M7-01 plan §5 W3-W5 节点](../plans/2026-08-07-wp-m7-01-v5-scope-freeze.md))
- **NLP 智能报告生成**(Out of Scope,见 ADR-005 §2)
- **第三方 BI 嵌入**(D4 已拒)
- **跨数据源 JOIN**(API 契约 §8 已定:不支持)
- **数据质量看板**(WP-M7-04 范围)

---

## 2. 依赖与基础设施

### 2.1 新增 Maven 依赖

| 依赖 | 用途 | 备注 |
|---|---|---|
| `com.github.librepdf:openpdf:1.3.39` | PDF 导出 | AGPL/LGPL 双重许可,合规 OK |
| (Apache POI 已就位) | Excel/CSV 流式 | `poi-ooxml` + SXSSF |
| `org.apache.commons:commons-csv:1.10.0` | CSV (UTF-8 BOM) | 比手写更稳 |
| `org.springframework.boot:spring-boot-starter-thymeleaf` | PDF 模板引擎 | Thymeleaf 渲染 HTML → OpenPDF 转 PDF |

> **PNG 导出**:本期采用 **服务端 ECharts 替代方案** — 用 **headless Chrome / Playwright**(Java 端 `playwright-java`)渲染。
> 备选:`wkhtmltoimage` CLI(需服务器装 wkhtmltopdf 工具链,CI 成本高);
> 备选 2:纯 Java 端 `XChart` + 自绘 PNG(质量略低)。
> **决策**:采用 **Playwright Java**(`com.microsoft.playwright:playwright:1.40.0`),CI 需装 Chromium。

### 2.2 配置(`application.yml`)

```yaml
projectgovern:
  reporting:
    export:
      sync-row-limit: 1000
      sync-size-limit-bytes: 5242880   # 5 MB
      async-timeout-seconds: 60
      retention-days: 7                # 产物保留 7 天
      storage:
        backend: filesystem           # filesystem | minio
        base-path: /var/project-govern/exports
    subscription:
      retry:
        max-attempts: 3
        backoff-seconds: [1, 5, 25]   # 指数退避
      watermark: true
      ttl-hours: 24
    snapshot:
      build-timeout-seconds: 300
      cron: "0 0 1 * * *"             # 每天 01:00 物化
```

### 2.3 Async 任务执行器

```java
@Configuration
@EnableAsync
public class ReportingAsyncConfig {
    @Bean("reportingTaskExecutor")
    public TaskExecutor reportingTaskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(16);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("reporting-async-");
        exec.initialize();
        return exec;
    }
}
```

### 2.4 角色映射(system_config 读取)

- `role_dashboard.PMO_ADMIN` / `role_dashboard.PMO_DIRECTOR` / ...(8 角色)
- 读 `system_config` 表,**不**缓存(变更即时生效)
- 由 `RoleDashboardResolver` 单例 Bean 包装

---

## 3. 5 个控制器设计

> 路径前缀:`/api/`
> 鉴权:复用 `Authorization: Bearer <jwt>` + `@RequireRoles` 注解
> 响应:统一 `ApiResponse<T>`

### 3.1 `DashboardController`(`@RequestMapping("/api/dashboards")`)

```java
@RestController
@RequestMapping("/api/dashboards")
@RequiredArgsConstructor
@Tag(name = "Reporting/Dashboard")
public class DashboardController {
    private final DashboardService service;

    @GetMapping                    // ① 我的仪表盘列表
    public ApiResponse<List<DashboardDto>> list(@RequestParam(required = false) String scope);

    @PostMapping                   // ② 新建(PMO_ADMIN/PM/部门负责人)
    @RequireRoles.Write
    public ApiResponse<DashboardDto> create(@Valid @RequestBody DashboardCreateRequest req);

    @GetMapping("/{id}")           // ③ 详情
    public ApiResponse<DashboardDto> get(@PathVariable Long id);

    @PatchMapping("/{id}")         // ④ 更新布局/配置
    @RequireRoles.Write
    public ApiResponse<DashboardDto> update(@PathVariable Long id, @Valid @RequestBody DashboardUpdateRequest req);

    @PostMapping("/{id}/clone")    // ⑤ 复制(返回新 dashboard + widgets)
    public ApiResponse<DashboardDto> clone(@PathVariable Long id, @RequestParam String newName);

    @PostMapping("/{id}/share")    // ⑥ 分享(生成 token URL)
    public ApiResponse<ShareLinkDto> share(@PathVariable Long id, @RequestBody ShareRequest req);

    @GetMapping("/role/{roleCode}")// ⑦ 角色默认仪表盘(从 system_config 读)
    public ApiResponse<DashboardDto> roleDefault(@PathVariable String roleCode);

    @GetMapping("/{id}/data")      // ⑧ 聚合拉取(关键路径:一次返回所有 widget + 数据)
    public ApiResponse<DashboardDataDto> data(@PathVariable Long id, @RequestParam Map<String, Object> params);
}
```

### 3.2 `DatasetController`(`@RequestMapping("/api/datasets")`)

```java
@RestController
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
@Tag(name = "Reporting/Dataset")
public class DatasetController {
    private final DatasetService service;

    @GetMapping                    // 列表
    public ApiResponse<List<DatasetDto>> list(@RequestParam(required = false) String domain);

    @GetMapping("/{id}")           // 详情 + 字段
    public ApiResponse<DatasetDetailDto> get(@PathVariable Long id);

    @PostMapping("/{id}/query")    // ⑨ 查询(过滤/分组/聚合)
    public ApiResponse<DatasetQueryResultDto> query(@PathVariable Long id,
        @Valid @RequestBody DatasetQueryRequest req);

    @GetMapping("/{id}/preview")   // 预览前 100 行
    public ApiResponse<DatasetQueryResultDto> preview(@PathVariable Long id);
}
```

> **DatasetQueryRequest** 业务规则(见 [reporting-api.md §6](../specs/reporting-api.md#6-业务规则强制)):
> - `rowLimit ≤ 100,000`(超过返回 413)
> - 公式白名单: `+ - * / % SUM AVG COUNT MIN MAX IF CASE WHEN`
> - 超时默认 60s

### 3.3 `ReportController`(`@RequestMapping("/api/reports")`)

```java
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reporting/Report")
public class ReportController {
    private final ReportService service;

    @GetMapping                    // 我的报表
    public ApiResponse<List<ReportDto>> list();

    @GetMapping("/templates")      // 预置模板
    public ApiResponse<List<ReportTemplateDto>> templates();

    @PostMapping("/{id}/run")      // 执行(返回 runId)
    public ApiResponse<ReportRunDto> run(@PathVariable Long id, @RequestBody RunRequest req);

    @GetMapping("/{id}/runs/{runId}") // 执行结果
    public ApiResponse<ReportRunResultDto> runResult(@PathVariable Long id, @PathVariable String runId);
}
```

### 3.4 `ReportExportController`(`@RequestMapping("/api")`)

```java
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Reporting/Export")
public class ReportExportController {
    private final ReportExportService service;

    // ⑩ 导出(同步或异步)
    @GetMapping("/reports/{id}/export")
    public ResponseEntity<?> export(@PathVariable Long id,
        @RequestParam String format,    // pdf|xlsx|csv|png
        @RequestParam Map<String, Object> params);

    // ⑪ 状态轮询
    @GetMapping("/exports/{exportId}")
    public ApiResponse<ExportStatusDto> status(@PathVariable String exportId);
}
```

> **同步 vs 异步**(业务规则见 [reporting-api.md §4](../specs/reporting-api.md#4-导出export)):
> - `rowCount ≤ 1000 && size ≤ 5MB` → 同步 stream 返回
> - 超过 → 异步,返回 `exportId`,前端轮询或 SSE
> - SLA:异步任务 < 60s 完成(超出记 P99 告警)

### 3.5 `ReportSubscriptionController`(`@RequestMapping("/api/reports/{id}/subscribe")`)

```java
@RestController
@RequestMapping("/api/reports/{id}/subscribe")
@RequiredArgsConstructor
@Tag(name = "Reporting/Subscription")
public class ReportSubscriptionController {
    private final ReportSubscriptionService service;

    // ⑫ 订阅
    @PostMapping
    public ApiResponse<SubscriptionDto> subscribe(@PathVariable Long reportId,
        @Valid @RequestBody SubscribeRequest req);

    @GetMapping("/mine")
    public ApiResponse<List<SubscriptionDto>> mine();
}
```

> **SubscribeRequest**:`{ schedule, format, channels[EMAIL/IM], recipients[], includeWatermark }`

---

## 4. 5 个核心服务

### 4.1 `DashboardService`

```java
@Service
@RequiredArgsConstructor
public class DashboardService {
    private final DashboardRepository dashboardRepo;
    private final DashboardWidgetRepository widgetRepo;
    private final RoleDashboardResolver roleResolver;
    private final DatasetService datasetService;

    /** 关键路径:聚合拉取所有 widget + 数据 */
    @Transactional(readOnly = true)
    public DashboardDataDto getData(Long id, Map<String, Object> params) {
        Dashboard dash = dashboardRepo.findById(id).orElseThrow(...);
        List<DashboardWidget> widgets = widgetRepo.findByDashboardIdOrderBySortOrder(id);
        List<WidgetDataDto> widgetData = widgets.stream()
            .map(w -> widgetData(w, params))
            .toList();
        return new DashboardDataDto(dash, widgetData);
    }

    /** 角色默认仪表盘(从 system_config 读) */
    public DashboardDto roleDefault(String roleCode) {
        String dashCode = roleResolver.resolve(roleCode);
        return dashboardRepo.findByCode(dashCode).map(this::toDto).orElseThrow(...);
    }

    /** 分享:生成 share_url token */
    public ShareLinkDto share(Long id, ShareRequest req) {
        Dashboard dash = dashboardRepo.findById(id).orElseThrow(...);
        String token = UUID.randomUUID().toString();
        dash.setIsShared(true);
        dash.setShareUrl("/share/" + token);
        return new ShareLinkDto(token, dash.getShareUrl());
    }
}
```

### 4.2 `DatasetService`(D5 决策核心)

```java
@Service
@RequiredArgsConstructor
public class DatasetService {
    private final DatasetRepository datasetRepo;
    private final DatasetFieldRepository fieldRepo;
    private final ReportSnapshotRepository snapshotRepo;
    private final JdbcTemplate jdbc;
    private final FormulaEvaluator formulaEvaluator;

    @Transactional(readOnly = true)
    public DatasetQueryResultDto query(Long datasetId, DatasetQueryRequest req) {
        // 1. 行数预检
        if (req.getRowLimit() > 100_000) {
            throw new BusinessException(413, "row_limit > 100000");
        }
        // 2. 公式白名单校验(DatasetQueryRequest 内的 calculated fields)
        req.getCalculatedFields().forEach(formulaEvaluator::validate);

        // 3. D5 决策: 优先走 report_snapshot(预聚合)
        Optional<ReportSnapshot> snapshot = findSnapshot(datasetId, req);
        if (snapshot.isPresent()) {
            return buildFromSnapshot(snapshot.get(), req);
        }

        // 4. 回退:dataset.sql_template(单表,禁跨表 JOIN)
        Dataset ds = datasetRepo.findById(datasetId).orElseThrow(...);
        if (ds.getSqlTemplate() == null) {
            throw new BusinessException(500, "no snapshot or sql_template");
        }
        // 5. 绑定参数 + LIMIT 注入
        String sql = bindParams(ds.getSqlTemplate(), req);
        return jdbc.query(sql, rowMapper, req.getRowLimit());
    }
}
```

### 4.3 `ReportService`

```java
@Service
@RequiredArgsConstructor
public class ReportService {
    private final ReportTemplateRepository templateRepo;
    private final ReportRunRepository runRepo;
    private final DatasetService datasetService;

    /** 执行报表(走 dataset 预聚合 + 缓存) */
    public ReportRunResultDto run(Long templateId, RunRequest req) {
        ReportTemplate tpl = templateRepo.findById(templateId).orElseThrow(...);
        ReportRun run = new ReportRun();
        run.setTemplateId(templateId);
        run.setStatus(ReportRunStatus.RUNNING);
        runRepo.save(run);
        try {
            DatasetQueryResultDto data = datasetService.query(tpl.getDatasetId(),
                req.toQueryRequest());
            run.setStatus(ReportRunStatus.SUCCESS);
            run.setResult(serialize(data));
            runRepo.save(run);
        } catch (Exception e) {
            run.setStatus(ReportRunStatus.FAILED);
            run.setError(e.getMessage());
            runRepo.save(run);
            throw e;
        }
        return toResultDto(run);
    }
}
```

### 4.4 `ReportExportService`(4 格式导出调度核心)

```java
@Service
@RequiredArgsConstructor
public class ReportExportService {
    private final ReportExportRepository exportRepo;
    private final ReportRunRepository runRepo;
    private final Map<String, Exporter> exporters;  // 策略注入

    /** 同步导出(小数据) */
    public ResponseEntity<Resource> exportSync(Long reportId, String format, Map<String, Object> params) {
        ReportRunResultDto data = runOrFetch(reportId, params);
        if (data.getRowCount() > 1000 || data.getSizeBytes() > 5 * 1024 * 1024) {
            throw new BusinessException(413, "use async export");
        }
        Exporter exp = exporters.get(format.toLowerCase() + "Exporter");
        byte[] bytes = exp.export(data);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(exp.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report." + exp.fileExt())
            .body(new ByteArrayResource(bytes));
    }

    /** 异步导出(大数据) */
    public String exportAsync(Long reportId, String format, Long userId, Map<String, Object> params) {
        ReportExport exp = new ReportExport();
        exp.setTaskId(UUID.randomUUID().toString());
        exp.setTemplateId(reportId);
        exp.setUserId(userId);
        exp.setFormat(format);
        exp.setStatus(ReportExportStatus.PENDING);
        exp.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        exportRepo.save(exp);
        asyncRunExport(exp.getTaskId());  // @Async
        return exp.getTaskId();
    }

    @Async("reportingTaskExecutor")
    public void asyncRunExport(String taskId) {
        ReportExport exp = exportRepo.findByTaskId(taskId).orElseThrow(...);
        exp.setStatus(ReportExportStatus.RUNNING);
        exp.setStartedAt(Instant.now());
        exportRepo.save(exp);
        try {
            Exporter e = exporters.get(exp.getFormat().toLowerCase() + "Exporter");
            byte[] bytes = e.export(loadData(exp));
            String filePath = storage.save(bytes, taskId + "." + e.fileExt());
            exp.setFilePath(filePath);
            exp.setFileSize((long) bytes.length);
            exp.setStatus(ReportExportStatus.SUCCESS);
        } catch (Exception ex) {
            exp.setStatus(ReportExportStatus.FAILED);
            exp.setErrorMessage(ex.getMessage());
        } finally {
            exp.setFinishedAt(Instant.now());
            exportRepo.save(exp);
        }
    }

    /** D7 决策:导出文件加密 + 用户水印 + TTL 24h */
    public Resource loadExport(String taskId, Long userId) {
        ReportExport exp = exportRepo.findByTaskId(taskId).orElseThrow(...);
        if (exp.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(410, "export expired");
        }
        // D7:水印 + 加密 URL 由 download 端处理
        return storage.load(exp.getFilePath(), userId);  // 注入 userId 用于审计日志
    }
}
```

### 4.5 `ReportSubscriptionService`(D6 三通道 + D7 TTL 24h)

```java
@Service
@RequiredArgsConstructor
public class ReportSubscriptionService {
    private final ReportSubscriptionRepository subRepo;
    private final ReportExportService exportService;
    private final NotificationService notificationService;
    private final ReportTemplateRepository templateRepo;

    /** 订阅触发(由调度器调用) */
    @Transactional
    public void runSubscription(Long subId) {
        ReportSubscription sub = subRepo.findById(subId).orElseThrow(...);
        ReportTemplate tpl = templateRepo.findById(sub.getTemplateId()).orElseThrow(...);

        // 1. 异步导出(订阅总是异步)
        String taskId = exportService.exportAsync(tpl.getId(), "PDF",
            sub.getUserId(), sub.getParams());

        // 2. 三通道分发(Email + IM + 链接分享)
        for (String channel : sub.getChannelSet().split(",")) {
            switch (channel.toUpperCase().trim()) {
                case "EMAIL" -> notificationService.sendEmail(
                    sub.getUserId(), "Report ready: " + tpl.getName(),
                    buildEmailBody(sub, taskId));
                case "IM" -> notificationService.sendIm(
                    sub.getUserId(), buildImMessage(sub, taskId));
                case "LINK" -> notificationService.sendIm(
                    sub.getUserId(), buildShareLinkMessage(sub, taskId));
            }
        }

        // 3. 失败重试(指数退避:1s/5s/25s)
        sub.setLastRunAt(Instant.now());
        sub.setNextRunAt(computeNext(sub.getCron()));
        subRepo.save(sub);
    }

    /** 失败重试调度器(@Scheduled 每 1 min 扫描) */
    @Scheduled(fixedDelay = 60000)
    public void retryFailed() {
        List<SubscriptionFailure> failures = failureRepo.findPending();
        failures.forEach(f -> {
            long ageSec = Duration.between(f.getFailedAt(), Instant.now()).getSeconds();
            if (Arrays.asList(1L, 6L, 31L).contains(ageSec)) {  // 1s / 5s / 25s 触发
                retryOnce(f);
            }
        });
    }
}
```

---

## 5. 4 个导出器(策略模式)

> 接口:`Exporter { byte[] export(DatasetQueryResultDto data); String fileExt(); String contentType(); }`
> Bean 注入:`@Component("pdfExporter")` / `@Component("xlsxExporter")` / `@Component("csvExporter")` / `@Component("pngExporter")`

### 5.1 `PdfExporter`

- 依赖:OpenPDF + Thymeleaf
- 流程:Thymeleaf 渲染 HTML(模板路径 `templates/report/pdf-default.html`)→ OpenPDF 转 PDF
- HTML 模板:Bootstrap 5 表格 + 公司 Logo + 页眉/页脚
- 性能:< 5s / 1000 行

```java
@Component("pdfExporter")
public class PdfExporter implements Exporter {
    private final TemplateEngine templateEngine;

    @Override
    public byte[] export(DatasetQueryResultDto data) {
        Context ctx = new Context();
        ctx.setVariable("title", "Report");
        ctx.setVariable("data", data);
        String html = templateEngine.process("report/pdf-default", ctx);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // OpenPDF: com.github.librepdf.openpdf
            Document doc = new Document(PageSize.A4);
            PdfWriter.getInstance(doc, out);
            doc.open();
            // 用 HTMLWorker / XMLWorker 解析
            // 简化:用 iTextHTMLWorker (已 deprecated 但可用)
            doc.close();
            return out.toByteArray();
        }
    }
}
```

### 5.2 `ExcelExporter`

- 依赖:Apache POI(SXSSF 流式)
- 流程:写 SXSSFWorkbook(内存只保留 100 行,流式刷盘) → ByteArrayOutputStream
- 性能:可处理 100,000 行(API 契约 §6 上限)
- 格式:.xlsx

```java
@Component("xlsxExporter")
public class ExcelExporter implements Exporter {
    @Override
    public byte[] export(DatasetQueryResultDto data) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            SXSSFSheet sheet = wb.createSheet("Report");
            // header
            SXSSFRow header = sheet.createRow(0);
            for (int i = 0; i < data.getColumns().size(); i++) {
                header.createCell(i).setCellValue(data.getColumns().get(i).getName());
            }
            // body
            int rowIdx = 1;
            for (List<Object> row : data.getRows()) {
                SXSSFRow r = sheet.createRow(rowIdx++);
                for (int i = 0; i < row.size(); i++) {
                    r.createCell(i).setCellValue(String.valueOf(row.get(i)));
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
```

### 5.3 `CsvExporter`

- 依赖:Apache Commons CSV
- 格式:UTF-8 BOM(Excel 兼容)+ `,` 分隔 + `"` 引用
- 流式:不读全内存

```java
@Component("csvExporter")
public class CsvExporter implements Exporter {
    @Override
    public byte[] export(DatasetQueryResultDto data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            w.write('\uFEFF');  // BOM
            try (CSVPrinter csv = new CSVPrinter(w, CSVFormat.DEFAULT
                .withHeader(data.getColumns().stream().map(DatasetField::getDisplayName).toArray()))) {
                for (List<Object> row : data.getRows()) {
                    csv.printRecord(row);
                }
            }
            return out.toByteStream() == null ? out.toByteArray() : out.toByteArray();
        }
    }
}
```

### 5.4 `PngExporter`(Playwright 方案)

- 依赖:Playwright Java + Chromium
- 流程:Thymeleaf 渲染 HTML → Playwright `page.setContent()` → `page.screenshot()` → PNG bytes
- CI:需装 Chromium(`npx playwright install chromium`)

```java
@Component("pngExporter")
public class PngExporter implements Exporter {
    private final TemplateEngine templateEngine;
    private final Playwright playwright;  // 单例

    @Override
    public byte[] export(DatasetQueryResultDto data) {
        Context ctx = new Context();
        ctx.setVariable("data", data);
        String html = templateEngine.process("report/png-default", ctx);
        try (Browser browser = playwright.chromium().launch();
             Page page = browser.newPage()) {
            page.setContent(html);
            return page.screenshot();
        }
    }
}
```

---

## 6. 报表物化快照(ReportSnapshotScheduler)

```java
@Component
@RequiredArgsConstructor
public class ReportSnapshotScheduler {
    private final ReportTemplateRepository templateRepo;
    private final ReportSnapshotRepository snapshotRepo;
    private final DatasetService datasetService;

    /** 每天 01:00 物化所有 report_template */
    @Scheduled(cron = "0 0 1 * * *")
    public void buildAllSnapshots() {
        templateRepo.findByStatus("PUBLISHED").forEach(this::buildSnapshot);
    }

    /** 物化单条(也供订阅触发用) */
    public void buildSnapshot(ReportTemplate tpl) {
        String period = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        DatasetQueryResultDto data = datasetService.query(tpl.getDatasetId(),
            DatasetQueryRequest.builder()
                .rowLimit(100_000)
                .params(tpl.getDefaultFilters())
                .build());
        ReportSnapshot snap = new ReportSnapshot();
        snap.setTemplateId(tpl.getId());
        snap.setPeriod(period);
        snap.setData(serialize(data));
        snap.setRowCount(data.getRowCount());
        snap.setStatus(ReportSnapshotStatus.READY);
        snap.setBuiltAt(Instant.now());
        snapshotRepo.save(snap);
    }
}
```

---

## 7. D7 数据安全(导出加密 + 水印 + TTL 24h)

| 安全点 | 实现 |
|---|---|
| **导出加密** | 文件名 + 路径用 UUID,不走可猜 ID;`/api/exports/{taskId}` 接口加 `Authorization` 校验 + 校验 task 归属 user |
| **用户水印** | PDF/PNG 模板里加 `<div class="watermark">${userId} · ${now}</div>`(半透明) |
| **TTL 24h** | `report_export.expires_at = now + 24h`(订阅分发) / 7d(主动导出,见 §2.2) |
| **审计日志** | 复用 `operation_log`,导出 / 下载 / 分享均写一条 |

---

## 8. 实现步骤(顺序执行,每步独立 commit)

### T-01 Maven 依赖 + application.yml 配置

- `pom.xml`:OpenPDF 1.3.39 + Commons CSV 1.10.0 + Playwright 1.40.0 + Thymeleaf starter
- `application.yml`:§2.2 配置
- 验证:`mvn -B compile` + `mvn -B test -Djacoco.skip=true` 不破坏

### T-02 5 个状态机 enum + 报表域实体 + Repository

- 实体:Dashboard / DashboardWidget / Dataset / DatasetField / ReportTemplate / ReportExport / ReportSnapshot / ReportSubscription(8 个)
- Repository:对应 8 个
- 状态机:已在 WP-M7-02 落地,无需新增
- 验证:`mvn -B compile`

### T-03 `DashboardService` + `DashboardController` + DTO

- DTO:`DashboardDto` / `DashboardCreateRequest` / `DashboardUpdateRequest` / `DashboardDataDto` / `WidgetDataDto` / `ShareLinkDto`
- 服务 + 控制器(8 端点)
- 单测:`DashboardServiceTest`(角色默认仪表盘 + 聚合拉取 5 case)
- 集成测试:`DashboardControllerIT`(@SpringBootTest + MockMvc)
- 验证:`mvn -B test -Dtest='Dashboard*' -Djacoco.skip=true` 全绿

### T-04 `DatasetService` + `DatasetController` + DTO

- DTO:`DatasetDto` / `DatasetDetailDto` / `DatasetQueryRequest` / `DatasetQueryResultDto` / `DatasetFieldDto`
- 服务 + 控制器(4 端点)
- **公式白名单 + 行数限制** 实现
- **D5 决策核心**:优先 `report_snapshot`,回退 `dataset.sql_template`
- 单测:`DatasetServiceTest`(白名单 5 case + 行数超限 413)
- 集成测试:`DatasetControllerIT`
- 验证:`mvn -B test -Dtest='Dataset*' -Djacoco.skip=true` 全绿

### T-05 4 个导出器(策略模式)

- `Exporter` 接口 + `PdfExporter` / `ExcelExporter` / `CsvExporter` / `PngExporter`
- 4 个 Thymeleaf 模板:`templates/report/{pdf,png}-default.html`
- 单测:`PdfExporterTest` / `ExcelExporterTest` / `CsvExporterTest` / `PngExporterTest`
- 验证:`mvn -B test -Dtest='*ExporterTest' -Djacoco.skip=true` 全绿
- **CI 注意**:PNG 导出需 Chromium,CI 需装

### T-06 `ReportExportService` + `ReportExportController` + 异步任务

- 同步 vs 异步逻辑
- `@Async("reportingTaskExecutor")` + 进度轮询 API
- D7 安全(加密 + 水印 + TTL 24h)
- 单测:`ReportExportServiceTest`(同步/异步/超时/重试 8 case)
- 集成测试:`ReportExportControllerIT`(端到端 4 格式导出)
- 验证:`mvn -B test -Dtest='ReportExport*' -Djacoco.skip=true` 全绿

### T-07 `ReportService` + `ReportController`

- DTO:`ReportDto` / `ReportTemplateDto` / `ReportRunDto` / `ReportRunResultDto`
- 服务 + 控制器(4 端点)
- 单测:`ReportServiceTest`
- 集成测试:`ReportControllerIT`
- 验证:`mvn -B test -Dtest='Report*' -Djacoco.skip=true` 全绿

### T-08 `ReportSubscriptionService` + `ReportSubscriptionController`

- DTO:`SubscribeRequest` / `SubscriptionDto`
- 服务 + 控制器(3 端点)
- **D6 决策**:Email + IM + 链接分享 3 通道
- **D6 失败重试**:3 次指数退避(1s/5s/25s)
- 单测:`ReportSubscriptionServiceTest`(3 通道 + 重试)
- 集成测试:`ReportSubscriptionControllerIT`
- 验证:`mvn -B test -Dtest='ReportSubscription*' -Djacoco.skip=true` 全绿

### T-09 `ReportSnapshotScheduler`(物化任务)

- `@Scheduled(cron = "0 0 1 * * *")` 扫描 `report_template.status='PUBLISHED'`
- 物化到 `report_snapshot` 表
- 单测:`ReportSnapshotSchedulerTest`(快照写入 + 状态机)
- 验证:`mvn -B test -Dtest='ReportSnapshot*' -Djacoco.skip=true` 全绿

### T-10 角色默认仪表盘解析器

- `RoleDashboardResolver` Bean(从 `system_config` 读 `role_dashboard.<ROLE>`)
- 缓存:`@Cacheable` 10 分钟
- 单测:`RoleDashboardResolverTest`(8 角色)
- 验证:`mvn -B test -Dtest='RoleDashboard*' -Djacoco.skip=true` 全绿

### T-11 报表域审计日志集成

- 复用 `operation_log`(已有)
- `DashboardService` / `ReportExportService` / `ReportSubscriptionService` 加 `@AuditLog` 切面
- 验证:写 `operation_log` 6 条 case

### T-12 端到端集成测试

- `ReportingE2ETest`(@SpringBootTest)
- 场景:登录 → 创建 dashboard → 加 widget → 拉取 data → 订阅 → 调度 → 导出 PDF → 邮件收到
- 验证:`mvn -B test -Dtest='ReportingE2E*' -Djacoco.skip=true` 全绿

### T-13 OpenAPI 导出

- `docs/specs/openapi/reporting.yaml`(springdoc 自动导出)
- 验证:`mvn -B springdoc:generate` + 链接到 spec
- docs-lint:OpenAPI 引用路径合法

### T-14 文档同步

- `WBS.md`:WP-M7-03 状态 → 🟡 active
- `STATUS.md`:M7-03 entry + last_head 同步
- `CHANGELOG.md`:M7-03 entry

---

## 9. 验收标准(DoD)

### 9.1 代码

- [ ] 5 控制器 + 5 服务 + 4 导出器 + 1 调度器 全部落地
- [ ] 12 API 端点全部实现 + 集成测试覆盖
- [ ] 4 格式导出全部跑通(单测 + 集成)
- [ ] D5 预聚合优先 + 实时跨表 JOIN 禁止
- [ ] D6 三通道分发 + 失败重试
- [ ] D7 加密 + 水印 + TTL 24h
- [ ] D8 异步导出 SLA < 60s

### 9.2 测试

- [ ] 14 个新测试类(7 服务 + 7 集成)
- [ ] `mvn -B test -Djacoco.skip=true` 全绿
- [ ] 端到端 `ReportingE2ETest` 通过
- [ ] 现有 `Initiation*` / `Timesheet*` / `Alert*` / `Finance*` 测试不破坏

### 9.3 文档

- [ ] `docs/specs/openapi/reporting.yaml` 导出
- [ ] `WBS.md` WP-M7-03 状态 → 🟡 active
- [ ] `STATUS.md` last_head 同步
- [ ] `CHANGELOG.md` M7-03 entry
- [ ] `make docs-lint` 全绿

### 9.4 门禁

- [ ] `mvn -B compile` 成功
- [ ] `mvn -B test -Djacoco.skip=true` 全绿
- [ ] OpenAPI 文件可被 `springdoc-openapi` 加载
- [ ] `make docs-lint` 0 error

---

## 10. 风险登记

| # | 风险 | 概率 | 影响 | 缓解 |
|:--:|---|:--:|:--:|---|
| R-M7-03-01 | OpenPDF 解析 HTML 能力有限(复杂布局不行) | 中 | 中 | T-05 Thymeleaf 模板用简单表格,避免 flex/grid;复杂图表走 PNG 导出 |
| R-M7-03-02 | Playwright 引入 CI 依赖(Chromium) | 中 | 中 | CI 镜像预装;提供 fallback:`XChart` 纯 Java 方案 |
| R-M7-03-03 | 异步任务堆积(> 100 任务)导致 OOM | 低 | 高 | §2.3 ThreadPoolTaskExecutor 队列 100,超出抛 `RejectedExecutionException` 走 503 |
| R-M7-03-04 | 公式注入(用户输入恶意 SQL 片段) | 中 | 高 | §4.2 公式白名单(`FormulaEvaluator.validate()`) + JPA 参数绑定(不用字符串拼接) |
| R-M7-03-05 | 跨表 JOIN 偷偷写进 sql_template | 中 | 高 | §4.2 `DatasetService` 检查 `dataset.sql_template` 含 JOIN/UNION 时拒绝 |
| R-M7-03-06 | 订阅 cron 表达式写错(死循环) | 中 | 中 | T-08 解析 cron 时校验 6 位 + 测试覆盖率 |
| R-M7-03-07 | 报表 PDF 中文乱码 | 中 | 中 | T-01 OpenPDF 嵌入中文字体(SimSun.ttf) |

---

## 11. 关联

- WBS:[`WP-M7-03 v5 核心功能`](../WBS.md#wp-m7-03-v5-核心功能ai-预测--智能推荐--异常--多租户--移动)(本 plan 落地)
- Spec:
  - [`reporting.md`](../specs/reporting.md) — 业务范围
  - [`reporting-api.md`](../specs/reporting-api.md) — API 契约(本 plan 实现的所有端点)
- Plan:
  - [`WP-M7-01 v5 立项评审`](../plans/2026-08-07-wp-m7-01-v5-scope-freeze.md)(前置依赖)
  - [`WP-M7-02 v5 数据模型增量`](../plans/2026-08-11-wp-m7-02-v5-data-model.md)(前置依赖 V7.0 schema)
  - `WP-M7-04 v5 可视化与 AI 看板`: 计划文件待定(本 plan 提供数据后端)
- ADR:[ADR-005 v5 立项范围与关键决策](../decisions/005-m7-v5-scope.md) D4(导出)/D5(数据集)/D6(订阅)/D7(安全)/D8(门禁)
- 关联 ADR:[ADR 004 · IM 平台回调接入推迟到 v5](../decisions/004-im-callback-deferred.md)(D6 IM 通道依赖)

---

## 评审记录

| 日期 | 评审人 | 意见 |
|---|---|---|
| 2026-08-11 | PMO | 通过 plan,等 V7.0 schema 落地后启动 |
| 2026-08-11 | 架构师 | 通过 §3-§5 控制器/服务/导出器设计,§5.4 PNG 建议用 Playwright 替代 wkhtmltoimage |
| 2026-08-11 | 后端 | 通过 §8 实现步骤,建议 T-06 异步任务加重试 + 死信队列 |
| 2026-08-11 | DBA | 通过 §5 D5 预聚合路径,§10 R-M7-03-05 跨表 JOIN 检查建议加 SQL lint |
| 2026-08-11 | QA | 通过 §9 验收标准,建议 T-12 E2E 用 docker-compose 起 PG |
| ⏳ D+7 | Sponsor | 待整合会议拍板 → 启动 |