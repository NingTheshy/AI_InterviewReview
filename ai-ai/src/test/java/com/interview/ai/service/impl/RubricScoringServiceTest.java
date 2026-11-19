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
                          "questionStartTimeMs":1000,"answerEndTimeMs":8000,"confidence":"HIGH",
                          "followUpTexts":[],"confidenceReason":""}]
                        """))
                .thenReturn(json(standards(100)))
                .thenReturn(json("""
                        [{"questionIndex":1,"improvementTip":"补充失效策略和边界。","items":[
                          {"id":"a","status":"FULLY_COVERED","criticalError":false,"evidence":"缓存","missingPoints":[],"incorrectClaims":[],"improvement":""},
                          {"id":"b","status":"MOSTLY_COVERED","criticalError":false,"evidence":"数据库","missingPoints":["失效策略"],"incorrectClaims":[],"improvement":"补充策略"},
                          {"id":"c","status":"PARTIALLY_COVERED","criticalError":false,"evidence":"缓存和数据库","missingPoints":["边界"],"incorrectClaims":[],"improvement":"补充边界"},
                          {"id":"d","status":"MISSING","criticalError":false,"evidence":"","missingPoints":["表达结构"],"incorrectClaims":[],"improvement":"梳理结构"},
                          {"id":"e","status":"INCORRECT","criticalError":false,"evidence":"我的回答","missingPoints":[],"incorrectClaims":["错误"],"improvement":"纠正错误"}
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
        assertEquals("rubric-v4", question.path("rubricDetail").path("version").asText());
        assertEquals("L2", question.path("difficultyLevel").asText());
        assertEquals("缓存", question.path("answerEvidence").get(0).asText());

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
                        [{"questionText":"问题","answerText":"回答","questionType":"TECHNICAL","confidence":"HIGH",
                          "followUpTexts":[],"confidenceReason":""}]
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
        savedDetail.put("version", "rubric-v4");
        for (String field : List.of("domain", "questionCategory", "difficultyReason")) {
            savedDetail.put(field, original.path(field).asText());
        }
        savedDetail.set("knowledgePoints", original.path("knowledgePoints"));
        savedDetail.put("knowledgeDepth", original.path("knowledgeDepth").asInt());
        savedDetail.put("problemComplexity", original.path("problemComplexity").asInt());
        savedDetail.putObject("context")
                .put("positionTitle", "Java 工程师")
                .put("industry", "互联网")
                .put("questionType", "TECHNICAL")
                .put("jdDigest", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .put("promptVersion", "rubric-v4");
        savedDetail.set("keyPoints", original.path("keyPoints"));
        savedDetail.set("items", original.path("items"));
        ScoringService.SavedRubric saved = new ScoringService.SavedRubric(
                "请解释缓存一致性。", "满分示范", savedDetail.toString());
        when(clientFactory.getDefaultLlmClient()).thenReturn(client);
        when(invoker.invoke(any(), anyString(), anyString(), isNull()))
                .thenReturn(json("""
                        [{"questionText":"请解释缓存一致性。","answerText":"我的回答","questionType":"TECHNICAL","confidence":"HIGH",
                          "followUpTexts":[],"confidenceReason":""}]
                        """))
                .thenReturn(json("""
                        [{"questionIndex":1,"improvementTip":"补充细节","items":[
                          {"id":"a","status":"MISSING","criticalError":false,"evidence":"","missingPoints":[],"incorrectClaims":[]},
                          {"id":"b","status":"MISSING","criticalError":false,"evidence":"","missingPoints":[],"incorrectClaims":[]},
                          {"id":"c","status":"MISSING","criticalError":false,"evidence":"","missingPoints":[],"incorrectClaims":[]},
                          {"id":"d","status":"MISSING","criticalError":false,"evidence":"","missingPoints":[],"incorrectClaims":[]},
                          {"id":"e","status":"MISSING","criticalError":false,"evidence":"","missingPoints":[],"incorrectClaims":[]}
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

    @Test
    void criticalErrorCapsQuestionScoreAndKeepsVerbatimEvidence() throws Exception {
        when(clientFactory.getDefaultLlmClient()).thenReturn(client);
        when(invoker.invoke(any(), anyString(), anyString(), isNull()))
                .thenReturn(json("""
                        [{"questionText":"如何处理缓存一致性？","followUpTexts":[],"answerText":"缓存永远不需要失效。",
                          "questionType":"TECHNICAL","confidence":"HIGH","confidenceReason":""}]
                        """))
                .thenReturn(json(standards(100)))
                .thenReturn(json("""
                        [{"questionIndex":1,"improvementTip":"先核对缓存失效策略。","items":[
                          {"id":"a","status":"INCORRECT","criticalError":true,"evidence":"永远不需要失效","missingPoints":[],"incorrectClaims":["缓存无需失效"],"improvement":"说明失效条件"},
                          {"id":"b","status":"FULLY_COVERED","criticalError":false,"evidence":"缓存","missingPoints":[],"incorrectClaims":[]},
                          {"id":"c","status":"FULLY_COVERED","criticalError":false,"evidence":"缓存","missingPoints":[],"incorrectClaims":[]},
                          {"id":"d","status":"FULLY_COVERED","criticalError":false,"evidence":"缓存","missingPoints":[],"incorrectClaims":[]},
                          {"id":"e","status":"FULLY_COVERED","criticalError":false,"evidence":"缓存","missingPoints":[],"incorrectClaims":[]}
                        ]}]
                        """))
                .thenReturn(json("""
                        {"dimensionCommunication":60,"strengths":"回答直接","weaknesses":"缓存失效认知错误","improvementSummary":"练习失效策略"}
                        """));

        JsonNode question = mapper.readTree(service.analyze("转录", "Java 后端", null,
                "Java 工程师", "互联网", 2, null, List.of())).path("questions").get(0);
        assertEquals(69, question.path("score").asInt());
        assertTrue(question.path("rubricDetail").path("criticalErrorCapApplied").asBoolean());
        assertEquals("永远不需要失效", question.path("answerEvidence").get(0).asText());
        assertEquals("缓存无需失效", question.path("criticalErrors").get(0).asText());
    }

    @Test
    void unansweredQuestionIsScoredZeroWithoutFabricatingAnswerEvidence() throws Exception {
        when(clientFactory.getDefaultLlmClient()).thenReturn(client);
        when(invoker.invoke(any(), anyString(), anyString(), isNull()))
                .thenReturn(json("""
                        [{"questionText":"解释事务隔离级别。","followUpTexts":[],"answerText":"",
                          "questionType":"TECHNICAL","confidence":"HIGH","confidenceReason":""}]
                        """))
                .thenReturn(json(standards(100)));

        JsonNode result = mapper.readTree(service.analyze("转录", null, null,
                "Java 工程师", "互联网", 2, null, List.of()));
        JsonNode question = result.path("questions").get(0);
        assertEquals(0, question.path("score").asInt());
        assertTrue(question.path("includedInScore").asBoolean());
        assertTrue(question.path("answerEvidence").isEmpty());
        assertEquals(0, result.path("dimensionCommunication").asInt());
        verify(invoker, times(2)).invoke(any(), anyString(), anyString(), isNull());
    }

    @Test
    void uncertainQuestionKeepsReasonAndDoesNotEnterScore() throws Exception {
        when(clientFactory.getDefaultLlmClient()).thenReturn(client);
        when(invoker.invoke(any(), anyString(), anyString(), isNull()))
                .thenReturn(json("""
                        [{"questionText":"请介绍项目。","followUpTexts":["你负责哪部分？"],"answerText":"可能负责接口",
                          "questionType":"PROJECT","confidence":"LOW","confidenceReason":"说话人角色无法确认"}]
                        """));

        JsonNode result = mapper.readTree(service.analyze("转录", null, "项目经历",
                "Java 工程师", "互联网", 2, null, List.of()));
        JsonNode question = result.path("questions").get(0);
        assertEquals(0, result.path("scorableQuestionCount").asInt());
        assertFalse(question.path("includedInScore").asBoolean());
        assertEquals("说话人角色无法确认", question.path("lowConfidenceReason").asText());
        assertEquals("你负责哪部分？", question.path("followUpTexts").get(0).asText());
        verify(invoker, times(1)).invoke(any(), anyString(), anyString(), isNull());
    }

    @Test
    void rejectsQuestionOrAnswerNotPresentInAsrSentences() throws Exception {
        String transcript = """
                [startTimeMs=100,endTimeMs=900,speakerId=0,speakerRole=INTERVIEWER] 请解释事务。
                [startTimeMs=1000,endTimeMs=2000,speakerId=1,speakerRole=CANDIDATE] 我只知道提交。
                """;
        when(clientFactory.getDefaultLlmClient()).thenReturn(client);
        when(invoker.invoke(any(), anyString(), anyString(), isNull())).thenReturn(json("""
                [{"questionText":"请解释事务。","followUpTexts":[],"answerText":"我精通所有隔离级别。",
                  "questionType":"TECHNICAL","questionSpeakerId":"0","answerSpeakerId":"1",
                  "questionStartTimeMs":100,"answerEndTimeMs":2000,"confidence":"HIGH","confidenceReason":""}]
                """));

        assertThrows(BusinessException.class, () -> service.analyze(transcript, null, null,
                "Java 工程师", "互联网", 2, null, List.of()));
        verify(invoker, times(2)).invoke(any(), anyString(), anyString(), isNull());
    }

    @Test
    void mergesFollowUpAnswersAndAcceptsDifferentCorrectWording() throws Exception {
        String transcript = """
                [startTimeMs=1000,endTimeMs=1500,speakerId=0] 如何处理并发？
                [startTimeMs=1600,endTimeMs=2100,speakerId=1] 使用版本号。
                [startTimeMs=2200,endTimeMs=2700,speakerId=0] 失败了怎么办？
                [startTimeMs=2800,endTimeMs=4000,speakerId=1] 失败后重试。
                """;
        when(clientFactory.getDefaultLlmClient()).thenReturn(client);
        when(invoker.invoke(any(), anyString(), anyString(), isNull()))
                .thenReturn(json("""
                        [{"questionText":"如何处理并发？","followUpTexts":[],"answerText":"使用版本号。",
                          "questionType":"TECHNICAL","questionSpeakerId":"0","answerSpeakerId":"1",
                          "questionStartTimeMs":1000,"answerEndTimeMs":2100,"confidence":"HIGH","confidenceReason":""},
                         {"questionText":"如何处理并发？","followUpTexts":["失败了怎么办？"],"answerText":"失败后重试。",
                          "questionType":"TECHNICAL","questionSpeakerId":"0","answerSpeakerId":"1",
                          "questionStartTimeMs":1000,"answerEndTimeMs":4000,"confidence":"HIGH","confidenceReason":""}]
                        """))
                .thenReturn(json(standards(100)))
                .thenReturn(json("""
                        [{"questionIndex":1,"improvementTip":"继续结合场景练习。","items":[
                          {"id":"a","status":"FULLY_COVERED","criticalError":false,"evidence":"版本号","missingPoints":[],"incorrectClaims":[]},
                          {"id":"b","status":"FULLY_COVERED","criticalError":false,"evidence":"重试","missingPoints":[],"incorrectClaims":[]},
                          {"id":"c","status":"FULLY_COVERED","criticalError":false,"evidence":"版本号","missingPoints":[],"incorrectClaims":[]},
                          {"id":"d","status":"FULLY_COVERED","criticalError":false,"evidence":"重试","missingPoints":[],"incorrectClaims":[]},
                          {"id":"e","status":"FULLY_COVERED","criticalError":false,"evidence":"版本号","missingPoints":[],"incorrectClaims":[]}
                        ]}]
                        """))
                .thenReturn(json("""
                        {"dimensionCommunication":80,"strengths":"能说明处理方法","weaknesses":"可补充实践","improvementSummary":"结合场景练习"}
                        """));

        JsonNode result = mapper.readTree(service.analyze(transcript, "并发控制", null,
                "Java 工程师", "互联网", 2, null, List.of()));
        JsonNode question = result.path("questions").get(0);
        assertEquals(1, result.path("questions").size());
        assertEquals("使用版本号。\n失败后重试。", question.path("answerText").asText());
        assertEquals("失败了怎么办？", question.path("followUpTexts").get(0).asText());
        assertEquals(100, question.path("score").asInt());
    }

    private JsonNode json(String value) throws Exception {
        return mapper.readTree(value);
    }

    private String standards(int lastWeight) {
        return """
                [{"questionIndex":1,"domain":"数据库","knowledgePoints":["缓存一致性"],"questionCategory":"原理题",
                  "knowledgeDepth":2,"problemComplexity":2,"difficultyReason":"基本机制与单步分析",
                  "referenceAnswer":"满分示范","keyPoints":["准确","完整"],"items":[
                  {"id":"a","dimension":"CONTENT","title":"内容一","weight":20,"fullCreditCriteria":"准确"},
                  {"id":"b","dimension":"CONTENT","title":"内容二","weight":20,"fullCreditCriteria":"完整"},
                  {"id":"c","dimension":"LOGIC","title":"逻辑","weight":20,"fullCreditCriteria":"有推理"},
                  {"id":"d","dimension":"EXPRESSION","title":"表达","weight":20,"fullCreditCriteria":"清晰"},
                  {"id":"e","dimension":"PROFESSIONAL","title":"专业","weight":%d,"fullCreditCriteria":"有实践"}
                ]}]
                """.formatted(lastWeight == 100 ? 20 : 10);
    }
}
