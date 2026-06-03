package com.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.interview.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 面试问题收藏实体类
 * <p>
 * 对应数据库 interview_favorite 表，存储用户收藏的面试问题记录。
 * 支持两种收藏粒度：
 * <ul>
 *   <li>整个面试（questionId 为 null）</li>
 *   <li>具体问题（questionId 不为 null）</li>
 * </ul>
 * 同一用户不能重复收藏同一面试/问题。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_favorite")
public class InterviewFavorite extends BaseEntity {

    /** 用户 ID，关联 user 表 */
    private Long userId;

    /** 面试 ID，关联 interview 表 */
    private Long interviewId;

    /** 问题 ID，关联 interview_question 表，为 null 时表示收藏整个面试 */
    private Long questionId;

    /** 收藏备注，如"系统设计典型题" */
    private String remark;
}
