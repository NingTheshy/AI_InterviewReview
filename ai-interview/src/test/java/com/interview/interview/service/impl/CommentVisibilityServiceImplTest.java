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
    @Mock CommentDeletionService deletionService;
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

    @Test
    void shouldReplyOnlyToActiveCommentInSameExperience() {
        InterviewExperience experience = new InterviewExperience();
        experience.setId(5L); experience.setPublishStatus(InterviewExperience.PUBLISHED);
        experience.setModerationStatus(InterviewExperience.NORMAL);
        when(experienceMapper.selectById(5L)).thenReturn(experience);
        InterviewComment parent = new InterviewComment();
        parent.setId(9L); parent.setExperienceId(5L); parent.setUserId(3L); parent.setDeleted(0);
        when(commentMapper.selectById(9L)).thenReturn(parent);

        service.addComment(5L, 2L, "回复", 9L);
        verify(commentMapper).insert(argThat(value -> value.getParentId().equals(9L)
                && value.getReplyToUserId().equals(3L)));

        parent.setDeleted(1);
        assertThrows(BusinessException.class, () -> service.addComment(5L, 2L, "不可回复", 9L));
        verify(commentMapper, times(1)).insert(any());
    }

    @Test
    void shouldRejectReplyToAnotherExperience() {
        InterviewExperience experience = new InterviewExperience();
        experience.setId(5L); experience.setPublishStatus(InterviewExperience.PUBLISHED);
        experience.setModerationStatus(InterviewExperience.NORMAL);
        when(experienceMapper.selectById(5L)).thenReturn(experience);
        InterviewComment parent = new InterviewComment();
        parent.setExperienceId(8L);
        when(commentMapper.selectById(9L)).thenReturn(parent);

        assertThrows(BusinessException.class, () -> service.addComment(5L, 2L, "越界回复", 9L));
        verify(commentMapper, never()).insert(any());
    }

}
