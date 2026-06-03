package com.interview.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论管理响应
 * <p>
 * 管理员查看所有评论时使用的响应对象。
 * 包含评论基本信息和关联的分享 ID。
 * </p>
 */
@Data
@Builder
public class CommentManagementResponse {

    /** 评论 ID */
    private Long id;

    /** 分享 ID */
    private Long shareId;

    /** 用户 ID */
    private Long userId;

    /** 用户名（关联查询） */
    private String username;

    /** 评论内容 */
    private String content;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
