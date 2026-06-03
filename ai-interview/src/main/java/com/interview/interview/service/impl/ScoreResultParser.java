package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Transactional
    public void parseAndSave(Interview interview, String scoreResultJson) {
        try {
            JsonNode root = objectMapper.readTree(scoreResultJson);

            Integer dimensionContent = getInt(root, "dimensionContent");
            Integer dimensionLogic = getInt(root, "dimensionLogic");
            Integer dimensionExpression = getInt(root, "dimensionExpression");
            Integer dimensionProfessional = getInt(root, "dimensionProfessional");
            Integer dimensionCommunication = getInt(root, "dimensionCommunication");

            int weightedOverallScore = ScoringWeights.calculateOverallScore(
                    dimensionContent, dimensionLogic, dimensionExpression,
                    dimensionProfessional, dimensionCommunication);

            interview.setOverallScore(weightedOverallScore);
            interview.setDimensionContent(dimensionContent);
            interview.setDimensionLogic(dimensionLogic);
            interview.setDimensionExpression(dimensionExpression);
            interview.setDimensionProfessional(dimensionProfessional);
            interview.setDimensionCommunication(dimensionCommunication);
            interview.setImprovementSummary(getText(root, "improvementSummary"));
            interview.setStrengths(getText(root, "strengths"));
            interview.setWeaknesses(getText(root, "weaknesses"));
            interviewMapper.updateById(interview);

            LambdaQueryWrapper<InterviewQuestion> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(InterviewQuestion::getInterviewId, interview.getId());
            questionMapper.delete(deleteWrapper);

            JsonNode questionsNode = root.path("questions");
            if (questionsNode.isArray()) {
                for (JsonNode qNode : questionsNode) {
                    InterviewQuestion question = new InterviewQuestion();
                    question.setInterviewId(interview.getId());
                    question.setQuestionIndex(getInt(qNode, "questionIndex"));
                    question.setQuestionText(getText(qNode, "questionText"));
                    question.setAnswerText(getText(qNode, "answerText"));
                    question.setScore(getInt(qNode, "score"));
                    question.setDimensionContent(getInt(qNode, "dimensionContent"));
                    question.setDimensionLogic(getInt(qNode, "dimensionLogic"));
                    question.setDimensionExpression(getInt(qNode, "dimensionExpression"));
                    question.setDimensionProfessional(getInt(qNode, "dimensionProfessional"));
                    question.setImprovementTip(getText(qNode, "improvementTip"));
                    question.setReferenceAnswer(getText(qNode, "referenceAnswer"));
                    questionMapper.insert(question);
                }
            }
            log.info("评分结果解析完成: interviewId={}, 问题数={}",
                    interview.getId(), questionsNode.size());
        } catch (Exception e) {
            log.error("解析评分结果失败", e);
            throw new BusinessException(ErrorCode.PROCESSING_FAILED.getCode(),
                    "评分结果解析失败: " + e.getMessage());
        }
    }

    private String getText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        return value.asText(null);
    }

    private Integer getInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        try {
            return value.asInt();
        } catch (Exception e) {
            return null;
        }
    }
}
