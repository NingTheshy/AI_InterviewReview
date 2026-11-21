package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.auth.mapper.UserMapper;
import com.interview.common.constant.InterviewStatus;
import com.interview.common.exception.BusinessException;
import com.interview.interview.entity.Interview;
import com.interview.interview.entity.InterviewExperience;
import com.interview.interview.entity.InterviewQuestion;
import com.interview.interview.entity.ExperienceQuestionSnapshot;
import com.interview.interview.dto.ExperiencePublicationRequest;
import com.interview.interview.dto.ExperienceDetailResponse;
import com.interview.interview.mapper.ExperienceQuestionSnapshotMapper;
import com.interview.interview.mapper.ExperienceInteractionMapper;
import com.interview.interview.mapper.InterviewExperienceMapper;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.mapper.InterviewQuestionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExperiencePublicationServiceImplTest {
    @Mock InterviewExperienceMapper experienceMapper;
    @Mock InterviewMapper interviewMapper;
    @Mock InterviewQuestionMapper questionMapper;
    @Mock ExperienceQuestionSnapshotMapper snapshotMapper;
    @Mock ExperienceInteractionMapper interactionMapper;
    @Mock UserMapper userMapper;
    @InjectMocks ExperiencePublicationServiceImpl service;

    @Test
    void shouldAcceptPlazaSortOptions() {
        when(experienceMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 12));

        for (String sortBy : List.of("publishedAt", "viewCount", "likeCount")) {
            assertDoesNotThrow(() -> service.listPublic(1, 12, null, null, sortBy));
        }
        verify(experienceMapper, times(3)).selectPage(any(), any());
        assertThrows(BusinessException.class,
                () -> service.listPublic(1, 12, null, null, "overallScore"));
    }

    @Test
    void shouldCreateUniquePublicationForCompletedInterview() {
        Interview interview = new Interview();
        interview.setId(10L); interview.setUserId(1L); interview.setPositionTitle("Java工程师");
        interview.setStatus(InterviewStatus.COMPLETED.getCode());
        when(interviewMapper.selectById(10L)).thenReturn(interview);
        when(experienceMapper.selectOne(any())).thenReturn(null);
        when(experienceMapper.selectCount(any())).thenReturn(0L);
        InterviewQuestion question = new InterviewQuestion();
        question.setId(5L); question.setQuestionIndex(1); question.setQuestionText("什么是事务？");
        question.setQuestionType("TECHNICAL"); question.setReferenceAnswer("事务具有 ACID 特性");
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        ExperienceQuestionSnapshot snapshot = new ExperienceQuestionSnapshot();
        snapshot.setSourceQuestionId(5L);
        when(snapshotMapper.selectList(any())).thenReturn(List.of(snapshot));
        when(experienceMapper.insert(any())).thenAnswer(invocation -> {
            InterviewExperience value = invocation.getArgument(0); value.setId(20L); return 1;
        });

        ExperiencePublicationRequest request = new ExperiencePublicationRequest();
        request.setSelectedQuestionIds(List.of(5L)); request.setShowReferenceAnswers(true);
        var result = service.publish(10L, 1L, request);

        assertEquals(20L, result.getId());
        assertEquals(InterviewExperience.PUBLISHED, result.getPublishStatus());
        verify(experienceMapper).insert(any(InterviewExperience.class));
    }

    @Test
    void shouldRejectUserRepublishAfterAdminBlock() {
        Interview interview = new Interview();
        interview.setId(10L); interview.setUserId(1L); interview.setStatus(InterviewStatus.COMPLETED.getCode());
        InterviewExperience experience = new InterviewExperience();
        experience.setInterviewId(10L); experience.setUserId(1L);
        experience.setModerationStatus(InterviewExperience.BLOCKED);
        when(interviewMapper.selectById(10L)).thenReturn(interview);
        when(experienceMapper.selectOne(any())).thenReturn(experience);
        when(experienceMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.publish(10L, 1L, new ExperiencePublicationRequest()));
        verify(experienceMapper, never()).updateById(any());
    }

    @Test
    void publicDetailOnlyIncludesSelectedSnapshotAndHidesAnswersWhenDisabled() {
        InterviewExperience experience = new InterviewExperience();
        experience.setId(20L); experience.setUserId(1L); experience.setTitle("Java面经");
        experience.setPublishStatus(InterviewExperience.PUBLISHED);
        experience.setModerationStatus(InterviewExperience.NORMAL);
        experience.setShowAnswers(0); experience.setViewCount(3);
        when(experienceMapper.selectById(20L)).thenReturn(experience);
        ExperienceQuestionSnapshot snapshot = new ExperienceQuestionSnapshot();
        snapshot.setId(50L); snapshot.setQuestionIndex(1);
        snapshot.setQuestionText("什么是事务？"); snapshot.setReferenceAnswer("ACID");
        when(snapshotMapper.selectList(any())).thenReturn(List.of(snapshot));

        ExperienceDetailResponse response = service.getDetail(20L, null);

        assertEquals("什么是事务？", response.getQuestions().get(0).getQuestionText());
        assertNull(response.getQuestions().get(0).getReferenceAnswer());
        assertEquals(4, response.getViewCount());
        verify(questionMapper, never()).selectList(any());
        verify(interviewMapper, never()).selectById(any());
    }

    @Test
    void rejectsVisiblePersonalIdentifierBeforeCreatingPublication() {
        Interview interview = new Interview();
        interview.setId(10L); interview.setUserId(1L); interview.setPositionTitle("Java工程师");
        interview.setStatus(InterviewStatus.COMPLETED.getCode());
        when(interviewMapper.selectById(10L)).thenReturn(interview);
        InterviewQuestion question = new InterviewQuestion();
        question.setId(5L); question.setQuestionIndex(1);
        question.setQuestionText("联系我 13800138000");
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        ExperiencePublicationRequest request = new ExperiencePublicationRequest();
        request.setSelectedQuestionIds(List.of(5L));

        assertThrows(BusinessException.class, () -> service.publish(10L, 1L, request));
        verify(experienceMapper, never()).insert(any());
    }

    @Test
    void deletingPublicationClearsInteractions() {
        InterviewExperience experience = new InterviewExperience();
        experience.setId(20L); experience.setUserId(1L);
        experience.setPublishStatus(InterviewExperience.PUBLISHED);
        experience.setModerationStatus(InterviewExperience.NORMAL);
        when(experienceMapper.selectById(20L)).thenReturn(experience);

        service.deletePublication(20L, 1L);

        assertEquals(InterviewExperience.DELETED, experience.getPublishStatus());
        assertEquals(InterviewExperience.NORMAL, experience.getModerationStatus());
        verify(interactionMapper).clearComments(20L);
        verify(interactionMapper).clearLikes(20L);
        verify(interactionMapper).clearFavorites(20L);
    }

    @Test
    void blockedPublicationCannotBeDeletedBeforeAdminUnblocksIt() {
        InterviewExperience experience = new InterviewExperience();
        experience.setId(20L); experience.setUserId(1L);
        experience.setPublishStatus(InterviewExperience.PUBLISHED);
        experience.setModerationStatus(InterviewExperience.BLOCKED);
        when(experienceMapper.selectById(20L)).thenReturn(experience);

        assertThrows(BusinessException.class, () -> service.deletePublication(20L, 1L));
        verify(experienceMapper, never()).updateById(any());
    }

    @Test
    void blockedPublicationCannotBeEditedByAuthor() {
        InterviewExperience experience = new InterviewExperience();
        experience.setId(20L); experience.setUserId(1L);
        experience.setPublishStatus(InterviewExperience.PUBLISHED);
        experience.setModerationStatus(InterviewExperience.BLOCKED);
        when(experienceMapper.selectById(20L)).thenReturn(experience);

        assertThrows(BusinessException.class,
                () -> service.updatePublication(20L, 1L, new ExperiencePublicationRequest()));
        verify(experienceMapper, never()).updateById(any());
    }

    @Test
    void republishUsesSpecifiedDeletedSnapshotAfterInterviewIsGone() {
        InterviewExperience old = new InterviewExperience();
        old.setId(20L); old.setInterviewId(10L); old.setUserId(1L);
        old.setPositionTitle("Java工程师"); old.setPublishStatus(InterviewExperience.DELETED);
        old.setModerationStatus(InterviewExperience.NORMAL);
        when(experienceMapper.selectById(20L)).thenReturn(old);
        ExperienceQuestionSnapshot snapshot = new ExperienceQuestionSnapshot();
        snapshot.setSourceQuestionId(5L); snapshot.setQuestionIndex(1);
        snapshot.setQuestionText("什么是事务？"); snapshot.setReferenceAnswer("ACID");
        when(snapshotMapper.selectList(any())).thenReturn(List.of(snapshot));
        when(experienceMapper.insert(any())).thenAnswer(invocation -> {
            InterviewExperience value = invocation.getArgument(0); value.setId(21L); return 1;
        });
        ExperiencePublicationRequest request = new ExperiencePublicationRequest();
        request.setSourceExperienceId(20L); request.setSelectedQuestionIds(List.of(5L));

        var result = service.publish(10L, 1L, request);

        assertEquals(21L, result.getId());
        verify(questionMapper, never()).selectList(any());
        verify(snapshotMapper).insert(any(ExperienceQuestionSnapshot.class));
    }

    @Test
    void repeatedLikeAndFavoriteDoNotIncrementCounters() {
        InterviewExperience experience = new InterviewExperience();
        experience.setId(20L); experience.setPublishStatus(InterviewExperience.PUBLISHED);
        experience.setModerationStatus(InterviewExperience.NORMAL);
        when(experienceMapper.selectById(20L)).thenReturn(experience);
        when(interactionMapper.addLike(20L, 2L)).thenReturn(1, 0);
        when(interactionMapper.addFavorite(20L, 2L)).thenReturn(1, 0);

        service.like(20L, 2L, true);
        service.like(20L, 2L, true);
        service.favorite(20L, 2L, true);
        service.favorite(20L, 2L, true);

        verify(interactionMapper, times(1)).changeLikeCount(20L, 1);
        verify(interactionMapper, times(1)).changeFavoriteCount(20L, 1);
    }

    @Test
    void blockedPublicationCannotReceiveInteractions() {
        InterviewExperience experience = new InterviewExperience();
        experience.setId(20L); experience.setPublishStatus(InterviewExperience.PUBLISHED);
        experience.setModerationStatus(InterviewExperience.BLOCKED);
        when(experienceMapper.selectById(20L)).thenReturn(experience);

        assertThrows(BusinessException.class, () -> service.like(20L, 2L, true));
        verifyNoInteractions(interactionMapper);
    }
}
