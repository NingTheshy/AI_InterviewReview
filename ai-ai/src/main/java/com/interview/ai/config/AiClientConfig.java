package com.interview.ai.config;

import com.interview.ai.client.*;
import com.interview.ai.factory.AiClientFactory;
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

    /**
     * 检查 API Key 是否是占位符（未配置）
     */
    private boolean isPlaceholderApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return true;
        }
        String lower = apiKey.toLowerCase();
        return lower.startsWith("your-") || lower.startsWith("your_") || lower.equals("your_api_key");
    }

    // ==================== ASR 客户端 ====================

    @Bean
    public AsrClient funasrAsrClient() {
        AiProviderProperties props = aiProperties.getProvider("funasr");
        log.debug("funasr 配置检查: props={}, apiKey={}", props, props != null ? props.getApiKey() : "null");
        if (props == null || isPlaceholderApiKey(props.getApiKey())) {
            log.warn("未配置 funasr ASR，跳过注册");
            return null;
        }
        AsrClient client = new DashScopeAsrClient(props);
        aiClientFactory.registerAsrClient("funasr", client);
        return client;
    }

    @Bean
    public AsrClient whisperAsrClient() {
        AiProviderProperties props = aiProperties.getProvider("whisper");
        if (props == null || isPlaceholderApiKey(props.getApiKey())) {
            log.warn("未配置 whisper ASR，跳过注册");
            return null;
        }
        AsrClient client = new WhisperAsrClient(props);
        aiClientFactory.registerAsrClient("whisper", client);
        return client;
    }

    @Bean
    public AsrClient xiaomiAsrClient() {
        AiProviderProperties props = aiProperties.getProvider("xiaomi-asr");
        if (props == null || isPlaceholderApiKey(props.getApiKey())) {
            log.warn("未配置 xiaomi-asr ASR，跳过注册");
            return null;
        }
        AsrClient client = new XiaomiAsrClient(props);
        aiClientFactory.registerAsrClient("xiaomi-asr", client);
        return client;
    }

    // ==================== LLM 客户端 ====================

    @Bean
    public LlmClient deepseekLlmClient() {
        AiProviderProperties props = aiProperties.getProvider("deepseek");
        if (props == null || isPlaceholderApiKey(props.getApiKey())) {
            log.warn("未配置 deepseek LLM，跳过注册");
            return null;
        }
        DeepSeekLlmClient client = new DeepSeekLlmClient(props);
        client.setCallTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));
        log.info("DeepSeek LLM 超时设置: {}s", props.getTimeoutSeconds());
        aiClientFactory.registerLlmClient("deepseek", client);
        return client;
    }

    @Bean
    public LlmClient xiaomiLlmClient() {
        AiProviderProperties props = aiProperties.getProvider("xiaomi");
        if (props == null || isPlaceholderApiKey(props.getApiKey())) {
            log.warn("未配置 xiaomi LLM，跳过注册");
            return null;
        }
        LlmClient client = new XiaomiLlmClient(props);
        aiClientFactory.registerLlmClient("xiaomi", client);
        return client;
    }
}
