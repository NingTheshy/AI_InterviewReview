package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.interview.entity.Interview;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.mapper.InterviewQuestionMapper;
import com.interview.interview.mapper.InterviewShareMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 面试资源所有权与公开可见性规则测试。 */
@ExtendWith(MockitoExtension.class)
class InterviewResourceAccessServiceImplTest {

    private static final Long INTERVIEW_ID = 100L;
    private static final Long QUESTION_ID = 10L;
    private static final Long OWNER_ID = 1L;

    @Mock private InterviewMapper interviewMapper;
    @Mock private InterviewQuestionMapper questionMapper;
    @Mock private InterviewShareMapper shareMapper;

    private InterviewResourceAccessServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InterviewResourceAccessServiceImpl(interviewMapper, questionMapper, shareMapper);
    }

    @Test
    @DisplayName("本人可以访问自己的面试")
    void ownerCanAccessInterview() {
        when(interviewMapper.selectById(INTERVIEW_ID)).thenReturn(interview(OWNER_ID));

        assertDoesNotThrow(() -> service.requireOwnedInterview(INTERVIEW_ID, OWNER_ID));
    }

    @Test
    @DisplayName("非所有者不能访问私有面试")
    void nonOwnerCannotAccessPrivateInterview() {
        when(interviewMapper.selectById(INTERVIEW_ID)).thenReturn(interview(OWNER_ID));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requireOwnedInterview(INTERVIEW_ID, 2L));

        assertEquals(ErrorCode.INTERVIEW_ACCESS_DENIED.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("不存在的面试返回统一业务错误")
    void missingInterviewIsRejected() {
        when(interviewMapper.selectById(INTERVIEW_ID)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requireOwnedInterview(INTERVIEW_ID, OWNER_ID));

        assertEquals(ErrorCode.INTERVIEW_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("公开且有效的面试允许其他用户收藏")
    void publicInterviewIsVisible() {
        when(interviewMapper.selectById(INTERVIEW_ID)).thenReturn(interview(OWNER_ID));
        when(shareMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertDoesNotThrow(() -> service.requireVisibleInterview(INTERVIEW_ID, 2L));
    }

    @Test
    @DisplayName("未公开面试不能被其他用户收藏")
    void privateInterviewIsNotVisible() {
        when(interviewMapper.selectById(INTERVIEW_ID)).thenReturn(interview(OWNER_ID));
        when(shareMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requireVisibleInterview(INTERVIEW_ID, 2L));

        assertEquals(ErrorCode.INTERVIEW_ACCESS_DENIED.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("问题必须真实属于请求中的面试")
    void questionMustBelongToInterview() {
        when(interviewMapper.selectById(INTERVIEW_ID)).thenReturn(interview(OWNER_ID));
        when(questionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requireOwnedQuestion(INTERVIEW_ID, QUESTION_ID, OWNER_ID));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verify(shareMapper, never()).selectCount(any());
    }

    private Interview interview(Long ownerId) {
        Interview interview = new Interview();
        interview.setId(INTERVIEW_ID);
        interview.setUserId(ownerId);
        return interview;
    }
}
