package com.interview.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interview.ai.entity.AiConfig;
import com.interview.ai.factory.AiClientFactory;
import com.interview.ai.service.AiConfigService;
import com.interview.ai.service.LlmClient;
import com.interview.ai.service.ScoringService;
import com.interview.ai.util.StructuredOutputInvoker;
import com.interview.common.constant.CompanyTier;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于标准答案量表的面试评分编排器。
 *
 * <p>评分流程分为问答识别、满分标准生成（或复用）、回答比对、服务端分数计算和整体总结五步。
 * LLM 只提供语义判断和结构化证据；权重、分数和 ASR 元数据边界均由服务端校验，避免模型自由
 * 输出直接影响最终结果。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RubricScoringService {

    private static final int TRANSCRIPT_CHUNK_SIZE = 5000;
    private static final int QUESTION_BATCH_SIZE = 8;
    private static final Pattern TRANSCRIPT_MARKER = Pattern.compile(
            "\\[startTimeMs=(\\d+),endTimeMs=(\\d+),speakerId=([^,\\]]+)(?:,speakerRole=([^\\]]+))?]");
    private static final List<String> DIMENSIONS = List.of("CONTENT", "LOGIC", "EXPRESSION", "PROFESSIONAL");
    private static final Map<String, Double> STATUS_FACTOR = Map.of(
            "FULLY_COVERED", 1.0,
            "MOSTLY_COVERED", 0.75,
            "PARTIALLY_COVERED", 0.5,
            "MISSING", 0.0,
            "INCORRECT", 0.0,
            "NOT_APPLICABLE", 0.0);

    private final AiClientFactory clientFactory;
    private final AiConfigService aiConfigService;
    private final StructuredOutputInvoker outputInvoker;
    private final ObjectMapper mapper;

    /**
     * 执行一次可追溯的量表评分。
     *
     * <p>问题按批处理以控制上下文长度。每一批的标准和比对结果都以 questionIndex 关联，而非
     * 依赖数组顺序；最终将各维度均分和问题明细交由解析器持久化。</p>
     *
     * @param transcript 含 ASR 时间戳与说话人编号的转写文本
     * @param savedRubrics 同岗位上下文下可复用的历史满分量表
     * @return 供 {@code ScoreResultParser} 解析的评分 JSON
     */
    public String analyze(String transcript, String jdText, String resumeText, String positionTitle,
                          String industry, Integer companyTier, Long configId,
                          List<ScoringService.SavedRubric> savedRubrics) {
        return analyzeWithClient(resolveClient(configId), transcript, jdText, resumeText, positionTitle,
                industry, companyTier, savedRubrics);
    }

    public String analyzeWithClient(LlmClient client, String transcript, String jdText, String resumeText,
                                    String positionTitle, String industry, Integer companyTier,
                                    List<ScoringService.SavedRubric> savedRubrics) {
        return analyzeWithClient(client, transcript, jdText, resumeText, positionTitle, null, industry,
                companyTier, savedRubrics);
    }

    public String analyzeWithClient(LlmClient client, String transcript, String jdText, String resumeText,
                                    String positionTitle, String companyName, String industry, Integer companyTier,
                                    List<ScoringService.SavedRubric> savedRubrics) {
        if (transcript == null || transcript.isBlank()) {
            throw failure("面试转录为空，无法评分");
        }
        Long configId = null;
        String tierName = CompanyTier.fromCode(companyTier == null ? 3 : companyTier).getName();
        ArrayNode questions = identifyQuestions(client, transcript, configId);
        ArrayNode scoredQuestions = mapper.createArrayNode();

        for (int start = 0; start < questions.size(); start += QUESTION_BATCH_SIZE) {
            ArrayNode batch = mapper.createArrayNode();
            for (int i = start; i < Math.min(start + QUESTION_BATCH_SIZE, questions.size()); i++) {
                batch.add(questions.get(i));
            }
            ArrayNode scoringBatch = mapper.createArrayNode();
            for (JsonNode question : batch) {
                if ("HIGH".equals(question.path("confidence").asText())) {
                    scoringBatch.add(question);
                } else {
                    scoredQuestions.add(unscoredQuestion(question));
                }
            }
            if (scoringBatch.isEmpty()) continue;
            ArrayNode standards = resolveStandards(client, scoringBatch, jdText, resumeText,
                    positionTitle, companyName, industry, tierName, companyTier, configId, savedRubrics);
            ArrayNode assessments = compareAnswers(client, scoringBatch, standards, configId);
            Map<Integer, JsonNode> standardByIndex = byQuestionIndex(standards);
            Map<Integer, JsonNode> assessmentByIndex = byQuestionIndex(assessments);
            for (JsonNode question : scoringBatch) {
                int index = question.path("questionIndex").asInt();
                scoredQuestions.add(scoreQuestion(question, standardByIndex.get(index),
                        assessmentByIndex.get(index), positionTitle, companyName, industry, companyTier, resumeText));
            }
        }

        ArrayNode includedQuestions = mapper.createArrayNode();
        for (JsonNode question : scoredQuestions) {
            if (question.path("includedInScore").asBoolean(false)) includedQuestions.add(question);
        }
        JsonNode summary = includedQuestions.isEmpty() ? emptySummary() : summarize(client, includedQuestions, configId);
        ObjectNode result = mapper.createObjectNode();
        result.put("scorableQuestionCount", includedQuestions.size());
        result.put("dimensionContent", average(includedQuestions, "dimensionContent"));
        result.put("dimensionLogic", average(includedQuestions, "dimensionLogic"));
        result.put("dimensionExpression", average(includedQuestions, "dimensionExpression"));
        result.put("dimensionProfessional", average(includedQuestions, "dimensionProfessional"));
        result.put("dimensionCommunication", summary.path("dimensionCommunication").asInt());
        // ScoreResultParser 统一使用 ScoringWeights 计算整体总分。
        result.put("overallScore", 0);
        result.put("improvementSummary", summary.path("improvementSummary").asText());
        result.put("strengths", summary.path("strengths").asText());
        result.put("weaknesses", summary.path("weaknesses").asText());
        result.set("questions", scoredQuestions);
        return result.toString();
    }

    /**
     * 优先复用上下文匹配的历史量表，仅为缺失问题调用模型生成标准答案。
     *
     * <p>复用条件同时包含规范化问题、岗位、行业和公司档次，避免将低门槛岗位的评分标准误用到
     * 高门槛面试。</p>
     */
    private ArrayNode resolveStandards(LlmClient client, ArrayNode questions, String jdText,
                                       String resumeText, String positionTitle, String companyName, String industry,
                                       String tierName, Integer companyTier, Long configId,
                                       List<ScoringService.SavedRubric> savedRubrics) {
        ArrayNode missingGeneric = mapper.createArrayNode();
        ArrayNode missingPersonal = mapper.createArrayNode();
        Map<Integer, JsonNode> standardsByIndex = new HashMap<>();
        for (JsonNode question : questions) {
            JsonNode reused = findReusableStandard(question, positionTitle, companyName, industry,
                    companyTier, resumeText, savedRubrics);
            if (reused == null) {
                (isPersonal(question) ? missingPersonal : missingGeneric).add(question);
            } else {
                standardsByIndex.put(question.path("questionIndex").asInt(), reused);
            }
        }
        if (!missingGeneric.isEmpty()) {
            ArrayNode generated = generateStandards(client, missingGeneric, jdText, null,
                    positionTitle, companyName, industry, tierName, configId, false);
            standardsByIndex.putAll(byQuestionIndex(generated));
        }
        if (!missingPersonal.isEmpty()) {
            ArrayNode generated = generateStandards(client, missingPersonal, jdText, resumeText,
                    positionTitle, companyName, industry, tierName, configId, true);
            standardsByIndex.putAll(byQuestionIndex(generated));
        }
        ArrayNode ordered = mapper.createArrayNode();
        for (JsonNode question : questions) {
            ordered.add(standardsByIndex.get(question.path("questionIndex").asInt()));
        }
        return ordered;
    }

    /** 校验历史量表的版本和上下文后重建为当前批次可用的标准对象。 */
    private JsonNode findReusableStandard(JsonNode question, String positionTitle, String companyName, String industry,
                                          Integer companyTier, String resumeText,
                                          List<ScoringService.SavedRubric> savedRubrics) {
        if (savedRubrics == null) return null;
        for (ScoringService.SavedRubric saved : savedRubrics) {
            if (!normalizedQuestion(question.path("questionText").asText())
                    .equals(normalizedQuestion(saved.questionText()))) continue;
            if (saved.referenceAnswer() == null || saved.referenceAnswer().isBlank()
                    || saved.rubricDetail() == null || saved.rubricDetail().isBlank()) continue;
            try {
                JsonNode detail = mapper.readTree(saved.rubricDetail());
                JsonNode context = detail.path("context");
                if (!"rubric-v2".equals(detail.path("version").asText())
                        || !Objects.equals(safe(positionTitle), context.path("positionTitle").asText())
                        || !Objects.equals(safe(companyName), context.path("companyName").asText())
                        || !Objects.equals(safe(industry), context.path("industry").asText())
                        || (companyTier != null && context.path("companyTier").asInt(-1) != companyTier)
                        || !Objects.equals(question.path("questionType").asText("OBJECTIVE"), context.path("questionType").asText())
                        || (isPersonal(question) && !Objects.equals(digest(resumeText), context.path("resumeDigest").asText()))) {
                    continue;
                }
                ObjectNode standard = mapper.createObjectNode();
                standard.put("questionIndex", question.path("questionIndex").asInt());
                standard.put("referenceAnswer", saved.referenceAnswer());
                ArrayNode items = standard.putArray("items");
                for (JsonNode old : detail.path("items")) {
                    items.add(mapper.createObjectNode()
                            .put("id", old.path("id").asText())
                            .put("dimension", old.path("dimension").asText())
                            .put("title", old.path("title").asText())
                            .put("weight", old.path("weight").asInt())
                            .put("fullCreditCriteria", old.path("fullCreditCriteria").asText()));
                }
                ArrayNode oneStandard = mapper.createArrayNode().add(standard);
                ArrayNode oneQuestion = mapper.createArrayNode().add(question);
                validateStandards(oneStandard, oneQuestion);
                return standard;
            } catch (Exception ignored) {
                log.warn("已保存量表无法复用，重新生成: questionIndex={}", question.path("questionIndex").asInt());
            }
        }
        return null;
    }

    private String normalizedQuestion(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").trim();
    }

    private boolean isPersonal(JsonNode question) {
        return Set.of("PERSONAL", "PROJECT", "BEHAVIORAL")
                .contains(question.path("questionType").asText("OBJECTIVE"));
    }

    /**
     * 按自然边界切分长转写并识别问答对。
     *
     * <p>每个分块的结果都要回填原始 ASR 时间戳和 speakerId；{@link #validateExtraction(JsonNode, String)}
     * 会拒绝不属于当前分块的元数据，防止模型猜测角色或时间。</p>
     */
    private ArrayNode identifyQuestions(LlmClient client, String transcript, Long configId) {
        List<String> sentenceLines = transcript.lines().filter(line -> !line.isBlank()).toList();
        List<String> chunks = sentenceChunks(sentenceLines);
        List<JsonNode> unique = new ArrayList<>();
        for (String chunk : chunks) {
            String prompt = "请从下面的面试转录中提取真实的问答对。保留原文，不要补写问题或回答。"
                    + "每句话前的 startTimeMs、endTimeMs、speakerId 和 speakerRole 均来自 ASR，必须原样引用。"
                    + "遇到段首或段尾不完整的问答，尽量保留可确认的部分。\n\n" + chunk;
            String system = """
                    你是面试转录整理员。根据语义及说话人标记区分面试官提问和候选人回答。
                    返回 JSON 数组，每项格式：
                    {"questionText":"原文问题","answerText":"原文回答","questionType":"TECHNICAL|OBJECTIVE|PERSONAL|PROJECT|BEHAVIORAL",
                     "questionSpeakerId":"提问句的原始speakerId","answerSpeakerId":"回答句的原始speakerId",
                     "questionStartTimeMs":提问首句的startTimeMs,"answerEndTimeMs":回答末句的endTimeMs,
                     "confidence":"HIGH或LOW"}
                    追问与补充可归并为同一问答；无法确认边界时标记 LOW。只返回真实出现的问答。
                    """;
            JsonNode extracted = invokeValidated(client, prompt, system, configId,
                    node -> validateExtraction(node, chunk));
            extracted.forEach(question -> { if (!isDuplicate(unique, question)) unique.add(question.deepCopy()); });
        }
        ArrayNode questions = mapper.createArrayNode();
        for (JsonNode question : unique) {
            ObjectNode item = mapper.createObjectNode();
            item.put("questionIndex", questions.size() + 1);
            item.put("questionText", question.path("questionText").asText());
            item.put("answerText", question.path("answerText").asText());
            item.put("confidence", question.path("confidence").asText());
            item.put("questionType", question.path("questionType").asText("OBJECTIVE"));
            copyText(question, item, "questionSpeakerId"); copyText(question, item, "answerSpeakerId");
            copyLong(question, item, "questionStartTimeMs"); copyLong(question, item, "answerEndTimeMs");
            questions.add(item);
        }
        if (questions.isEmpty()) throw failure("未能从转录中识别面试问答");
        log.info("面试转录识别出 {} 个问答对", questions.size());
        return questions;
    }

    private List<String> sentenceChunks(List<String> lines) {
        if (lines.isEmpty()) return List.of();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < lines.size()) {
            StringBuilder chunk = new StringBuilder();
            int end = start;
            while (end < lines.size() && (chunk.length() == 0 || chunk.length() + lines.get(end).length() + 1 <= TRANSCRIPT_CHUNK_SIZE)) {
                if (chunk.length() > 0) chunk.append('\n');
                chunk.append(lines.get(end++));
            }
            chunks.add(chunk.toString());
            if (end >= lines.size()) break;
            start = Math.max(start + 1, end - 4);
        }
        return chunks;
    }

    private boolean isDuplicate(List<JsonNode> existing, JsonNode candidate) {
        String normalized = normalizedQuestion(candidate.path("questionText").asText());
        long start = candidate.path("questionStartTimeMs").asLong(-1);
        long end = candidate.path("answerEndTimeMs").asLong(-1);
        return existing.stream().anyMatch(old -> normalized.equals(normalizedQuestion(old.path("questionText").asText()))
                && overlaps(start, end, old.path("questionStartTimeMs").asLong(-1), old.path("answerEndTimeMs").asLong(-1)));
    }

    private boolean overlaps(long aStart, long aEnd, long bStart, long bEnd) {
        return aStart >= 0 && bStart >= 0 && aStart <= bEnd + 5000 && bStart <= aEnd + 5000;
    }

    /** 为当前批次生成候选人回答应达到的满分标准和可计分条目。 */
    private ArrayNode generateStandards(LlmClient client, ArrayNode questions, String jdText,
                                        String resumeText, String positionTitle, String companyName, String industry,
                                        String tierName, Long configId, boolean includeResume) {
        ArrayNode input = mapper.createArrayNode();
        for (JsonNode question : questions) {
            input.add(mapper.createObjectNode()
                    .put("questionIndex", question.path("questionIndex").asInt())
                    .put("questionText", question.path("questionText").asText())
                    .put("questionType", question.path("questionType").asText("OBJECTIVE")));
        }
        String prompt = "题目：" + input + "\n岗位：" + safe(positionTitle) + "\n公司：" + safe(companyName) + "\n行业：" + safe(industry)
                + "\n企业等级：" + tierName + "\nJD：" + safe(jdText)
                + (includeResume ? "\n候选人简历：" + safe(resumeText) : "");
        String system = """
                你是资深面试官。根据题型、题目及岗位背景，为每题独立建立满分标准。
                TECHNICAL 和 OBJECTIVE 题不得使用候选人简历改变客观结论；PERSONAL、PROJECT、BEHAVIORAL
                题必须以简历事实为边界生成个性化示范回答，严禁编造简历中不存在的经历。
                你不会看到候选人的回答。标准答案应说明准确结论、核心原理、边界条件和权衡。
                返回与输入题目一一对应的 JSON 数组：
                [{"questionIndex":1,"referenceAnswer":"完整的满分示范回答","items":[
                  {"id":"该题唯一的短标识","dimension":"CONTENT|LOGIC|EXPRESSION|PROFESSIONAL",
                   "title":"评分要点","weight":20,"fullCreditCriteria":"获得满分的可检验标准"}
                ]}]
                每题必须有 5 至 8 个要点，四个维度均至少出现一次，正整数权重合计必须恰好为 100。
                CONTENT 评估准确性和内容完整性；LOGIC 评估结构、推理及边界；
                EXPRESSION 评估表达清晰度；PROFESSIONAL 评估专业深度及实践。
                对开放式问题，允许不同的合理方案获得满分，不将示范回答当作唯一措辞。
                """;
        JsonNode output = invokeValidated(client, prompt, system, configId,
                node -> validateStandards(node, questions));
        return (ArrayNode) output;
    }

    /** 将候选人回答逐项与同 questionIndex 的量表比较，只接收覆盖状态和证据描述。 */
    private ArrayNode compareAnswers(LlmClient client, ArrayNode questions, ArrayNode standards, Long configId) {
        String prompt = "固定评分标准：" + standards + "\n候选人问答：" + questions;
        String system = """
                你是严谨的面试评分员。逐项对照固定量表评估候选人回答。
                允许语义等价的正确回答；不要因与示范回答措辞不同而扣分。
                不可修改标准、权重或题号，也不要输出分数。
                只返回 JSON 数组，每题格式：
                {"questionIndex":1,"improvementTip":"针对性改进建议","items":[
                  {"id":"量表中的原 id","status":"FULLY_COVERED|MOSTLY_COVERED|PARTIALLY_COVERED|MISSING|INCORRECT|NOT_APPLICABLE",
                   "evidence":"候选人回答中的原文连续片段","missingPoints":["未回答的关键点"],
                   "incorrectClaims":["具体错误"],"improvement":"该要点的改进方法"}
                ]}
                FULLY_COVERED 表示准确且完整，MOSTLY_COVERED 表示核心覆盖但有次要遗漏，
                PARTIALLY_COVERED 表示相关但明显不完整，MISSING 表示未提及，INCORRECT 表示有事实错误。
                NOT_APPLICABLE 仅用于评分标准确实不适用于本题，不得因候选人未答而使用。
                除 MISSING 和 NOT_APPLICABLE 外，evidence 必须逐字摘录候选人回答中的连续片段；
                没有原文证据就应使用 MISSING。不得编造证据。
                """;
        JsonNode output = invokeValidated(client, prompt, system, configId,
                node -> validateAssessments(node, questions, standards));
        return (ArrayNode) output;
    }

    /** 生成跨问题的沟通维度和改进总结；逐题分数不由该阶段覆盖。 */
    private JsonNode summarize(LlmClient client, ArrayNode questions, Long configId) {
        ArrayNode compact = mapper.createArrayNode();
        for (JsonNode question : questions) {
            ObjectNode item = mapper.createObjectNode();
            item.put("questionIndex", question.path("questionIndex").asInt());
            item.put("questionText", question.path("questionText").asText());
            String answer = question.path("answerText").asText();
            item.put("answerExcerpt", answer.substring(0, Math.min(answer.length(), 300)));
            item.put("score", question.path("score").asInt());
            item.put("improvementTip", question.path("improvementTip").asText());
            ArrayNode gaps = item.putArray("gaps");
            for (JsonNode rubric : question.path("rubricDetail").path("items")) {
                String status = rubric.path("status").asText();
                if ("MISSING".equals(status) || "INCORRECT".equals(status)
                        || "PARTIALLY_COVERED".equals(status)) {
                    gaps.add(rubric.path("title").asText());
                }
            }
            compact.add(item);
        }
        String system = """
                根据逐题表现生成整场面试总结。沟通协作分评估倾听理解、互动和澄清能力。
                仅根据现有问答证据判断，不要推测音色、语速或语气。
                返回 JSON 对象：{"dimensionCommunication":0至100的整数,
                "strengths":"具体优势","weaknesses":"具体不足","improvementSummary":"可执行的改进建议"}。
                """;
        return invokeValidated(client, "逐题结果：" + compact, system, configId, this::validateSummary);
    }

    /**
     * 将量表覆盖状态折算为服务端分数并附带可回放的问答元数据。
     *
     * <p>LLM 不提供最终数值分数；每个条目的权重乘以固定覆盖系数后累加，保证同一输入结果可
     * 解释、可复算。</p>
     */
    private ObjectNode scoreQuestion(JsonNode question, JsonNode standard, JsonNode assessment,
                                     String positionTitle, String companyName, String industry, Integer companyTier,
                                     String resumeText) {
        Map<String, JsonNode> assessedItems = new HashMap<>();
        assessment.path("items").forEach(item -> assessedItems.put(item.path("id").asText(), item));
        double totalWeight = 0;
        double earned = 0;
        Map<String, Double> dimensionWeights = new HashMap<>();
        Map<String, Double> dimensionEarned = new HashMap<>();
        ArrayNode detailItems = mapper.createArrayNode();
        for (JsonNode rubric : standard.path("items")) {
            String id = rubric.path("id").asText();
            String dimension = rubric.path("dimension").asText();
            int weight = rubric.path("weight").asInt();
            JsonNode evaluation = assessedItems.get(id);
            String status = evaluation.path("status").asText();
            double awarded = weight * STATUS_FACTOR.get(status);
            if (!"NOT_APPLICABLE".equals(status)) {
                totalWeight += weight;
                earned += awarded;
                dimensionWeights.merge(dimension, (double) weight, Double::sum);
                dimensionEarned.merge(dimension, awarded, Double::sum);
            }
            ObjectNode detail = mapper.createObjectNode();
            detail.put("id", id);
            detail.put("dimension", dimension);
            detail.put("title", rubric.path("title").asText());
            detail.put("weight", weight);
            detail.put("fullCreditCriteria", rubric.path("fullCreditCriteria").asText());
            detail.put("status", status);
            detail.put("awardedScore", awarded);
            detail.put("evidence", evaluation.path("evidence").asText(""));
            detail.set("missingPoints", evaluation.path("missingPoints").deepCopy());
            detail.set("incorrectClaims", evaluation.path("incorrectClaims").deepCopy());
            detail.put("improvement", evaluation.path("improvement").asText(""));
            detailItems.add(detail);
        }
        if (totalWeight == 0) throw failure("整题量表均被标为不适用");
        int score = (int) Math.round(earned / totalWeight * 100);
        ObjectNode dimensionScores = mapper.createObjectNode();
        for (String dimension : DIMENSIONS) {
            double weight = dimensionWeights.getOrDefault(dimension, 0.0);
            int dimensionScore = weight == 0 ? 0 : (int) Math.round(dimensionEarned.get(dimension) / weight * 100);
            dimensionScores.put(dimension.toLowerCase(), dimensionScore);
        }
        ObjectNode detail = mapper.createObjectNode();
        detail.put("version", "rubric-v2");
        ObjectNode context = detail.putObject("context");
        context.put("positionTitle", safe(positionTitle));
        context.put("companyName", safe(companyName));
        context.put("industry", safe(industry));
        if (companyTier != null) context.put("companyTier", companyTier);
        context.put("questionType", question.path("questionType").asText("OBJECTIVE"));
        if (isPersonal(question)) context.put("resumeDigest", digest(resumeText));
        context.put("promptVersion", "rubric-v2");
        detail.put("confidence", question.path("confidence").asText());
        detail.set("items", detailItems);
        detail.set("dimensionScores", dimensionScores);
        detail.put("questionScore", score);

        ObjectNode result = mapper.createObjectNode();
        result.put("questionIndex", question.path("questionIndex").asInt());
        result.put("questionText", question.path("questionText").asText());
        result.put("questionType", question.path("questionType").asText("OBJECTIVE"));
        result.put("recognitionConfidence", question.path("confidence").asText());
        result.put("includedInScore", true);
        result.put("answerText", question.path("answerText").asText());
        if (question.hasNonNull("questionSpeakerId")) {
            result.put("questionSpeakerId", question.path("questionSpeakerId").asText());
            result.put("questionSpeaker", "INTERVIEWER");
        }
        if (question.hasNonNull("answerSpeakerId")) {
            result.put("answerSpeakerId", question.path("answerSpeakerId").asText());
            result.put("answerSpeaker", "CANDIDATE");
        }
        if (question.path("questionStartTimeMs").isIntegralNumber()) {
            result.put("startTime", Math.toIntExact(question.path("questionStartTimeMs").asLong() / 1000));
        }
        if (question.path("answerEndTimeMs").isIntegralNumber()) {
            long endTimeMs = question.path("answerEndTimeMs").asLong();
            result.put("endTime", Math.toIntExact((endTimeMs + 999) / 1000));
        }
        result.put("score", score);
        result.put("dimensionContent", dimensionScores.path("content").asInt());
        result.put("dimensionLogic", dimensionScores.path("logic").asInt());
        result.put("dimensionExpression", dimensionScores.path("expression").asInt());
        result.put("dimensionProfessional", dimensionScores.path("professional").asInt());
        result.put("referenceAnswer", standard.path("referenceAnswer").asText());
        result.put("improvementTip", assessment.path("improvementTip").asText());
        result.put("answerDifference", answerDifference(assessment));
        result.set("rubricDetail", detail);
        return result;
    }

    private String answerDifference(JsonNode assessment) {
        List<String> missing = new ArrayList<>();
        List<String> incorrect = new ArrayList<>();
        assessment.path("items").forEach(item -> {
            item.path("missingPoints").forEach(value -> { if (value.isTextual() && !value.asText().isBlank()) missing.add(value.asText()); });
            item.path("incorrectClaims").forEach(value -> { if (value.isTextual() && !value.asText().isBlank()) incorrect.add(value.asText()); });
        });
        List<String> parts = new ArrayList<>();
        if (!missing.isEmpty()) parts.add("缺失：" + String.join("、", missing));
        if (!incorrect.isEmpty()) parts.add("错误：" + String.join("、", incorrect));
        return parts.isEmpty() ? "与满分标准无明显关键差距" : String.join("；", parts);
    }

    private String digest(String value) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 低置信度问答仅供用户核对，不生成分数或进入汇总。 */
    private ObjectNode unscoredQuestion(JsonNode question) {
        ObjectNode result = mapper.createObjectNode();
        result.put("questionIndex", question.path("questionIndex").asInt());
        result.put("questionText", question.path("questionText").asText());
        result.put("answerText", question.path("answerText").asText());
        result.put("questionType", question.path("questionType").asText("OBJECTIVE"));
        result.put("recognitionConfidence", "LOW");
        result.put("includedInScore", false);
        copyText(question, result, "questionSpeakerId");
        copyText(question, result, "answerSpeakerId");
        result.put("questionSpeaker", "INTERVIEWER");
        result.put("answerSpeaker", "CANDIDATE");
        if (question.path("questionStartTimeMs").isIntegralNumber()) {
            result.put("startTime", question.path("questionStartTimeMs").asLong() / 1000);
        }
        if (question.path("answerEndTimeMs").isIntegralNumber()) {
            result.put("endTime", (question.path("answerEndTimeMs").asLong() + 999) / 1000);
        }
        result.put("improvementTip", "问答边界或说话人角色置信度较低，请先校正说话人后重新分析");
        return result;
    }

    private ObjectNode emptySummary() {
        ObjectNode summary = mapper.createObjectNode();
        summary.put("dimensionCommunication", 0);
        summary.put("strengths", "");
        summary.put("weaknesses", "未识别到可可靠评分的问题");
        summary.put("improvementSummary", "请校正说话人角色后重新分析");
        return summary;
    }

    /** 通过结构化输出调用模型，并在返回前执行阶段特定校验。 */
    private JsonNode invokeValidated(LlmClient client, String prompt, String system, Long configId,
                                     Consumer<JsonNode> validator) {
        String error = "模型结构化输出不完整";
        for (int attempt = 0; attempt < 2; attempt++) {
            JsonNode response = outputInvoker.invoke(client,
                    attempt == 0 ? prompt : prompt + "\n上次格式校验失败：" + error + "。请完整重新输出。",
                    system, configId);
            try {
                validator.accept(response);
                return response;
            } catch (IllegalArgumentException ex) {
                error = ex.getMessage();
                log.warn("评分结构校验失败，尝试修复: {}", error);
            }
        }
        throw failure("AI 评分结果校验失败：" + error);
    }

    /**
     * 验证问答识别结果引用的 speakerId、起止时间都实际存在于本段 ASR 转写中。
     */
    private void validateExtraction(JsonNode node, String transcriptChunk) {
        require(node.isArray(), "问答识别必须返回数组");
        TranscriptMetadata metadata = transcriptMetadata(transcriptChunk);
        for (JsonNode question : node) {
            requiredText(question, "questionText");
            require(question.path("answerText").isTextual(), "回答必须是文本");
            require(Set.of("TECHNICAL", "OBJECTIVE", "PERSONAL", "PROJECT", "BEHAVIORAL")
                    .contains(question.path("questionType").asText()), "问题类型无效");
            require(Set.of("HIGH", "LOW").contains(question.path("confidence").asText()), "识别置信度无效");
            if (!metadata.speakerIds().isEmpty()) {
                String questionSpeakerId = requiredText(question, "questionSpeakerId");
                String answerSpeakerId = requiredText(question, "answerSpeakerId");
                require(metadata.speakerIds().contains(questionSpeakerId), "提问者 speakerId 不在当前转写片段中");
                require(metadata.speakerIds().contains(answerSpeakerId), "回答者 speakerId 不在当前转写片段中");

                JsonNode startTime = question.path("questionStartTimeMs");
                JsonNode endTime = question.path("answerEndTimeMs");
                require(startTime.isIntegralNumber() && metadata.startTimes().contains(startTime.asLong()),
                        "问题开始时间必须引用 ASR 句子时间戳");
                require(endTime.isIntegralNumber() && metadata.endTimes().contains(endTime.asLong()),
                        "回答结束时间必须引用 ASR 句子时间戳");
                require(endTime.asLong() >= startTime.asLong(), "问答结束时间不能早于开始时间");
            }
        }
    }

    /** 从转写标记提取允许引用的时间点和说话人编号，用于约束模型输出边界。 */
    private TranscriptMetadata transcriptMetadata(String transcriptChunk) {
        Set<Long> startTimes = new HashSet<>();
        Set<Long> endTimes = new HashSet<>();
        Set<String> speakerIds = new HashSet<>();
        Matcher matcher = TRANSCRIPT_MARKER.matcher(transcriptChunk);
        while (matcher.find()) {
            startTimes.add(Long.parseLong(matcher.group(1)));
            endTimes.add(Long.parseLong(matcher.group(2)));
            speakerIds.add(matcher.group(3));
        }
        return new TranscriptMetadata(startTimes, endTimes, speakerIds);
    }

    private void copyText(JsonNode source, ObjectNode target, String field) {
        if (source.path(field).isTextual()) {
            target.put(field, source.path(field).asText());
        }
    }

    private void copyLong(JsonNode source, ObjectNode target, String field) {
        if (source.path(field).isIntegralNumber()) {
            target.put(field, source.path(field).asLong());
        }
    }

    private record TranscriptMetadata(Set<Long> startTimes, Set<Long> endTimes, Set<String> speakerIds) {
    }

    /** 确保每个问题恰有一份结构完整、权重有效的满分量表。 */
    private void validateStandards(JsonNode node, ArrayNode questions) {
        validateQuestionArray(node, questions, "标准答案");
        for (JsonNode standard : node) {
            requiredText(standard, "referenceAnswer");
            JsonNode items = standard.path("items");
            require(items.isArray() && items.size() >= 5 && items.size() <= 8, "每题必须有 5 至 8 个评分要点");
            Set<String> ids = new HashSet<>();
            Set<String> dimensions = new HashSet<>();
            int total = 0;
            for (JsonNode item : items) {
                String id = requiredText(item, "id");
                require(ids.add(id), "评分要点 id 重复");
                String dimension = requiredText(item, "dimension");
                require(DIMENSIONS.contains(dimension), "评分维度无效");
                dimensions.add(dimension);
                requiredText(item, "title");
                requiredText(item, "fullCreditCriteria");
                JsonNode weight = item.path("weight");
                require(weight.isIntegralNumber() && weight.asInt() > 0, "要点权重必须是正整数");
                total += weight.asInt();
            }
            require(total == 100, "要点权重合计必须为 100");
            require(dimensions.containsAll(DIMENSIONS), "量表必须覆盖四个评分维度");
        }
    }

    /** 确保比对结果与量表条目一一对应，且覆盖状态属于固定枚举。 */
    private void validateAssessments(JsonNode node, ArrayNode questions, ArrayNode standards) {
        validateQuestionArray(node, questions, "逐项比对");
        Map<Integer, JsonNode> standardByIndex = byQuestionIndex(standards);
        Map<Integer, JsonNode> questionByIndex = byQuestionIndex(questions);
        for (JsonNode assessment : node) {
            int index = assessment.path("questionIndex").asInt();
            requiredText(assessment, "improvementTip");
            JsonNode standard = standardByIndex.get(index);
            String answer = questionByIndex.get(index).path("answerText").asText();
            JsonNode items = assessment.path("items");
            require(items.isArray() && items.size() == standard.path("items").size(), "比对要点数量不符");
            Set<String> expected = new HashSet<>();
            standard.path("items").forEach(item -> expected.add(item.path("id").asText()));
            Set<String> actual = new HashSet<>();
            for (JsonNode item : items) {
                String id = requiredText(item, "id");
                require(expected.contains(id) && actual.add(id), "比对要点 id 不匹配或重复");
                String status = requiredText(item, "status");
                require(STATUS_FACTOR.containsKey(status), "比对状态无效");
                JsonNode evidenceNode = item.path("evidence");
                require(evidenceNode.isTextual(), "证据必须是文本");
                String evidence = evidenceNode.asText();
                if (!"MISSING".equals(status) && !"NOT_APPLICABLE".equals(status)) {
                    require(!evidence.isBlank() && answer.contains(evidence), "证据必须逐字出现在候选人回答中");
                }
                require(item.path("missingPoints").isArray(), "缺失点必须是数组");
                require(item.path("incorrectClaims").isArray(), "错误点必须是数组");
                if ("INCORRECT".equals(status)) {
                    require(!item.path("incorrectClaims").isEmpty(), "判定错误时必须说明具体错误");
                }
            }
            require(actual.equals(expected), "比对要点不完整");
        }
    }

    /** 校验整体总结只包含允许的维度和 0–100 的沟通分数。 */
    private void validateSummary(JsonNode summary) {
        require(summary.isObject(), "整场总结必须是对象");
        JsonNode communication = summary.path("dimensionCommunication");
        require(communication.isIntegralNumber() && communication.asInt() >= 0 && communication.asInt() <= 100,
                "沟通分必须在 0 至 100 之间");
        requiredText(summary, "strengths");
        requiredText(summary, "weaknesses");
        requiredText(summary, "improvementSummary");
    }

    private void validateQuestionArray(JsonNode node, ArrayNode questions, String stage) {
        require(node.isArray() && node.size() == questions.size(), stage + "题目数量不符");
        Set<Integer> expected = new HashSet<>();
        questions.forEach(question -> expected.add(question.path("questionIndex").asInt()));
        Set<Integer> actual = new HashSet<>();
        node.forEach(item -> {
            JsonNode index = item.path("questionIndex");
            require(index.isIntegralNumber(), stage + "题号缺失");
            actual.add(index.asInt());
        });
        require(actual.equals(expected) && actual.size() == node.size(), stage + "题号不匹配或重复");
    }

    private Map<Integer, JsonNode> byQuestionIndex(JsonNode array) {
        Map<Integer, JsonNode> result = new HashMap<>();
        array.forEach(item -> result.put(item.path("questionIndex").asInt(), item));
        return result;
    }

    private int average(ArrayNode questions, String field) {
        if (questions.isEmpty()) return 0;
        int total = 0;
        for (JsonNode question : questions) total += question.path(field).asInt();
        return (int) Math.round((double) total / questions.size());
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        require(value.isTextual() && !value.asText().isBlank(), field + " 缺失");
        return value.asText();
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private BusinessException failure(String message) {
        return new BusinessException(ErrorCode.PROCESSING_FAILED.getCode(), message);
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) return "未提供";
        return value.substring(0, Math.min(value.length(), 4000));
    }

    private LlmClient resolveClient(Long configId) {
        if (configId != null && configId > 0) {
            AiConfig config = aiConfigService.getDetail(configId);
            return clientFactory.getLlmClient(config.getProvider());
        }
        return clientFactory.getDefaultLlmClient();
    }
}
