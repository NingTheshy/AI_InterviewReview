package com.interview.interview.service.impl;

import com.interview.ai.service.CompanyClassifier;
import com.interview.ai.service.ScoringService;
import com.interview.ai.service.TranscriptionService;
import com.interview.ai.dto.AsrSentence;
import com.interview.ai.dto.TranscriptionResult;
import com.interview.common.constant.CompanyTier;
import com.interview.common.constant.InterviewStatus;
import com.interview.interview.entity.Interview;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.mapper.InterviewQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * InterviewAsyncServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InterviewAsyncService 单元测试")
class InterviewAsyncServiceImplTest {

    @Mock
    private InterviewMapper interviewMapper;

    @Mock
    private InterviewQuestionMapper questionMapper;

    @Mock
    private TranscriptionService transcriptionService;

    @Mock
    private ScoringService scoringService;

    @Mock
    private ScoreResultParser scoreResultParser;

    @Mock
    private CompanyClassifier companyClassifier;

    @InjectMocks
    private InterviewAsyncServiceImpl interviewAsyncService;

    private Interview testInterview;
    private static final Long TEST_INTERVIEW_ID = 100L;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("async-test");
        ReflectionTestUtils.setField(interviewAsyncService, "uploadDir", tempDir.toString());

        testInterview = new Interview();
        testInterview.setId(TEST_INTERVIEW_ID);
        testInterview.setStatus(InterviewStatus.PROCESSING.getCode());
        testInterview.setAudioFilePath("test-audio.wav");
        testInterview.setProcessingToken("test-token");
    }

    @Test
    @DisplayName("异步处理面试成功")
    void processInterview_Success() {
        when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);
        when(interviewMapper.updateById(any(Interview.class))).thenReturn(1);
        when(interviewMapper.ownsTask(TEST_INTERVIEW_ID, "test-token")).thenReturn(1);
        when(interviewMapper.heartbeat(eq(TEST_INTERVIEW_ID), eq("test-token"), any())).thenReturn(1);
        when(transcriptionService.transcribeDetailedWithSnapshot(anyString(), isNull())).thenReturn(
                new TranscriptionResult("面试对话文本",
                        List.of(new AsrSentence("面试对话文本", "0", "UNKNOWN", 0, 1000)), 1));
        when(companyClassifier.classify(isNull(), isNull(), isNull())).thenReturn(CompanyTier.TIER_3);
        when(scoringService.analyzeWithRubricSnapshot(anyString(), isNull(), isNull(), isNull(), isNull(), anyInt(), isNull(), anyList()))
                .thenReturn("{\"overallScore\":85,\"questions\":[]}");
        when(scoreResultParser.parseAndSave(eq(testInterview), anyString())).thenReturn(1);

        interviewAsyncService.processInterview(TEST_INTERVIEW_ID, "test-token");

        verify(interviewMapper, atLeast(2)).updateById(any(Interview.class));
        verify(transcriptionService).transcribeDetailedWithSnapshot(anyString(), isNull());
        verify(companyClassifier).classify(isNull(), isNull(), isNull());
        verify(scoringService).analyzeWithRubricSnapshot(anyString(), isNull(), isNull(), isNull(), isNull(), anyInt(), isNull(), anyList());
        verify(scoreResultParser).parseAndSave(eq(testInterview), anyString());
        org.junit.jupiter.api.Assertions.assertEquals(InterviewStatus.COMPLETED.getCode(), testInterview.getStatus());
    }

    @Test
    @DisplayName("异步处理 - 面试记录不存在")
    void processInterview_NotFound() {
        when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(null);

        interviewAsyncService.processInterview(TEST_INTERVIEW_ID, "test-token");

        verify(interviewMapper, never()).updateById(any(Interview.class));
    }

    @Test
    @DisplayName("异步处理 - AI服务异常时标记失败")
    void processInterview_AiServiceError() {
        when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);
        when(interviewMapper.updateById(any(Interview.class))).thenReturn(1);
        when(interviewMapper.ownsTask(TEST_INTERVIEW_ID, "test-token")).thenReturn(1);
        when(transcriptionService.transcribeDetailedWithSnapshot(anyString(), isNull()))
                .thenThrow(new RuntimeException("AI服务不可用"));

        interviewAsyncService.processInterview(TEST_INTERVIEW_ID, "test-token");

        verify(interviewMapper, atLeastOnce()).updateById(argThat(interview ->
                interview.getStatus() == InterviewStatus.FAILED.getCode()));
    }
}
