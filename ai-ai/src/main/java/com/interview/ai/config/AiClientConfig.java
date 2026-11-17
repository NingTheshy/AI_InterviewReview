package com.interview.ai.config;

import com.interview.ai.client.*;
import com.interview.ai.factory.AiClientFactory;
import com.interview.ai.model.dashscope.DashScopeFileTranscriptionModel;
import com.interview.ai.service.AsrClient;
import com.interview.ai.service.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI 客户端配置类
 * <p>
 * 根据 application.yml 中的配置创建 AI 客户端实例，并注册到 {@link AiClientFactory}。
 * </p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AiClientConfig {

    private final AiProperties aiProperties;
    private final AiClientFactory aiClientFactory;
    private final DashScopeAsrProperties dashScopeAsrProperties;

    /** 检查 API Key 是否缺失或仍为示例值；示例值绝不能注册为可用客户端。 */
    private boolean isPlaceholderApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return true;
        }
        String lower = apiKey.toLowerCase();
        return lower.startsWith("your-") || lower.startsWith("your_") || lower.equals("your_api_key");
    }

    // ==================== ASR 客户端 ====================

    /**
     * 注册 DashScope 完整文件客户端。
     *
     * <p>只有配置真实密钥时才注册，避免默认占位值导致应用启动后出现不可预期的远程调用失败。</p>
     */
    @Bean
    public AsrClient dashscopeAsrClient() {
        AiProviderProperties props = aiProperties.getProvider("dashscope");
        log.debug("dashscope ASR 配置检查: configured={}", props != null);
        if (props == null || isPlaceholderApiKey(props.getApiKey())) {
            log.warn("未配置 dashscope ASR，跳过注册");
            return null;
        }
        DashScopeFileTranscriptionModel model = new DashScopeFileTranscriptionModel(props, dashScopeAsrProperties);
        AsrClient client = new SpringAiDashScopeAsrClient(model, props.getModelName());
        aiClientFactory.registerAsrClient("dashscope", client);
        return client;
    }

    // ==================== LLM 客户端 ====================

    /**
     * 注册基于 Spring AI 的 DeepSeek 客户端。
     *
     * <p>超时时间从 provider 配置读取，使长文本评分能使用较长超时而不影响接口契约。</p>
     */
    @Bean
    public LlmClient deepseekLlmClient() {
        AiProviderProperties props = aiProperties.getProvider("deepseek");
        if (props == null || isPlaceholderApiKey(props.getApiKey())) {
            log.warn("未配置 deepseek LLM，跳过注册");
            return null;
        }
        SpringAiDeepSeekLlmClient client = new SpringAiDeepSeekLlmClient(props);
        client.setCallTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));
        log.info("DeepSeek LLM 超时设置: {}s", props.getTimeoutSeconds());
        aiClientFactory.registerLlmClient("deepseek", client);
        return client;
    }

}
