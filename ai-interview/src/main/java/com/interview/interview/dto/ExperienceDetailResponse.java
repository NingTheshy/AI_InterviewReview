package com.interview.interview.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面经详情响应 DTO
 * <p>
 * 对应需求文档 4.6 节"面经详情响应"，包含面试基本信息、
 * 评分维度、问题列表和 AI 改进建议。不包含音频、转写文本和个人笔记。
 * </p>
 */
@Data
@Builder
public class ExperienceDetailResponse {

    /** 分享 Token */
    private String token;

    /** 面试标题 */
    private String title;

    /** 公司名称 */
    private String companyName;

    /** 职位名称 */
    private String positionTitle;

    /** 行业 */
    private String industry;

    /** 面试类型 */
    private String interviewType;

    /** 整体评分(0-100) */
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
    private List<QuestionItem> questions;

    /** 浏览量 */
    private Integer viewCount;

    /** 发布时间 */
    private LocalDateTime createdAt;

    /**
     * 问题条目
     */
    @Data
    @Builder
    public static class QuestionItem {
        private Long id;
        private Integer questionIndex;
        private String questionText;
        private Integer score;
        private String improvementTip;
        private String referenceAnswer;
    }
}
