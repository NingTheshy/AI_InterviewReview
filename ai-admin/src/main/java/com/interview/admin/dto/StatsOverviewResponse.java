package com.interview.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 系统统计概览响应
 * <p>
 * 包含系统核心数据指标，用于管理员仪表盘展示。
 * </p>
 */
@Data
@Builder
public class StatsOverviewResponse {

    /**
     * 用户总数
     */
    private Long totalUsers;

    /**
     * 今日新增用户
     */
    private Long todayNewUsers;

    /**
     * 面试记录总数
     */
    private Long totalInterviews;

    /**
     * 今日新增面试
     */
    private Long todayNewInterviews;

    /**
     * 各处理状态面试数分布
     * <p>
     * Key: processing-处理中, completed-已完成, failed-失败
     * Value: 该状态的面试数量
     * </p>
     */
    private Map<String, Long> statusDistribution;

    /**
     * 平均面试评分(0-100)
     */
    private Integer averageScore;

    /**
     * 面经总数（公开分享）
     */
    private Long totalExperiences;

    /**
     * 评论总数
     */
    private Long totalComments;
}
