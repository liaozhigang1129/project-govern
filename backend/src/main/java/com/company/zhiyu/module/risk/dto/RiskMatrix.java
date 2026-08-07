package com.company.zhiyu.module.risk.dto;

import java.util.List;

/**
 * 风险矩阵 (5x5 probability × impact 热力图) — P4 看板组件用。
 * <p>每个 cell 含该 (probability, impact) 组合的活跃风险数 + 风险列表。
 */
public final class RiskMatrix {

    private RiskMatrix() {}

    /** 单格数据 */
    public record Cell(int probability, int impact, long count, List<RiskResponse> risks) {}

    /** 5x5 矩阵 */
    public record Matrix(List<Cell> cells) {
        public Cell get(int p, int i) {
            return cells.stream()
                    .filter(c -> c.probability() == p && c.impact() == i)
                    .findFirst()
                    .orElse(new Cell(p, i, 0, List.of()));
        }
    }
}
