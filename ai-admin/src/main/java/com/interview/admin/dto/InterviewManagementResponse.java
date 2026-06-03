package com.interview.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试管理响应
 * <p>
 * 管理员查看所有用户面试记录时使用的响应对象。
 * 包含面试基本信息、状态和评分。
 * </p>
 */
@Data
@Builder
public class InterviewManagementResponse {

    /** 面试 ID */
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 用户名（关联查询） */
    private String username;

    /** 面试标题 */
    private String title;

    /** 公司名称 */
    private String companyName;

    /** 职位名称 */
    private String positionTitle;

    /** 面试状态：0-待处理, 1-转写中, 2-分析中, 3-已完成, 4-失败 */
    private Integer status;

    /** 综合评分(0-100) */
    private Integer overallScore;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
