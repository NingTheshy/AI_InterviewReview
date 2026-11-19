package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.interview.ai.factory.AiClientFactory;
import com.interview.ai.service.ScoringService;
import com.interview.common.constant.ConfigType;
import com.interview.interview.entity.Interview;
import com.interview.interview.entity.QuestionStandardCache;
import com.interview.interview.mapper.QuestionStandardCacheMapper;
import com.interview.interview.service.QuestionStandardCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** 技术题允许跨面试复用；个性化题严格限制到用户、面试和简历版本。 */
@Service
@RequiredArgsConstructor
public class QuestionStandardCacheServiceImpl implements QuestionStandardCacheService {
    private static final String PROMPT_VERSION = "rubric-v4";
    private static final Set<String> PERSONAL_TYPES = Set.of("PERSONAL", "PROJECT", "BEHAVIORAL");
    private final QuestionStandardCacheMapper cacheMapper;
    private final AiClientFactory aiClientFactory;
    private final ObjectMapper objectMapper;

    @Override
    public List<ScoringService.SavedRubric> load(Interview interview) {
        AiClientFactory.SnapshotMetadata model = aiClientFactory.snapshotMetadata(interview.getLlmConfigSnapshot(), ConfigType.LLM);
        LambdaQueryWrapper<QuestionStandardCache> query = new LambdaQueryWrapper<QuestionStandardCache>()
                .eq(QuestionStandardCache::getProvider, model.provider())
                .eq(QuestionStandardCache::getModelName, model.modelName())
                .eq(QuestionStandardCache::getConfigVersion, model.version())
                .eq(QuestionStandardCache::getPromptVersion, PROMPT_VERSION)
                .and(w -> w.eq(QuestionStandardCache::getScopeType, "GENERIC")
                        .or(personal -> personal.eq(QuestionStandardCache::getScopeType, "PERSONAL")
                                .eq(QuestionStandardCache::getUserId, interview.getUserId())
                                .eq(QuestionStandardCache::getInterviewId, interview.getId())
                                .eq(QuestionStandardCache::getResumeId, interview.getResumeId())));
        return cacheMapper.selectList(query).stream()
                .map(value -> new ScoringService.SavedRubric(value.getQuestionText(), value.getReferenceAnswer(), value.getRubricDetail()))
                .toList();
    }

    @Override
    public void save(Interview interview, String scoreResultJson) {
        try {
            AiClientFactory.SnapshotMetadata model = aiClientFactory.snapshotMetadata(interview.getLlmConfigSnapshot(), ConfigType.LLM);
            JsonNode questions = objectMapper.readTree(scoreResultJson).path("questions");
            if (!questions.isArray()) return;
            for (JsonNode question : questions) {
                if (!question.path("followUpTexts").isEmpty()) continue;
                String reference = question.path("referenceAnswer").asText("");
                JsonNode rubric = question.path("rubricDetail");
                if (reference.isBlank() || !rubric.isObject()) continue;
                String type = question.path("questionType").asText("OBJECTIVE");
                boolean personal = PERSONAL_TYPES.contains(type);
                String normalizedQuestion = normalize(question.path("questionText").asText());
                String context = normalizedQuestion + '|' + normalize(interview.getPositionTitle()) + '|'
                        + normalize(interview.getIndustry()) + '|'
                        + sha256(interview.getJdText() == null ? "" : interview.getJdText())
                        + '|' + model.provider() + '|' + model.modelName()
                        + '|' + model.version() + '|' + PROMPT_VERSION
                        + (personal ? '|' + interview.getUserId() + '|' + interview.getId() + '|' + interview.getResumeId() : "");
                String key = sha256(context);
                if (cacheMapper.selectCount(new LambdaQueryWrapper<QuestionStandardCache>()
                        .eq(QuestionStandardCache::getCacheKey, key)) > 0) continue;
                QuestionStandardCache value = new QuestionStandardCache();
                value.setCacheKey(key); value.setScopeType(personal ? "PERSONAL" : "GENERIC");
                value.setQuestionText(question.path("questionText").asText()); value.setContextDigest(sha256(context));
                if (personal) { value.setUserId(interview.getUserId()); value.setInterviewId(interview.getId()); value.setResumeId(interview.getResumeId()); }
                value.setProvider(model.provider()); value.setModelName(model.modelName()); value.setConfigVersion(model.version());
                value.setPromptVersion(PROMPT_VERSION); value.setReferenceAnswer(reference);
                // 通用缓存可跨用户复用，只保存标准，不复制当前候选人的证据与错误。
                ObjectNode standard = objectMapper.createObjectNode();
                standard.put("version", PROMPT_VERSION);
                for (String field : List.of("domain", "questionCategory", "difficultyReason")) {
                    standard.put(field, rubric.path(field).asText());
                }
                standard.set("knowledgePoints", rubric.path("knowledgePoints").deepCopy());
                standard.put("knowledgeDepth", rubric.path("knowledgeDepth").asInt());
                standard.put("problemComplexity", rubric.path("problemComplexity").asInt());
                standard.set("context", rubric.path("context").deepCopy());
                standard.set("keyPoints", rubric.path("keyPoints").deepCopy());
                ArrayNode items = standard.putArray("items");
                for (JsonNode item : rubric.path("items")) {
                    items.add(objectMapper.createObjectNode()
                            .put("id", item.path("id").asText())
                            .put("dimension", item.path("dimension").asText())
                            .put("title", item.path("title").asText())
                            .put("weight", item.path("weight").asInt())
                            .put("fullCreditCriteria", item.path("fullCreditCriteria").asText()));
                }
                value.setRubricDetail(objectMapper.writeValueAsString(standard)); cacheMapper.insert(value);
            }
        } catch (Exception e) {
            throw new IllegalStateException("标准答案缓存保存失败", e);
        }
    }

    private String normalize(String value) { return value == null ? "" : value.toLowerCase().replaceAll("[\\p{P}\\p{Z}\\s]+", ""); }
    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
