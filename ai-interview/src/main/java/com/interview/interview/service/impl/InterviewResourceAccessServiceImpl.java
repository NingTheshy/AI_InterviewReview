package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.interview.entity.Interview;
import com.interview.interview.entity.InterviewQuestion;
import com.interview.interview.entity.InterviewExperience;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.mapper.InterviewQuestionMapper;
import com.interview.interview.mapper.InterviewExperienceMapper;
import com.interview.interview.service.InterviewResourceAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 统一实现面试、问题的所有权与可见性校验。 */
@Service
@RequiredArgsConstructor
public class InterviewResourceAccessServiceImpl implements InterviewResourceAccessService {

    private final InterviewMapper interviewMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewExperienceMapper experienceMapper;

    @Override
    public void requireOwnedInterview(Long interviewId, Long userId) {
        Interview interview = requireInterview(interviewId);
        if (userId == null || !userId.equals(interview.getUserId())) {
            throw new BusinessException(ErrorCode.INTERVIEW_ACCESS_DENIED.getCode(),
                    ErrorCode.INTERVIEW_ACCESS_DENIED.getMessage());
        }
    }

    @Override
    public void requireOwnedQuestion(Long interviewId, Long questionId, Long userId) {
        requireOwnedInterview(interviewId, userId);
        requireQuestionInInterview(interviewId, questionId);
    }

    @Override
    public void requireVisibleInterview(Long interviewId, Long userId) {
        Interview interview = requireInterview(interviewId);
        if (userId != null && userId.equals(interview.getUserId())) {
            return;
        }

        LambdaQueryWrapper<InterviewExperience> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewExperience::getInterviewId, interviewId)
                .eq(InterviewExperience::getPublishStatus, InterviewExperience.PUBLISHED)
                .eq(InterviewExperience::getModerationStatus, InterviewExperience.NORMAL);
        if (experienceMapper.selectCount(wrapper) == 0) {
            throw new BusinessException(ErrorCode.INTERVIEW_ACCESS_DENIED.getCode(),
                    ErrorCode.INTERVIEW_ACCESS_DENIED.getMessage());
        }
    }

    @Override
    public void requireVisibleQuestion(Long interviewId, Long questionId, Long userId) {
        requireVisibleInterview(interviewId, userId);
        requireQuestionInInterview(interviewId, questionId);
    }

    private Interview requireInterview(Long interviewId) {
        Interview interview = interviewMapper.selectById(interviewId);
        if (interview == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_FOUND.getCode(),
                    ErrorCode.INTERVIEW_NOT_FOUND.getMessage());
        }
        return interview;
    }

    private void requireQuestionInInterview(Long interviewId, Long questionId) {
        if (questionId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "面试问题不存在");
        }
        LambdaQueryWrapper<InterviewQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewQuestion::getId, questionId);
        wrapper.eq(InterviewQuestion::getInterviewId, interviewId);
        if (questionMapper.selectCount(wrapper) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "面试问题不存在");
        }
    }
}
