package com.company.pmo.module.finance;

import com.company.pmo.common.exception.BusinessException;
import com.company.pmo.module.finance.dto.FinanceDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * F3 付款服务 (P1)
 *
 *  - create: 录入付款 (PENDING)
 *  - confirm: PENDING → CONFIRMED + 自动 markPaid(invoice) + 写 cost_item
 *  - reject: PENDING → REJECTED
 *  - 余额校验: 付款金额 ≤ 合同余额 (穿透到 InvoiceService)
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository repo;
    private final InvoiceService invoiceService;
    private final ContractService contractService;
    private final CostItemService costItemService;

    @Transactional
    public PaymentDto create(PaymentUpsertRequest req) {
        if (req.code() == null || req.code().isBlank()) {
            throw new BusinessException("INVALID_CODE", "付款单号必填");
        }
        repo.findByCodeAndDeletedFalse(req.code()).ifPresent(p -> {
            throw new BusinessException("PAYMENT_EXISTS", "付款单号已存在: " + req.code());
        });
        if (req.amount() == null || req.amount().signum() <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "付款金额必须 > 0");
        }
        // 校验发票存在且 MATCHED
        Invoice inv = invoiceService.mustGet(req.invoiceId());
        if (inv.getStatus() != Invoice.Status.MATCHED) {
            throw new BusinessException("INVOICE_NOT_MATCHED",
                "仅 MATCHED 发票可付款, 当前=" + inv.getStatus());
        }

        Payment p = new Payment();
        p.setCode(req.code());
        p.setInvoiceId(req.invoiceId());
        p.setPaymentDate(req.paymentDate());
        p.setAmount(req.amount());
        p.setStatus(Payment.Status.PENDING);
        p.setBankRef(req.bankRef());
        p.setApproverUserId(req.approverUserId());
        p.setRemark(req.remark());

        return PaymentDto.from(repo.save(p));
    }

    @Transactional
    public PaymentDto confirm(Long id, Long operatorUserId) {
        Payment p = mustGet(id);
        if (p.getStatus() != Payment.Status.PENDING) {
            throw new BusinessException("INVALID_STATUS", "仅 PENDING 可确认");
        }

        // 二次校验: 合同余额
        Invoice inv = invoiceService.mustGet(p.getInvoiceId());
        if (inv.getContractId() != null) {
            BigDecimal remaining = contractService.remaining(inv.getContractId());
            // 余额 = amount - sumConfirmedPayment; 新加的 PENDING 也算
            BigDecimal pendingTotal = repo
                .findAllByInvoiceIdAndDeletedFalseOrderByIdDesc(p.getInvoiceId())
                .stream()
                .filter(x -> x.getStatus() == Payment.Status.PENDING && !x.getId().equals(id))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalAfter = remaining.add(p.getAmount()).subtract(pendingTotal);
            if (p.getAmount().compareTo(remaining) > 0) {
                throw new BusinessException("AMOUNT_EXCEEDS_BALANCE",
                    "付款金额 %s 超过合同余额 %s".formatted(p.getAmount(), remaining));
            }
            // 简化: 仅校验单个不超, 实际工程可加"总待付"判断
        }

        p.setStatus(Payment.Status.CONFIRMED);
        p.setApproverUserId(operatorUserId);
        Payment saved = repo.save(p);

        // 联动: invoice → PAID
        invoiceService.markPaid(p.getInvoiceId());

        // 联动: 写 cost_item (项目成本归集)
        if (inv.getContractId() != null) {
            Contract c = contractService.mustGet(inv.getContractId());
            if (c.getProjectId() != null) {
                costItemService.recordFromPayment(c.getProjectId(), saved, inv, c);
            }
        }

        return PaymentDto.from(saved);
    }

    @Transactional
    public PaymentDto reject(Long id) {
        Payment p = mustGet(id);
        if (p.getStatus() != Payment.Status.PENDING) {
            throw new BusinessException("INVALID_STATUS", "仅 PENDING 可 REJECTED");
        }
        p.setStatus(Payment.Status.REJECTED);
        return PaymentDto.from(repo.save(p));
    }

    @Transactional(readOnly = true)
    public PaymentDto get(Long id) {
        return PaymentDto.from(mustGet(id));
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> listAll() {
        return repo.findAllByDeletedFalseOrderByIdDesc().stream()
                .map(PaymentDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> listByInvoice(Long invoiceId) {
        return repo.findAllByInvoiceIdAndDeletedFalseOrderByIdDesc(invoiceId).stream()
                .map(PaymentDto::from).toList();
    }

    Payment mustGet(Long id) {
        return repo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "付款不存在 id=" + id));
    }
}
