package com.hex.projectgovern.module.risk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RiskResponseRepository extends JpaRepository<RiskResponse, Long> {

    /** 某风险的所有应对行动, 按创建时间升序 */
    List<RiskResponse> findByRiskIdAndDeletedFalseOrderByIdAsc(Long riskId);

    /** 某项目所有应对行动(给面板用) */
    List<RiskResponse> findByRiskIdInAndDeletedFalse(List<Long> riskIds);
}
