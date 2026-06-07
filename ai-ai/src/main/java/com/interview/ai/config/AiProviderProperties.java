package com.interview.ai.config;

import lombok.Data;

/**
 * AI 提供商配置属性
 * <p>
 * 定义单个 AI 提供商的配置信息：API Key、端点、模型名称。
 * </p>
 */
@Data
public class AiProviderProperties {

    private String apiKey;
    private String apiEndpoint;
    private String modelName;
    /** LLM 调用超时时间（秒），默认 300（5 分钟） */
    private int timeoutSeconds = 300;
}
