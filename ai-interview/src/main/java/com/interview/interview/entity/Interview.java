package com.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.interview.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;




@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview")
public class Interview extends BaseEntity {

    private Long userId;
    private String title;
    private String companyName;
    private String positionTitle;
    private String industry;
    private Integer companyTier;
    private String interviewType;
    private String audioFilePath;
    private Long audioFileSize;
    private Integer audioDuration;
    private String resumeFilePath;
    private String resumeText;
    private String jdText;
    private String transcriptText;
    private Integer overallScore;
    private Integer dimensionContent;
    private Integer dimensionLogic;
    private Integer dimensionExpression;
    private Integer dimensionProfessional;
    private Integer dimensionCommunication;
    private String improvementSummary;
    private String strengths;
    private String weaknesses;
    private Integer status;
    private Integer processingStep;
    private String errorMessage;
}
