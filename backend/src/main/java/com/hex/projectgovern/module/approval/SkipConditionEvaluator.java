package com.hex.projectgovern.module.approval;

/**
 * 跳过条件求值器 (解析 step.skip_when 表达式)
 * 当前实现仅支持 KEY=VALUE 形式 (e.g. "amount<1000"),预留扩展
 */
public interface SkipConditionEvaluator {
    boolean shouldSkip(String skipWhen, String bizPayload);
}