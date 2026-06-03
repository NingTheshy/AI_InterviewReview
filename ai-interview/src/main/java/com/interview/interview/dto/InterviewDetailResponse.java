package com.interview.interview.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面试详情响应
 * <p>
 * 面试复盘详情页使用的响应对象，包含完整评分信息、问题列表和 AI 建议。
 * </p>
 */
@Data
@Builder
public class InterviewDetailResponse {

    /** 面试 ID */
    private Long id;

    /** 面试标题 */
    private String title;

    /** 公司名称 */
    private String companyName;

    /** 职位名称 */
    private String positionTitle;

    /** 行业分类 */
    private String industry;

    /** 面试类型 */
    private String interviewType;

    /** 处理状态 */
    private Integer status;

    /** 处理步骤：0=待处理, 1=转写中, 2=问题识别, 3=逐题评分, 4=整体评分 */
    private Integer processingStep;

    /** 音频文件名（前端需拼接 /files/ 前缀访问，如 /files/{audioFilePath}） */
    private String audioFilePath;

    /** 面试时长（秒） */
    private Integer audioDuration;

    /** 转写文本 */
    private String transcriptText;

    /** 综合评分(0-100) */
    private Integer overallScore;

    /** 内容质量维度评分(0-100) */
    private Integer dimensionContent;

    /** 逻辑性维度评分(0-100) */
    private Integer dimensionLogic;

    /** 表达能力维度评分(0-100) */
    private Integer dimensionExpression;

    /** 专业度维度评分(0-100) */
    private Integer dimensionProfessional;

    /** 沟通技巧维度评分(0-100) */
    private Integer dimensionCommunication;

    /** 整体改进建议 */
    private String improvementSummary;

    /** 候选人优势总结 */
    private String strengths;

    /** 需要提升的方面 */
    private String weaknesses;

    /** 问题列表 */
    private List<QuestionResponse> questions;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /**
     * 问题响应
     */
    @Data
    @Builder
    public static class QuestionResponse {

        /** 问题 ID */
        private Long id;

        /** 问题序号 */
        private Integer questionIndex;

        /** 问题内容 */
        private String questionText;

        /** 回答内容 */
        private String answerText;

        /** 评分(0-100) */
        private Integer score;

        /** 内容质量评分(0-100) */
        private Integer dimensionContent;

        /** 逻辑性评分(0-100) */
        private Integer dimensionLogic;

        /** 表达能力评分(0-100) */
        private Integer dimensionExpression;

        /** 专业度评分(0-100) */
        private Integer dimensionProfessional;

        /** 改进建议 */
        private String improvementTip;

        /** 参考答案 */
        private String referenceAnswer;
    }
}
