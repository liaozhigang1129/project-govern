package com.hex.projectgovern.module.finance;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.audit.AuditLog;
import com.hex.projectgovern.common.security.SecurityUtils;
import com.hex.projectgovern.module.finance.dto.FinanceDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F3 财务闭环 — 合同/发票/付款/成本项 HTTP 入口 (P1)
 *
 *  - 合同 4 端点: list/get/create/activate/close
 *  - 发票 4 端点: list/get/create/match/autoMatch/reject
 *  - 付款 3 端点: list/get/create/confirm/reject
 *  - 成本项 4 端点: byProject/byContract/byInvoice/createManual
 *
 * 权限: PMO_ADMIN / ADMIN / FINANCE (F3 财务模块)
 */
@RestController
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final ContractService contractService;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final CostItemService costItemService;
    private final SecurityUtils securityUtils;
    private final ReconciliationService reconciliationService;

    // ============================================================
    // 合同
    // ============================================================

    @GetMapping("/contracts")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE','EXEC')")
    @Operation(summary = "合同列表 (F3.1)")
    public ApiResponse<List<ContractDto>> listContracts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long projectId) {
        if (projectId != null) {
            return ApiResponse.ok(contractService.listByProject(projectId));
        }
        if (status != null && !status.isBlank()) {
            return ApiResponse.ok(contractService.listByStatus(Contract.Status.valueOf(status.toUpperCase())));
        }
        return ApiResponse.ok(contractService.listAll());
    }

    @GetMapping("/contracts/{id}")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE','EXEC')")
    @Operation(summary = "合同详情 (含 paid/remaining)")
    public ApiResponse<ContractDto> getContract(@PathVariable Long id) {
        return ApiResponse.ok(contractService.get(id));
    }

    @PostMapping("/contracts")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE')")
    @AuditLog(module = "FINANCE", action = "CREATE_CONTRACT")
    @Operation(summary = "新建合同 (DRAFT)")
    public ApiResponse<ContractDto> createContract(@RequestBody ContractUpsertRequest req) {
        return ApiResponse.ok(contractService.create(req));
    }

    @PostMapping("/contracts/{id}/activate")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE')")
    @AuditLog(module = "FINANCE", action = "ACTIVATE_CONTRACT")
    @Operation(summary = "激活合同 (DRAFT → ACTIVE)")
    public ApiResponse<ContractDto> activateContract(@PathVariable Long id) {
        return ApiResponse.ok(contractService.activate(id));
    }

    @PostMapping("/contracts/{id}/close")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE')")
    @AuditLog(module = "FINANCE", action = "CLOSE_CONTRACT")
    @Operation(summary = "关闭合同 (ACTIVE → CLOSED)")
    public ApiResponse<ContractDto> closeContract(@PathVariable Long id) {
        return ApiResponse.ok(contractService.close(id));
    }

    // ============================================================
    // 发票
    // ============================================================

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE','EXEC')")
    @Operation(summary = "发票列表 (F3.2)")
    public ApiResponse<List<InvoiceDto>> listInvoices(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long contractId) {
        if (contractId != null) {
            return ApiResponse.ok(invoiceService.listByContract(contractId));
        }
        if (status != null && !status.isBlank()) {
            return ApiResponse.ok(invoiceService.listByStatus(Invoice.Status.valueOf(status.toUpperCase())));
        }
        return ApiResponse.ok(invoiceService.listAll());
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE','EXEC')")
    @Operation(summary = "发票详情")
    public ApiResponse<InvoiceDto> getInvoice(@PathVariable Long id) {
        return ApiResponse.ok(invoiceService.get(id));
    }

    @PostMapping("/invoices")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE')")
    @AuditLog(module = "FINANCE", action = "CREATE_INVOICE")
    @Operation(summary = "上传发票 (PENDING)")
    public ApiResponse<InvoiceDto> createInvoice(@RequestBody InvoiceUpsertRequest req) {
        return ApiResponse.ok(invoiceService.create(req));
    }

    @PostMapping("/invoices/{id}/match")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE')")
    @AuditLog(module = "FINANCE", action = "MATCH_INVOICE")
    @Operation(summary = "手动匹配 (PENDING → MATCHED, 校验 ≤ 合同余额)")
    public ApiResponse<InvoiceDto> matchInvoice(
            @PathVariable Long id,
            @RequestParam Long contractId) {
        return ApiResponse.ok(invoiceService.match(id, contractId, securityUtils.currentUserId()));
    }

    @PostMapping("/invoices/{id}/auto-match")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE')")
    @AuditLog(module = "FINANCE", action = "AUTO_MATCH_INVOICE")
    @Operation(summary = "AUTO 匹配 (按合同号启发式)")
    public ApiResponse<InvoiceDto> autoMatchInvoice(
            @PathVariable Long id,
            @RequestParam Long contractId) {
        return ApiResponse.ok(invoiceService.autoMatch(id, contractId, securityUtils.currentUserId()));
    }

    @PostMapping("/invoices/{id}/reject")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE')")
    @AuditLog(module = "FINANCE", action = "REJECT_INVOICE")
    @Operation(summary = "拒绝发票 (PENDING/MATCHED → REJECTED)")
    public ApiResponse<InvoiceDto> rejectInvoice(@PathVariable Long id) {
        return ApiResponse.ok(invoiceService.reject(id));
    }

    // ============================================================
    // 付款
    // ============================================================

    @GetMapping("/payments")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE','EXEC')")
    @Operation(summary = "付款列表 (F3.3)")
    public ApiResponse<List<PaymentDto>> listPayments(
            @RequestParam(required = false) Long invoiceId,
            @RequestParam(required = false) String status) {
        if (invoiceId != null) {
            return ApiResponse.ok(paymentService.listByInvoice(invoiceId));
        }
        return ApiResponse.ok(paymentService.listAll());
    }

    @GetMapping("/payments/{id}")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE','EXEC')")
    @Operation(summary = "付款详情")
    public ApiResponse<PaymentDto> getPayment(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.get(id));
    }

    @PostMapping("/payments")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE')")
    @AuditLog(module = "FINANCE", action = "CREATE_PAYMENT")
    @Operation(summary = "录入付款 (PENDING, 校验 invoice=MATCHED)")
    public ApiResponse<PaymentDto> createPayment(@RequestBody PaymentUpsertRequest req) {
        return ApiResponse.ok(paymentService.create(req));
    }

    @PostMapping("/payments/{id}/confirm")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE')")
    @AuditLog(module = "FINANCE", action = "CONFIRM_PAYMENT")
    @Operation(summary = "确认付款 (PENDING → CONFIRMED, 联动 invoice=PAID + 写 cost_item)")
    public ApiResponse<PaymentDto> confirmPayment(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.confirm(id, securityUtils.currentUserId()));
    }

    @PostMapping("/payments/{id}/reject")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE')")
    @AuditLog(module = "FINANCE", action = "REJECT_PAYMENT")
    @Operation(summary = "拒绝付款 (PENDING → REJECTED)")
    public ApiResponse<PaymentDto> rejectPayment(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.reject(id));
    }

    // ============================================================
    // 成本项
    // ============================================================

    @GetMapping("/cost-items/by-project/{projectId}")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE','EXEC','PM')")
    @Operation(summary = "项目成本项 (F3.4: 财务/PMO 视角)")
    public ApiResponse<List<CostItemDto>> listByProject(@PathVariable Long projectId) {
        return ApiResponse.ok(costItemService.listByProject(projectId));
    }

    @GetMapping("/cost-items/by-contract/{contractId}")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE','EXEC')")
    @Operation(summary = "合同下成本项 (审计追溯)")
    public ApiResponse<List<CostItemDto>> listByContract(@PathVariable Long contractId) {
        return ApiResponse.ok(costItemService.listByContract(contractId));
    }

    @GetMapping("/cost-items/by-invoice/{invoiceId}")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE','EXEC')")
    @Operation(summary = "发票下成本项 (审计追溯)")
    public ApiResponse<List<CostItemDto>> listByInvoice(@PathVariable Long invoiceId) {
        return ApiResponse.ok(costItemService.listByInvoice(invoiceId));
    }

    @PostMapping("/cost-items")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE')")
    @AuditLog(module = "FINANCE", action = "CREATE_COST_ITEM")
    @Operation(summary = "手工录入成本项 (差旅/服务费等)")
    public ApiResponse<CostItemDto> createCostItem(@RequestBody CostItemUpsertRequest req) {
        return ApiResponse.ok(costItemService.createManual(req));
    }

    // ============================================================
    // 3-way match 对账 (V5.0 / WP-M4-03 / T-05)
    // ============================================================

    /**
     * 对账列表 (分页 + 多条件)
     * - page 默认 0, size 默认 20, 最大 100
     * - 排序: reconciledAt DESC
     */
    @GetMapping("/reconciliation")
    @PreAuthorize("hasAnyRole(\'PMO_ADMIN\',\'ADMIN\',\'FINANCE\',\'EXEC\')")
    @Operation(summary = "财务-成本对账列表 (V5.0)")
    public ApiResponse<Map<String, Object>> listReconciliation(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size <= 0 || size > 100) size = 20;
        if (page < 0) page = 0;

        CostReconciliation.MatchStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = CostReconciliation.MatchStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ApiResponse.fail(400,
                    "Invalid status: " + status + " (allowed: "
                    + java.util.Arrays.toString(CostReconciliation.MatchStatus.values()) + ")");
            }
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "reconciledAt"));
        var pg = reconciliationService.search(projectId, statusEnum, from, to, pageable);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", pg.getTotalElements());
        data.put("page", pg.getNumber());
        data.put("size", pg.getSize());
        data.put("totalPages", pg.getTotalPages());
        data.put("items", pg.getContent());
        return ApiResponse.ok(data);
    }

    /** 单条对账详情 */
    @GetMapping("/reconciliation/{id}")
    @PreAuthorize("hasAnyRole(\'PMO_ADMIN\',\'ADMIN\',\'FINANCE\',\'EXEC\')")
    @Operation(summary = "对账详情 (V5.0)")
    public ApiResponse<ReconciliationDto> getReconciliation(@PathVariable Long id) {
        ReconciliationDto dto = reconciliationService.get(id);
        if (dto == null) return ApiResponse.fail(404, "Reconciliation not found: " + id);
        return ApiResponse.ok(dto);
    }

    /**
     * 重跑单条对账 (实际重算整 project)
     */
    @PostMapping("/reconciliation/retry/{id}")
    @PreAuthorize("hasAnyRole(\'PMO_ADMIN\',\'ADMIN\',\'FINANCE\')")
    @AuditLog(module = "FINANCE", action = "RETRY_RECONCILIATION")
    @Operation(summary = "重跑单条对账 (重算整 project)")
    public ApiResponse<ReconciliationDto> retryReconciliation(@PathVariable Long id) {
        Long operatorUserId = securityUtils.currentUserId();
        return ApiResponse.ok(reconciliationService.retry(id, operatorUserId));
    }

    /** 对账健康度聚合 */
    @GetMapping("/reconciliation/health")
    @PreAuthorize("hasAnyRole(\'PMO_ADMIN\',\'ADMIN\',\'FINANCE\',\'EXEC\')")
    @Operation(summary = "对账健康度聚合 (V5.0)")
    public ApiResponse<Map<String, Object>> reconciliationHealth(
            @RequestParam(required = false) Long projectId) {
        var h = reconciliationService.health(projectId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectId", projectId);
        data.put("total", h.total());
        data.put("matched", h.matched());
        data.put("mismatch", h.mismatch());
        data.put("partial", h.partial());
        data.put("pending", h.pending());
        data.put("totalDiff", h.totalDiff());
        data.put("greenRate", h.greenRate());
        return ApiResponse.ok(data);
    }
}
