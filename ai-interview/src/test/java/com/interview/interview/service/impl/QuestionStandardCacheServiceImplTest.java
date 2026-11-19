package com.interview.interview.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.factory.AiClientFactory;
import com.interview.common.constant.ConfigType;
import com.interview.interview.entity.Interview;
import com.interview.interview.entity.QuestionStandardCache;
import com.interview.interview.mapper.QuestionStandardCacheMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionStandardCacheServiceImplTest {

    @Mock private QuestionStandardCacheMapper cacheMapper;
    @Mock private AiClientFactory clientFactory;

    @Test
    void genericCacheSeparatesJdAndDoesNotPersistCandidateEvidence() throws Exception {
        when(clientFactory.snapshotMetadata(any(), eq(ConfigType.LLM)))
                .thenReturn(new AiClientFactory.SnapshotMetadata(1L, "deepseek", "deepseek-chat", "1"));
        when(cacheMapper.selectCount(any())).thenReturn(0L);
        QuestionStandardCacheServiceImpl service = new QuestionStandardCacheServiceImpl(
                cacheMapper, clientFactory, new ObjectMapper());
        Interview interview = new Interview();
        interview.setId(11L);
        interview.setUserId(12L);
        interview.setPositionTitle("Java 工程师");
        interview.setCompanyName("示例公司");
        interview.setIndustry("互联网");
        interview.setCompanyTier(2);
        interview.setLlmConfigSnapshot("encrypted");

        interview.setJdText("要求缓存经验");
        service.save(interview, result());
        interview.setJdText("要求数据库经验");
        service.save(interview, result());

        ArgumentCaptor<QuestionStandardCache> saved = ArgumentCaptor.forClass(QuestionStandardCache.class);
        verify(cacheMapper, times(2)).insert(saved.capture());
        assertNotEquals(saved.getAllValues().get(0).getCacheKey(), saved.getAllValues().get(1).getCacheKey());
        for (QuestionStandardCache cache : saved.getAllValues()) {
            assertEquals("rubric-v4", cache.getPromptVersion());
            JsonNode detail = new ObjectMapper().readTree(cache.getRubricDetail());
            assertEquals("准确性", detail.path("keyPoints").get(0).asText());
            assertFalse(detail.toString().contains("候选人的私密原话"));
            assertFalse(detail.path("items").get(0).has("evidence"));
        }
    }

    private String result() {
        return """
                {"questions":[{"questionText":"解释缓存一致性","questionType":"TECHNICAL",
                  "referenceAnswer":"面试口述范例","rubricDetail":{"version":"rubric-v4",
                  "domain":"数据库","knowledgePoints":["缓存一致性"],"questionCategory":"原理题",
                  "knowledgeDepth":2,"problemComplexity":2,"difficultyReason":"基本机制",
                  "context":{"positionTitle":"Java 工程师","companyName":"示例公司"},
                  "keyPoints":["准确性"],"items":[{"id":"a","dimension":"CONTENT","title":"正确",
                  "weight":100,"fullCreditCriteria":"准确","evidence":"候选人的私密原话",
                  "incorrectClaims":["私密错误"]}]}}]}
                """;
    }
}
