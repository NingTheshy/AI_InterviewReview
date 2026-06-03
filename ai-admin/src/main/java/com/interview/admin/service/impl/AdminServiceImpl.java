package com.interview.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.admin.dto.*;
import com.interview.admin.service.AdminService;
import com.interview.auth.entity.User;
import com.interview.auth.mapper.UserMapper;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.common.result.PageResult;
import com.interview.interview.entity.Interview;
import com.interview.interview.entity.InterviewComment;
import com.interview.interview.entity.InterviewShare;
import com.interview.interview.mapper.InterviewCommentMapper;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.mapper.InterviewShareMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员服务实现类
 * <p>
 * 提供用户管理、系统统计等功能的业务逻辑实现。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserMapper userMapper;
    private final InterviewMapper interviewMapper;
    private final InterviewShareMapper interviewShareMapper;
    private final InterviewCommentMapper interviewCommentMapper;

    // ==================== 用户管理 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<UserManagementResponse> listUsers(int page, int size, String keyword, Integer status) {
        Page<User> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        // 关键词搜索
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w
                    .like(User::getUsername, keyword)
                    .or()
                    .like(User::getEmail, keyword));
        }

        // 状态筛选
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }

        // 按创建时间倒序
        wrapper.orderByDesc(User::getCreatedAt);

        Page<User> result = userMapper.selectPage(pageParam, wrapper);

        // 转换为响应对象
        List<UserManagementResponse> records = result.getRecords().stream()
                .map(this::convertToUserResponse)
                .collect(Collectors.toList());

        return new PageResult<>(records, result.getTotal(), page, size);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserManagementResponse getUserDetail(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(),
                    ErrorCode.NOT_FOUND.getMessage());
        }
        return convertToUserResponse(user);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateUserStatus(Long userId, Integer status) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(),
                    ErrorCode.NOT_FOUND.getMessage());
        }

        user.setStatus(status);
        userMapper.updateById(user);

        log.info("管理员更新用户状态: userId={}, status={}", userId, status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateUserRole(Long userId, Integer role) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(),
                    ErrorCode.NOT_FOUND.getMessage());
        }

        user.setRole(role);
        userMapper.updateById(user);

        log.info("管理员更新用户角色: userId={}, role={}", userId, role);
    }

    // ==================== 系统统计 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public StatsOverviewResponse getStatsOverview() {
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        // 用户统计
        Long totalUsers = userMapper.selectCount(null);
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.ge(User::getCreatedAt, todayStart);
        userWrapper.le(User::getCreatedAt, todayEnd);
        Long todayNewUsers = userMapper.selectCount(userWrapper);

        // 面试统计
        Long totalInterviews = interviewMapper.selectCount(null);
        LambdaQueryWrapper<Interview> interviewWrapper = new LambdaQueryWrapper<>();
        interviewWrapper.ge(Interview::getCreatedAt, todayStart);
        interviewWrapper.le(Interview::getCreatedAt, todayEnd);
        Long todayNewInterviews = interviewMapper.selectCount(interviewWrapper);

        // 各处理状态面试数分布
        java.util.Map<String, Long> statusDistribution = new java.util.HashMap<>();
        statusDistribution.put("processing", getCountByStatus(0));
        statusDistribution.put("completed", getCountByStatus(1));
        statusDistribution.put("failed", getCountByStatus(2));

        // 平均面试评分（仅计算已完成的面试）
        LambdaQueryWrapper<Interview> scoreWrapper = new LambdaQueryWrapper<>();
        scoreWrapper.eq(Interview::getStatus, 1); // COMPLETED
        scoreWrapper.isNotNull(Interview::getOverallScore);
        List<Interview> completedInterviews = interviewMapper.selectList(scoreWrapper);
        Integer averageScore = null;
        if (!completedInterviews.isEmpty()) {
            int totalScore = completedInterviews.stream()
                    .map(Interview::getOverallScore)
                    .filter(java.util.Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();
            averageScore = totalScore / completedInterviews.size();
        }

        // 面经统计（公开分享）
        LambdaQueryWrapper<InterviewShare> shareWrapper = new LambdaQueryWrapper<>();
        shareWrapper.eq(InterviewShare::getIsPublic, 1);
        Long totalExperiences = interviewShareMapper.selectCount(shareWrapper);

        // 评论统计
        Long totalComments = interviewCommentMapper.selectCount(null);

        return StatsOverviewResponse.builder()
                .totalUsers(totalUsers)
                .todayNewUsers(todayNewUsers)
                .totalInterviews(totalInterviews)
                .todayNewInterviews(todayNewInterviews)
                .statusDistribution(statusDistribution)
                .averageScore(averageScore)
                .totalExperiences(totalExperiences)
                .totalComments(totalComments)
                .build();
    }

    // ==================== 面试管理 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<InterviewManagementResponse> listInterviews(int page, int size, Long userId, Integer status) {
        Page<Interview> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Interview> wrapper = new LambdaQueryWrapper<>();

        if (userId != null) {
            wrapper.eq(Interview::getUserId, userId);
        }
        if (status != null) {
            wrapper.eq(Interview::getStatus, status);
        }
        wrapper.orderByDesc(Interview::getCreatedAt);

        Page<Interview> result = interviewMapper.selectPage(pageParam, wrapper);

        // 批量查询用户信息
        List<Long> userIds = result.getRecords().stream()
                .map(Interview::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> usernameMap = getUsernamesByIds(userIds);

        List<InterviewManagementResponse> records = result.getRecords().stream()
                .map(interview -> InterviewManagementResponse.builder()
                        .id(interview.getId())
                        .userId(interview.getUserId())
                        .username(usernameMap.get(interview.getUserId()))
                        .title(interview.getTitle())
                        .companyName(interview.getCompanyName())
                        .positionTitle(interview.getPositionTitle())
                        .status(interview.getStatus())
                        .overallScore(interview.getOverallScore())
                        .createdAt(interview.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return new PageResult<>(records, result.getTotal(), page, size);
    }

    // ==================== 面经管理 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<ExperienceManagementResponse> listExperiences(int page, int size) {
        Page<InterviewShare> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<InterviewShare> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(InterviewShare::getCreatedAt);

        Page<InterviewShare> result = interviewShareMapper.selectPage(pageParam, wrapper);

        // 批量查询用户信息
        List<Long> userIds = result.getRecords().stream()
                .map(InterviewShare::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> usernameMap = getUsernamesByIds(userIds);

        List<ExperienceManagementResponse> records = result.getRecords().stream()
                .map(share -> ExperienceManagementResponse.builder()
                        .id(share.getId())
                        .interviewId(share.getInterviewId())
                        .userId(share.getUserId())
                        .username(usernameMap.get(share.getUserId()))
                        .shareToken(share.getShareToken())
                        .isPublic(share.getIsPublic())
                        .viewCount(share.getViewCount())
                        .createdAt(share.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return new PageResult<>(records, result.getTotal(), page, size);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setExperienceStatus(String token, Integer isPublic) {
        LambdaQueryWrapper<InterviewShare> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewShare::getShareToken, token);

        InterviewShare share = interviewShareMapper.selectOne(wrapper);
        if (share == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND.getCode(),
                    ErrorCode.SHARE_NOT_FOUND.getMessage());
        }

        share.setIsPublic(isPublic);
        interviewShareMapper.updateById(share);

        log.info("管理员设置面经状态: token={}, isPublic={}", token, isPublic);
    }

    // ==================== 评论管理 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<CommentManagementResponse> listComments(int page, int size, Long userId) {
        Page<InterviewComment> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<InterviewComment> wrapper = new LambdaQueryWrapper<>();

        if (userId != null) {
            wrapper.eq(InterviewComment::getUserId, userId);
        }
        wrapper.orderByDesc(InterviewComment::getCreatedAt);

        Page<InterviewComment> result = interviewCommentMapper.selectPage(pageParam, wrapper);

        // 批量查询用户信息
        List<Long> userIds = result.getRecords().stream()
                .map(InterviewComment::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> usernameMap = getUsernamesByIds(userIds);

        List<CommentManagementResponse> records = result.getRecords().stream()
                .map(comment -> CommentManagementResponse.builder()
                        .id(comment.getId())
                        .shareId(comment.getShareId())
                        .userId(comment.getUserId())
                        .username(usernameMap.get(comment.getUserId()))
                        .content(comment.getContent())
                        .createdAt(comment.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return new PageResult<>(records, result.getTotal(), page, size);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteComment(Long commentId) {
        InterviewComment comment = interviewCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(),
                    ErrorCode.NOT_FOUND.getMessage());
        }

        interviewCommentMapper.deleteById(commentId);
        log.info("管理员删除评论: commentId={}", commentId);
    }

    // ==================== 私有方法 ====================

    /**
     * 将 User 实体转换为 UserManagementResponse
     *
     * @param user 用户实体
     * @return 用户管理响应
     */
    private UserManagementResponse convertToUserResponse(User user) {
        return UserManagementResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .role(user.getRole())
                .status(user.getStatus())
                .loginCount(user.getLoginCount())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * 批量获取用户名映射
     * <p>
     * 根据用户 ID 列表查询用户名，返回 ID -> 用户名 的映射。
     * </p>
     *
     * @param userIds 用户 ID 列表
     * @return 用户名映射
     */
    private Map<Long, String> getUsernamesByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.in("id", userIds);
        wrapper.select("id", "username");

        return userMapper.selectList(wrapper).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
    }

    /**
     * 根据状态值统计面试数量
     *
     * @param status 面试状态
     * @return 该状态的面试数量
     */
    private Long getCountByStatus(Integer status) {
        LambdaQueryWrapper<Interview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Interview::getStatus, status);
        return interviewMapper.selectCount(wrapper);
    }
}
