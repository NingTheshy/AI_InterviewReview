package com.interview.ai.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.config.AiProperties;
import com.interview.ai.config.FunAsrProperties;
import com.interview.ai.entity.AiConfig;
import com.interview.ai.mapper.AiConfigMapper;
import com.interview.ai.security.AiConfigSecretCipher;
import com.interview.ai.service.AsrClient;
import com.interview.ai.service.LlmClient;
import com.interview.common.constant.ConfigType;
import com.interview.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiClientFactory 测试")
class AiClientFactoryTest {

    @Mock
    private AiConfigMapper aiConfigMapper;

    private AiClientFactory aiClientFactory;

    @Mock
    private AiConfigSecretCipher secretCipher;

    private final AiProperties aiProperties = new AiProperties();
    private final FunAsrProperties funAsrProperties = new FunAsrProperties();

    @BeforeEach
    void setUp() {
        aiClientFactory = new AiClientFactory(aiConfigMapper, secretCipher, aiProperties,
                funAsrProperties, new ObjectMapper());
        lenient().when(secretCipher.decrypt(anyString())).thenReturn("test-api-key");
    }

    @Test
    @DisplayName("getDefaultAsrClient - 返回 ASR 客户端")
    void getDefaultAsrClient_returnsAsrClient() {
        when(aiConfigMapper.selectOne(any())).thenReturn(config("funasr", ConfigType.ASR));

        AsrClient client = aiClientFactory.getDefaultAsrClient();
        assertNotNull(client);
    }

    @Test
    @DisplayName("getDefaultLlmClient - 返回 LLM 客户端")
    void getDefaultLlmClient_returnsLlmClient() {
        when(aiConfigMapper.selectOne(any())).thenReturn(config("deepseek", ConfigType.LLM));

        LlmClient client = aiClientFactory.getDefaultLlmClient();
        assertNotNull(client);
    }

    @Test
    @DisplayName("getAsrClient - 数据库中已启用的 provider 返回客户端")
    void getAsrClient_enabledProvider_returnsClient() {
        when(aiConfigMapper.selectOne(any())).thenReturn(config("funasr", ConfigType.ASR));
        AsrClient client = aiClientFactory.getAsrClient("funasr");
        assertNotNull(client);
    }

    @Test
    @DisplayName("getAsrClient - 未注册的 provider 抛出异常")
    void getAsrClient_unregisteredProvider_throwsException() {
        when(aiConfigMapper.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> aiClientFactory.getAsrClient("unknown"));
    }

    @Test
    @DisplayName("getLlmClient - 数据库中已启用的 provider 返回客户端")
    void getLlmClient_enabledProvider_returnsClient() {
        when(aiConfigMapper.selectOne(any())).thenReturn(config("deepseek", ConfigType.LLM));
        LlmClient client = aiClientFactory.getLlmClient("deepseek");
        assertNotNull(client);
    }

    @Test
    @DisplayName("getLlmClient - 未注册的 provider 抛出异常")
    void getLlmClient_unregisteredProvider_throwsException() {
        when(aiConfigMapper.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> aiClientFactory.getLlmClient("unknown"));
    }

    private AiConfig config(String provider, ConfigType type) {
        AiConfig config = new AiConfig();
        config.setId(type == ConfigType.ASR ? 1L : 2L);
        config.setProvider(provider);
        config.setConfigType(type.getCode());
        config.setModelName(type == ConfigType.ASR ? "fun-asr" : "deepseek-chat");
        config.setApiEndpoint(type == ConfigType.ASR
                ? "https://dashscope.aliyuncs.com/api/v1"
                : "https://api.deepseek.com/v1");
        config.setApiKey("encrypted-key");
        config.setStatus(1);
        config.setTestStatus(1);
        config.setIsDefault(1);
        return config;
    }
}
