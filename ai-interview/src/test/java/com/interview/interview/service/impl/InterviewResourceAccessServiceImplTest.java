package com.interview.interview.service.impl;

import com.interview.common.exception.BusinessException;
import com.interview.interview.entity.Interview;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.mapper.InterviewQuestionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewResourceAccessServiceImplTest {
    @Mock InterviewMapper interviewMapper;
    @Mock InterviewQuestionMapper questionMapper;
    @InjectMocks InterviewResourceAccessServiceImpl service;

    @Test
    void publishedExperienceDoesNotExposeOriginalInterviewQuestions() {
        Interview interview = new Interview();
        interview.setId(10L);
        interview.setUserId(1L);
        when(interviewMapper.selectById(10L)).thenReturn(interview);

        assertThrows(BusinessException.class, () -> service.requireVisibleQuestion(10L, 5L, 2L));
        assertDoesNotThrow(() -> service.requireVisibleInterview(10L, 1L));
    }
}
