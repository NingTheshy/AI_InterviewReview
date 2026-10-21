package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.auth.entity.User;
import com.interview.auth.mapper.UserMapper;
import com.interview.common.constant.ErrorCode;
import com.interview.common.constant.InterviewStatus;
import com.interview.common.exception.BusinessException;
import com.interview.common.result.PageResult;
import com.interview.interview.dto.ExperienceDetailResponse;
import com.interview.interview.dto.ExperienceListResponse;
import com.interview.interview.dto.ExperiencePublicationResponse;
import com.interview.interview.entity.Interview;
import com.interview.interview.entity.InterviewExperience;
import com.interview.interview.entity.InterviewQuestion;
import com.interview.interview.mapper.InterviewExperienceMapper;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.mapper.InterviewQuestionMapper;
import com.interview.interview.service.ExperienceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 公开面经发布与广场查询服务。 */
@Service
@RequiredArgsConstructor
public class ExperiencePublicationServiceImpl implements ExperienceService {
    private final InterviewExperienceMapper experienceMapper;
    private final InterviewMapper interviewMapper;
    private final InterviewQuestionMapper questionMapper;
    private final UserMapper userMapper;

    @Override
    public PageResult<ExperienceListResponse> listPublic(int page, int size, String companyName,
            String positionTitle, String industry, String sortBy) {
        if (page < 1 || size < 1 || size > 100 || (sortBy != null && !Set.of("viewCount", "publishedAt").contains(sortBy))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "分页或排序参数不合法");
        }
        LambdaQueryWrapper<Interview> filter = new LambdaQueryWrapper<Interview>()
                .eq(Interview::getStatus, InterviewStatus.COMPLETED.getCode());
        if (notBlank(companyName)) filter.like(Interview::getCompanyName, companyName.trim());
        if (notBlank(positionTitle)) filter.like(Interview::getPositionTitle, positionTitle.trim());
        if (notBlank(industry)) filter.eq(Interview::getIndustry, industry.trim());
        List<Long> interviewIds = interviewMapper.selectList(filter).stream().map(Interview::getId).toList();
        if (interviewIds.isEmpty()) return new PageResult<>(List.of(), 0, page, size);
        LambdaQueryWrapper<InterviewExperience> query = new LambdaQueryWrapper<InterviewExperience>()
                .eq(InterviewExperience::getPublishStatus, InterviewExperience.PUBLISHED)
                .eq(InterviewExperience::getModerationStatus, InterviewExperience.NORMAL)
                .in(InterviewExperience::getInterviewId, interviewIds);
        if ("viewCount".equals(sortBy)) query.orderByDesc(InterviewExperience::getViewCount);
        else query.orderByDesc(InterviewExperience::getPublishedAt);
        Page<InterviewExperience> result = experienceMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(toListResponses(result.getRecords()), result.getTotal(), page, size);
    }

    @Override
    @Transactional
    public ExperienceDetailResponse getDetail(Long id) {
        InterviewExperience experience = requireVisible(id);
        Interview interview = interviewMapper.selectById(experience.getInterviewId());
        if (interview == null || !Integer.valueOf(InterviewStatus.COMPLETED.getCode()).equals(interview.getStatus())) throw notFound();
        experienceMapper.update(null, new LambdaUpdateWrapper<InterviewExperience>()
                .eq(InterviewExperience::getId, id).setSql("view_count=view_count+1"));
        experience.setViewCount((experience.getViewCount() == null ? 0 : experience.getViewCount()) + 1);
        User author = userMapper.selectById(experience.getUserId());
        List<InterviewQuestion> questions = questionMapper.selectList(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getInterviewId, interview.getId()).eq(InterviewQuestion::getIsActive, 1)
                .orderByAsc(InterviewQuestion::getQuestionIndex));
        return ExperienceDetailResponse.builder().id(experience.getId())
                .authorNickname(author == null ? null : author.getNickname())
                .authorAvatar(author == null ? null : author.getAvatar()).summaryMarkdown(experience.getSummaryMarkdown())
                .title(interview.getTitle()).companyName(interview.getCompanyName()).positionTitle(interview.getPositionTitle())
                .industry(interview.getIndustry()).interviewType(interview.getInterviewType()).overallScore(interview.getOverallScore())
                .dimensionContent(interview.getDimensionContent()).dimensionLogic(interview.getDimensionLogic())
                .dimensionExpression(interview.getDimensionExpression()).dimensionProfessional(interview.getDimensionProfessional())
                .dimensionCommunication(interview.getDimensionCommunication()).improvementSummary(interview.getImprovementSummary())
                .strengths(interview.getStrengths()).weaknesses(interview.getWeaknesses())
                .questions(questions.stream().map(q -> ExperienceDetailResponse.QuestionItem.builder()
                        .id(q.getId()).questionIndex(q.getQuestionIndex()).questionText(q.getQuestionText()).score(q.getScore())
                        .answerDifference(q.getAnswerDifference()).improvementTip(q.getImprovementTip())
                        .referenceAnswer(q.getReferenceAnswer()).build()).toList())
                .viewCount(experience.getViewCount()).publishedAt(experience.getPublishedAt()).build();
    }

    @Override
    @Transactional
    public ExperiencePublicationResponse publish(Long interviewId, Long userId, String summaryMarkdown) {
        validateSummary(summaryMarkdown);
        Interview interview = requireOwnedCompleted(interviewId, userId);
        InterviewExperience value = findByInterview(interviewId);
        if (value == null) {
            value = new InterviewExperience();
            value.setInterviewId(interview.getId()); value.setUserId(userId);
            value.setModerationStatus(InterviewExperience.NORMAL); value.setViewCount(0);
        } else if (InterviewExperience.BLOCKED.equals(value.getModerationStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "该面经已被管理员下架，不能自行重新发布");
        }
        value.setSummaryMarkdown(trim(summaryMarkdown)); value.setPublishStatus(InterviewExperience.PUBLISHED);
        value.setPublishedAt(LocalDateTime.now());
        if (value.getId() == null) experienceMapper.insert(value); else experienceMapper.updateById(value);
        return ExperiencePublicationResponse.from(value);
    }

    @Override public ExperiencePublicationResponse getPublication(Long interviewId, Long userId) {
        requireOwned(interviewId, userId);
        InterviewExperience value = findByInterview(interviewId);
        if (value == null) throw notFound();
        return ExperiencePublicationResponse.from(value);
    }

    @Override @Transactional public ExperiencePublicationResponse updatePublication(Long interviewId, Long userId, String summaryMarkdown) {
        validateSummary(summaryMarkdown); requireOwned(interviewId, userId);
        InterviewExperience value = findByInterview(interviewId);
        if (value == null) throw notFound();
        value.setSummaryMarkdown(trim(summaryMarkdown)); experienceMapper.updateById(value);
        return ExperiencePublicationResponse.from(value);
    }

    @Override @Transactional public void withdraw(Long interviewId, Long userId) {
        requireOwned(interviewId, userId); withdrawForInterview(interviewId);
    }

    @Override public void withdrawForInterview(Long interviewId) {
        experienceMapper.update(null, new LambdaUpdateWrapper<InterviewExperience>()
                .eq(InterviewExperience::getInterviewId, interviewId)
                .set(InterviewExperience::getPublishStatus, InterviewExperience.WITHDRAWN));
    }

    private List<ExperienceListResponse> toListResponses(List<InterviewExperience> values) {
        if (values.isEmpty()) return List.of();
        Map<Long, Interview> interviews = interviewMapper.selectBatchIds(values.stream().map(InterviewExperience::getInterviewId).toList())
                .stream().collect(Collectors.toMap(Interview::getId, Function.identity()));
        Map<Long, User> users = userMapper.selectBatchIds(values.stream().map(InterviewExperience::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Long> counts = questionMapper.selectList(new LambdaQueryWrapper<InterviewQuestion>()
                        .in(InterviewQuestion::getInterviewId, interviews.keySet()).eq(InterviewQuestion::getIsActive, 1))
                .stream().collect(Collectors.groupingBy(InterviewQuestion::getInterviewId, Collectors.counting()));
        return values.stream().map(value -> {
            Interview interview = interviews.get(value.getInterviewId()); User author = users.get(value.getUserId());
            return ExperienceListResponse.builder().id(value.getId()).authorNickname(author == null ? null : author.getNickname())
                    .authorAvatar(author == null ? null : author.getAvatar()).title(interview.getTitle())
                    .companyName(interview.getCompanyName()).positionTitle(interview.getPositionTitle())
                    .industry(interview.getIndustry()).interviewType(interview.getInterviewType())
                    .overallScore(interview.getOverallScore()).questionCount(counts.getOrDefault(interview.getId(), 0L).intValue())
                    .viewCount(value.getViewCount()).publishedAt(value.getPublishedAt()).build();
        }).toList();
    }

    private InterviewExperience requireVisible(Long id) { InterviewExperience v=experienceMapper.selectById(id); if(v==null||!v.isVisible())throw notFound(); return v; }
    private InterviewExperience findByInterview(Long id) { return experienceMapper.selectOne(new LambdaQueryWrapper<InterviewExperience>().eq(InterviewExperience::getInterviewId,id)); }
    private Interview requireOwned(Long id, Long userId) { Interview v=interviewMapper.selectById(id); if(v==null)throw new BusinessException(ErrorCode.INTERVIEW_NOT_FOUND.getCode(),ErrorCode.INTERVIEW_NOT_FOUND.getMessage()); if(!userId.equals(v.getUserId()))throw new BusinessException(ErrorCode.INTERVIEW_ACCESS_DENIED.getCode(),ErrorCode.INTERVIEW_ACCESS_DENIED.getMessage()); return v; }
    private Interview requireOwnedCompleted(Long id, Long userId) { Interview v=requireOwned(id,userId); if(!Integer.valueOf(InterviewStatus.COMPLETED.getCode()).equals(v.getStatus()))throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(),"只有已完成面试可以发布面经"); return v; }
    private BusinessException notFound(){return new BusinessException(ErrorCode.EXPERIENCE_NOT_FOUND.getCode(),ErrorCode.EXPERIENCE_NOT_FOUND.getMessage());}
    private boolean notBlank(String v){return v!=null&&!v.isBlank();} private String trim(String v){return v==null?null:v.trim();}
    private void validateSummary(String v){if(v!=null&&v.length()>10000)throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(),"经验总结不能超过10000个字符");}
}
