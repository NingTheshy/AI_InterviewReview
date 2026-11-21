package com.interview.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.admin.dto.*;
import com.interview.admin.service.AdminService;
import com.interview.auth.entity.User;
import com.interview.auth.mapper.UserMapper;
import com.interview.auth.service.AuditService;
import com.interview.auth.service.RbacService;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.common.result.PageResult;
import com.interview.common.utils.SecurityUtils;
import com.interview.interview.entity.Interview;
import com.interview.interview.entity.InterviewComment;
import com.interview.interview.entity.InterviewExperience;
import com.interview.interview.entity.ExperienceQuestionSnapshot;
import com.interview.interview.mapper.InterviewCommentMapper;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.mapper.InterviewExperienceMapper;
import com.interview.interview.mapper.ExperienceQuestionSnapshotMapper;
import com.interview.interview.service.InterviewService;
import com.interview.interview.service.impl.CommentDeletionService;
import com.interview.interview.dto.InterviewDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final InterviewExperienceMapper interviewExperienceMapper;
    private final ExperienceQuestionSnapshotMapper experienceQuestionSnapshotMapper;
    private final InterviewCommentMapper interviewCommentMapper;
    private final RbacService rbacService;
    private final AuditService auditService;
    private final InterviewService interviewService;
    private final CommentDeletionService commentDeletionService;

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
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void updateUserStatus(Long operatorUserId, Long userId, Integer status) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(),
                    ErrorCode.NOT_FOUND.getMessage());
        }

        Integer beforeStatus = user.getStatus();
        if (Integer.valueOf(0).equals(status) && !Integer.valueOf(0).equals(beforeStatus)) {
            rbacService.assertCanDisableUser(operatorUserId, userId);
        }
        user.setStatus(status);
        userMapper.updateById(user);
        auditService.recordSuccess(operatorUserId, "USER_STATUS_UPDATE", "USER",
                String.valueOf(userId), beforeStatus, status);

        log.info("管理员更新用户状态: userId={}, status={}", userId, status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateUserRoles(Long operatorUserId, Long userId, Set<String> roleCodes) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(),
                    ErrorCode.NOT_FOUND.getMessage());
        }

        rbacService.replaceUserRoles(operatorUserId, userId, roleCodes);
        log.info("管理员更新用户角色: userId={}, roles={}", userId, roleCodes);
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
        statusDistribution.put("cancelled", getCountByStatus(3));

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
        LambdaQueryWrapper<InterviewExperience> shareWrapper = new LambdaQueryWrapper<>();
        shareWrapper.eq(InterviewExperience::getPublishStatus, InterviewExperience.PUBLISHED)
                .eq(InterviewExperience::getModerationStatus, InterviewExperience.NORMAL);
        Long totalExperiences = interviewExperienceMapper.selectCount(shareWrapper);

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

    @Override
    public PageResult<InterviewManagementResponse> searchInterviews(AdminInterviewQuery query) {
        validatePage(query.getPage(), query.getSize());
        if (query.getStatus() != null && (query.getStatus() < 0 || query.getStatus() > 3)) badFilter();
        if (query.getDeleted() != null && query.getDeleted() != 0 && query.getDeleted() != 1) badFilter();
        validateRange(query.getFrom(), query.getTo());
        Page<Interview> result = interviewMapper.selectAdminPage(new Page<>(query.getPage(), query.getSize()),
                trim(query.getTitle()), trim(query.getUserKeyword()), trim(query.getCompany()),
                trim(query.getPosition()), query.getStatus(), query.getDeleted(), query.getFrom(), query.getTo());
        List<Long> ids = result.getRecords().stream().map(Interview::getUserId).distinct().toList();
        Map<Long, User> users = getUsersByIds(ids);
        List<InterviewManagementResponse> records = result.getRecords().stream().map(interview -> {
            User owner = users.get(interview.getUserId());
            String username = owner == null ? null : owner.getUsername();
            String displayName = owner != null && owner.getNickname() != null && !owner.getNickname().isBlank()
                    ? owner.getNickname() : username;
            return InterviewManagementResponse.builder()
                    .id(interview.getId()).userId(interview.getUserId()).username(username)
                    .ownerDisplayName(displayName).title(interview.getTitle())
                    .companyName(interview.getCompanyName()).positionTitle(interview.getPositionTitle())
                    .interviewRound(interview.getInterviewRound()).interviewAt(interview.getInterviewAt())
                    .status(interview.getStatus()).processingStep(interview.getProcessingStep())
                    .deleted(interview.getDeleted()).purgeAfter(interview.getPurgeAfter())
                    .errorMessage(interview.getErrorMessage()).overallScore(interview.getOverallScore())
                    .createdAt(interview.getCreatedAt()).build();
        }).toList();
        return new PageResult<>(records, result.getTotal(), query.getPage(), query.getSize());
    }

    @Override
    public InterviewDetailResponse getInterviewDetail(Long interviewId) {
        return interviewService.getDetailForAdmin(interviewId);
    }

    @Override
    @Transactional
    public void cancelInterview(Long interviewId) {
        Interview interview = requireAdminInterview(interviewId);
        interviewService.cancelByAdmin(interviewId, interview.getUserId());
        auditService.recordSuccess(SecurityUtils.getCurrentUserId(), "INTERVIEW_ADMIN_CANCEL",
                "INTERVIEW", String.valueOf(interviewId), String.valueOf(interview.getStatus()), "3");
    }

    @Override
    @Transactional
    public void retryInterview(Long interviewId) {
        Interview interview = requireAdminInterview(interviewId);
        interviewService.retry(interviewId, interview.getUserId());
        auditService.recordSuccess(SecurityUtils.getCurrentUserId(), "INTERVIEW_ADMIN_RETRY",
                "INTERVIEW", String.valueOf(interviewId), String.valueOf(interview.getStatus()), "0");
    }

    @Override
    @Transactional
    public void editInterview(Long interviewId, AdminInterviewEditRequest request) {
        Interview interview = requireAdminInterview(interviewId);
        interview.setTitle(request.getTitle().trim());
        interview.setCompanyName(trim(request.getCompanyName()));
        interview.setPositionTitle(request.getPositionTitle().trim());
        interview.setInterviewRound(trim(request.getInterviewRound()));
        interviewMapper.updateById(interview);
        auditService.recordSuccess(SecurityUtils.getCurrentUserId(), "INTERVIEW_ADMIN_EDIT",
                "INTERVIEW", String.valueOf(interviewId), null, null);
    }

    @Override
    @Transactional
    public void deleteInterview(Long interviewId) {
        Interview interview = requireAdminInterview(interviewId);
        interviewService.delete(interviewId, interview.getUserId());
        auditService.recordSuccess(SecurityUtils.getCurrentUserId(), "INTERVIEW_ADMIN_DELETE",
                "INTERVIEW", String.valueOf(interviewId), null, null);
    }

    @Override
    @Transactional
    public void restoreInterview(Long interviewId) {
        Interview interview = interviewMapper.selectDeletedById(interviewId);
        if (interview == null) throw new BusinessException(ErrorCode.INTERVIEW_NOT_FOUND.getCode(),
                ErrorCode.INTERVIEW_NOT_FOUND.getMessage());
        interviewService.restore(interviewId, interview.getUserId());
        auditService.recordSuccess(SecurityUtils.getCurrentUserId(), "INTERVIEW_ADMIN_RESTORE",
                "INTERVIEW", String.valueOf(interviewId), null, null);
    }

    private Interview requireAdminInterview(Long interviewId) {
        Interview interview = interviewMapper.selectById(interviewId);
        if (interview == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_FOUND.getCode(),
                    ErrorCode.INTERVIEW_NOT_FOUND.getMessage());
        }
        return interview;
    }

    // ==================== 面经管理 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<ExperienceManagementResponse> listExperiences(int page, int size) {
        Page<InterviewExperience> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<InterviewExperience> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(InterviewExperience::getPublishedAt);

        Page<InterviewExperience> result = interviewExperienceMapper.selectPage(pageParam, wrapper);

        // 批量查询用户信息
        List<Long> userIds = result.getRecords().stream()
                .map(InterviewExperience::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> usernameMap = getUsernamesByIds(userIds);

        List<ExperienceManagementResponse> records = result.getRecords().stream()
                .map(share -> ExperienceManagementResponse.builder()
                        .id(share.getId())
                        .interviewId(share.getInterviewId())
                        .userId(share.getUserId())
                        .username(usernameMap.get(share.getUserId()))
                        .publishStatus(share.getPublishStatus())
                        .moderationStatus(share.getModerationStatus())
                        .title(share.getTitle())
                        .positionTitle(share.getPositionTitle())
                        .viewCount(share.getViewCount())
                        .publishedAt(share.getPublishedAt())
                        .build())
                .collect(Collectors.toList());

        return new PageResult<>(records, result.getTotal(), page, size);
    }

    @Override
    public PageResult<ExperienceManagementResponse> searchExperiences(AdminExperienceQuery query) {
        validatePage(query.getPage(), query.getSize());
        if (query.getModeration() != null && !query.getModeration().isBlank()
                && !InterviewExperience.NORMAL.equals(query.getModeration())
                && !InterviewExperience.BLOCKED.equals(query.getModeration())) badFilter();
        validateRange(query.getFrom(), query.getTo());
        Page<InterviewExperience> result = interviewExperienceMapper.selectAdminPage(
                new Page<>(query.getPage(), query.getSize()), trim(query.getTitle()), trim(query.getAuthor()),
                trim(query.getCompany()), trim(query.getPosition()), trim(query.getModeration()),
                query.getFrom(), query.getTo());
        Map<Long, User> users = getUsersByIds(result.getRecords().stream()
                .map(InterviewExperience::getUserId).distinct().toList());
        List<ExperienceManagementResponse> records = result.getRecords().stream().map(experience -> {
            User author = users.get(experience.getUserId());
            String authorName = author != null && author.getNickname() != null && !author.getNickname().isBlank()
                    ? author.getNickname() : author == null ? null : author.getUsername();
            Interview interview = interviewMapper.selectAnyById(experience.getInterviewId());
            return ExperienceManagementResponse.builder()
                    .id(experience.getId()).interviewId(experience.getInterviewId())
                    .userId(experience.getUserId()).username(author == null ? null : author.getUsername())
                    .authorDisplayName(authorName)
                    .title(experience.getTitle()).positionTitle(experience.getPositionTitle())
                    .companyName(interview == null ? null : interview.getCompanyName())
                    .publishStatus(experience.getPublishStatus()).moderationStatus(experience.getModerationStatus())
                    .publishedAt(experience.getPublishedAt()).viewCount(experience.getViewCount()).build();
        }).toList();
        return new PageResult<>(records, result.getTotal(), query.getPage(), query.getSize());
    }

    @Override
    public ExperienceManagementDetailResponse getExperienceDetail(Long experienceId) {
        InterviewExperience experience = interviewExperienceMapper.selectById(experienceId);
        if (experience == null || !InterviewExperience.PUBLISHED.equals(experience.getPublishStatus())) {
            throw new BusinessException(ErrorCode.EXPERIENCE_NOT_FOUND.getCode(), ErrorCode.EXPERIENCE_NOT_FOUND.getMessage());
        }
        User author = userMapper.selectById(experience.getUserId());
        Interview source = interviewMapper.selectAnyById(experience.getInterviewId());
        boolean showAnswers = Integer.valueOf(1).equals(experience.getShowAnswers());
        List<ExperienceManagementDetailResponse.QuestionItem> questions = experienceQuestionSnapshotMapper
                .selectList(new LambdaQueryWrapper<ExperienceQuestionSnapshot>()
                        .eq(ExperienceQuestionSnapshot::getExperienceId, experienceId)
                        .eq(ExperienceQuestionSnapshot::getSelected, 1)
                        .orderByAsc(ExperienceQuestionSnapshot::getQuestionIndex))
                .stream().map(question -> ExperienceManagementDetailResponse.QuestionItem.builder()
                        .questionIndex(question.getQuestionIndex())
                        .questionText(question.getQuestionText())
                        .referenceAnswer(showAnswers ? question.getReferenceAnswer() : null)
                        .build()).toList();
        return ExperienceManagementDetailResponse.builder()
                .id(experience.getId()).title(experience.getTitle()).positionTitle(experience.getPositionTitle())
                .companyName(source == null ? null : source.getCompanyName())
                .username(author == null ? null : author.getUsername())
                .publishStatus(experience.getPublishStatus()).moderationStatus(experience.getModerationStatus())
                .showReferenceAnswers(showAnswers).publishedAt(experience.getPublishedAt())
                .questions(questions).build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void setExperienceStatus(Long experienceId, Integer isPublic) {
        InterviewExperience share = interviewExperienceMapper.selectById(experienceId);
        if (share == null || !InterviewExperience.PUBLISHED.equals(share.getPublishStatus())) {
            throw new BusinessException(ErrorCode.EXPERIENCE_NOT_FOUND.getCode(), ErrorCode.EXPERIENCE_NOT_FOUND.getMessage());
        }
        if (isPublic == null || (isPublic != 0 && isPublic != 1)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "面经治理状态只能是0或1");
        }
        String beforeStatus = share.getModerationStatus();
        String nextStatus = isPublic == 1 ? InterviewExperience.NORMAL : InterviewExperience.BLOCKED;
        if (nextStatus.equals(beforeStatus)) return;
        share.setModerationStatus(nextStatus);
        interviewExperienceMapper.updateById(share);
        auditService.recordSuccess(SecurityUtils.getCurrentUserId(), "EXPERIENCE_STATUS_UPDATE",
                "INTERVIEW_EXPERIENCE", String.valueOf(share.getId()), beforeStatus, nextStatus);

        log.info("管理员设置面经状态: experienceId={}, moderationStatus={}", experienceId, nextStatus);
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
                        .experienceId(comment.getExperienceId())
                        .userId(comment.getUserId())
                        .username(usernameMap.get(comment.getUserId()))
                        .content(comment.getContent())
                        .createdAt(comment.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return new PageResult<>(records, result.getTotal(), page, size);
    }

    @Override
    public PageResult<CommentManagementResponse> searchComments(AdminCommentQuery query) {
        if (query.getPage() < 1 || !Set.of(10, 20, 50).contains(query.getSize())
                || (query.getStatus() == null || (query.getStatus() != 0 && query.getStatus() != 1))
                || (query.getStartTime() != null && query.getEndTime() != null
                    && query.getStartTime().isAfter(query.getEndTime()))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "分页或筛选参数不合法");
        }
        Page<InterviewComment> result = interviewCommentMapper.selectAdminPage(
                new Page<>(query.getPage(), query.getSize()), query.getKeyword(), query.getAuthor(),
                query.getExperience(), query.getUserId(), query.getStatus(),
                query.getStartTime(), query.getEndTime());
        List<CommentManagementResponse> records = result.getRecords().stream()
                .map(this::toCommentManagementResponse).toList();
        return new PageResult<>(records, result.getTotal(), query.getPage(), query.getSize());
    }

    @Override
    public CommentManagementResponse getCommentDetail(Long commentId) {
        InterviewComment comment = interviewCommentMapper.selectAdminById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        return toCommentManagementResponse(comment);
    }

    private CommentManagementResponse toCommentManagementResponse(InterviewComment comment) {
        String displayName = comment.getAuthorNickname();
        if (displayName == null || displayName.isBlank()) displayName = comment.getAuthorUsername();
        return CommentManagementResponse.builder()
                .id(comment.getId()).parentId(comment.getParentId()).parentContent(comment.getParentContent())
                .experienceId(comment.getExperienceId()).experienceTitle(comment.getExperienceTitle())
                .userId(comment.getUserId()).username(comment.getAuthorUsername())
                .authorDisplayName(displayName).content(comment.getContent())
                .deleted(comment.getDeleted()).createdAt(comment.getCreatedAt())
                .deletedAt(comment.getDeletedAt()).build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteComment(Long commentId) {
        InterviewComment comment = interviewCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(),
                    ErrorCode.NOT_FOUND.getMessage());
        }

        commentDeletionService.deleteThread(comment);
        auditService.recordSuccess(SecurityUtils.getCurrentUserId(), "COMMENT_DELETE",
                "INTERVIEW_COMMENT", String.valueOf(commentId),
                Map.of("experienceId", comment.getExperienceId(), "userId", comment.getUserId()), null);
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
                .roles(rbacService.getRoleCodes(user.getId()))
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

    private Map<Long, User> getUsersByIds(List<Long> ids) {
        if (ids.isEmpty()) return java.util.Collections.emptyMap();
        return userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, ids)).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    private void validatePage(int page, int size) {
        if (page < 1 || (size != 10 && size != 20 && size != 50)) badFilter();
    }

    private void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to)) badFilter();
    }

    private void badFilter() {
        throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "分页或筛选参数不合法");
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
