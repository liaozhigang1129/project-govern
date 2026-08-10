package com.hex.projectgovern.module.finance;

import com.hex.projectgovern.module.finance.dto.FinanceDtos.ReconciliationHealth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReconciliationService 单测 — 覆盖 4 种对账状态判定 (WP-M4-03 / T-02)
 *
 * 这里只测纯函数 classify(Bucket) — 不需要 mock repository / event publisher。
 * 集成场景 (reconcileByProject / retry / search) 留给后续 Service-IT。
 */
class ReconciliationServiceTest {

    private final ReconciliationService svc = new ReconciliationService(
            null, null, null, null, null, null);

    private ReconciliationService.Bucket bucket(Long c, Long i, Long p, Long ci, String period) {
        return new ReconciliationService.Bucket(c, i, p, ci, period);
    }

    private ReconciliationService.Bucket amounts(
            Long c, Long i, Long p, Long ci, String period,
            String contract, String invoice, String payment, String cost) {
        ReconciliationService.Bucket b = bucket(c, i, p, ci, period);
        b.contractAmount = contract == null ? BigDecimal.ZERO : new BigDecimal(contract);
        b.invoiceAmount  = invoice  == null ? BigDecimal.ZERO : new BigDecimal(invoice);
        b.paymentAmount  = payment  == null ? BigDecimal.ZERO : new BigDecimal(payment);
        b.costAmount     = cost     == null ? BigDecimal.ZERO : new BigDecimal(cost);
        return b;
    }

    // ============================================================
    // MATCHED
    // ============================================================

    @Test
    @DisplayName("MATCHED: 4 维金额全部 ¥10000.00, 差异 0 → MATCHED")
    void matched_allEqual() {
        var b = amounts(1L, 2L, 3L, 4L, "2026-08",
                "10000.00", "10000.00", "10000.00", "10000.00");
        var d = svc.classify(b);
        assertThat(d.status()).isEqualTo(CostReconciliation.MatchStatus.MATCHED);
        assertThat(d.diff()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("MATCHED: 差异 ¥0.005 (在容差 ±¥0.01 内) → MATCHED")
    void matched_withinTolerance() {
        var b = amounts(1L, null, null, null, "2026-08",
                "10000.00", "10000.005", "10000.00", "10000.00");
        var d = svc.classify(b);
        assertThat(d.status()).isEqualTo(CostReconciliation.MatchStatus.MATCHED);
        assertThat(d.diff()).isEqualByComparingTo("0.01"); // rounded HALF_UP
    }

    // ============================================================
    // PARTIAL
    // ============================================================

    @Test
    @DisplayName("PARTIAL: 差异 ¥50, 在容差与阈值之间 → PARTIAL")
    void partial_50_diff() {
        var b = amounts(1L, null, null, null, "2026-08",
                "10000.00", "9950.00", "10000.00", "10000.00");
        var d = svc.classify(b);
        assertThat(d.status()).isEqualTo(CostReconciliation.MatchStatus.PARTIAL);
        assertThat(d.diff()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("PARTIAL: 边界 ¥100.00 等于阈值, 不大于 → PARTIAL")
    void partial_atThreshold() {
        var b = amounts(1L, null, null, null, "2026-08",
                "10000.00", "9900.00", "10000.00", "10000.00");
        var d = svc.classify(b);
        // diff = max - min = 10000 - 9900 = 100; 严格 > 100 才 MISMATCH
        assertThat(d.status()).isEqualTo(CostReconciliation.MatchStatus.PARTIAL);
    }

    // ============================================================
    // MISMATCH
    // ============================================================

    @Test
    @DisplayName("MISMATCH: 差异 ¥500 > 阈值 ¥100 → MISMATCH")
    void mismatch_500_diff() {
        var b = amounts(1L, null, null, null, "2026-08",
                "10000.00", "9500.00", "10000.00", "10000.00");
        var d = svc.classify(b);
        assertThat(d.status()).isEqualTo(CostReconciliation.MatchStatus.MISMATCH);
        assertThat(d.diff()).isEqualByComparingTo("500.00");
        assertThat(d.reason()).contains("合同 vs 开票");
    }

    @Test
    @DisplayName("MISMATCH: 边界 ¥100.01 > 阈值 → MISMATCH")
    void mismatch_justOverThreshold() {
        var b = amounts(1L, null, null, null, "2026-08",
                "10000.00", "9899.99", "10000.00", "10000.00");
        var d = svc.classify(b);
        assertThat(d.status()).isEqualTo(CostReconciliation.MatchStatus.MISMATCH);
    }

    // ============================================================
    // PENDING
    // ============================================================

    @Test
    @DisplayName("PENDING: 4 个 id 全空 + 4 个金额全 0 → PENDING")
    void pending_noDimension() {
        var b = bucket(null, null, null, null, "2026-08");
        var d = svc.classify(b);
        assertThat(d.status()).isEqualTo(CostReconciliation.MatchStatus.PENDING);
    }

    @Test
    @DisplayName("PENDING: 只有 cost_item (无合同/开票/实付), 金额非 0 → PARTIAL (有金额但缺维度)")
    void pending_partialDimension() {
        var b = amounts(null, null, null, 99L, "2026-08",
                "0", "0", "0", "5000.00");
        var d = svc.classify(b);
        // max = 5000, min = 0, diff = 5000 > 100 → MISMATCH (这里 cost 是真实入账但无对应合同)
        // 这个 case 实际归 MISMATCH, 不应 PENDING — 仅当 4 维全空才是 PENDING
        assertThat(d.status()).isEqualTo(CostReconciliation.MatchStatus.MISMATCH);
    }

    // ============================================================
    // 容差常量 + key 生成
    // ============================================================

    @Test
    @DisplayName("key: null 字段用 _ 占位")
    void key_withNulls() {
        assertThat(ReconciliationService.key(null, 1L, null, 2L, "2026-08"))
                .isEqualTo("_|1|_|2|2026-08");
    }

    @Test
    @DisplayName("常量: TOLERANCE = ¥0.01, MISMATCH_THRESHOLD = ¥100")
    void constants() {
        assertThat(ReconciliationService.TOLERANCE).isEqualByComparingTo("0.01");
        assertThat(ReconciliationService.MISMATCH_THRESHOLD).isEqualByComparingTo("100.00");
    }

    // ============================================================
    // ReconciliationHealth 工具
    // ============================================================

    @Test
    @DisplayName("Health.greenRate: total=0 → 1.0 (无数据视为全绿)")
    void greenRate_empty() {
        assertThat(ReconciliationHealth.empty().greenRate()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Health.greenRate: matched=8, total=10 → 0.8")
    void greenRate_80pct() {
        var h = new ReconciliationHealth(10L, 8L, 1L, 1L, 0L, BigDecimal.ZERO);
        assertThat(h.greenRate()).isEqualTo(0.8);
    }
}
