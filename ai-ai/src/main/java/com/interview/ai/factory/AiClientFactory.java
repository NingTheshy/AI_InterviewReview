package com.interview.ai.factory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.ai.client.DashScopeAsrClient;
import com.interview.ai.client.OpenAiCompatibleClient;
import com.interview.ai.config.AiProperties;
import com.interview.ai.config.AiProviderProperties;
import com.interview.ai.entity.AiConfig;
import com.interview.ai.mapper.AiConfigMapper;
import com.interview.ai.service.AiModelClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 客户端工厂
 * <p>
 * 根据配置创建和管理 AI 模型客户端实例。支持按 provider 名称或配置类型获取客户端。
 * 优先从数据库查询默认配置，若无则 fallback 到 yml 中已注册的 provider。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiClientFactory {

    private final AiProperties aiProperties;
    private final AiConfigMapper aiConfigMapper;

    /** 注册表：provider 名称 -> AiModelClient 实例 */
    private final ConcurrentHashMap<String, AiModelClient> registry = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        Map<String, AiProviderProperties> providers = aiProperties.getProviders();
        if (providers == null || providers.isEmpty()) {
            log.warn("未配置任何 AI 提供商");
            return;
        }

        for (Map.Entry<String, AiProviderProperties> entry : providers.entrySet()) {
            String name = entry.getKey();
            AiProviderProperties props = entry.getValue();
            if (props.getApiKey() == null || props.getApiKey().startsWith("your-")) {
                log.warn("跳过未配置 API Key 的 AI 提供商: {}", name);
                continue;
            }
            AiModelClient client;
            if ("funasr".equals(name)) {
                client = new DashScopeAsrClient(props);
            } else {
                client = new OpenAiCompatibleClient(props);
            }
            registry.put(name, client);
            log.info("注册 AI 提供商: provider={}, model={}", name, props.getModelName());
        }

        log.info("AI 客户端注册完成, 已注册: {}", registry.keySet());
    }

    /**
     * 按 provider 名称获取客户端
     */
    public AiModelClient getClient(String provider) {
        AiModelClient client = registry.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("未注册的 AI 提供商: " + provider
                    + ", 已注册: " + registry.keySet());
        }
        return client;
    }

    /**
     * 按配置类型获取默认客户端
     * 优先从数据库 ai_config 表查询 is_default=1 的配置，
     * 若无则 fallback 到 yml 中已注册的第一个可用 provider
     */
    public AiModelClient getDefaultClient(Integer configType) {
        // 从数据库查询默认配置
        LambdaQueryWrapper<AiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiConfig::getConfigType, configType);
        wrapper.eq(AiConfig::getIsDefault, 1);
        wrapper.eq(AiConfig::getStatus, 1);

        AiConfig defaultConfig = aiConfigMapper.selectOne(wrapper);
        if (defaultConfig != null) {
            AiModelClient client = registry.get(defaultConfig.getProvider());
            if (client != null) {
                return client;
            }
            log.warn("数据库默认配置的 provider 未注册: {}, 使用 fallback",
                    defaultConfig.getProvider());
        }

        // Fallback：返回该类型对应的第一个已注册 provider
        return getFallbackClient(configType);
    }

    /**
     * 根据配置类型获取 fallback 客户端
     * configType=1(ASR) 优先 funasr，configType=2(LLM) 优先 deepseek
     */
    private AiModelClient getFallbackClient(Integer configType) {
        String preferred;
        String fallback;
        if (configType != null && configType == 1) {
            preferred = "funasr";
            fallback = "openai";
        } else {
            preferred = "deepseek";
            fallback = "xiaomi";
        }

        AiModelClient client = registry.get(preferred);
        if (client != null) return client;

        client = registry.get(fallback);
        if (client != null) return client;

        // 返回任意一个已注册的客户端
        Set<String> keys = registry.keySet();
        if (!keys.isEmpty()) {
            String firstKey = keys.iterator().next();
            log.warn("无匹配的 fallback 客户端, 使用: {}", firstKey);
            return registry.get(firstKey);
        }

        throw new IllegalStateException("无可用的 AI 客户端, 请检查配置");
    }
}
