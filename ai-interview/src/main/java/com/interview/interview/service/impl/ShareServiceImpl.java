package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.interview.dto.ExperienceDetailResponse;
import com.interview.interview.dto.ShareResponse;
import com.interview.interview.entity.Interview;
import com.interview.interview.entity.InterviewQuestion;
import com.interview.interview.entity.InterviewShare;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.mapper.InterviewQuestionMapper;
import com.interview.interview.mapper.InterviewShareMapper;
import com.interview.interview.service.ShareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 分享服务实现类
 * <p>
 * 提供面试记录的分享链接管理功能。每个分享记录包含：
 * <ul>
 *   <li>唯一 Token（UUID，32位无连字符）</li>
 *   <li>过期时间（可选）</li>
 *   <li>公开状态（是否在面经广场展示）</li>
 *   <li>浏览量统计</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private final InterviewShareMapper shareMapper;
    private final InterviewMapper interviewMapper;
    private final InterviewQuestionMapper questionMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public ShareResponse createShare(Long interviewId, Long userId, String expireType, Boolean isPublic) {
        // 验证面试记录存在且属于当前用户
        Interview interview = interviewMapper.selectById(interviewId);
        if (interview == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_FOUND.getCode(),
                    ErrorCode.INTERVIEW_NOT_FOUND.getMessage());
        }
        if (!interview.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.INTERVIEW_ACCESS_DENIED.getCode(),
                    ErrorCode.INTERVIEW_ACCESS_DENIED.getMessage());
        }

        // 生成唯一 token
        String token = UUID.randomUUID().toString().replace("-", "");

        // 计算过期时间
        LocalDateTime expireAt = null;
        if (expireType != null) {
            switch (expireType) {
                case "24h":
                    expireAt = LocalDateTime.now().plusHours(24);
                    break;
                case "7d":
                    expireAt = LocalDateTime.now().plusDays(7);
                    break;
                case "30d":
                    expireAt = LocalDateTime.now().plusDays(30);
                    break;
                case "never":
                    expireAt = null;
                    break;
                default:
                    expireAt = LocalDateTime.now().plusDays(7);
            }
        }

        InterviewShare share = new InterviewShare();
        share.setInterviewId(interviewId);
        share.setUserId(userId);
        share.setShareToken(token);
        share.setExpireAt(expireAt);
        share.setIsPublic(isPublic != null && isPublic ? 1 : 0);
        share.setViewCount(0);
        shareMapper.insert(share);

        log.info("创建分享: interviewId={}, token={}", interviewId, token);
        return ShareResponse.builder()
                .id(share.getId())
                .interviewId(share.getInterviewId())
                .token(share.getShareToken())
                .expireAt(share.getExpireAt())
                .isPublic(share.getIsPublic())
                .viewCount(share.getViewCount())
                .createdAt(share.getCreatedAt())
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ShareResponse> listShares(Long interviewId, Long userId) {
        LambdaQueryWrapper<InterviewShare> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewShare::getInterviewId, interviewId);
        wrapper.eq(InterviewShare::getUserId, userId);
        // 过滤掉已过期的分享（永不过期的视为有效）
        wrapper.and(w -> w.isNull(InterviewShare::getExpireAt)
                .or()
                .gt(InterviewShare::getExpireAt, LocalDateTime.now()));
        wrapper.orderByDesc(InterviewShare::getCreatedAt);

        return shareMapper.selectList(wrapper).stream()
                .map(s -> ShareResponse.builder()
                        .id(s.getId())
                        .interviewId(s.getInterviewId())
                        .token(s.getShareToken())
                        .expireAt(s.getExpireAt())
                        .isPublic(s.getIsPublic())
                        .viewCount(s.getViewCount())
                        .createdAt(s.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteShare(String token, Long userId) {
        LambdaQueryWrapper<InterviewShare> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewShare::getShareToken, token);
        wrapper.eq(InterviewShare::getUserId, userId);

        InterviewShare share = shareMapper.selectOne(wrapper);
        if (share == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND.getCode(),
                    ErrorCode.SHARE_NOT_FOUND.getMessage());
        }

        shareMapper.deleteById(share.getId());
        log.info("删除分享: token={}, userId={}", token, userId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void togglePublic(String token, Long userId) {
        LambdaQueryWrapper<InterviewShare> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewShare::getShareToken, token);
        wrapper.eq(InterviewShare::getUserId, userId);

        InterviewShare share = shareMapper.selectOne(wrapper);
        if (share == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND.getCode(),
                    ErrorCode.SHARE_NOT_FOUND.getMessage());
        }

        share.setIsPublic(share.getIsPublic() == 1 ? 0 : 1);
        shareMapper.updateById(share);

        log.info("切换公开状态: token={}, isPublic={}", token, share.getIsPublic());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExperienceDetailResponse getShareDetail(String token) {
        LambdaQueryWrapper<InterviewShare> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewShare::getShareToken, token);

        InterviewShare share = shareMapper.selectOne(wrapper);
        if (share == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND.getCode(),
                    ErrorCode.SHARE_NOT_FOUND.getMessage());
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

        // 重新查询最新数据返回
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
