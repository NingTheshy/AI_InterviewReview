package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.common.result.PageResult;
import com.interview.interview.dto.FavoriteResponse;
import com.interview.interview.entity.InterviewFavorite;
import com.interview.interview.mapper.InterviewFavoriteMapper;
import com.interview.interview.service.FavoriteService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 收藏服务实现类
 * <p>
 * 提供面试问题的收藏管理功能。收藏支持两种粒度：
 * <ul>
 *   <li>整个面试（questionId 为 null）</li>
 *   <li>具体问题（questionId 不为 null）</li>
 * </ul>
 * 同一用户不能重复收藏同一面试/问题。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final InterviewFavoriteMapper favoriteMapper;

    /**
     * 每个用户的收藏上限
     */
    private static final int FAVORITE_LIMIT = 100;

    /**
     * {@inheritDoc}
     * <p>
     * 实现逻辑：
     * 1. 查询是否已收藏（按用户+面试+问题去重）
     * 2. 已收藏则抛出异常
     * 3. 否则插入新收藏记录
     * </p>
     */
    @Override
    public void addFavorite(Long userId, Long interviewId, Long questionId, String remark) {
        // 检查收藏上限
        LambdaQueryWrapper<InterviewFavorite> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(InterviewFavorite::getUserId, userId);
        if (favoriteMapper.selectCount(countWrapper) >= FAVORITE_LIMIT) {
            throw new BusinessException(ErrorCode.FAVORITE_LIMIT.getCode(),
                    ErrorCode.FAVORITE_LIMIT.getMessage());
        }

        // 检查是否已收藏
        LambdaQueryWrapper<InterviewFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewFavorite::getUserId, userId);
        wrapper.eq(InterviewFavorite::getInterviewId, interviewId);
        if (questionId != null) {
            wrapper.eq(InterviewFavorite::getQuestionId, questionId);
        }

        if (favoriteMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.FAVORITE_EXISTS.getCode(),
                    ErrorCode.FAVORITE_EXISTS.getMessage());
        }

        InterviewFavorite favorite = new InterviewFavorite();
        favorite.setUserId(userId);
        favorite.setInterviewId(interviewId);
        favorite.setQuestionId(questionId);
        favorite.setRemark(remark);
        favoriteMapper.insert(favorite);

        log.info("添加收藏: userId={}, interviewId={}", userId, interviewId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 实现逻辑：校验收藏存在性及用户所有权后执行删除。
     * </p>
     */
    @Override
    public void removeFavorite(Long id, Long userId) {
        InterviewFavorite favorite = favoriteMapper.selectById(id);
        if (favorite == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(),
                    ErrorCode.NOT_FOUND.getMessage());
        }
        if (!favorite.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.INTERVIEW_ACCESS_DENIED.getCode(),
                    ErrorCode.INTERVIEW_ACCESS_DENIED.getMessage());
        }

        favoriteMapper.deleteById(id);
        log.info("取消收藏: id={}, userId={}", id, userId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 按创建时间倒序排列。
     * </p>
     */
    @Override
    public PageResult<FavoriteResponse> list(Long userId, int page, int size) {
        Page<InterviewFavorite> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<InterviewFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewFavorite::getUserId, userId);
        wrapper.orderByDesc(InterviewFavorite::getCreatedAt);

        Page<InterviewFavorite> result = favoriteMapper.selectPage(pageParam, wrapper);
        List<FavoriteResponse> records = result.getRecords().stream()
                .map(f -> FavoriteResponse.builder()
                        .id(f.getId())
                        .interviewId(f.getInterviewId())
                        .questionId(f.getQuestionId())
                        .remark(f.getRemark())
                        .createdAt(f.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), page, size);
    }
}
