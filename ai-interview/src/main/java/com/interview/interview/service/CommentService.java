package com.interview.interview.service;

import com.interview.common.result.PageResult;
import com.interview.interview.dto.CommentResponse;

/**
 * 评论服务接口
 * <p>
 * 提供面经评论的查看、发表和删除功能。
 * </p>
 */
public interface CommentService {

    /**
     * 分页获取面经评论列表（无需认证）
     *
     * @param token 分享 Token
     * @param page  页码
     * @param size  每页大小
     * @return 评论列表（分页，按创建时间倒序），包含评论者昵称和头像
     * @throws com.interview.common.exception.BusinessException 分享不存在
     */
    PageResult<CommentResponse> listByShareToken(String token, int page, int size);

    /**
     * 发表评论
     *
     * @param token   分享 Token
     * @param userId  用户 ID
     * @param content 评论内容（1-500 字符）
     * @throws com.interview.common.exception.BusinessException 分享不存在、内容为空或超长
     */
    void addComment(String token, Long userId, String content);

    /**
     * 删除评论（仅评论作者可删除）
     *
     * @param id     评论 ID
     * @param userId 用户 ID
     * @throws com.interview.common.exception.BusinessException 评论不存在或无权限
     */
    void deleteComment(Long id, Long userId);
}
