package com.interview.interview.service.impl;

import com.interview.interview.entity.InterviewComment;
import com.interview.interview.mapper.InterviewCommentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentDeletionServiceTest {
    @Mock InterviewCommentMapper mapper;
    @InjectMocks CommentDeletionService service;

    @Test
    void deletingParentAlsoDeletesEveryReplyLevel() {
        InterviewComment root = new InterviewComment();
        root.setId(1L); root.setDeleted(0);
        root.setExperienceId(10L);
        when(mapper.selectById(1L)).thenReturn(root);
        when(mapper.selectChildIds(List.of(1L))).thenReturn(List.of(2L, 3L));
        when(mapper.selectChildIds(List.of(2L, 3L))).thenReturn(List.of(4L));
        when(mapper.selectChildIds(List.of(4L))).thenReturn(List.of());
        when(mapper.softDeleteIds(anyList())).thenReturn(4);

        service.deleteThread(root);

        verify(mapper).lockExperience(10L);
        verify(mapper).softDeleteIds(List.of(1L, 2L, 3L, 4L));
    }
}
