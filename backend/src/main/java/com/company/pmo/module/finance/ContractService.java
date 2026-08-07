package com.company.pmo.module.finance;

import com.company.pmo.common.exception.BusinessException;
import com.company.pmo.module.finance.dto.FinanceDtos.ContractDto;
import com.company.pmo.module.finance.dto.FinanceDtos.ContractUpsertRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * F3 合同服务 (P1)
 *
 *  - create: 新建合同 (DRAFT)
 *  - activate: DRAFT → ACTIVE
 *  - close: ACTIVE → CLOSED
 *  - balance: 余额 = amount - sumPaidAmount
 *  - listByStatus / listByProject
 */
@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository repo;

    @Transactional
    public ContractDto create(ContractUpsertRequest req) {
        if (req.code() == null || req.code().isBlank()) {
            throw new BusinessException("INVALID_CODE", "合同号必填");
        }
        repo.findByCodeAndDeletedFalse(req.code()).ifPresent(c -> {
            throw new BusinessException("CONTRACT_EXISTS", "合同号已存在: " + req.code());
        });
        if (req.amount() == null || req.amount().signum() < 0) {
            throw new BusinessException("INVALID_AMOUNT", "合同金额必须 >= 0");
        }

        Contract c = new Contract();
        c.setCode(req.code());
        c.setName(req.name());
        c.setVendorId(req.vendorId());
        c.setVendorName(req.vendorName());
        c.setProjectId(req.projectId());
        c.setAmount(req.amount());
        c.setStatus(Contract.Status.DRAFT);
        c.setSignDate(req.signDate());
        c.setStartDate(req.startDate());
        c.setEndDate(req.endDate());
        c.setOwnerUserId(req.ownerUserId());
        c.setRemark(req.remark());

        return ContractDto.from(repo.save(c));
    }

    @Transactional
    public ContractDto activate(Long id) {
        Contract c = mustGet(id);
        if (c.getStatus() != Contract.Status.DRAFT) {
            throw new BusinessException("INVALID_STATUS", "仅 DRAFT 状态可激活");
        }
        c.setStatus(Contract.Status.ACTIVE);
        return ContractDto.from(repo.save(c));
    }

    @Transactional
    public ContractDto close(Long id) {
        Contract c = mustGet(id);
        if (c.getStatus() != Contract.Status.ACTIVE) {
            throw new BusinessException("INVALID_STATUS", "仅 ACTIVE 状态可关闭");
        }
        c.setStatus(Contract.Status.CLOSED);
        return ContractDto.from(repo.save(c));
    }

    @Transactional(readOnly = true)
    public ContractDto get(Long id) {
        return ContractDto.withBalance(mustGet(id), repo.sumPaidAmount(id));
    }

    @Transactional(readOnly = true)
    public List<ContractDto> listAll() {
        return repo.findAllByDeletedFalseOrderByIdDesc().stream()
                .map(c -> ContractDto.withBalance(c, repo.sumPaidAmount(c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ContractDto> listByStatus(Contract.Status status) {
        return repo.findAllByStatusAndDeletedFalseOrderByIdDesc(status).stream()
                .map(c -> ContractDto.withBalance(c, repo.sumPaidAmount(c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ContractDto> listByProject(Long projectId) {
        return repo.findAllByProjectIdAndDeletedFalseOrderByIdDesc(projectId).stream()
                .map(c -> ContractDto.withBalance(c, repo.sumPaidAmount(c.getId())))
                .toList();
    }

    Contract mustGet(Long id) {
        return repo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("CONTRACT_NOT_FOUND", "合同不存在 id=" + id));
    }

    BigDecimal remaining(Long contractId) {
        Contract c = mustGet(contractId);
        return c.getAmount().subtract(repo.sumPaidAmount(contractId));
    }
}
