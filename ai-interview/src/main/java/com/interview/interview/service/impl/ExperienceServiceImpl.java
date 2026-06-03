package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.common.result.PageResult;
import com.interview.interview.dto.ExperienceDetailResponse;
import com.interview.interview.dto.ExperienceListResponse;
import com.interview.interview.entity.Interview;
import com.interview.interview.entity.InterviewQuestion;
import com.interview.interview.entity.InterviewShare;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.mapper.InterviewQuestionMapper;
import com.interview.interview.mapper.InterviewShareMapper;
import com.interview.interview.service.ExperienceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 面经服务实现类
 * <p>
 * 提供面经广场的浏览功能。面经来源于用户主动公开的分享记录，
 * 仅返回 isPublic=1 且未过期的记录。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExperienceServiceImpl implements ExperienceService {

    private final InterviewShareMapper shareMapper;
    private final InterviewMapper interviewMapper;
    private final InterviewQuestionMapper questionMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<ExperienceListResponse> listPublic(int page, int size,
                                                          String companyName, String positionTitle,
                                                          String industry, String sortBy) {
        // 先用 Interview 表筛选出匹配的 interviewId 列表，再用 IN 查询 InterviewShare（带分页）
        LambdaQueryWrapper<Interview> interviewWrapper = new LambdaQueryWrapper<>();
        if (companyName != null && !companyName.isEmpty()) {
            interviewWrapper.like(Interview::getCompanyName, companyName);
        }
        if (positionTitle != null && !positionTitle.isEmpty()) {
            interviewWrapper.like(Interview::getPositionTitle, positionTitle);
        }
        if (industry != null && !industry.isEmpty()) {
            interviewWrapper.eq(Interview::getIndustry, industry);
        }

        List<Long> filteredInterviewIds;
        boolean hasFilters = (companyName != null && !companyName.isEmpty())
                || (positionTitle != null && !positionTitle.isEmpty())
                || (industry != null && !industry.isEmpty());

        if (hasFilters) {
            filteredInterviewIds = interviewMapper.selectList(interviewWrapper).stream()
                    .map(Interview::getId)
                    .collect(Collectors.toList());
            if (filteredInterviewIds.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0, page, size);
            }
        } else {
            filteredInterviewIds = null;
        }

        Page<InterviewShare> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<InterviewShare> wrapper = new LambdaQueryWrapper<>();

        // 只查询公开且未过期的面经
        wrapper.eq(InterviewShare::getIsPublic, 1);
        wrapper.and(w -> w.isNull(InterviewShare::getExpireAt)
                .or()
                .gt(InterviewShare::getExpireAt, LocalDateTime.now()));

        // 筛选条件（SQL 层面过滤）
        if (filteredInterviewIds != null) {
            wrapper.in(InterviewShare::getInterviewId, filteredInterviewIds);
        }

        // 排序
        if ("viewCount".equals(sortBy)) {
            wrapper.orderByDesc(InterviewShare::getViewCount);
        } else {
            wrapper.orderByDesc(InterviewShare::getCreatedAt);
        }

        Page<InterviewShare> result = shareMapper.selectPage(pageParam, wrapper);

        // 批量查询关联的面试信息
        List<Long> interviewIds = result.getRecords().stream()
                .map(InterviewShare::getInterviewId)
                .collect(Collectors.toList());

        final Map<Long, Interview> interviewMap;
        if (!interviewIds.isEmpty()) {
            List<Interview> interviews = interviewMapper.selectBatchIds(interviewIds);
            interviewMap = interviews.stream()
                    .collect(Collectors.toMap(Interview::getId, i -> i, (a, b) -> a));
        } else {
            interviewMap = Collections.emptyMap();
        }

        List<ExperienceListResponse> records = result.getRecords().stream()
                .map(share -> {
                    Interview interview = interviewMap.get(share.getInterviewId());
                    if (interview == null) return null;

                    return ExperienceListResponse.builder()
                            .token(share.getShareToken())
                            .title(interview.getTitle())
                            .companyName(interview.getCompanyName())
                            .positionTitle(interview.getPositionTitle())
                            .industry(interview.getIndustry())
                            .interviewType(interview.getInterviewType())
                            .overallScore(interview.getOverallScore())
                            .viewCount(share.getViewCount())
                            .createdAt(share.getCreatedAt())
                            .build();
                })
                .filter(r -> r != null)
                .collect(Collectors.toList());

        // 查询问题数量
        if (!interviewIds.isEmpty()) {
            LambdaQueryWrapper<InterviewQuestion> qWrapper = new LambdaQueryWrapper<>();
            qWrapper.in(InterviewQuestion::getInterviewId, interviewIds);
            List<InterviewQuestion> allQuestions = questionMapper.selectList(qWrapper);
            Map<Long, Long> questionCountMap = allQuestions.stream()
                    .collect(Collectors.groupingBy(InterviewQuestion::getInterviewId, Collectors.counting()));

            records.forEach(r -> {
                InterviewShare matchingShare = result.getRecords().stream()
                        .filter(s -> s.getShareToken().equals(r.getToken()))
                        .findFirst().orElse(null);
                if (matchingShare != null) {
                    r.setQuestionCount(questionCountMap.getOrDefault(
                            matchingShare.getInterviewId(), 0L).intValue());
                }
            });
        }

        return new PageResult<>(records, result.getTotal(), page, size);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExperienceDetailResponse getDetail(String token) {
        LambdaQueryWrapper<InterviewShare> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewShare::getShareToken, token);

        InterviewShare share = shareMapper.selectOne(wrapper);
        if (share == null) {
            throw new BusinessException(ErrorCode.EXPERIENCE_NOT_FOUND.getCode(),
                    ErrorCode.EXPERIENCE_NOT_FOUND.getMessage());
        }

        // 检查是否过期
        if (share.getExpireAt() != null && share.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.SHARE_EXPIRED.getCode(),
                    ErrorCode.SHARE_EXPIRED.getMessage());
        }

        // 增加浏览量（原子操作，防止并发丢失更新）
        LambdaUpdateWrapper<InterviewShare> viewUpdate = new LambdaUpdateWrapper<>();
        viewUpdate.eq(InterviewShare::getId, share.getId());
        viewUpdate.setSql("view_count = view_count + 1");
        shareMapper.update(null, viewUpdate);

        // 重新查询最新数据
        share = shareMapper.selectById(share.getId());

        // 查询关联的面试信息
        Interview interview = interviewMapper.selectById(share.getInterviewId());
        if (interview == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_FOUND.getCode(),
                    ErrorCode.INTERVIEW_NOT_FOUND.getMessage());
        }

        // 查询问题列表
        LambdaQueryWrapper<InterviewQuestion> questionWrapper = new LambdaQueryWrapper<>();
        questionWrapper.eq(InterviewQuestion::getInterviewId, share.getInterviewId());
        questionWrapper.orderByAsc(InterviewQuestion::getQuestionIndex);
        List<InterviewQuestion> questions = questionMapper.selectList(questionWrapper);

        List<ExperienceDetailResponse.QuestionItem> questionItems = questions.stream()
                .map(q -> ExperienceDetailResponse.QuestionItem.builder()
                        .id(q.getId())
                        .questionIndex(q.getQuestionIndex())
                        .questionText(q.getQuestionText())
                        .score(q.getScore())
                        .improvementTip(q.getImprovementTip())
                        .referenceAnswer(q.getReferenceAnswer())
                        .build())
                .collect(Collectors.toList());

        return ExperienceDetailResponse.builder()
                .token(share.getShareToken())
                .title(interview.getTitle())
                .companyName(interview.getCompanyName())
                .positionTitle(interview.getPositionTitle())
                .industry(interview.getIndustry())
                .interviewType(interview.getInterviewType())
                .overallScore(interview.getOverallScore())
                .dimensionContent(interview.getDimensionContent())
                .dimensionLogic(interview.getDimensionLogic())
                .dimensionExpression(interview.getDimensionExpression())
                .dimensionProfessional(interview.getDimensionProfessional())
                .dimensionCommunication(interview.getDimensionCommunication())
                .improvementSummary(interview.getImprovementSummary())
                .strengths(interview.getStrengths())
                .weaknesses(interview.getWeaknesses())
                .questions(questionItems)
                .viewCount(share.getViewCount())
                .createdAt(share.getCreatedAt())
                .build();
    }
}
