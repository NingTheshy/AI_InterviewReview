package com.interview.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interview.ai.factory.AiClientFactory;
import com.interview.ai.service.AiConfigService;
import com.interview.ai.service.LlmClient;
import com.interview.ai.service.ScoringService;
import com.interview.ai.util.StructuredOutputInvoker;
import com.interview.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RubricScoringServiceTest {

    @Mock private AiClientFactory clientFactory;
    @Mock private AiConfigService configService;
    @Mock private StructuredOutputInvoker invoker;
    @Mock private LlmClient client;

    private final ObjectMapper mapper = new ObjectMapper();
    private RubricScoringService service;

    @BeforeEach
    void setUp() {
        service = new RubricScoringService(clientFactory, configService, invoker, mapper);
    }

    @Test
    void standardsAreIndependentAndScoresAreCalculatedByServer() throws Exception {
        String transcript = """
                [startTimeMs=1000,endTimeMs=2500,speakerId=0] 请解释缓存一致性。
                [startTimeMs=2600,endTimeMs=8000,speakerId=1] 我的回答提到了缓存和数据库。
                """;
        when(clientFactory.getDefaultLlmClient()).thenReturn(client);
        when(invoker.invoke(any(), anyString(), anyString(), isNull()))
                .thenReturn(json("""
                        [{"questionText":"请解释缓存一致性。","answerText":"我的回答提到了缓存和数据库。",
                          "questionType":"TECHNICAL",
                          "questionSpeakerId":"0","answerSpeakerId":"1",
                          "questionStartTimeMs":1000,"answerEndTimeMs":8000,"confidence":"HIGH"}]
                        """))
                .thenReturn(json(standards(100)))
                .thenReturn(json("""
                        [{"questionIndex":1,"improvementTip":"补充失效策略和边界。","items":[
                          {"id":"a","status":"FULLY_COVERED","evidence":"缓存","missingPoints":[],"incorrectClaims":[],"improvement":""},
                          {"id":"b","status":"MOSTLY_COVERED","evidence":"数据库","missingPoints":["失效策略"],"incorrectClaims":[],"improvement":"补充策略"},
                          {"id":"c","status":"PARTIALLY_COVERED","evidence":"缓存和数据库","missingPoints":["边界"],"incorrectClaims":[],"improvement":"补充边界"},
                          {"id":"d","status":"MISSING","evidence":"","missingPoints":["表达结构"],"incorrectClaims":[],"improvement":"梳理结构"},
                          {"id":"e","status":"INCORRECT","evidence":"我的回答","missingPoints":[],"incorrectClaims":["错误"],"improvement":"纠正错误"}
                        ]}]
                        """))
                .thenReturn(json("""
                        {"dimensionCommunication":80,"strengths":"有基础认识","weaknesses":"缺少边界条件","improvementSummary":"补充边界与实践"}
                        """));

        JsonNode result = mapper.readTree(service.analyze(transcript, "Java 后端", null,
                "Java 工程师", "互联网", 2, null, List.of()));
        JsonNode question = result.path("questions").get(0);
        assertEquals(45, question.path("score").asInt());
        assertEquals(88, question.path("dimensionContent").asInt());
        assertEquals(50, question.path("dimensionLogic").asInt());
        assertEquals(0, question.path("dimensionExpression").asInt());
        assertEquals(80, result.path("dimensionCommunication").asInt());
        assertEquals("满分示范", question.path("referenceAnswer").asText());
        assertEquals("INTERVIEWER", question.path("questionSpeaker").asText());
        assertEquals("CANDIDATE", question.path("answerSpeaker").asText());
        assertEquals(1, question.path("startTime").asInt());
        assertEquals(8, question.path("endTime").asInt());
        assertEquals("rubric-v1", question.path("rubricDetail").path("version").asText());

        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(invoker, times(4)).invoke(any(), prompts.capture(), anyString(), isNull());
        assertFalse(prompts.getAllValues().get(1).contains("我的回答提到了缓存和数据库"));
        assertTrue(prompts.getAllValues().get(2).contains("我的回答提到了缓存和数据库"));
    }

    @Test
    void invalidRubricWeightIsRetriedThenRejected() throws Exception {
        when(clientFactory.getDefaultLlmClient()).thenReturn(client);
        when(invoker.invoke(any(), anyString(), anyString(), isNull()))
                .thenReturn(json("""
                        [{"questionText":"问题","answerText":"回答","questionType":"TECHNICAL","confidence":"HIGH"}]
                        """))
                .thenReturn(json(standards(90)));

        assertThrows(BusinessException.class, () -> service.analyze("问题 回答", null, null,
                null, null, 3, null, List.of()));
        verify(invoker, times(3)).invoke(any(), anyString(), anyString(), isNull());
    }

    @Test
    void reusesSavedStandardWhenQuestionAndContextMatch() throws Exception {
        JsonNode original = json(standards(100)).get(0);
        ObjectNode savedDetail = mapper.createObjectNode();
        savedDetail.put("version", "rubric-v1");
        savedDetail.putObject("context")
                .put("positionTitle", "Java 工程师")
                .put("industry", "互联网")
                .put("companyTier", 2);
        savedDetail.set("items", original.path("items"));
        ScoringService.SavedRubric saved = new ScoringService.SavedRubric(
                "请解释缓存一致性。", "满分示范", savedDetail.toString());
        when(clientFactory.getDefaultLlmClient()).thenReturn(client);
        when(invoker.invoke(any(), anyString(), anyString(), isNull()))
                .thenReturn(json("""
                        [{"questionText":"请解释缓存一致性。","answerText":"我的回答","questionType":"TECHNICAL","confidence":"HIGH"}]
                        """))
                .thenReturn(json("""
                        [{"questionIndex":1,"improvementTip":"补充细节","items":[
                          {"id":"a","status":"MISSING","evidence":"","missingPoints":[],"incorrectClaims":[]},
                          {"id":"b","status":"MISSING","evidence":"","missingPoints":[],"incorrectClaims":[]},
                          {"id":"c","status":"MISSING","evidence":"","missingPoints":[],"incorrectClaims":[]},
                          {"id":"d","status":"MISSING","evidence":"","missingPoints":[],"incorrectClaims":[]},
                          {"id":"e","status":"MISSING","evidence":"","missingPoints":[],"incorrectClaims":[]}
                        ]}]
                        """))
                .thenReturn(json("""
                        {"dimensionCommunication":50,"strengths":"愿意回答","weaknesses":"缺少细节","improvementSummary":"补充细节"}
                        """));

        JsonNode result = mapper.readTree(service.analyze("转录", null, null,
                "Java 工程师", "互联网", 2, null, List.of(saved)));
        assertEquals(0, result.path("questions").get(0).path("score").asInt());
        verify(invoker, times(3)).invoke(any(), anyString(), anyString(), isNull());
    }

    private JsonNode json(String value) throws Exception {
        return mapper.readTree(value);
    }

    private String standards(int lastWeight) {
        return """
                [{"questionIndex":1,"referenceAnswer":"满分示范","items":[
                  {"id":"a","dimension":"CONTENT","title":"内容一","weight":20,"fullCreditCriteria":"准确"},
                  {"id":"b","dimension":"CONTENT","title":"内容二","weight":20,"fullCreditCriteria":"完整"},
                  {"id":"c","dimension":"LOGIC","title":"逻辑","weight":20,"fullCreditCriteria":"有推理"},
                  {"id":"d","dimension":"EXPRESSION","title":"表达","weight":20,"fullCreditCriteria":"清晰"},
                  {"id":"e","dimension":"PROFESSIONAL","title":"专业","weight":%d,"fullCreditCriteria":"有实践"}
                ]}]
                """.formatted(lastWeight == 100 ? 20 : 10);
    }
}
