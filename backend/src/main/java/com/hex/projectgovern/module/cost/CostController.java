package com.hex.projectgovern.module.cost;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.audit.AuditLog;
import com.hex.projectgovern.common.security.RequireRoles;
import com.hex.projectgovern.common.security.SecurityUtils;
import com.hex.projectgovern.module.cost.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * P0-A.1 成本引擎 HTTP 入口 — F1 工时→成本核算
 *
 * <p>URL 设计:
 * <ul>
 *   <li>{@code /api/cost/hourly-rates/...} — 费率 CRUD + CSV 上传</li>
 *   <li>{@code /api/cost/role-defaults/...} — 6 角色档默认价</li>
 *   <li>{@code /api/cost/user/{userId}?month=2026-06[&date=2026-06-15]} — F1 主验收</li>
 * </ul>
 */
@Tag(name = "Cost Engine", description = "P0-A.1 工时→成本引擎 (F1)")
@RestController
@RequestMapping("/cost")
@RequiredArgsConstructor
public class CostController {

    private final HourlyRateService rateService;
    private final CostEngineService engine;
    private final CostDimensionService dimension;
    private final SecurityUtils securityUtils;

    // ============================================================
    // 角色档默认价 (RoleCostDefault)
    // ============================================================

    @GetMapping("/role-defaults")
    @RequireRoles.Read
    @Operation(summary = "6 角色档默认价列表 (财务可调)")
    public ApiResponse<List<RoleCostDefaultItem>> listRoleDefaults() {
        return ApiResponse.ok(rateService.listRoleDefaults());
    }

    @PutMapping("/role-defaults")
    @RequireRoles.Admin
    @AuditLog(module = "COST", action = "UPDATE_ROLE_DEFAULT", extractResourceId = false)
    @Operation(summary = "更新角色档默认价 (限 PMO_ADMIN/ADMIN)")
    public ApiResponse<RoleCostDefaultItem> updateRoleDefault(@Valid @RequestBody RoleCostDefaultUpdateRequest req) {
        return ApiResponse.ok(rateService.updateRoleDefault(req));
    }

    // ============================================================
    // HourlyRate CRUD
    // ============================================================

    @GetMapping("/hourly-rates")
    @RequireRoles.Read
    @Operation(summary = "费率列表 (可按 userId 过滤)")
    public ApiResponse<List<HourlyRateItem>> list(@RequestParam(required = false) Long userId) {
        return ApiResponse.ok(rateService.list(userId));
    }

    @GetMapping("/hourly-rates/{id}")
    @RequireRoles.Read
    @Operation(summary = "费率详情")
    public ApiResponse<HourlyRateItem> get(@PathVariable Long id) {
        return ApiResponse.ok(rateService.get(id));
    }

    @PostMapping("/hourly-rates")
    @RequireRoles.Admin
    @AuditLog(module = "COST", action = "CREATE_RATE")
    @Operation(summary = "新建费率行 (限 PMO_ADMIN/ADMIN)")
    public ApiResponse<HourlyRateItem> create(@Valid @RequestBody HourlyRateUpsertRequest req) {
        return ApiResponse.ok(rateService.create(req, securityUtils.currentUserId()));
    }

    @PutMapping("/hourly-rates/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "COST", action = "UPDATE_RATE")
    @Operation(summary = "更新费率行 (限 PMO_ADMIN/ADMIN)")
    public ApiResponse<HourlyRateItem> update(@PathVariable Long id, @Valid @RequestBody HourlyRateUpsertRequest req) {
        return ApiResponse.ok(rateService.update(id, req, securityUtils.currentUserId()));
    }

    @PostMapping("/hourly-rates/{id}/close")
    @RequireRoles.Admin
    @AuditLog(module = "COST", action = "CLOSE_RATE")
    @Operation(summary = "软关费率行 (endMonth=指定月末,限 PMO_ADMIN/ADMIN)")
    public ApiResponse<HourlyRateItem> close(@PathVariable Long id, @RequestParam String atMonth) {
        return ApiResponse.ok(rateService.close(id, YearMonth.parse(atMonth), securityUtils.currentUserId()));
    }

    @DeleteMapping("/hourly-rates/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "COST", action = "DELETE_RATE")
    @Operation(summary = "删除未生效的费率行 (已生效需 close,限 PMO_ADMIN/ADMIN)")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        rateService.delete(id, securityUtils.currentUserId());
        return ApiResponse.ok(null);
    }

    // ============================================================
    // CSV 上传 / 模板下载
    // ============================================================

    @GetMapping(path = "/hourly-rates/csv-template", produces = "text/csv;charset=UTF-8")
    @RequireRoles.Admin
    @Operation(summary = "下载 CSV 模板")
    public ResponseEntity<String> csvTemplate() {
        String body = rateService.exportCsvTemplate();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=cost_rates_template.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }

    @PostMapping(path = "/hourly-rates/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRoles.Admin
    @AuditLog(module = "COST", action = "IMPORT_CSV")
    @Operation(summary = "上传 CSV 批量 upsert 费率 (限 PMO_ADMIN/ADMIN)")
    public ApiResponse<HourlyRateService.CsvRowResult> importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            return ApiResponse.ok(rateService.importCsv(reader, securityUtils.currentUserId()));
        }
    }

    // ============================================================
    // F1 主验收 — 成本查询
    // ============================================================

    @GetMapping("/user/{userId}")
    @RequireRoles.Read
    @Operation(summary = "用户月度成本 (F1 主验收: month=2026-06)")
    public ApiResponse<UserMonthCostResponse> userMonthCost(
            @PathVariable Long userId,
            @RequestParam String month) {
        return ApiResponse.ok(engine.computeUserMonthCost(userId, YearMonth.parse(month)));
    }

    @GetMapping("/user/{userId}/day")
    @RequireRoles.Read
    @Operation(summary = "用户单日成本 (辅助: date=2026-06-15)")
    public ApiResponse<UserDayCostResponse> userDayCost(
            @PathVariable Long userId,
            @RequestParam String date) {
        return ApiResponse.ok(engine.computeUserDayCost(userId, LocalDate.parse(date)));
    }

    // ============================================================
    // T3 F2 — 多维成本核算 (复用 V4.1 视图)
    // ============================================================

    @GetMapping("/dimension")
    @RequireRoles.Read
    @Operation(summary = "多维成本核算 (T3: PROJECT/PHASE/DEPT, F2 价值核心)")
    public ApiResponse<com.hex.projectgovern.module.cost.dto.CostDtos.CostDimensionResponse> dimension(
            @RequestParam(defaultValue = "PROJECT") String dim,
            @RequestParam(required = false) String month) {
        return ApiResponse.ok(dimension.query(dim, month));
    }
}