package com.hex.projectgovern.module.finance;

import com.hex.projectgovern.module.finance.dto.FinanceDtos.ReconciliationDto;
import com.hex.projectgovern.module.finance.dto.FinanceDtos.ReconciliationHealth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * F3: 财务-成本 3-way match 对账服务 (V5.0 / WP-M4-03)
 *
 * <p>核心思路:
 * <ol>
 *   <li>拉取 project 下所有 contract / invoice(MATCHED+PAID) / payment(CONFIRMED) / cost_item</li>
 *   <li>按 (project_id, contract_id, invoice_id, payment_id, cost_item_id, period) 聚合
 *       — period 取 cost_item.date 或 invoice.invoice_date 月份,无则用 now</li>
 *   <li>对每个聚合桶:
 *     <ul>
 *       <li>无任何维度 → PENDING</li>
 *       <li>所有金额在容差内 → MATCHED</li>
 *       <li>差异 > ¥100 阈值 → MISMATCH + diffReason</li>
 *       <li>其余 → PARTIAL</li>
 *     </ul>
 *   </li>
 *   <li>幂等:用 uk 查找现有记录,有则 update,无则 insert</li>
 * </ol>
 *
 * <p><b>对账口径 v1.0</b>(待 R-001 评审确认):
 * <ul>
 *   <li>金额容差 = ¥0.01</li>
 *   <li>差异告警阈值 = ¥100</li>
 *   <li>签约额 vs 开票价税合计 vs 实付合计 vs 入账成本合计 = 4 维比对</li>
 * </ul>
 *
 * @since V5.0 / WP-M4-03
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    /** 容差 = 1 分 */
    public static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    /** 差异告警阈值 = 100 元 */
    public static final BigDecimal MISMATCH_THRESHOLD = new BigDecimal("100.00");

    private final CostReconciliationRepository repo;
    private final ContractRepository contractRepo;
    private final InvoiceRepository invoiceRepo;
    private final PaymentRepository paymentRepo;
    private final CostItemRepository costItemRepo;
    private final ApplicationEventPublisher events;

    // ============================================================
    // 对账入口
    // ============================================================

    /**
     * 对账整个项目:按 4 维聚合,逐桶写入 cost_reconciliation。
     * @return 影响的对账行数
     */
    @Transactional
    public int reconcileByProject(Long projectId, Long operatorUserId) {
        if (projectId == null) throw new IllegalArgumentException("projectId required");

        // 1) 拉数据
        List<Contract> contracts = contractRepo.findAllByProjectIdAndDeletedFalseOrderByIdDesc(projectId);
        List<Invoice> invoices = new ArrayList<>();
        for (Contract c : contracts) {
            invoices.addAll(invoiceRepo.findAllByContractIdAndDeletedFalseOrderByIdDesc(c.getId()));
        }
        // 还要拉无合同关联的发票(若有 projectId 直接走的)
        // 这里假设所有 invoice 都有 contract_id,简化;若有 project_id 维度的扩展可加
        List<CostItem> costItems = costItemRepo.findAllByProjectIdAndDeletedFalseOrderByDateDesc(projectId);

        // 2) 构造聚合桶: key = (contractId, invoiceId, paymentId, costItemId, period)
        Map<String, Bucket> buckets = new HashMap<>();

        // 2.1) 合同桶 (period = 签约月)
        for (Contract c : contracts) {
            String period = c.getSignDate() != null
                    ? CostReconciliation.periodOf(c.getSignDate())
                    : CostReconciliation.periodOf(LocalDate.now());
            String key = key(c.getId(), null, null, null, period);
            buckets.computeIfAbsent(key, k -> new Bucket(c.getId(), null, null, null, period))
                   .contractAmount = nullSafe(c.getAmount());
        }

        // 2.2) 发票桶
        for (Invoice i : invoices) {
            String period = CostReconciliation.periodOf(i.getInvoiceDate());
            String key = key(i.getContractId(), i.getId(), null, null, period);
            buckets.computeIfAbsent(key, k -> new Bucket(i.getContractId(), i.getId(), null, null, period))
                   .invoiceAmount = nullSafe(i.getTotalAmount());
        }

        // 2.3) 付款桶 (CONFIRMED 才入账)
        for (Invoice i : invoices) {
            List<Payment> pays = paymentRepo.findAllByInvoiceIdAndDeletedFalseOrderByIdDesc(i.getId());
            for (Payment p : pays) {
                if (p.getStatus() != Payment.Status.CONFIRMED) continue;
                String period = CostReconciliation.periodOf(p.getPaymentDate());
                String key = key(i.getContractId(), i.getId(), p.getId(), null, period);
                buckets.computeIfAbsent(key, k -> new Bucket(i.getContractId(), i.getId(), p.getId(), null, period))
                       .paymentAmount = nullSafe(p.getAmount());
            }
        }

        // 2.4) 成本项桶
        for (CostItem ci : costItems) {
            String period = CostReconciliation.periodOf(ci.getDate());
            String key = key(ci.getContractId(), ci.getInvoiceId(), ci.getPaymentId(), ci.getId(), period);
            buckets.computeIfAbsent(key, k -> new Bucket(ci.getContractId(), ci.getInvoiceId(), ci.getPaymentId(), ci.getId(), period))
                   .costAmount = nullSafe(ci.getAmount());
        }

        // 3) 逐桶 upsert
        int count = 0;
        Instant now = Instant.now();
        for (Bucket b : buckets.values()) {
            Optional<CostReconciliation> existing = repo
                .findByProjectIdAndContractIdAndInvoiceIdAndPaymentIdAndCostItemIdAndPeriodAndDeletedFalse(
                    projectId, b.contractId, b.invoiceId, b.paymentId, b.costItemId, b.period);

            CostReconciliation r = existing.orElseGet(CostReconciliation::new);
            r.setProjectId(projectId);
            r.setContractId(b.contractId);
            r.setInvoiceId(b.invoiceId);
            r.setPaymentId(b.paymentId);
            r.setCostItemId(b.costItemId);
            r.setPeriod(b.period);
            r.setContractAmount(b.contractAmount);
            r.setInvoiceAmount(b.invoiceAmount);
            r.setPaymentAmount(b.paymentAmount);
            r.setCostAmount(b.costAmount);

            MatchDecision dec = classify(b);
            r.setMatchStatus(dec.status);
            r.setDiffAmount(dec.diff);
            r.setDiffReason(dec.reason);
            r.setReconciledAt(now);
            r.setReconciledBy(operatorUserId);

            repo.save(r);
            count++;
        }

        log.info("[Reconciliation] project={} reconciled buckets={}", projectId, count);

        // 4) 触发告警(差异 > 阈值时)
        if (buckets.values().stream().anyMatch(b -> classify(b).diff.compareTo(MISMATCH_THRESHOLD) > 0)) {
            events.publishEvent(new CostDiffDetectedEvent(projectId, count));
        }

        return count;
    }

    // ============================================================
    // 对账算法 — 公开便于单测
    // ============================================================

    /** 分类决策 */
    public record MatchDecision(BigDecimal diff, CostReconciliation.MatchStatus status, String reason) {}

    public MatchDecision classify(Bucket b) {
        BigDecimal max = max4(b.contractAmount, b.invoiceAmount, b.paymentAmount, b.costAmount);
        BigDecimal min = min4(b.contractAmount, b.invoiceAmount, b.paymentAmount, b.costAmount);
        BigDecimal diff = max.subtract(min).setScale(2, RoundingMode.HALF_UP);

        // 全 0 → PENDING (无任何维度)
        if (max.signum() == 0 && b.contractId == null && b.invoiceId == null
                && b.paymentId == null && b.costItemId == null) {
            return new MatchDecision(BigDecimal.ZERO,
                    CostReconciliation.MatchStatus.PENDING, "无对账维度");
        }

        // 容差内 = MATCHED
        if (diff.compareTo(TOLERANCE) <= 0) {
            return new MatchDecision(diff, CostReconciliation.MatchStatus.MATCHED,
                    "4 维金额在容差内(±¥0.01)");
        }

        // 差异 > 阈值 = MISMATCH
        if (diff.compareTo(MISMATCH_THRESHOLD) > 0) {
            String reason = buildMismatchReason(b);
            return new MatchDecision(diff, CostReconciliation.MatchStatus.MISMATCH, reason);
        }

        // 其余 = PARTIAL
        return new MatchDecision(diff, CostReconciliation.MatchStatus.PARTIAL,
                "金额差异 ¥%s, 在容差与阈值之间,待财务复核".formatted(diff.toPlainString()));
    }

    private String buildMismatchReason(Bucket b) {
        StringBuilder sb = new StringBuilder("差异 ¥");
        sb.append(b.contractAmount.subtract(b.invoiceAmount).abs().setScale(2, RoundingMode.HALF_UP));
        sb.append(" (合同 vs 开票); ");
        sb.append("¥").append(b.invoiceAmount.subtract(b.paymentAmount).abs().setScale(2, RoundingMode.HALF_UP));
        sb.append(" (开票 vs 实付); ");
        sb.append("¥").append(b.paymentAmount.subtract(b.costAmount).abs().setScale(2, RoundingMode.HALF_UP));
        sb.append(" (实付 vs 入账)");
        return sb.toString();
    }

    // ============================================================
    // 查询
    // ============================================================

    @Transactional(readOnly = true)
    public ReconciliationHealth health(Long projectId) {
        ReconciliationHealth h = repo.health(projectId);
        return h == null ? ReconciliationHealth.empty() : h;
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ReconciliationDto> search(
            Long projectId, CostReconciliation.MatchStatus status,
            Instant from, Instant to, org.springframework.data.domain.Pageable pageable) {
        return repo.search(projectId, status, from, to, pageable).map(ReconciliationDto::from);
    }

    @Transactional(readOnly = true
)
    public ReconciliationDto get(Long id) {
        return repo.findByIdAndDeletedFalse(id).map(ReconciliationDto::from).orElse(null);
    }

    /** 重跑单条:把现有行的 4 维数据按当前业务数据重算 */
    @Transactional
    public ReconciliationDto retry(Long id, Long operatorUserId) {
        CostReconciliation r = repo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new com.hex.projectgovern.common.exception.BusinessException(
                        "RECONCILIATION_NOT_FOUND", "对账记录不存在: " + id));
        // 简化:把整 project 重跑一遍(对账粒度本就是 project 维度)
        reconcileByProject(r.getProjectId(), operatorUserId);
        return get(id);
    }

    // ============================================================
    // 工具
    // ============================================================

    /** 对账聚合桶 */
    public static class Bucket {
        Long contractId, invoiceId, paymentId, costItemId;
        String period;
        BigDecimal contractAmount = BigDecimal.ZERO;
        BigDecimal invoiceAmount  = BigDecimal.ZERO;
        BigDecimal paymentAmount  = BigDecimal.ZERO;
        BigDecimal costAmount     = BigDecimal.ZERO;

        public Bucket(Long c, Long i, Long p, Long ci, String period) {
            this.contractId = c; this.invoiceId = i; this.paymentId = p;
            this.costItemId = ci; this.period = period;
        }
    }

    public static String key(Long c, Long i, Long p, Long ci, String period) {
        return (c == null ? "_" : c) + "|"
             + (i == null ? "_" : i) + "|"
             + (p == null ? "_" : p) + "|"
             + (ci == null ? "_" : ci) + "|" + period;
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal max4(BigDecimal a, BigDecimal b, BigDecimal c, BigDecimal d) {
        return max(a, max(b, max(c, d)));
    }

    private static BigDecimal min4(BigDecimal a, BigDecimal b, BigDecimal c, BigDecimal d) {
        return min(a, min(b, min(c, d)));
    }

    private static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    // ============================================================
    // 事件 — T-03 用
    // ============================================================

    /** 检测到成本差异时发布(由 alert 模块订阅) */
    public record CostDiffDetectedEvent(Long projectId, int affectedBuckets) {}

    /**
     * 从 invoiceId 反查 projectId (invoice → contract → project)。
     * 供 ReconciliationEventListener 调用。
     * 返回 null = invoice 不存在 / 无合同 / 合同无 projectId。
     */
    @Transactional(readOnly = true)
    public Long resolveProjectByInvoice(Long invoiceId) {
        var inv = invoiceRepo.findByIdAndDeletedFalse(invoiceId);
        if (inv.isEmpty() || inv.get().getContractId() == null) return null;
        var c = contractRepo.findByIdAndDeletedFalse(inv.get().getContractId());
        return c.map(Contract::getProjectId).orElse(null);
    }
}
