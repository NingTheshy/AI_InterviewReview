package com.interview.ai.factory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.ai.config.AiProperties;
import com.interview.ai.entity.AiConfig;
import com.interview.ai.mapper.AiConfigMapper;
import com.interview.ai.service.AsrClient;
import com.interview.ai.service.LlmClient;
import com.interview.common.constant.ConfigType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 客户端工厂
 * <p>
 * 管理和提供 AI 客户端实例。支持按 provider 名称或配置类型获取客户端。
 *
 * <p>运行时配置只决定选择哪个已注册客户端，客户端实例仍在应用启动时由 Spring 配置创建。
 * 未找到有效数据库默认项时才回退至注册表中的可用客户端；该回退会记录日志，便于发现配置缺失。</p>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiClientFactory {

    private final AiConfigMapper aiConfigMapper;

    /** ASR 客户端注册表：provider 名称 -> AsrClient 实例 */
    private final Map<String, AsrClient> asrRegistry = new HashMap<>();

    /** LLM 客户端注册表：provider 名称 -> LlmClient 实例 */
    private final Map<String, LlmClient> llmRegistry = new HashMap<>();

    /** 注册 ASR 客户端；同名 provider 的后注册实例会替换旧实例。 */
    public void registerAsrClient(String provider, AsrClient client) {
        asrRegistry.put(provider, client);
        log.info("注册 ASR 提供商: provider={}", provider);
    }

    /** 注册 LLM 客户端；同名 provider 的后注册实例会替换旧实例。 */
    public void registerLlmClient(String provider, LlmClient client) {
        llmRegistry.put(provider, client);
        log.info("注册 LLM 提供商: provider={}", provider);
    }

    /**
     * 按 provider 名称获取 ASR 客户端
     */
    public AsrClient getAsrClient(String provider) {
        AsrClient client = asrRegistry.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("未注册的 ASR 提供商: " + provider
                    + ", 已注册: " + asrRegistry.keySet());
        }
        return client;
    }

    /**
     * 按 provider 名称获取 LLM 客户端
     */
    public LlmClient getLlmClient(String provider) {
        LlmClient client = llmRegistry.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("未注册的 LLM 提供商: " + provider
                    + ", 已注册: " + llmRegistry.keySet());
        }
        return client;
    }

    /**
     * 获取默认 ASR 客户端
     * 优先从数据库查询默认配置，若无则 fallback 到已注册的第一个可用 provider
     */
    public AsrClient getDefaultAsrClient() {
        return getDefaultClient(ConfigType.ASR, asrRegistry);
    }

    /**
     * 获取默认 LLM 客户端
     * 优先从数据库查询默认配置，若无则 fallback 到已注册的第一个可用 provider
     */
    public LlmClient getDefaultLlmClient() {
        return getDefaultClient(ConfigType.LLM, llmRegistry);
    }

    /** 根据有效配置 ID 选择 ASR 客户端；无效配置 ID 回退到默认客户端。 */
    public AsrClient getAsrClientByConfigId(Long configId) {
        if (configId != null && configId > 0) {
            AiConfig config = aiConfigMapper.selectById(configId);
            if (config != null && config.getProvider() != null) {
                AsrClient client = asrRegistry.get(config.getProvider());
                if (client != null) {
                    return client;
                }
                log.warn("数据库配置的 ASR provider 未注册: {}", config.getProvider());
            }
        }
        return getDefaultAsrClient();
    }

    /** 根据有效配置 ID 选择 LLM 客户端；无效配置 ID 回退到默认客户端。 */
    public LlmClient getLlmClientByConfigId(Long configId) {
        if (configId != null && configId > 0) {
            AiConfig config = aiConfigMapper.selectById(configId);
            if (config != null && config.getProvider() != null) {
                LlmClient client = llmRegistry.get(config.getProvider());
                if (client != null) {
                    return client;
                }
                log.warn("数据库配置的 LLM provider 未注册: {}", config.getProvider());
            }
        }
        return getDefaultLlmClient();
    }

    /**
     * 按配置类型获取默认客户端。
     *
     * <p>数据库默认项必须启用且其 provider 已注册；否则选择注册表中第一个客户端作为应用可用性
     * 兜底。生产环境应始终显式配置默认项，不应依赖 HashMap 的回退顺序。</p>
     */
    @SuppressWarnings("unchecked")
    private <T> T getDefaultClient(ConfigType configType, Map<String, T> registry) {
        // 从数据库查询默认配置
        LambdaQueryWrapper<AiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiConfig::getConfigType, configType.getCode());
        wrapper.eq(AiConfig::getIsDefault, 1);
        wrapper.eq(AiConfig::getStatus, 1);

        AiConfig defaultConfig = aiConfigMapper.selectOne(wrapper);
        if (defaultConfig != null && defaultConfig.getProvider() != null) {
            T client = registry.get(defaultConfig.getProvider());
            if (client != null) {
                return client;
            }
            log.warn("数据库默认配置的 provider 未注册: {}, 使用 fallback",
                    defaultConfig.getProvider());
        }

        // Fallback：返回第一个已注册的客户端
        if (!registry.isEmpty()) {
            String firstKey = registry.keySet().iterator().next();
            log.info("使用 fallback 客户端: {}", firstKey);
            return registry.get(firstKey);
        }

        throw new IllegalStateException("无可用的 " + configType.getDescription() + " 客户端, 请检查配置");
    }
}
