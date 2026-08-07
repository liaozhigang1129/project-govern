package com.company.zhiyu.module.wbs.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 工时燃尽图 (按日) 数据 */
public class BurndownResponse {

    /** 项目 ID */
    public Long projectId;

    /** 计划开始 / 结束 */
    public LocalDate planStart;
    public LocalDate planEnd;

    /** 总工时 (BAC 等价) */
    public BigDecimal totalHours;

    /** 理想燃尽线 (按时长均匀消耗) */
    public List<BurndownPoint> idealLine;

    /** 实际燃尽线 (按日 actual_hours 累加) */
    public List<BurndownPoint> actualLine;

    /** 每天一条 (横轴) */
    public static class BurndownPoint {
        public LocalDate date;
        /** 第几天 (从 1 开始) */
        public int dayIndex;
        /** 累计工时 (理想/实际) */
        public BigDecimal cumulativeHours;
        /** 剩余工时 */
        public BigDecimal remainingHours;
    }
}
