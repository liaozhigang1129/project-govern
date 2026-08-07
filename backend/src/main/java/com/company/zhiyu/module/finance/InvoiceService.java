package com.company.zhiyu.module.finance;

import com.company.zhiyu.common.exception.BusinessException;
import com.company.zhiyu.module.finance.dto.FinanceDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * F3 发票服务 (P1)
 *
 *  - create: 上传发票 (PENDING)
 *  - match: PENDING → MATCHED (绑定合同)
 *  - autoMatch: 启发式 AUTO 匹配 (按 vendor + 金额)
 *  - reject: PENDING → REJECTED
 *  - 状态机: PENDING → MATCHED → PAID (Payment 触发)
 */
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository repo;
    private final ContractService contractService;

    @Transactional
    public InvoiceDto create(InvoiceUpsertRequest req) {
        if (req.code() == null || req.code().isBlank()) {
            throw new BusinessException("INVALID_CODE", "发票号必填");
        }
        repo.findByCodeAndDeletedFalse(req.code()).ifPresent(i -> {
            throw new BusinessException("INVOICE_EXISTS", "发票号已存在: " + req.code());
        });
        if (req.totalAmount() == null || req.totalAmount().signum() <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "价税合计必须 > 0");
        }

        Invoice i = new Invoice();
        i.setCode(req.code());
        i.setContractId(req.contractId());
        i.setVendorId(req.vendorId());
        i.setVendorName(req.vendorName());
        i.setInvoiceDate(req.invoiceDate() != null ? req.invoiceDate() : LocalDate.now());
        i.setAmount(req.amount() != null ? req.amount() : req.totalAmount());
        i.setTaxAmount(req.taxAmount());
        i.setTotalAmount(req.totalAmount());
        i.setStatus(Invoice.Status.PENDING);
        i.setFileUrl(req.fileUrl());
        i.setRemark(req.remark());

        return InvoiceDto.from(repo.save(i));
    }

    /**
     * 手动匹配: PENDING → MATCHED
     *  校验: 合同存在 + 发票金额 ≤ 合同余额
     */
    @Transactional
    public InvoiceDto match(Long invoiceId, Long contractId, Long operatorUserId) {
        Invoice i = mustGet(invoiceId);
        if (i.getStatus() != Invoice.Status.PENDING) {
            throw new BusinessException("INVALID_STATUS", "仅 PENDING 状态可匹配");
        }
        Contract c = contractService.mustGet(contractId);

        BigDecimal remaining = contractService.remaining(contractId);
        if (i.getTotalAmount().compareTo(remaining) > 0) {
            throw new BusinessException("AMOUNT_EXCEEDS_BALANCE",
                "发票金额 %s 超过合同余额 %s".formatted(i.getTotalAmount(), remaining));
        }

        i.setContractId(contractId);
        i.setStatus(Invoice.Status.MATCHED);
        i.setMatchStrategy(Invoice.MatchStrategy.MANUAL);
        i.setMatchedAt(LocalDate.now());
        i.setMatchedByUserId(operatorUserId);

        // 同步: 合同的 vendorId (首次关联时)
        if (c.getVendorId() != null && i.getVendorId() == null) {
            i.setVendorId(c.getVendorId());
        }

        return InvoiceDto.from(repo.save(i));
    }

    /**
     * AUTO 匹配: 启发式 = 选同 vendor 的 ACTIVE 合同中金额最接近的
     * 简化版: 直接用入参 contractId, 但标记 strategy=AUTO
     */
    @Transactional
    public InvoiceDto autoMatch(Long invoiceId, Long contractId, Long operatorUserId) {
        Invoice i = mustGet(invoiceId);
        Contract c = contractService.mustGet(contractId);
        if (c.getStatus() != Contract.Status.ACTIVE) {
            throw new BusinessException("CONTRACT_NOT_ACTIVE", "仅 ACTIVE 合同可 AUTO 匹配");
        }
        BigDecimal remaining = contractService.remaining(contractId);
        if (i.getTotalAmount().compareTo(remaining) > 0) {
            throw new BusinessException("AMOUNT_EXCEEDS_BALANCE",
                "发票金额 %s 超过合同余额 %s".formatted(i.getTotalAmount(), remaining));
        }

        i.setContractId(contractId);
        i.setStatus(Invoice.Status.MATCHED);
        i.setMatchStrategy(Invoice.MatchStrategy.AUTO);
        i.setMatchedAt(LocalDate.now());
        i.setMatchedByUserId(operatorUserId);

        return InvoiceDto.from(repo.save(i));
    }

    @Transactional
    public InvoiceDto reject(Long invoiceId) {
        Invoice i = mustGet(invoiceId);
        if (i.getStatus() == Invoice.Status.PAID) {
            throw new BusinessException("CANNOT_REJECT_PAID", "已付款的发票不可 REJECTED");
        }
        i.setStatus(Invoice.Status.REJECTED);
        return InvoiceDto.from(repo.save(i));
    }

    /** Payment 触发: MATCHED → PAID */
    @Transactional
    public void markPaid(Long invoiceId) {
        Invoice i = mustGet(invoiceId);
        if (i.getStatus() != Invoice.Status.MATCHED) {
            throw new BusinessException("INVALID_STATUS", "仅 MATCHED 可 PAID");
        }
        i.setStatus(Invoice.Status.PAID);
        repo.save(i);
    }

    @Transactional(readOnly = true)
    public InvoiceDto get(Long id) {
        return InvoiceDto.from(mustGet(id));
    }

    @Transactional(readOnly = true)
    public List<InvoiceDto> listAll() {
        return repo.findAllByDeletedFalseOrderByIdDesc().stream()
                .map(InvoiceDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<InvoiceDto> listByContract(Long contractId) {
        return repo.findAllByContractIdAndDeletedFalseOrderByIdDesc(contractId).stream()
                .map(InvoiceDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<InvoiceDto> listByStatus(Invoice.Status status) {
        return repo.findAllByStatusAndDeletedFalseOrderByIdDesc(status).stream()
                .map(InvoiceDto::from).toList();
    }

    Invoice mustGet(Long id) {
        return repo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("INVOICE_NOT_FOUND", "发票不存在 id=" + id));
    }
}
