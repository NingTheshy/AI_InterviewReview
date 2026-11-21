package com.interview.admin.service;

import com.interview.admin.dto.*;
import com.interview.common.result.PageResult;

import java.util.Set;

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
    void updateUserStatus(Long operatorUserId, Long userId, Integer status);

    /**
     * 更新用户角色
     *
     * @param userId 用户 ID
     * @param roleCodes 角色编码集合；空集合自动回退为 user
     * @throws com.interview.common.exception.BusinessException 用户不存在时抛出
     */
    void updateUserRoles(Long operatorUserId, Long userId, Set<String> roleCodes);

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

    PageResult<InterviewManagementResponse> searchInterviews(AdminInterviewQuery query);

    /** 管理员读取完整面试复盘。 */
    com.interview.interview.dto.InterviewDetailResponse getInterviewDetail(Long interviewId);

    /** 管理员取消处理中的面试。 */
    void cancelInterview(Long interviewId);

    /** 管理员重试失败或已取消的面试。 */
    void retryInterview(Long interviewId);

    void editInterview(Long interviewId, AdminInterviewEditRequest request);
    void deleteInterview(Long interviewId);
    void restoreInterview(Long interviewId);

    // ==================== 面经管理 ====================

    /**
     * 获取所有面经列表（分页）
     *
     * @param page 页码
     * @param size 每页数量
     * @return 面经管理列表
     */
    PageResult<ExperienceManagementResponse> listExperiences(int page, int size);

    PageResult<ExperienceManagementResponse> searchExperiences(AdminExperienceQuery query);

    /** 查看已选题目的公开快照，下架后仍可供管理员核查。 */
    ExperienceManagementDetailResponse getExperienceDetail(Long experienceId);

    /**
     * 设置面经公开状态（下架/恢复）
     *
     * @param token    分享 Token
     * @param isPublic 公开状态：0=下架，1=上架
     * @throws com.interview.common.exception.BusinessException 分享不存在时抛出
     */
    void setExperienceStatus(Long experienceId, Integer isPublic);

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

    /** 按正文、作者、面经、状态和时间组合查询评论。 */
    PageResult<CommentManagementResponse> searchComments(AdminCommentQuery query);

    /** 查看评论全文及其回复上下文。 */
    CommentManagementResponse getCommentDetail(Long commentId);

    /**
     * 删除评论（逻辑删除）
     *
     * @param commentId 评论 ID
     * @throws com.interview.common.exception.BusinessException 评论不存在时抛出
     */
    void deleteComment(Long commentId);
}
