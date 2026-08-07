package com.hex.projectgovern.module.ml;

import java.util.Map;

/**
 * ML 预测服务 (P5-智能预警 ML 增强)
 *
 * 模型: LightGBM 多分类 (INFO / WARNING / CRITICAL)
 * 训练: scripts/ml/milestone_lgbm.py
 * 加载: joblib pkl 文件 (Java 端用 lib 库 或 REST 调 Python service)
 *
 * 实现策略:
 *  - 接口 + Feature flag
 *  - 默认实现 = 调 Python ml-service REST (轻量 + 灵活)
 *  - 后续可换 Java 内嵌 LightGBM (DJL / ONNX Runtime)
 */
public interface MlPredictor {

    /**
     * 单条预测
     *
     * @param features 特征字典 (key=FEATURE_COLS 中任一)
     * @return 预测结果: severity + confidence + proba
     */
    Prediction predict(Map<String, Double> features);

    /**
     * 预测结果
     */
    record Prediction(
            String severity,                     // INFO / WARNING / CRITICAL
            double confidence,                   // 0-1
            Map<String, Double> proba,           // {INFO:0.1, WARNING:0.7, CRITICAL:0.2}
            String modelVersion                  // 训练时间戳
    ) {}
}
