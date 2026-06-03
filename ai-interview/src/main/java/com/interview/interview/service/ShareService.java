package com.interview.interview.service;

import com.interview.interview.dto.ExperienceDetailResponse;
import com.interview.interview.dto.ShareResponse;

import java.util.List;

/**
 * 分享服务接口
 * <p>
 * 提供面试记录的分享链接生成、管理、公开状态切换等功能。
 * 分享链接通过唯一 Token 访问，支持设置过期时间。
 * </p>
 */
public interface ShareService {

    /**
     * 创建分享链接
     * <p>
     * 生成唯一 Token，可设置过期时间（7天/30天/永久）和公开状态。
     * </p>
     *
     * @param interviewId 面试 ID
     * @param userId      用户 ID
     * @param expireType  过期类型：7d / 30d / never
     * @param isPublic    是否公开到面经广场
     * @return 新建的分享记录
     * @throws com.interview.common.exception.BusinessException 面试不存在或无权限
     */
    ShareResponse createShare(Long interviewId, Long userId, String expireType, Boolean isPublic);

    /**
     * 获取面试记录的分享列表
     *
     * @param interviewId 面试 ID
     * @param userId      用户 ID
     * @return 分享记录列表（按创建时间倒序）
     */
    List<ShareResponse> listShares(Long interviewId, Long userId);

    /**
     * 删除分享链接（仅分享创建者可操作）
     *
     * @param token  分享 Token
     * @param userId 用户 ID
     * @throws com.interview.common.exception.BusinessException 分享不存在
     */
    void deleteShare(String token, Long userId);

    /**
     * 切换面经公开状态（在公开/私有之间切换）
     *
     * @param token  分享 Token
     * @param userId 用户 ID
     * @throws com.interview.common.exception.BusinessException 分享不存在
     */
    void togglePublic(String token, Long userId);

    /**
     * 获取分享详情（通过 Token 访问，无需认证）
     * <p>
     * 会检查分享是否过期，并增加浏览量。
     * 返回面经详情，包含问题列表、评分维度和 AI 建议。
     * </p>
     *
     * @param token 分享 Token
     * @return 面经详情
     * @throws com.interview.common.exception.BusinessException 分享不存在或已过期
     */
    ExperienceDetailResponse getShareDetail(String token);
}
