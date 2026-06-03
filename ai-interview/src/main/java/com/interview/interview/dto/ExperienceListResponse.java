package com.interview.interview.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面经广场列表响应 DTO
 * <p>
 * 对应需求文档 4.6 节"面经广场列表响应"，每条面经卡片显示：
 * 标题、公司名称、职位名称、行业、整体评分、问题数量、发布时间、浏览量。
 * </p>
 */
@Data
@Builder
public class ExperienceListResponse {

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

    /** 问题数量 */
    private Integer questionCount;

    /** 浏览量 */
    private Integer viewCount;

    /** 发布时间 */
    private LocalDateTime createdAt;
}
