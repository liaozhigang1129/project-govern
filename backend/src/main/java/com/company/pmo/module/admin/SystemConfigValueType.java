package com.company.pmo.module.admin;

/** 值的 JSON Schema 提示 + 编辑器控件 */
public enum SystemConfigValueType {
    STRING,    // 普通文本
    NUMBER,    // 整数 / 浮点
    BOOLEAN,   // 开关
    JSON,      // 多行文本 (按 JSON 校验)
    ENUM       // 枚举 (用 options 字段)
}
