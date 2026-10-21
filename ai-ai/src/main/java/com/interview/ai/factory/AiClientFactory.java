package com.interview.ai.factory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.config.AiProperties;
import com.interview.ai.config.AiProviderProperties;
import com.interview.ai.config.FunAsrProperties;
import com.interview.ai.client.SpringAiDeepSeekLlmClient;
import com.interview.ai.client.SpringAiFunAsrClient;
import com.interview.ai.client.SpringAiOpenAiCompatibleLlmClient;
import com.interview.ai.entity.AiConfig;
import com.interview.ai.mapper.AiConfigMapper;
import com.interview.ai.model.funasr.FunAsrTranscriptionModel;
import com.interview.ai.security.AiConfigSecretCipher;
import com.interview.ai.service.AsrClient;
import com.interview.ai.service.LlmClient;
import com.interview.common.constant.ConfigType;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;
import org.springframework.util.StringUtils;

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
    private final AiConfigSecretCipher secretCipher;
    private final AiProperties aiProperties;
    private final FunAsrProperties funAsrProperties;
    private final ObjectMapper objectMapper;

    private final Map<String, AsrClient> dynamicAsrCache = new ConcurrentHashMap<>();
    private final Map<String, LlmClient> dynamicLlmCache = new ConcurrentHashMap<>();

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
        AiConfig config = findEnabledByProvider(provider, ConfigType.ASR);
        return buildAsr(config);
    }

    /**
     * 按 provider 名称获取 LLM 客户端
     */
    public LlmClient getLlmClient(String provider) {
        AiConfig config = findEnabledByProvider(provider, ConfigType.LLM);
        return buildLlm(config);
    }

    /**
     * 获取默认 ASR 客户端
     * 优先从数据库查询默认配置，若无则 fallback 到已注册的第一个可用 provider
     */
    public AsrClient getDefaultAsrClient() {
        return buildAsr(defaultConfig(ConfigType.ASR));
    }

    /**
     * 获取默认 LLM 客户端
     * 优先从数据库查询默认配置，若无则 fallback 到已注册的第一个可用 provider
     */
    public LlmClient getDefaultLlmClient() {
        return buildLlm(defaultConfig(ConfigType.LLM));
    }

    /** 根据有效配置 ID 选择 ASR 客户端；无效配置 ID 回退到默认客户端。 */
    public AsrClient getAsrClientByConfigId(Long configId) {
        return configId == null ? getDefaultAsrClient() : buildAsr(requireConfig(configId, ConfigType.ASR));
    }

    /** 根据有效配置 ID 选择 LLM 客户端；无效配置 ID 回退到默认客户端。 */
    public LlmClient getLlmClientByConfigId(Long configId) {
        return configId == null ? getDefaultLlmClient() : buildLlm(requireConfig(configId, ConfigType.LLM));
    }

    /**
     * 按配置类型获取默认客户端。
     *
     * <p>数据库默认项必须启用且其 provider 已注册；否则选择注册表中第一个客户端作为应用可用性
     * 兜底。生产环境应始终显式配置默认项，不应依赖 HashMap 的回退顺序。</p>
     */
    public String snapshotDefault(ConfigType configType) {
        try {
            AiConfig config = defaultConfig(configType);
            RuntimeConfig runtime = toRuntime(config);
            return secretCipher.encrypt(objectMapper.writeValueAsString(runtime));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_FOUND.getCode(), "生成AI配置快照失败: " + e.getMessage());
        }
    }

    public AsrClient getAsrClientFromSnapshot(String snapshot) {
        return buildAsr(fromSnapshot(snapshot, ConfigType.ASR));
    }

    public LlmClient getLlmClientFromSnapshot(String snapshot) {
        return buildLlm(fromSnapshot(snapshot, ConfigType.LLM));
    }

    /** 返回不含密钥的快照元数据，供缓存和审计隔离使用。 */
    public SnapshotMetadata snapshotMetadata(String snapshot, ConfigType type) {
        RuntimeConfig runtime = fromSnapshot(snapshot, type);
        return new SnapshotMetadata(runtime.id(), runtime.provider(), runtime.modelName(), runtime.version());
    }

    private AiConfig defaultConfig(ConfigType configType) {
        LambdaQueryWrapper<AiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiConfig::getConfigType, configType.getCode());
        wrapper.eq(AiConfig::getIsDefault, 1);
        wrapper.eq(AiConfig::getStatus, 1);
        wrapper.eq(AiConfig::getTestStatus, 1);

        AiConfig defaultConfig = aiConfigMapper.selectOne(wrapper);
        if (defaultConfig == null) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_FOUND.getCode(),
                    "未配置已启用且测试成功的默认" + configType.getDescription());
        }
        return defaultConfig;
    }

    private AiConfig findEnabledByProvider(String provider, ConfigType type) {
        AiConfig config = aiConfigMapper.selectOne(new LambdaQueryWrapper<AiConfig>()
                .eq(AiConfig::getProvider, provider)
                .eq(AiConfig::getConfigType, type.getCode())
                .eq(AiConfig::getStatus, 1)
                .orderByDesc(AiConfig::getIsDefault)
                .last("LIMIT 1"));
        if (config == null) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_FOUND.getCode(), "没有可用的AI配置: " + provider);
        }
        return config;
    }

    private AiConfig requireConfig(Long id, ConfigType type) {
        AiConfig config = aiConfigMapper.selectById(id);
        if (config == null || !Integer.valueOf(type.getCode()).equals(config.getConfigType())
                || !Integer.valueOf(1).equals(config.getStatus())) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_FOUND.getCode(), "AI配置不存在、类型错误或已停用");
        }
        return config;
    }

    private AsrClient buildAsr(AiConfig config) {
        return buildAsr(toRuntime(config));
    }

    private AsrClient buildAsr(RuntimeConfig runtime) {
        if (!"funasr".equalsIgnoreCase(runtime.provider())) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_FOUND.getCode(), "当前仅支持FunASR语音识别");
        }
        return dynamicAsrCache.computeIfAbsent(runtime.cacheKey(), ignored -> {
            AiProviderProperties properties = runtime.toProperties();
            return new SpringAiFunAsrClient(new FunAsrTranscriptionModel(properties, funAsrProperties),
                    properties.getModelName());
        });
    }

    private LlmClient buildLlm(AiConfig config) {
        return buildLlm(toRuntime(config));
    }

    private LlmClient buildLlm(RuntimeConfig runtime) {
        return dynamicLlmCache.computeIfAbsent(runtime.cacheKey(), ignored -> {
            AiProviderProperties properties = runtime.toProperties();
            if ("deepseek".equalsIgnoreCase(runtime.provider())) {
                SpringAiDeepSeekLlmClient client = new SpringAiDeepSeekLlmClient(properties);
                client.setCallTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
                return client;
            }
            if ("xiaomi".equalsIgnoreCase(runtime.provider())) {
                return new SpringAiOpenAiCompatibleLlmClient(properties);
            }
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_FOUND.getCode(),
                    "不支持的文本模型供应商: " + runtime.provider());
        });
    }

    private RuntimeConfig toRuntime(AiConfig config) {
        String apiKey = secretCipher.decrypt(config.getApiKey());
        if (!StringUtils.hasText(apiKey)) {
            AiProviderProperties fallback = aiProperties.getProvider(config.getProvider());
            apiKey = fallback == null ? null : fallback.getApiKey();
        }
        if (!StringUtils.hasText(apiKey) || apiKey.startsWith("your_")) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_FOUND.getCode(), "AI配置缺少可用API Key");
        }
        String endpoint = config.getApiEndpoint() == null ? "" : config.getApiEndpoint().replaceAll("/+$", "");
        if ("xiaomi".equalsIgnoreCase(config.getProvider()) && endpoint.endsWith("/v1")) {
            endpoint = endpoint.substring(0, endpoint.length() - 3);
        }
        return new RuntimeConfig(config.getId(), config.getProvider(), config.getModelName(), endpoint,
                apiKey, config.getConfigType(), String.valueOf(config.getUpdatedAt()));
    }

    private RuntimeConfig fromSnapshot(String snapshot, ConfigType type) {
        try {
            RuntimeConfig runtime = objectMapper.readValue(secretCipher.decrypt(snapshot), RuntimeConfig.class);
            if (runtime.configType() != type.getCode()) throw new IllegalArgumentException("配置类型不匹配");
            return runtime;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_FOUND.getCode(), "AI配置快照不可用: " + e.getMessage());
        }
    }

    public record RuntimeConfig(Long id, String provider, String modelName, String apiEndpoint,
                                String apiKey, int configType, String version) {
        String cacheKey() { return id + ":" + version + ":" + provider + ":" + modelName + ":" + apiEndpoint; }

        AiProviderProperties toProperties() {
            AiProviderProperties properties = new AiProviderProperties();
            properties.setApiKey(apiKey);
            properties.setApiEndpoint(apiEndpoint);
            properties.setModelName(modelName);
            return properties;
        }
    }

    public record SnapshotMetadata(Long configId, String provider, String modelName, String version) {}
}
