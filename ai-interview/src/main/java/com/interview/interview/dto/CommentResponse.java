package com.interview.interview.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论响应 DTO
 * <p>
 * 对应需求文档 4.6 节"评论列表响应"，包含评论者昵称、头像、
 * 评论内容和发布时间。
 * </p>
 */
@Data
@Builder
public class CommentResponse {

    /** 评论 ID */
    private Long id;

    /** 评论者用户 ID */
    private Long userId;

    /** 评论者昵称 */
    private String nickname;

    /** 评论者头像 */
    private String avatar;

    /** 评论内容 */
    private String content;

    /** 发布时间 */
    private LocalDateTime createdAt;
}
