package com.interview.ai.factory;

import com.interview.ai.config.AiProperties;
import com.interview.ai.mapper.AiConfigMapper;
import com.interview.ai.service.AsrClient;
import com.interview.ai.service.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiClientFactory 测试")
class AiClientFactoryTest {

    @Mock
    private AiConfigMapper aiConfigMapper;

    @InjectMocks
    private AiClientFactory aiClientFactory;

    private MockAsrClient mockAsrClient;
    private MockLlmClient mockLlmClient;

    @BeforeEach
    void setUp() {
        mockAsrClient = new MockAsrClient();
        mockLlmClient = new MockLlmClient();

        // 手动注册客户端
        aiClientFactory.registerAsrClient("mock-asr", mockAsrClient);
        aiClientFactory.registerLlmClient("mock-llm", mockLlmClient);
    }

    @Test
    @DisplayName("getDefaultAsrClient - 返回 ASR 客户端")
    void getDefaultAsrClient_returnsAsrClient() {
        when(aiConfigMapper.selectOne(any())).thenReturn(null);

        AsrClient client = aiClientFactory.getDefaultAsrClient();
        assertNotNull(client);
        assertTrue(client instanceof MockAsrClient);
    }

    @Test
    @DisplayName("getDefaultLlmClient - 返回 LLM 客户端")
    void getDefaultLlmClient_returnsLlmClient() {
        when(aiConfigMapper.selectOne(any())).thenReturn(null);

        LlmClient client = aiClientFactory.getDefaultLlmClient();
        assertNotNull(client);
        assertTrue(client instanceof MockLlmClient);
    }

    @Test
    @DisplayName("getAsrClient - 已注册的 provider 返回客户端")
    void getAsrClient_registeredProvider_returnsClient() {
        AsrClient client = aiClientFactory.getAsrClient("mock-asr");
        assertNotNull(client);
    }

    @Test
    @DisplayName("getAsrClient - 未注册的 provider 抛出异常")
    void getAsrClient_unregisteredProvider_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> aiClientFactory.getAsrClient("unknown"));
    }

    @Test
    @DisplayName("getLlmClient - 已注册的 provider 返回客户端")
    void getLlmClient_registeredProvider_returnsClient() {
        LlmClient client = aiClientFactory.getLlmClient("mock-llm");
        assertNotNull(client);
    }

    @Test
    @DisplayName("getLlmClient - 未注册的 provider 抛出异常")
    void getLlmClient_unregisteredProvider_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> aiClientFactory.getLlmClient("unknown"));
    }

    // 模拟客户端实现
    static class MockAsrClient implements AsrClient {
        @Override
        public String transcribe(MultipartFile audioFile, String language) {
            return "mock transcript";
        }
    }

    static class MockLlmClient implements LlmClient {
        @Override
        public String call(String prompt, String systemPrompt, Long configId) {
            return "mock response";
        }
    }
}
