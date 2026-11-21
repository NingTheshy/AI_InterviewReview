package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.auth.entity.User;
import com.interview.auth.mapper.UserMapper;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.common.result.PageResult;
import com.interview.common.utils.AvatarPaths;
import com.interview.interview.dto.CommentResponse;
import com.interview.interview.entity.InterviewComment;
import com.interview.interview.entity.InterviewExperience;
import com.interview.interview.mapper.InterviewCommentMapper;
import com.interview.interview.mapper.InterviewExperienceMapper;
import com.interview.interview.service.CommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.Collectors;

/**
 * 评论服务实现类
 * <p>
 * 提供面经评论的查看、发表和删除功能。
 * 评论通过面经 ID 关联，根评论和每层回复分别分页。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final InterviewCommentMapper commentMapper;
    private final InterviewExperienceMapper experienceMapper;
    private final UserMapper userMapper;
    private final CommentDeletionService deletionService;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<CommentResponse> listByExperienceId(Long experienceId, int page, int size) {
        InterviewExperience experience = requireVisible(experienceId);
        return listPage(experience.getId(), null, page, size);
    }

    @Override
    public PageResult<CommentResponse> listReplies(Long experienceId, Long parentId, int page, int size) {
        InterviewExperience experience = requireVisible(experienceId);
        InterviewComment parent = commentMapper.selectById(parentId);
        if (parent == null || !experience.getId().equals(parent.getExperienceId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        return listPage(experienceId, parentId, page, size);
    }

    private PageResult<CommentResponse> listPage(Long experienceId, Long parentId, int page, int size) {
        if (page < 1 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "分页参数不合法");
        }
        Page<InterviewComment> result = commentMapper.selectThreadPage(new Page<>(page, size), experienceId, parentId);

        List<Long> userIds = result.getRecords().stream()
                .flatMap(comment -> Stream.of(comment.getUserId(), comment.getReplyToUserId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, User> userMap = Collections.emptyMap();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            userMap = users.stream()
                    .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        }

        Map<Long, User> finalUserMap = userMap;
        List<CommentResponse> commentResponses = result.getRecords().stream()
                .map(comment -> {
                    User user = finalUserMap.get(comment.getUserId());
                    User parentAuthor = finalUserMap.get(comment.getReplyToUserId());
                    return CommentResponse.builder()
                            .id(comment.getId())
                            .parentId(comment.getParentId())
                            .replyCount(comment.getReplyCount())
                            .replyToNickname(parentAuthor != null ? displayName(parentAuthor) : null)
                            .userId(comment.getUserId())
                            .nickname(user != null ? displayName(user) : "未知用户")
                            .avatar(user == null ? null : AvatarPaths.visibleUrl(user.getAvatar()))
                            .content(comment.getContent())
                            .createdAt(comment.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        return new PageResult<>(commentResponses, result.getTotal(), page, size);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void addComment(Long experienceId, Long userId, String content) {
        addComment(experienceId, userId, content, null);
    }

    @Override
    @Transactional
    public void addComment(Long experienceId, Long userId, String content, Long parentId) {
        // 验证评论内容
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.COMMENT_EMPTY.getCode(),
                    ErrorCode.COMMENT_EMPTY.getMessage());
        }
        if (content.length() > 500) {
            throw new BusinessException(ErrorCode.COMMENT_TOO_LONG.getCode(),
                    ErrorCode.COMMENT_TOO_LONG.getMessage());
        }

        InterviewExperience experience = requireVisible(experienceId);
        commentMapper.lockExperience(experience.getId());
        Long replyToUserId = null;
        if (parentId != null) {
            InterviewComment parent = commentMapper.selectById(parentId);
            if (parent == null || !experience.getId().equals(parent.getExperienceId())
                    || Integer.valueOf(1).equals(parent.getDeleted())) {
                throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "回复的评论不存在或已删除");
            }
            replyToUserId = parent.getUserId();
        }

        // 创建评论
        InterviewComment comment = new InterviewComment();
        comment.setExperienceId(experience.getId());
        comment.setParentId(parentId);
        comment.setReplyToUserId(replyToUserId);
        comment.setUserId(userId);
        comment.setContent(content.trim());
        commentMapper.insert(comment);

        log.info("用户发表评论: userId={}, experienceId={}", userId, experienceId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteComment(Long commentId, Long userId) {
        InterviewComment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(),
                    ErrorCode.NOT_FOUND.getMessage());
        }

        // 检查权限：评论作者或面经发布者可删除
        if (comment.getUserId().equals(userId)) {
            // 评论作者可以删除
            deletionService.deleteThread(comment);
            log.info("评论作者删除评论: commentId={}, userId={}", commentId, userId);
            return;
        }

        // 检查是否是面经发布者
        InterviewExperience experience = experienceMapper.selectById(comment.getExperienceId());
        if (experience != null && experience.getUserId().equals(userId)) {
            // 面经发布者可以删除评论
            deletionService.deleteThread(comment);
            log.info("面经发布者删除评论: commentId={}, userId={}", commentId, userId);
            return;
        }

        // 无权删除
        throw new BusinessException(ErrorCode.COMMENT_ACCESS_DENIED.getCode(),
                ErrorCode.COMMENT_ACCESS_DENIED.getMessage());
    }

    private InterviewExperience requireVisible(Long id) {
        InterviewExperience value = experienceMapper.selectById(id);
        if (value == null || !value.isVisible()) {
            throw new BusinessException(ErrorCode.EXPERIENCE_NOT_FOUND.getCode(),
                    ErrorCode.EXPERIENCE_NOT_FOUND.getMessage());
        }
        return value;
    }

    private String displayName(User user) {
        return user.getNickname() == null || user.getNickname().isBlank()
                ? user.getUsername() : user.getNickname();
    }
}
