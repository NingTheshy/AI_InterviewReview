package com.interview.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 结构化输出配置属性
 * <p>
 * 控制 LLM 调用的重试策略、JSON 修复和响应清理行为。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.structured-output")
public class StructuredOutputProperties {

    /**
     * 最大重试次数
     */
    private int maxRetryAttempts = 2;

    /**
     * 重试时是否附加严格 JSON 输出指令
     */
    private boolean retryWithStrictInstruction = true;

    /**
     * 是否启用本地 JSON 修复
     */
    private boolean enableJsonRepair = true;

    /**
     * 是否启用响应清理（去除 markdown 代码块）
     */
    private boolean enableResponseCleanup = true;
}
