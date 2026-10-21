package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interview.common.constant.ErrorCode;
import com.interview.common.constant.ScoringWeights;
import com.interview.common.exception.BusinessException;
import com.interview.interview.entity.Interview;
import com.interview.interview.entity.InterviewQuestion;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.mapper.InterviewQuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 评分结果解析器
 * <p>
 * 将 LLM 返回的 JSON 评分结果解析并持久化到数据库。
 * 提取为独立 Service 以确保 @Transactional 生效。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreResultParser {

    private final InterviewMapper interviewMapper;
    private final InterviewQuestionMapper questionMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 校验并持久化 LLM 编排层生成的评分 JSON。
     *
     * <p>整体分数不会直接信任模型输出，而是由 {@link ScoringWeights} 根据各维度统一计算。问题
     * 列表采用先删后写策略，使重试后的结果不会与旧问题混合；整个过程在事务中执行，任一步失败
     * 都不会留下部分评分数据。</p>
     *
     * @param interview 要更新的面试实体
     * @param scoreResultJson 已通过评分服务生成的结构化 JSON
     */
    @Transactional
    public int parseAndSave(Interview interview, String scoreResultJson) {
        try {
            JsonNode root = objectMapper.readTree(scoreResultJson);
            JsonNode questionsNode = root.path("questions");
            int scorableQuestionCount = root.has("scorableQuestionCount")
                    ? root.path("scorableQuestionCount").asInt(0)
                    : inferLegacyScorableCount(questionsNode);

            Integer dimensionContent = getInt(root, "dimensionContent");
            Integer dimensionLogic = getInt(root, "dimensionLogic");
            Integer dimensionExpression = getInt(root, "dimensionExpression");
            Integer dimensionProfessional = getInt(root, "dimensionProfessional");
            Integer dimensionCommunication = getInt(root, "dimensionCommunication");

            if (scorableQuestionCount > 0) {
                int weightedOverallScore = ScoringWeights.calculateOverallScore(
                        dimensionContent, dimensionLogic, dimensionExpression,
                        dimensionProfessional, dimensionCommunication);
                interview.setOverallScore(weightedOverallScore);
                interview.setDimensionContent(dimensionContent);
                interview.setDimensionLogic(dimensionLogic);
                interview.setDimensionExpression(dimensionExpression);
                interview.setDimensionProfessional(dimensionProfessional);
                interview.setDimensionCommunication(dimensionCommunication);
            } else {
                interview.setOverallScore(null);
                interview.setDimensionContent(null);
                interview.setDimensionLogic(null);
                interview.setDimensionExpression(null);
                interview.setDimensionProfessional(null);
                interview.setDimensionCommunication(null);
            }
            interview.setImprovementSummary(getText(root, "improvementSummary"));
            interview.setStrengths(getText(root, "strengths"));
            interview.setWeaknesses(getText(root, "weaknesses"));
            applySpeakerRoles(interview, questionsNode);
            interviewMapper.updateById(interview);

            LambdaQueryWrapper<InterviewQuestion> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(InterviewQuestion::getInterviewId, interview.getId());
            questionMapper.delete(deleteWrapper);

            if (questionsNode.isArray()) {
                for (JsonNode qNode : questionsNode) {
                    InterviewQuestion question = new InterviewQuestion();
                    question.setInterviewId(interview.getId());
                    question.setQuestionIndex(getInt(qNode, "questionIndex"));
                    question.setQuestionText(getText(qNode, "questionText"));
                    question.setQuestionType(getText(qNode, "questionType"));
                    question.setRecognitionConfidence(getText(qNode, "recognitionConfidence"));
                    boolean includedInScore = qNode.has("includedInScore")
                            ? qNode.path("includedInScore").asBoolean(false) : true;
                    question.setIncludedInScore(includedInScore ? 1 : 0);
                    question.setQuestionSpeaker(getText(qNode, "questionSpeaker"));
                    question.setAnswerText(getText(qNode, "answerText"));
                    question.setAnswerSpeaker(getText(qNode, "answerSpeaker"));
                    question.setStartTime(getInt(qNode, "startTime"));
                    question.setEndTime(getInt(qNode, "endTime"));
                    question.setScore(getInt(qNode, "score"));
                    question.setDimensionContent(getInt(qNode, "dimensionContent"));
                    question.setDimensionLogic(getInt(qNode, "dimensionLogic"));
                    question.setDimensionExpression(getInt(qNode, "dimensionExpression"));
                    question.setDimensionProfessional(getInt(qNode, "dimensionProfessional"));
                    question.setImprovementTip(getText(qNode, "improvementTip"));
                    question.setReferenceAnswer(getText(qNode, "referenceAnswer"));
                    question.setAnswerDifference(getText(qNode, "answerDifference"));
                    JsonNode rubricDetail = qNode.path("rubricDetail");
                    if (!rubricDetail.isMissingNode() && !rubricDetail.isNull()) {
                        if (!rubricDetail.isObject()) {
                            throw new IllegalArgumentException("rubricDetail 必须是 JSON 对象");
                        }
                        question.setRubricDetail(objectMapper.writeValueAsString(rubricDetail));
                    }
                    questionMapper.insert(question);
                }
            }
            log.info("评分结果解析完成: interviewId={}, 问题数={}",
                    interview.getId(), questionsNode.size());
            return scorableQuestionCount;
        } catch (Exception e) {
            log.error("解析评分结果失败", e);
            throw new BusinessException(ErrorCode.PROCESSING_FAILED.getCode(),
                    "评分结果解析失败: " + e.getMessage());
        }
    }

    /** 兼容改造前没有可计分标记的历史评分结果；旧问题默认都参与评分。 */
    private int inferLegacyScorableCount(JsonNode questionsNode) {
        if (!questionsNode.isArray()) return 0;
        int count = 0;
        for (JsonNode question : questionsNode) {
            if (!question.has("includedInScore") || question.path("includedInScore").asBoolean(false)) {
                count++;
            }
        }
        return count;
    }

    /** 读取可选文本字段；缺失和 JSON null 都映射为 {@code null}。 */
    private String getText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        return value.asText(null);
    }

    /** 读取可选整数，异常格式不抛出解析器底层异常，交由后续业务字段校验处理。 */
    private Integer getInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        try {
            return value.asInt();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 根据问答识别结果建立 {@code speakerId -> 业务角色} 的整场映射，并同步更新可读转写文本。
     *
     * <p>Fun-ASR 提供的是匿名说话人编号，不直接知道业务角色。此处对每个问答对投票：提问者
     * 计为面试官，回答者计为候选人；两人场景中若仅一人已确定，可补全另一人的相反角色。票数
     * 相同或多人无法确认时保留 UNKNOWN，避免错误标签污染复盘结果。</p>
     */
    private void applySpeakerRoles(Interview interview, JsonNode questionsNode) throws Exception {
        if (interview.getTranscriptSegments() == null || interview.getTranscriptSegments().isBlank()
                || !questionsNode.isArray()) {
            return;
        }
        JsonNode parsedSegments = objectMapper.readTree(interview.getTranscriptSegments());
        if (!(parsedSegments instanceof ArrayNode segments)) {
            return;
        }

        Map<String, int[]> votes = new HashMap<>();
        for (JsonNode question : questionsNode) {
            addRoleVote(votes, getText(question, "questionSpeakerId"), true);
            addRoleVote(votes, getText(question, "answerSpeakerId"), false);
        }

        Set<String> allSpeakerIds = new HashSet<>();
        for (JsonNode segment : segments) {
            String speakerId = getText(segment, "speakerId");
            if (speakerId != null) {
                allSpeakerIds.add(speakerId);
            }
        }

        Map<String, String> roles = new HashMap<>();
        for (String speakerId : allSpeakerIds) {
            int[] counts = votes.getOrDefault(speakerId, new int[2]);
            if (counts[0] > counts[1]) {
                roles.put(speakerId, "INTERVIEWER");
            } else if (counts[1] > counts[0]) {
                roles.put(speakerId, "CANDIDATE");
            } else {
                roles.put(speakerId, "UNKNOWN");
            }
        }
        completeTwoSpeakerMapping(roles);

        StringBuilder readableTranscript = new StringBuilder();
        for (JsonNode segment : segments) {
            if (!(segment instanceof ObjectNode objectSegment)) {
                continue;
            }
            String speakerId = getText(segment, "speakerId");
            String role = roles.getOrDefault(speakerId, "UNKNOWN");
            objectSegment.put("speakerRole", role);
            if (readableTranscript.length() > 0) {
                readableTranscript.append('\n');
            }
            readableTranscript.append('[').append(roleLabel(role, speakerId)).append("] ")
                    .append(getText(segment, "text"));
        }
        interview.setTranscriptSegments(objectMapper.writeValueAsString(segments));
        if (readableTranscript.length() > 0) {
            interview.setTranscriptText(readableTranscript.toString());
        }
    }

    /** 为一条问答证据累积说话人角色票数，忽略空或未识别的编号。 */
    private void addRoleVote(Map<String, int[]> votes, String speakerId, boolean interviewer) {
        if (speakerId == null || speakerId.isBlank() || "UNKNOWN".equals(speakerId)) {
            return;
        }
        int[] counts = votes.computeIfAbsent(speakerId, ignored -> new int[2]);
        counts[interviewer ? 0 : 1]++;
    }

    /** 仅在恰有两名说话人且一方角色已确定时，安全补全另一方角色。 */
    private void completeTwoSpeakerMapping(Map<String, String> roles) {
        if (roles.size() != 2) {
            return;
        }
        String knownId = roles.entrySet().stream()
                .filter(entry -> !"UNKNOWN".equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        long knownCount = roles.values().stream().filter(role -> !"UNKNOWN".equals(role)).count();
        if (knownId == null || knownCount != 1) {
            return;
        }
        String oppositeRole = "INTERVIEWER".equals(roles.get(knownId)) ? "CANDIDATE" : "INTERVIEWER";
        roles.entrySet().stream()
                .filter(entry -> "UNKNOWN".equals(entry.getValue()))
                .findFirst()
                .ifPresent(entry -> entry.setValue(oppositeRole));
    }

    /** 将内部角色枚举转换为面向用户的转写标签。 */
    private String roleLabel(String role, String speakerId) {
        return switch (role) {
            case "INTERVIEWER" -> "面试官";
            case "CANDIDATE" -> "候选人";
            default -> "说话人 " + (speakerId == null ? "未知" : speakerId);
        };
    }
}
