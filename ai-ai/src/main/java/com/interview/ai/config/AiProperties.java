package com.interview.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 配置属性类
 * <p>
 * 从 application.yml 的 ai.providers 前缀加载 AI 提供商配置。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private Map<String, AiProviderProperties> providers = new HashMap<>();

    public AiProviderProperties getProvider(String name) {
        return providers.get(name);
    }
}
