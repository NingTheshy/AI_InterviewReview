package com.interview.interview.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试列表响应
 * <p>
 * 面试记录列表页使用的响应对象，包含面试基本信息，不包含敏感数据。
 * </p>
 */
@Data
@Builder
public class InterviewListResponse {

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

    /** 面试类型：coding/behavioral/system_design/comprehensive */
    private String interviewType;

    /** 处理状态：0=处理中, 1=已完成, 2=失败 */
    private Integer status;

    /** 综合评分(0-100) */
    private Integer overallScore;

    /** 面试时长（秒） */
    private Integer audioDuration;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
