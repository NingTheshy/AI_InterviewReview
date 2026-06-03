package com.interview.interview.service;

import com.interview.common.result.PageResult;
import com.interview.interview.dto.FavoriteResponse;

/**
 * 收藏服务接口
 * <p>
 * 提供面试问题的收藏管理功能，包括添加收藏、取消收藏和列表查询。
 * </p>
 */
public interface FavoriteService {

    /**
     * 添加收藏
     * <p>
     * 同一用户不能重复收藏同一问题。收藏可关联整个面试或具体问题。
     * </p>
     *
     * @param userId      用户 ID
     * @param interviewId 面试 ID
     * @param questionId  问题 ID（可选，为 null 时收藏整个面试）
     * @param remark      备注
     * @throws com.interview.common.exception.BusinessException 已收藏时抛出
     */
    void addFavorite(Long userId, Long interviewId, Long questionId, String remark);

    /**
     * 取消收藏（仅收藏者本人可操作）
     *
     * @param id     收藏记录 ID
     * @param userId 用户 ID
     * @throws com.interview.common.exception.BusinessException 记录不存在或无权限
     */
    void removeFavorite(Long id, Long userId);

    /**
     * 分页查询当前用户的收藏列表
     *
     * @param userId 用户 ID
     * @param page   页码
     * @param size   每页大小
     * @return 收藏列表（分页）
     */
    PageResult<FavoriteResponse> list(Long userId, int page, int size);
}
