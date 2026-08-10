package com.hex.projectgovern.module.finance.dto;

import com.hex.projectgovern.module.finance.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class FinanceDtos {

    // ============================================================
    // 合同
    // ============================================================

    public record ContractUpsertRequest(
            String code,
            String name,
            Long vendorId,
            String vendorName,
            Long projectId,
            BigDecimal amount,
            LocalDate signDate,
            LocalDate startDate,
            LocalDate endDate,
            Long ownerUserId,
            String remark
    ) {}

    public record ContractDto(
            Long id,
            String code,
            String name,
            Long vendorId,
            String vendorName,
            Long projectId,
            BigDecimal amount,
            BigDecimal paidAmount,
            BigDecimal remaining,
            Contract.Status status,
            LocalDate signDate,
            LocalDate startDate,
            LocalDate endDate,
            Long ownerUserId,
            String remark
    ) {
        public static ContractDto from(Contract c) {
            return new ContractDto(c.getId(), c.getCode(), c.getName(),
                    c.getVendorId(), c.getVendorName(), c.getProjectId(),
                    c.getAmount(), BigDecimal.ZERO, c.getAmount(),
                    c.getStatus(), c.getSignDate(), c.getStartDate(), c.getEndDate(),
                    c.getOwnerUserId(), c.getRemark());
        }

        public static ContractDto withBalance(Contract c, BigDecimal paid) {
            BigDecimal paid2 = paid != null ? paid : BigDecimal.ZERO;
            BigDecimal remaining = c.getAmount().subtract(paid2);
            return new ContractDto(c.getId(), c.getCode(), c.getName(),
                    c.getVendorId(), c.getVendorName(), c.getProjectId(),
                    c.getAmount(), paid2, remaining,
                    c.getStatus(), c.getSignDate(), c.getStartDate(), c.getEndDate(),
                    c.getOwnerUserId(), c.getRemark());
        }
    }

    // ============================================================
    // 发票
    // ============================================================

    public record InvoiceUpsertRequest(
            String code,
            Long contractId,
            Long vendorId,
            String vendorName,
            LocalDate invoiceDate,
            BigDecimal amount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            String fileUrl,
            String remark
    ) {}

    public record InvoiceDto(
            Long id,
            String code,
            Long contractId,
            Long vendorId,
            String vendorName,
            LocalDate invoiceDate,
            BigDecimal amount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            Invoice.Status status,
            Invoice.MatchStrategy matchStrategy,
            LocalDate matchedAt,
            Long matchedByUserId,
            String fileUrl,
            String remark
    ) {
        public static InvoiceDto from(Invoice i) {
            return new InvoiceDto(i.getId(), i.getCode(), i.getContractId(),
                    i.getVendorId(), i.getVendorName(), i.getInvoiceDate(),
                    i.getAmount(), i.getTaxAmount(), i.getTotalAmount(),
                    i.getStatus(), i.getMatchStrategy(), i.getMatchedAt(),
                    i.getMatchedByUserId(), i.getFileUrl(), i.getRemark());
        }
    }

    // ============================================================
    // 付款
    // ============================================================

    public record PaymentUpsertRequest(
            String code,
            Long invoiceId,
            LocalDate paymentDate,
            BigDecimal amount,
            String bankRef,
            Long approverUserId,
            String remark
    ) {}

    public record PaymentDto(
            Long id,
            String code,
            Long invoiceId,
            LocalDate paymentDate,
            BigDecimal amount,
            Payment.Status status,
            String bankRef,
            Long approverUserId,
            String remark
    ) {
        public static PaymentDto from(Payment p) {
            return new PaymentDto(p.getId(), p.getCode(), p.getInvoiceId(),
                    p.getPaymentDate(), p.getAmount(), p.getStatus(),
                    p.getBankRef(), p.getApproverUserId(), p.getRemark());
        }
    }

    // ============================================================
    // 成本项
    // ============================================================

    public record CostItemUpsertRequest(
            Long projectId,
            CostItem.Type type,
            BigDecimal amount,
            LocalDate date,
            Long contractId,
            Long invoiceId,
            Long paymentId,
            String remark
    ) {}

    public record CostItemDto(
            Long id,
            Long projectId,
            CostItem.Type type,
            BigDecimal amount,
            LocalDate date,
            CostItem.Source source,
            Long contractId,
            Long invoiceId,
            Long paymentId,
            Long userId,
            String remark
    ) {
        public static CostItemDto from(CostItem c) {
            return new CostItemDto(c.getId(), c.getProjectId(), c.getType(), c.getAmount(),
                    c.getDate(), c.getSource(),
                    c.getContractId(), c.getInvoiceId(), c.getPaymentId(),
                    c.getUserId(), c.getRemark());
        }
    }

    // ============================================================
    // 3-way match 对账 (V5.0 / WP-M4-03)
    // ============================================================

    public record ReconciliationDto(
            Long id,
            Long projectId,
            Long contractId,
            Long invoiceId,
            Long paymentId,
            Long costItemId,
            String period,
            BigDecimal contractAmount,
            BigDecimal invoiceAmount,
            BigDecimal paymentAmount,
            BigDecimal costAmount,
            BigDecimal diffAmount,
            String diffReason,
            CostReconciliation.MatchStatus matchStatus,
            java.time.Instant reconciledAt,
            Long reconciledBy
    ) {
        public static ReconciliationDto from(CostReconciliation r) {
            return new ReconciliationDto(r.getId(), r.getProjectId(),
                    r.getContractId(), r.getInvoiceId(),
                    r.getPaymentId(), r.getCostItemId(),
                    r.getPeriod(),
                    r.getContractAmount(), r.getInvoiceAmount(),
                    r.getPaymentAmount(), r.getCostAmount(),
                    r.getDiffAmount(), r.getDiffReason(),
                    r.getMatchStatus(), r.getReconciledAt(), r.getReconciledBy());
        }
    }

    /** 健康度聚合: 总数/绿灯/红/黄/待/差异总额 */
    public record ReconciliationHealth(
            Long total,
            Long matched,
            Long mismatch,
            Long partial,
            Long pending,
            BigDecimal totalDiff
    ) {
        public static ReconciliationHealth empty() {
            return new ReconciliationHealth(0L, 0L, 0L, 0L, 0L, BigDecimal.ZERO);
        }

        /** 绿灯率 = matched / total (0..1) */
        public double greenRate() {
            if (total == null || total == 0) return 1.0;
            return matched.doubleValue() / total.doubleValue();
        }
    }
}
