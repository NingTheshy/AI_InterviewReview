package com.interview.ai.service.impl;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.entity.AiConfig;
import com.interview.ai.factory.AiClientFactory;
import com.interview.ai.service.AiConfigService;
import com.interview.ai.service.AiModelClient;
import com.interview.ai.util.StructuredOutputInvoker;
import com.interview.common.constant.CompanyTier;
import com.interview.common.constant.ConfigType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScoringServiceImpl Test")
class ScoringServiceImplTest {

    @Mock
    private AiClientFactory aiClientFactory;

    @Mock
    private AiConfigService aiConfigService;

    @Mock
    private StructuredOutputInvoker outputInvoker;

    @Mock
    private AiModelClient aiModelClient;

    @InjectMocks
    private ScoringServiceImpl scoringService;

    private AiConfig mockConfig;

    @BeforeEach
    void setUp() {
        mockConfig = new AiConfig();
        mockConfig.setId(1L);
        mockConfig.setProvider("deepseek");
        mockConfig.setConfigType(ConfigType.LLM.getCode());
    }

    @Test
    @DisplayName("analyzeAndScore - with configId uses specific client")
    void analyzeAndScore_withConfigId_usesSpecificClient() {
        when(aiConfigService.getDetail(1L)).thenReturn(mockConfig);
        when(aiClientFactory.getClient("deepseek")).thenReturn(aiModelClient);
        when(aiModelClient.call(anyString(), anyString(), eq(1L)))
                .thenReturn("{\"overallScore\":75}");

        String result = scoringService.analyzeAndScore("interview text", "JD text", "resume text", 1L);

        assertNotNull(result);
        verify(aiConfigService).getDetail(1L);
        verify(aiClientFactory).getClient("deepseek");
        verify(aiModelClient).call(anyString(), anyString(), eq(1L));
    }

    @Test
    @DisplayName("analyzeAndScore - null configId uses default client")
    void analyzeAndScore_nullConfigId_usesDefaultClient() {
        lenient().when(aiClientFactory.getDefaultClient(ConfigType.LLM.getCode())).thenReturn(aiModelClient);
        lenient().when(aiModelClient.call(anyString(), anyString(), isNull()))
                .thenReturn("{\"overallScore\":75}");

        String result = scoringService.analyzeAndScore("interview text", "JD text", "resume text", null);

        assertNotNull(result);
        verify(aiClientFactory).getDefaultClient(ConfigType.LLM.getCode());
    }

    @Test
    @DisplayName("analyzeAndScore - with company tier")
    void analyzeAndScore_withCompanyTier_includesTierInPrompt() {
        lenient().when(aiClientFactory.getDefaultClient(ConfigType.LLM.getCode())).thenReturn(aiModelClient);
        lenient().when(aiModelClient.call(anyString(), anyString(), isNull()))
                .thenReturn("{\"overallScore\":75}");

        String result = scoringService.analyzeAndScore(
                "interview text", "JD text", "resume text", null, CompanyTier.TIER_1.getCode());

        assertNotNull(result);
        verify(aiModelClient).call(anyString(), anyString(), isNull());
    }

    @Test
    @DisplayName("analyzeAndScoreBatch - processes multiple questions")
    void analyzeAndScoreBatch_multipleQuestions_processesInBatches() {
        String text = "Q1: What is Spring Boot?\nA: ...\n\nQ2: What is MyBatis?\nA: ...";
        lenient().when(aiClientFactory.getDefaultClient(ConfigType.LLM.getCode())).thenReturn(aiModelClient);

        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode arrayNode = objectMapper.createArrayNode();
        lenient().when(outputInvoker.invoke(eq(aiModelClient), anyString(), anyString(), isNull()))
                .thenReturn(arrayNode);

        String result = scoringService.analyzeAndScoreBatch(
                text, "JD", "resume", null, CompanyTier.TIER_3.getCode());

        assertNotNull(result);
    }
}
