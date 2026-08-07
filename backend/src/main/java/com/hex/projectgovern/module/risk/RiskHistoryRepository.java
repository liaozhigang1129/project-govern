package com.hex.projectgovern.module.risk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiskHistoryRepository extends JpaRepository<RiskHistory, Long> {

    /** 某风险的全部历史, 按时间倒序 */
    List<RiskHistory> findByRiskIdOrderByCreatedAtDescIdDesc(Long riskId);

    /** 某项目所有风险的历史(给面板用) */
    List<RiskHistory> findByRiskIdInOrderByCreatedAtDescIdDesc(List<Long> riskIds);
}
