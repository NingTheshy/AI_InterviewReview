package com.interview.interview.service.impl;

import com.interview.auth.mapper.UserMapper;
import com.interview.common.exception.BusinessException;
import com.interview.interview.entity.InterviewComment;
import com.interview.interview.entity.InterviewExperience;
import com.interview.interview.mapper.InterviewCommentMapper;
import com.interview.interview.mapper.InterviewExperienceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentVisibilityServiceImplTest {
    @Mock InterviewCommentMapper commentMapper;
    @Mock InterviewExperienceMapper experienceMapper;
    @Mock UserMapper userMapper;
    @InjectMocks CommentServiceImpl service;

    @Test
    void shouldAllowCommentOnlyOnVisibleExperience() {
        InterviewExperience experience = new InterviewExperience();
        experience.setId(5L); experience.setPublishStatus(InterviewExperience.PUBLISHED);
        experience.setModerationStatus(InterviewExperience.NORMAL);
        when(experienceMapper.selectById(5L)).thenReturn(experience);

        service.addComment(5L, 2L, "有帮助");

        verify(commentMapper).insert(argThat(value -> value.getExperienceId().equals(5L)));
    }

    @Test
    void shouldHideCommentsWhenExperienceIsWithdrawn() {
        InterviewExperience experience = new InterviewExperience();
        experience.setPublishStatus(InterviewExperience.WITHDRAWN);
        experience.setModerationStatus(InterviewExperience.NORMAL);
        when(experienceMapper.selectById(5L)).thenReturn(experience);

        assertThrows(BusinessException.class, () -> service.listByExperienceId(5L, 1, 20));
    }
}
