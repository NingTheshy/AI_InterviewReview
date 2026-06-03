package com.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.interview.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_question")
public class InterviewQuestion extends BaseEntity {

    private Long interviewId;
    private Integer questionIndex;
    private String questionText;
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
}
