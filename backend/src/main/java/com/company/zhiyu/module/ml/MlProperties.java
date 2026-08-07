package com.company.zhiyu.module.ml;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * P5-ML 推理配置 (绑定 application.yml pmo.ml.*)
 *
 * 默认 enabled=false → 零侵入;生产 env 打开 (PMO_ML_ENABLED=true)
 */
@ConfigurationProperties(prefix = "pmo.ml")
public class MlProperties {

    private boolean enabled = false;
    private String modelPath = "./models/milestone_lgbm_v1.pkl";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getModelPath() { return modelPath; }
    public void setModelPath(String modelPath) { this.modelPath = modelPath; }
}
