package com.interview.ai.config;

import com.interview.ai.model.dashscope.DashScopeFileTranscriptionModel;
import com.interview.common.constant.ConfigType;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * AI 供应商边界策略。
 *
 * <p>文本生成只保留 DeepSeek 客户端，语音识别只保留 DashScope 客户端；
 * API 端点允许由管理员配置，以兼容官方服务和后续代理部署。该策略同时用于
 * 配置写入和运行时客户端创建，防止历史数据、直接修改数据库或配置快照绕过
 * 供应商与模型类型边界。</p>
 */
public final class AiProviderPolicy {

    public static final String DEEPSEEK_PROVIDER = "deepseek";
    public static final String DASHSCOPE_PROVIDER = "dashscope";
    private static final Set<String> HTTP_SCHEMES = Set.of("http", "https");

    private AiProviderPolicy() {
    }

    /**
     * 校验配置类型、供应商、模型和服务端点之间的一致性。
     *
     * @param configType 配置类型，取值见 {@link ConfigType}
     * @param provider 供应商标识
     * @param modelName 模型名称
     * @param apiEndpoint API 根地址
     */
    public static void validate(int configType, String provider, String modelName, String apiEndpoint) {
        String normalizedProvider = requireText(provider, "供应商").toLowerCase(Locale.ROOT);
        String normalizedModel = requireText(modelName, "模型名称");
        parseEndpoint(apiEndpoint);

        if (configType == ConfigType.LLM.getCode()) {
            requireProvider(normalizedProvider, DEEPSEEK_PROVIDER, "文本模型仅支持 deepseek");
            return;
        }

        if (configType == ConfigType.ASR.getCode()) {
            requireProvider(normalizedProvider, DASHSCOPE_PROVIDER, "ASR 供应商仅支持 dashscope");
            if (!DashScopeFileTranscriptionModel.SUPPORTED_MODELS.contains(normalizedModel)) {
                throw badRequest("ASR 模型仅支持 qwen-audio-3.1-asr-flash-filetrans 或 paraformer-v2");
            }
            return;
        }

        throw badRequest("不支持的 AI 配置类型: " + configType);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw badRequest(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static URI parseEndpoint(String value) {
        try {
            URI endpoint = URI.create(requireText(value, "API端点"));
            String scheme = endpoint.getScheme() == null
                    ? ""
                    : endpoint.getScheme().toLowerCase(Locale.ROOT);
            if (!HTTP_SCHEMES.contains(scheme) || endpoint.getHost() == null || endpoint.getUserInfo() != null) {
                throw new IllegalArgumentException("invalid endpoint");
            }
            return endpoint;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw badRequest("API端点必须是有效的HTTP(S)地址");
        }
    }

    private static void requireProvider(String actual, String expected, String message) {
        if (!expected.equals(actual)) {
            throw badRequest(message);
        }
    }

    private static BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST.getCode(), message);
    }
}
