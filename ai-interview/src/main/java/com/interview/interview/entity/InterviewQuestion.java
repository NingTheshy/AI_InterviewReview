package com.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.interview.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_question")
public class InterviewQuestion extends BaseEntity {

    private Long interviewId;
    private Integer questionIndex;
    private String questionText;
    private String followUpTexts;
    private String questionType;
    private String domain;
    private String knowledgePoints;
    private String questionCategory;
    private Integer knowledgeDepth;
    private Integer problemComplexity;
    private String difficultyLevel;
    private String difficultyReason;
    private String difficultyRuleVersion;
    private String recognitionConfidence;
    private String lowConfidenceReason;
    private Integer includedInScore;
    private String questionSpeaker;
    private String answerText;
    private String answerSpeaker;
    private Integer startTime;
    private Integer endTime;
    private Integer score;
    private Integer dimensionContent;
    private Integer dimensionLogic;
    private Integer dimensionExpression;
    private Integer dimensionProfessional;
    private String improvementTip;
    private String referenceAnswer;
    private String answerDifference;
    private String answerEvidence;
    private String criticalErrors;
    private String rubricDetail;
    private String questionFingerprint;
    private Integer isActive;
    private LocalDateTime archivedAt;
}
