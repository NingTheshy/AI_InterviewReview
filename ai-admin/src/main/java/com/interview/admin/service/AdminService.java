package com.interview.admin.service;

import com.interview.admin.dto.*;
import com.interview.common.result.PageResult;

/**
 * 管理员服务接口
 * <p>
 * 提供用户管理、系统统计、面试管理、面经管理、评论管理等功能。
 * 所有接口仅限管理员角色访问。
 * </p>
 */
public interface AdminService {

    // ==================== 用户管理 ====================

    /**
     * 获取用户列表（分页，支持关键词搜索和状态筛选）
     *
     * @param page     页码
     * @param size     每页数量
     * @param keyword  搜索关键词（用户名/邮箱），可为 null
     * @param status   用户状态筛选（0-禁用，1-正常），可为 null
     * @return 用户列表
     */
    PageResult<UserManagementResponse> listUsers(int page, int size, String keyword, Integer status);

    /**
     * 获取用户详情
     *
     * @param userId 用户 ID
     * @return 用户详情
     * @throws com.interview.common.exception.BusinessException 用户不存在时抛出
     */
    UserManagementResponse getUserDetail(Long userId);

    /**
     * 更新用户状态（启用/禁用）
     *
     * @param userId 用户 ID
     * @param status 状态值（0-禁用，1-正常）
     * @throws com.interview.common.exception.BusinessException 用户不存在时抛出
     */
    void updateUserStatus(Long userId, Integer status);

    /**
     * 更新用户角色
     *
     * @param userId 用户 ID
     * @param role   角色值（0-user，1-admin）
     * @throws com.interview.common.exception.BusinessException 用户不存在时抛出
     */
    void updateUserRole(Long userId, Integer role);

    // ==================== 系统统计 ====================

    /**
     * 获取系统统计概览
     * <p>
     * 包含用户总数、今日新增用户、面试总数、今日新增面试、
     * 面经总数（公开分享）、评论总数。
     * </p>
     *
     * @return 统计数据
     */
    StatsOverviewResponse getStatsOverview();

    // ==================== 面试管理 ====================

    /**
     * 获取所有面试记录列表（分页，支持按用户和状态筛选）
     *
     * @param page   页码
     * @param size   每页数量
     * @param userId 用户 ID 筛选，可为 null
     * @param status 面试状态筛选，可为 null
     * @return 面试管理列表
     */
    PageResult<InterviewManagementResponse> listInterviews(int page, int size, Long userId, Integer status);

    // ==================== 面经管理 ====================

    /**
     * 获取所有面经列表（分页）
     *
     * @param page 页码
     * @param size 每页数量
     * @return 面经管理列表
     */
    PageResult<ExperienceManagementResponse> listExperiences(int page, int size);

    /**
     * 设置面经公开状态（下架/恢复）
     *
     * @param token    分享 Token
     * @param isPublic 公开状态：0=下架，1=上架
     * @throws com.interview.common.exception.BusinessException 分享不存在时抛出
     */
    void setExperienceStatus(String token, Integer isPublic);

    // ==================== 评论管理 ====================

    /**
     * 获取所有评论列表（分页，支持按用户筛选）
     *
     * @param page   页码
     * @param size   每页数量
     * @param userId 用户 ID 筛选，可为 null
     * @return 评论管理列表
     */
    PageResult<CommentManagementResponse> listComments(int page, int size, Long userId);

    /**
     * 删除评论（逻辑删除）
     *
     * @param commentId 评论 ID
     * @throws com.interview.common.exception.BusinessException 评论不存在时抛出
     */
    void deleteComment(Long commentId);
}
