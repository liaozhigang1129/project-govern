package com.hex.projectgovern.module.finance.event;

import com.hex.projectgovern.module.finance.ReconciliationService;
import com.hex.projectgovern.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ConcurrentHashMap;

/**
 * F3: 财务-成本对账事件监听器 (V5.0 / WP-M4-03 / T-03)
 *
 * 订阅 3 类事件,各自触发对账:
 * <ul>
 *   <li>{@link InvoiceConfirmedEvent}  →  对账 invoice.contractId 对应 project</li>
 *   <li>{@link PaymentConfirmedEvent}  →  对账 invoice.contractId 对应 project</li>
 *   <li>{@link CostMonthlySettledEvent} →  对账 projectId</li>
 * </ul>
 *
 * 失败隔离:
 * <ul>
 *   <li>{@code @Async} — 不阻塞业务事务</li>
 *   <li>try-catch — 失败仅 warn log,主业务不感知</li>
 *   <li>5 分钟幂等键 — (projectId + source + period) 短期去重,避免重复触发</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationEventListener {

    private final ReconciliationService reconciliationService;

    /** 保留可注入位置 (后续: CostMonthlySettledEvent 需要 costItemService) */
    // 注: invoiceService / paymentService 通过 reconciliationService.resolveProjectByInvoice() 间接调用
    //     以避免 ReconciliationEventListener 与业务 Service 的直接耦合

    /** 5 分钟幂等键缓存:key → expiry epoch ms */
    private final ConcurrentHashMap<String, Long> idempotencyKeys = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_WINDOW_MS = 5 * 60 * 1000L;

    // ============================================================
    // 发票入账
    // ============================================================

    @Async
    @EventListener
    public void onInvoiceConfirmed(InvoiceConfirmedEvent event) {
        runIdempotently("invoice:" + event.invoiceId(), () -> {
            Long projectId = resolveProjectByInvoice(event.invoiceId());
            if (projectId == null) {
                log.warn("[Reconciliation] InvoiceConfirmedEvent invoiceId={} 无合同/无 projectId, 跳过",
                        event.invoiceId());
                return;
            }
            int n = reconciliationService.reconcileByProject(projectId, event.operatorUserId());
            log.info("[Reconciliation] InvoiceConfirmedEvent invoiceId={} projectId={} reconciled={}",
                    event.invoiceId(), projectId, n);
        });
    }

    // ============================================================
    // 付款确认
    // ============================================================

    @Async
    @EventListener
    public void onPaymentConfirmed(PaymentConfirmedEvent event) {
        runIdempotently("payment:" + event.paymentId(), () -> {
            Long projectId = resolveProjectByInvoice(event.invoiceId());
            if (projectId == null) {
                log.warn("[Reconciliation] PaymentConfirmedEvent invoiceId={} 无 projectId, 跳过",
                        event.invoiceId());
                return;
            }
            int n = reconciliationService.reconcileByProject(projectId, event.operatorUserId());
            log.info("[Reconciliation] PaymentConfirmedEvent paymentId={} projectId={} reconciled={}",
                    event.paymentId(), projectId, n);
        });
    }

    // ============================================================
    // 成本月结
    // ============================================================

    @Async
    @EventListener
    @Transactional
    public void onCostMonthlySettled(CostMonthlySettledEvent event) {
        String key = "settle:" + event.projectId() + ":" + event.period();
        runIdempotently(key, () -> {
            int n = reconciliationService.reconcileByProject(event.projectId(), event.operatorUserId());
            log.info("[Reconciliation] CostMonthlySettledEvent projectId={} period={} reconciled={}",
                    event.projectId(), event.period(), n);
        });
    }

    // ============================================================
    // 工具
    // ============================================================

    /**
     * 从 invoiceId 反查 projectId (invoice → contract → project)。
     * 抛错时返回 null(失败隔离)。
     */
    private Long resolveProjectByInvoice(Long invoiceId) {
        try {
            return reconciliationService.resolveProjectByInvoice(invoiceId);
        } catch (Exception e) {
            log.warn("[Reconciliation] resolveProjectByInvoice failed: {}", e.getMessage());
            return null;
        }
    }

    /** 幂等运行:同 key 在窗口期内只跑一次 */
    private void runIdempotently(String key, Runnable task) {
        long now = System.currentTimeMillis();
        // 清理过期键(简单策略:每次 put 前清理一个)
        idempotencyKeys.entrySet().removeIf(e -> e.getValue() < now);

        Long prev = idempotencyKeys.putIfAbsent(key, now + IDEMPOTENCY_WINDOW_MS);
        if (prev != null && prev > now) {
            log.debug("[Reconciliation] skip idempotent key={}", key);
            return;
        }
        try {
            task.run();
        } catch (Exception e) {
            // 失败隔离:warn log,不抛
            log.warn("[Reconciliation] task failed key={} err={}", key, e.getMessage(), e);
            // 出错则移除幂等键,允许重试
            idempotencyKeys.remove(key);
        }
    }
}
