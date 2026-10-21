package com.interview.interview.service.impl;

import com.interview.auth.mapper.UserMapper;
import com.interview.common.constant.InterviewStatus;
import com.interview.common.exception.BusinessException;
import com.interview.interview.entity.Interview;
import com.interview.interview.entity.InterviewExperience;
import com.interview.interview.mapper.InterviewExperienceMapper;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.mapper.InterviewQuestionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExperiencePublicationServiceImplTest {
    @Mock InterviewExperienceMapper experienceMapper;
    @Mock InterviewMapper interviewMapper;
    @Mock InterviewQuestionMapper questionMapper;
    @Mock UserMapper userMapper;
    @InjectMocks ExperiencePublicationServiceImpl service;

    @Test
    void shouldCreateUniquePublicationForCompletedInterview() {
        Interview interview = new Interview();
        interview.setId(10L); interview.setUserId(1L); interview.setStatus(InterviewStatus.COMPLETED.getCode());
        when(interviewMapper.selectById(10L)).thenReturn(interview);
        when(experienceMapper.selectOne(any())).thenReturn(null);
        when(experienceMapper.insert(any())).thenAnswer(invocation -> {
            InterviewExperience value = invocation.getArgument(0); value.setId(20L); return 1;
        });

        var result = service.publish(10L, 1L, "复盘总结");

        assertEquals(20L, result.getId());
        assertEquals(InterviewExperience.PUBLISHED, result.getPublishStatus());
        verify(experienceMapper).insert(any(InterviewExperience.class));
    }

    @Test
    void shouldRejectUserRepublishAfterAdminBlock() {
        Interview interview = new Interview();
        interview.setId(10L); interview.setUserId(1L); interview.setStatus(InterviewStatus.COMPLETED.getCode());
        InterviewExperience experience = new InterviewExperience();
        experience.setModerationStatus(InterviewExperience.BLOCKED);
        when(interviewMapper.selectById(10L)).thenReturn(interview);
        when(experienceMapper.selectOne(any())).thenReturn(experience);

        assertThrows(BusinessException.class, () -> service.publish(10L, 1L, null));
        verify(experienceMapper, never()).updateById(any());
    }
}
