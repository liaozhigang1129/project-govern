package com.company.pmo.module.finance;

import com.company.pmo.module.finance.dto.FinanceDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * F3 成本项服务 (P1)
 *
 *  - recordFromPayment: Payment CONFIRMED 联动写 1 条 cost_item
 *  - recordLabor: 工时成本自动归集 (F1 联动) - 后续 V4.3 实现
 *  - queryByProject: 项目成本账
 *  - queryByContract/Invoice/Payment: 审计追溯
 */
@Service
@RequiredArgsConstructor
public class CostItemService {

    private final CostItemRepository repo;

    /**
     * Payment → cost_item (PURCHASE 类型)
     * 触发: PaymentService.confirm()
     */
    @Transactional
    public CostItemDto recordFromPayment(Long projectId, Payment payment, Invoice invoice, Contract contract) {
        CostItem ci = new CostItem();
        ci.setProjectId(projectId);
        ci.setType(CostItem.Type.PURCHASE);
        ci.setAmount(payment.getAmount());
        ci.setDate(payment.getPaymentDate() != null ? payment.getPaymentDate() : LocalDate.now());
        ci.setSource(CostItem.Source.INVOICE);
        ci.setContractId(contract.getId());
        ci.setInvoiceId(invoice.getId());
        ci.setPaymentId(payment.getId());
        ci.setRemark("合同 %s 付款 %s".formatted(contract.getCode(), payment.getCode()));
        return CostItemDto.from(repo.save(ci));
    }

    /**
     * 工时成本自动归集 (F1 → F3 联动)
     * 后续 V4.3: 在 WorkloadService 计算后调用本方法写 cost_item
     */
    @Transactional
    public CostItemDto recordLabor(Long projectId, Long userId, BigDecimal amount, LocalDate date) {
        CostItem ci = new CostItem();
        ci.setProjectId(projectId);
        ci.setType(CostItem.Type.LABOR);
        ci.setAmount(amount);
        ci.setDate(date);
        ci.setSource(CostItem.Source.HOURS_AUTO);
        ci.setUserId(userId);
        ci.setRemark("工时自动归集 (F1 联动)");
        return CostItemDto.from(repo.save(ci));
    }

    @Transactional
    public CostItemDto createManual(CostItemUpsertRequest req) {
        if (req.amount() == null || req.amount().signum() <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        if (req.date() == null) {
            throw new IllegalArgumentException("date is required");
        }
        CostItem ci = new CostItem();
        ci.setProjectId(req.projectId());
        ci.setType(req.type());
        ci.setAmount(req.amount());
        ci.setDate(req.date());
        ci.setSource(CostItem.Source.MANUAL);
        ci.setContractId(req.contractId());
        ci.setInvoiceId(req.invoiceId());
        ci.setPaymentId(req.paymentId());
        ci.setRemark(req.remark());
        return CostItemDto.from(repo.save(ci));
    }

    @Transactional(readOnly = true)
    public List<CostItemDto> listByProject(Long projectId) {
        return repo.findAllByProjectIdAndDeletedFalseOrderByDateDesc(projectId).stream()
                .map(CostItemDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CostItemDto> listByContract(Long contractId) {
        return repo.findAllByContractIdAndDeletedFalseOrderByDateDesc(contractId).stream()
                .map(CostItemDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CostItemDto> listByInvoice(Long invoiceId) {
        return repo.findAllByInvoiceIdAndDeletedFalseOrderByDateDesc(invoiceId).stream()
                .map(CostItemDto::from).toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal sumByProject(Long projectId) {
        return repo.sumByProjectAndDateRange(projectId, LocalDate.of(1970, 1, 1), LocalDate.of(2999, 12, 31));
    }
}
