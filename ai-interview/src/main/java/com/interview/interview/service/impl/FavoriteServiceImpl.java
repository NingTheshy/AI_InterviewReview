package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.common.result.PageResult;
import com.interview.interview.dto.FavoriteResponse;
import com.interview.interview.entity.InterviewFavorite;
import com.interview.interview.entity.InterviewQuestion;
import com.interview.interview.entity.Interview;
import com.interview.interview.mapper.InterviewFavoriteMapper;
import com.interview.interview.mapper.InterviewQuestionMapper;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.service.FavoriteService;
import com.interview.interview.service.InterviewResourceAccessService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 收藏服务实现类
 * <p>
 * 提供面试问题的收藏管理功能。
 * 同一用户不能重复收藏同一面试/问题。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final InterviewFavoriteMapper favoriteMapper;
    private final InterviewResourceAccessService resourceAccessService;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewMapper interviewMapper;

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
        if (questionId == null) throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "收藏仅支持具体问题");
        resourceAccessService.requireVisibleQuestion(interviewId, questionId, userId);

        // 检查是否已收藏
        LambdaQueryWrapper<InterviewFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewFavorite::getUserId, userId);
        wrapper.eq(InterviewFavorite::getInterviewId, interviewId);
        wrapper.eq(InterviewFavorite::getQuestionId, questionId);

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
    public PageResult<FavoriteResponse> list(Long userId, int page, int size, Long interviewId,
                                              Integer minScore, Integer maxScore, String sortBy) {
        if (sortBy == null || sortBy.isBlank()) sortBy = "createdAt";
        if (page < 1 || size < 1 || size > 100 || !Set.of("createdAt", "score").contains(sortBy)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "分页或排序参数不合法");
        }
        LambdaQueryWrapper<InterviewFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewFavorite::getUserId, userId);
        if (interviewId != null) wrapper.eq(InterviewFavorite::getInterviewId, interviewId);
        wrapper.orderByDesc(InterviewFavorite::getCreatedAt);

        // 分数筛选和分数排序依赖问题表，必须先完成关联与筛选再分页，
        // 否则会出现当前页数量不足且 total 仍为筛选前数量的问题。
        List<InterviewFavorite> favorites = favoriteMapper.selectList(wrapper);
        List<Long> questionIds = favorites.stream().map(InterviewFavorite::getQuestionId).toList();
        Map<Long, InterviewQuestion> questionMap = questionIds.isEmpty() ? Collections.emptyMap()
                : questionMapper.selectBatchIds(questionIds).stream().collect(Collectors.toMap(InterviewQuestion::getId, Function.identity()));
        List<Long> interviewIds = favorites.stream().map(InterviewFavorite::getInterviewId).distinct().toList();
        Map<Long, Interview> interviewMap = interviewIds.isEmpty() ? Collections.emptyMap()
                : interviewMapper.selectBatchIds(interviewIds).stream().collect(Collectors.toMap(Interview::getId, Function.identity()));
        Comparator<FavoriteResponse> comparator = "score".equals(sortBy)
                ? Comparator.comparing(FavoriteResponse::getScore, Comparator.nullsLast(Comparator.reverseOrder()))
                : Comparator.comparing(FavoriteResponse::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        List<FavoriteResponse> filtered = favorites.stream()
                .map(f -> toResponse(f, questionMap.get(f.getQuestionId()), interviewMap.get(f.getInterviewId())))
                .filter(r -> minScore == null || r.getScore() != null && r.getScore() >= minScore)
                .filter(r -> maxScore == null || r.getScore() != null && r.getScore() <= maxScore)
                .sorted(comparator)
                .collect(Collectors.toList());
        int from = Math.min((page - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        return new PageResult<>(filtered.subList(from, to), (long) filtered.size(), page, size);
    }

    private FavoriteResponse toResponse(InterviewFavorite favorite, InterviewQuestion question, Interview interview) {
        return FavoriteResponse.builder().id(favorite.getId()).interviewId(favorite.getInterviewId())
                .questionId(favorite.getQuestionId()).questionText(question == null ? null : question.getQuestionText())
                .referenceAnswer(question == null ? null : question.getReferenceAnswer())
                .score(question == null ? null : question.getScore())
                .interviewTitle(interview == null ? null : interview.getTitle())
                .archived(question != null && Integer.valueOf(0).equals(question.getIsActive()))
                .remark(favorite.getRemark()).createdAt(favorite.getCreatedAt()).build();
    }
}
