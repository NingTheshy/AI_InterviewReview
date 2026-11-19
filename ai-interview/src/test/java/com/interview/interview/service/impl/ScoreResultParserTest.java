package com.interview.interview.service.impl;

import com.interview.interview.entity.Interview;
import com.interview.interview.entity.InterviewQuestion;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.mapper.InterviewQuestionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Collections;

@ExtendWith(MockitoExtension.class)
class ScoreResultParserTest {

    @Mock private InterviewMapper interviewMapper;
    @Mock private InterviewQuestionMapper questionMapper;

    @Test
    void storesRubricAsJsonAndComputesOverallScoreOnServer() {
        Interview interview = new Interview();
        interview.setId(7L);
        interview.setProcessingToken("token-7");
        when(interviewMapper.selectForUpdate(7L)).thenReturn(interview);
        when(questionMapper.selectList(any())).thenReturn(Collections.emptyList());
        int scorableCount = new ScoreResultParser(interviewMapper, questionMapper).parseAndSave(interview, """
                {"overallScore":0,"dimensionContent":80,"dimensionLogic":70,
                 "dimensionExpression":60,"dimensionProfessional":90,"dimensionCommunication":50,
                 "questions":[{"questionIndex":1,"questionText":"题目","followUpTexts":["追问"],
                   "domain":"数据库","knowledgePoints":["索引"],"questionCategory":"原理题",
                   "knowledgeDepth":2,"problemComplexity":3,"difficultyLevel":"L2",
                   "difficultyReason":"需解释原理并分析场景","difficultyRuleVersion":"difficulty-v1",
                   "answerText":"回答","answerEvidence":["回答"],"criticalErrors":["关键错误"],
                   "score":75,"referenceAnswer":"标准答案",
                   "rubricDetail":{"version":"rubric-v1","items":[{"id":"a","weight":100}]}}]}
                """);

        assertEquals(1, scorableCount);
        assertEquals(73, interview.getOverallScore());
        ArgumentCaptor<InterviewQuestion> saved = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(questionMapper).insert(saved.capture());
        assertEquals("标准答案", saved.getValue().getReferenceAnswer());
        assertEquals("[\"追问\"]", saved.getValue().getFollowUpTexts());
        assertEquals("[\"回答\"]", saved.getValue().getAnswerEvidence());
        assertEquals("[\"关键错误\"]", saved.getValue().getCriticalErrors());
        assertEquals("数据库", saved.getValue().getDomain());
        assertEquals("[\"索引\"]", saved.getValue().getKnowledgePoints());
        assertEquals(2, saved.getValue().getKnowledgeDepth());
        assertEquals(3, saved.getValue().getProblemComplexity());
        assertEquals("L2", saved.getValue().getDifficultyLevel());
        assertEquals("difficulty-v1", saved.getValue().getDifficultyRuleVersion());
        assertTrue(saved.getValue().getRubricDetail().contains("rubric-v1"));
    }

    @Test
    void legacyResultKeepsRubricNull() {
        Interview interview = new Interview();
        interview.setId(8L);
        interview.setProcessingToken("token-8");
        when(interviewMapper.selectForUpdate(8L)).thenReturn(interview);
        when(questionMapper.selectList(any())).thenReturn(Collections.emptyList());
        int scorableCount = new ScoreResultParser(interviewMapper, questionMapper).parseAndSave(interview, """
                {"dimensionContent":70,"dimensionLogic":70,"dimensionExpression":70,
                 "dimensionProfessional":70,"dimensionCommunication":70,
                 "questions":[{"questionIndex":1,"questionText":"题目","score":70}]}
                """);

        assertEquals(1, scorableCount);
        ArgumentCaptor<InterviewQuestion> saved = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(questionMapper).insert(saved.capture());
        assertNull(saved.getValue().getRubricDetail());
    }

    @Test
    void persistsLowConfidenceReasonWithoutScoringIt() {
        Interview interview = new Interview();
        interview.setId(10L);
        interview.setProcessingToken("token-10");
        interview.setTranscriptSegments("""
                [{"text":"项目问题","speakerId":"0","speakerRole":"UNKNOWN","startTimeMs":100,"endTimeMs":900},
                 {"text":"不确定的回答","speakerId":"1","speakerRole":"UNKNOWN","startTimeMs":1000,"endTimeMs":2000}]
                """);
        when(interviewMapper.selectForUpdate(10L)).thenReturn(interview);
        when(questionMapper.selectList(any())).thenReturn(Collections.emptyList());

        int count = new ScoreResultParser(interviewMapper, questionMapper).parseAndSave(interview, """
                {"scorableQuestionCount":0,"questions":[{"questionIndex":1,"questionText":"项目问题",
                  "answerText":"不确定的回答","recognitionConfidence":"LOW","includedInScore":false,
                  "questionSpeakerId":"0","answerSpeakerId":"1",
                  "lowConfidenceReason":"说话人角色无法确认","answerEvidence":[],"criticalErrors":[]}]}
                """);

        assertEquals(0, count);
        assertNull(interview.getOverallScore());
        assertTrue(interview.getTranscriptSegments().contains("\"speakerRole\":\"UNKNOWN\""));
        ArgumentCaptor<InterviewQuestion> saved = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(questionMapper).insert(saved.capture());
        assertEquals("说话人角色无法确认", saved.getValue().getLowConfidenceReason());
    }

    @Test
    void mapsSpeakerIdsToBusinessRolesAndStoresQuestionTimestamps() {
        Interview interview = new Interview();
        interview.setId(9L);
        interview.setProcessingToken("token-9");
        when(interviewMapper.selectForUpdate(9L)).thenReturn(interview);
        when(questionMapper.selectList(any())).thenReturn(Collections.emptyList());
        interview.setTranscriptSegments("""
                [{"text":"请介绍项目","speakerId":"0","speakerRole":"UNKNOWN","startTimeMs":100,"endTimeMs":900},
                 {"text":"我负责核心模块","speakerId":"1","speakerRole":"UNKNOWN","startTimeMs":1000,"endTimeMs":4100}]
                """);

        int scorableCount = new ScoreResultParser(interviewMapper, questionMapper).parseAndSave(interview, """
                {"dimensionContent":80,"dimensionLogic":80,"dimensionExpression":80,
                 "dimensionProfessional":80,"dimensionCommunication":80,
                 "questions":[{"questionIndex":1,"questionText":"请介绍项目","answerText":"我负责核心模块",
                   "questionSpeakerId":"0","answerSpeakerId":"1",
                   "questionSpeaker":"INTERVIEWER","answerSpeaker":"CANDIDATE",
                   "startTime":0,"endTime":5,"score":80}]}
                """);

        assertEquals(1, scorableCount);
        assertTrue(interview.getTranscriptText().contains("[面试官] 请介绍项目"));
        assertTrue(interview.getTranscriptText().contains("[候选人] 我负责核心模块"));
        assertTrue(interview.getTranscriptSegments().contains("\"speakerRole\":\"INTERVIEWER\""));
        ArgumentCaptor<InterviewQuestion> saved = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(questionMapper).insert(saved.capture());
        assertEquals("INTERVIEWER", saved.getValue().getQuestionSpeaker());
        assertEquals("CANDIDATE", saved.getValue().getAnswerSpeaker());
        assertEquals(0, saved.getValue().getStartTime());
        assertEquals(5, saved.getValue().getEndTime());
    }
}
