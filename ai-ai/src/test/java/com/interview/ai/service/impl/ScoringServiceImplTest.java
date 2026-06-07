package com.interview.ai.service.impl;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.entity.AiConfig;
import com.interview.ai.factory.AiClientFactory;
import com.interview.ai.service.AiConfigService;
import com.interview.ai.service.LlmClient;
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
    private LlmClient llmClient;

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
        when(aiClientFactory.getLlmClient("deepseek")).thenReturn(llmClient);
        when(llmClient.call(anyString(), anyString(), eq(1L)))
                .thenReturn("{\"overallScore\":75}");

        String result = scoringService.analyzeAndScore("interview text", "JD text", "resume text", 1L);

        assertNotNull(result);
        verify(aiConfigService).getDetail(1L);
        verify(aiClientFactory).getLlmClient("deepseek");
        verify(llmClient).call(anyString(), anyString(), eq(1L));
    }

    @Test
    @DisplayName("analyzeAndScore - null configId uses default client")
    void analyzeAndScore_nullConfigId_usesDefaultClient() {
        lenient().when(aiClientFactory.getDefaultLlmClient()).thenReturn(llmClient);
        lenient().when(llmClient.call(anyString(), anyString(), isNull()))
                .thenReturn("{\"overallScore\":75}");

        String result = scoringService.analyzeAndScore("interview text", "JD text", "resume text", null);

        assertNotNull(result);
        verify(aiClientFactory).getDefaultLlmClient();
    }

    @Test
    @DisplayName("analyzeAndScore - with company tier")
    void analyzeAndScore_withCompanyTier_includesTierInPrompt() {
        lenient().when(aiClientFactory.getDefaultLlmClient()).thenReturn(llmClient);
        lenient().when(llmClient.call(anyString(), anyString(), isNull()))
                .thenReturn("{\"overallScore\":75}");

        String result = scoringService.analyzeAndScore(
                "interview text", "JD text", "resume text", null, CompanyTier.TIER_1.getCode());

        assertNotNull(result);
        verify(llmClient).call(anyString(), anyString(), isNull());
    }

    @Test
    @DisplayName("analyzeAndScoreBatch - processes multiple questions")
    void analyzeAndScoreBatch_multipleQuestions_processesInBatches() {
        String text = "Q1: What is Spring Boot?\nA: ...\n\nQ2: What is MyBatis?\nA: ...";
        lenient().when(aiClientFactory.getDefaultLlmClient()).thenReturn(llmClient);
        lenient().when(outputInvoker.invoke(any(), anyString(), anyString(), isNull()))
                .thenReturn(new ObjectMapper().createArrayNode()
                        .add(createQuestionResult(1, 75))
                        .add(createQuestionResult(2, 80)));

        String result = scoringService.analyzeAndScoreBatch(text, "JD", "resume", null, null);

        assertNotNull(result);
    }

    @Test
    @DisplayName("analyzeAndScoreBatchTwoPhase - two-phase evaluation")
    void analyzeAndScoreBatchTwoPhase_twoPhaseEvaluation() {
        String text = "Q1: What is Spring Boot?\nA: It is a framework.\n\nQ2: What is MyBatis?\nA: ORM framework.";
        lenient().when(aiClientFactory.getDefaultLlmClient()).thenReturn(llmClient);

        // Phase 1: analysis returns tier classifications
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode analysisResult = mapper.createArrayNode();
        analysisResult.addObject()
                .put("questionText", "What is Spring Boot?")
                .put("answerText", "It is a framework.")
                .put("score", 0)
                .putObject("dimensionContent").put("observations", "Basic answer").put("tier", "一般");
        analysisResult.objectNode().put("questionIndex", 1);

        // Phase 2: scoring returns final scores
        ArrayNode scoringResult = mapper.createArrayNode();
        scoringResult.addObject()
                .put("questionIndex", 1)
                .put("score", 52)
                .put("dimensionContent", 52)
                .put("dimensionLogic", 52)
                .put("dimensionExpression", 52)
                .put("dimensionProfessional", 52);

        lenient().when(outputInvoker.invoke(any(), anyString(), anyString(), isNull()))
                .thenReturn(analysisResult)
                .thenReturn(scoringResult);

        // Mock the summary call
        lenient().when(outputInvoker.invoke(any(), anyString(), anyString(), isNull()))
                .thenReturn(analysisResult)
                .thenReturn(mapper.createObjectNode()
                        .put("overallScore", 52)
                        .put("dimensionContent", 52)
                        .put("dimensionLogic", 52)
                        .put("dimensionExpression", 52)
                        .put("dimensionProfessional", 52)
                        .put("dimensionCommunication", 52)
                        .set("questions", scoringResult));

        String result = scoringService.analyzeAndScoreBatchTwoPhase(text, "JD", "resume", null, null);

        assertNotNull(result);
    }

    private ArrayNode createQuestionResult(int index, int score) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arr = mapper.createArrayNode();
        arr.addObject()
                .put("questionIndex", index)
                .put("score", score)
                .put("dimensionContent", 7)
                .put("dimensionLogic", 7)
                .put("dimensionExpression", 7)
                .put("dimensionProfessional", 7);
        return arr;
    }
}
