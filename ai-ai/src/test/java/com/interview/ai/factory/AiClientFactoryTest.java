package com.interview.ai.factory;

import com.interview.ai.config.AiProperties;
import com.interview.ai.config.AiProviderProperties;
import com.interview.ai.mapper.AiConfigMapper;
import com.interview.ai.service.AiModelClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiClientFactory 测试")
class AiClientFactoryTest {

    @Mock
    private AiProperties aiProperties;

    @Mock
    private AiConfigMapper aiConfigMapper;

    @InjectMocks
    private AiClientFactory aiClientFactory;

    @BeforeEach
    void setUp() {
        // 初始化注册表
        Map<String, AiProviderProperties> providers = new HashMap<>();
        AiProviderProperties deepseekProps = new AiProviderProperties();
        deepseekProps.setApiKey("test-api-key");
        deepseekProps.setModelName("deepseek-chat");
        providers.put("deepseek", deepseekProps);
        when(aiProperties.getProviders()).thenReturn(providers);
        aiClientFactory.init();
    }

    @Test
    @DisplayName("getClient - 已注册的 provider 返回客户端")
    void getClient_registeredProvider_returnsClient() {
        AiModelClient client = aiClientFactory.getClient("deepseek");
        assertNotNull(client);
    }

    @Test
    @DisplayName("getClient - 未注册的 provider 抛出异常")
    void getClient_unregisteredProvider_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> aiClientFactory.getClient("unknown"));
    }

    @Test
    @DisplayName("getDefaultClient - 无数据库配置时使用 fallback")
    void getDefaultClient_noDbConfig_usesFallback() {
        when(aiConfigMapper.selectOne(any())).thenReturn(null);

        AiModelClient client = aiClientFactory.getDefaultClient(2);

        assertNotNull(client);
    }
}
