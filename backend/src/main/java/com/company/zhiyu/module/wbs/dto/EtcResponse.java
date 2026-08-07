package com.company.zhiyu.module.wbs.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** ETC 完工尚需成本 + WBS 工时燃尽数据 */
public class EtcResponse {

    /** 项目 ID */
    public Long projectId;

    /** 计划预算 BAC */
    public BigDecimal bac;

    /** 完工估算 EAC (EAC = AC + ETC) */
    public BigDecimal eac;

    /** 完工尚需成本 ETC = EAC - AC */
    public BigDecimal etc;

    /** 完工偏差 VAC = BAC - EAC */
    public BigDecimal vac;

    /** 计划总工时 (WBS plan_hours 之和) */
    public BigDecimal planHours;

    /** 已实际消耗工时 */
    public BigDecimal actualHours;

    /** 剩余工时 = planHours - actualHours */
    public BigDecimal remainingHours;

    /** 燃尽率 (actualHours / planHours) 0~1 */
    public BigDecimal burndownRatio;

    /** 计划开始 / 结束 */
    public Instant planStart;
    public Instant planEnd;

    /** 数据时点 */
    public Instant computedAt;

    /** 按父阶段分布 (含 ETC 占比) */
    public List<EtcByStage> byStage;

    public static class EtcByStage {
        public Long wbsId;
        public String wbsCode;
        public String name;
        public BigDecimal planHours;
        public BigDecimal actualHours;
        public BigDecimal remainingHours;
        public int progressPct;
        public String status;
    }
}
