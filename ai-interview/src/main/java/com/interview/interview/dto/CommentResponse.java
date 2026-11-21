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

    /** 直接回复的评论 ID，顶层评论为空。 */
    private Long parentId;

    /** 直接回复的用户昵称。 */
    private String replyToNickname;

    /** 该评论下直接回复的数量。 */
    private Long replyCount;

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
