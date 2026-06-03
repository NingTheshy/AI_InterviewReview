package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.auth.entity.User;
import com.interview.auth.mapper.UserMapper;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.common.result.PageResult;
import com.interview.interview.dto.CommentResponse;
import com.interview.interview.entity.InterviewComment;
import com.interview.interview.entity.InterviewShare;
import com.interview.interview.mapper.InterviewCommentMapper;
import com.interview.interview.mapper.InterviewShareMapper;
import com.interview.interview.service.CommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评论服务实现类
 * <p>
 * 提供面经评论的查看、发表和删除功能。
 * 评论通过分享 Token 关联到具体的面经。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final InterviewCommentMapper commentMapper;
    private final InterviewShareMapper shareMapper;
    private final UserMapper userMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<CommentResponse> listByShareToken(String token, int page, int size) {
        // 通过 token 查找分享记录
        LambdaQueryWrapper<InterviewShare> shareWrapper = new LambdaQueryWrapper<>();
        shareWrapper.eq(InterviewShare::getShareToken, token);
        InterviewShare share = shareMapper.selectOne(shareWrapper);

        if (share == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND.getCode(),
                    ErrorCode.SHARE_NOT_FOUND.getMessage());
        }

        // 查询该分享下的所有评论（分页）
        Page<InterviewComment> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<InterviewComment> commentWrapper = new LambdaQueryWrapper<>();
        commentWrapper.eq(InterviewComment::getShareId, share.getId());
        commentWrapper.orderByDesc(InterviewComment::getCreatedAt);

        Page<InterviewComment> result = commentMapper.selectPage(pageParam, commentWrapper);

        // 批量查询用户信息
        List<Long> userIds = result.getRecords().stream()
                .map(InterviewComment::getUserId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, User> userMap = Collections.emptyMap();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            userMap = users.stream()
                    .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        }

        // 构建带用户信息的评论响应
        Map<Long, User> finalUserMap = userMap;
        List<CommentResponse> commentResponses = result.getRecords().stream()
                .map(comment -> {
                    User user = finalUserMap.get(comment.getUserId());
                    return CommentResponse.builder()
                            .id(comment.getId())
                            .userId(comment.getUserId())
                            .nickname(user != null ? user.getNickname() : "未知用户")
                            .avatar(user != null ? user.getAvatar() : null)
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
    public void addComment(String token, Long userId, String content) {
        // 验证评论内容
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.COMMENT_EMPTY.getCode(),
                    ErrorCode.COMMENT_EMPTY.getMessage());
        }
        if (content.length() > 500) {
            throw new BusinessException(ErrorCode.COMMENT_TOO_LONG.getCode(),
                    ErrorCode.COMMENT_TOO_LONG.getMessage());
        }

        // 查找分享记录
        LambdaQueryWrapper<InterviewShare> shareWrapper = new LambdaQueryWrapper<>();
        shareWrapper.eq(InterviewShare::getShareToken, token);
        InterviewShare share = shareMapper.selectOne(shareWrapper);

        if (share == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND.getCode(),
                    ErrorCode.SHARE_NOT_FOUND.getMessage());
        }

        // 创建评论
        InterviewComment comment = new InterviewComment();
        comment.setShareId(share.getId());
        comment.setUserId(userId);
        comment.setContent(content.trim());
        commentMapper.insert(comment);

        log.info("用户发表评论: userId={}, token={}", userId, token);
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
            commentMapper.deleteById(commentId);
            log.info("评论作者删除评论: commentId={}, userId={}", commentId, userId);
            return;
        }

        // 检查是否是面经发布者
        InterviewShare share = shareMapper.selectById(comment.getShareId());
        if (share != null && share.getUserId().equals(userId)) {
            // 面经发布者可以删除评论
            commentMapper.deleteById(commentId);
            log.info("面经发布者删除评论: commentId={}, userId={}", commentId, userId);
            return;
        }

        // 无权删除
        throw new BusinessException(ErrorCode.COMMENT_ACCESS_DENIED.getCode(),
                ErrorCode.COMMENT_ACCESS_DENIED.getMessage());
    }
}
