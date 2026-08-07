package com.company.pmo.module.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * P5-LLM 润色配置 (绑定 application.yml pmo.llm.*)
 *
 * 关键: 默认 enabled=false → 零侵入;生产 env 打开 (PMO_LLM_ENABLED=true)
 *
 * 限流:
 *  - timeout-seconds: 单次请求超时(规则引擎不能被 LLM 拖垮,默认 8s)
 *  - max-suggestions: 送 LLM 的 Top-N 信号(避免 prompt 过长 + token 钱)
 *
 * 供应商:
 *  - DEEPSEEK (推荐: 国产便宜 + 中文好)
 *  - OPENAI
 *  - CLAUDE
 */
@ConfigurationProperties(prefix = "pmo.llm")
public class LlmProperties {

    private boolean enabled = false;
    private String provider = "DEEPSEEK";
    private int timeoutSeconds = 8;
    private int maxSuggestions = 4;

    private Provider deepseek = new Provider();
    private Provider openai = new Provider();
    private Provider claude = new Provider();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public int getMaxSuggestions() { return maxSuggestions; }
    public void setMaxSuggestions(int maxSuggestions) { this.maxSuggestions = maxSuggestions; }

    public Provider getDeepseek() { return deepseek; }
    public void setDeepseek(Provider deepseek) { this.deepseek = deepseek; }

    public Provider getOpenai() { return openai; }
    public void setOpenai(Provider openai) { this.openai = openai; }

    public Provider getClaude() { return claude; }
    public void setClaude(Provider claude) { this.claude = claude; }

    public Provider activeProvider() {
        return switch (provider.toUpperCase()) {
            case "OPENAI" -> openai;
            case "CLAUDE" -> claude;
            default -> deepseek;
        };
    }

    public static class Provider {
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }
}
