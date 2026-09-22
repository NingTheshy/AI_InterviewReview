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

@ExtendWith(MockitoExtension.class)
class ScoreResultParserTest {

    @Mock private InterviewMapper interviewMapper;
    @Mock private InterviewQuestionMapper questionMapper;

    @Test
    void storesRubricAsJsonAndComputesOverallScoreOnServer() {
        Interview interview = new Interview();
        interview.setId(7L);
        new ScoreResultParser(interviewMapper, questionMapper).parseAndSave(interview, """
                {"overallScore":0,"dimensionContent":80,"dimensionLogic":70,
                 "dimensionExpression":60,"dimensionProfessional":90,"dimensionCommunication":50,
                 "questions":[{"questionIndex":1,"questionText":"题目","answerText":"回答",
                   "score":75,"referenceAnswer":"标准答案",
                   "rubricDetail":{"version":"rubric-v1","items":[{"id":"a","weight":100}]}}]}
                """);

        assertEquals(73, interview.getOverallScore());
        ArgumentCaptor<InterviewQuestion> saved = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(questionMapper).insert(saved.capture());
        assertEquals("标准答案", saved.getValue().getReferenceAnswer());
        assertTrue(saved.getValue().getRubricDetail().contains("rubric-v1"));
    }

    @Test
    void legacyResultKeepsRubricNull() {
        Interview interview = new Interview();
        interview.setId(8L);
        new ScoreResultParser(interviewMapper, questionMapper).parseAndSave(interview, """
                {"dimensionContent":70,"dimensionLogic":70,"dimensionExpression":70,
                 "dimensionProfessional":70,"dimensionCommunication":70,
                 "questions":[{"questionIndex":1,"questionText":"题目","score":70}]}
                """);

        ArgumentCaptor<InterviewQuestion> saved = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(questionMapper).insert(saved.capture());
        assertNull(saved.getValue().getRubricDetail());
    }

    @Test
    void mapsSpeakerIdsToBusinessRolesAndStoresQuestionTimestamps() {
        Interview interview = new Interview();
        interview.setId(9L);
        interview.setTranscriptSegments("""
                [{"text":"请介绍项目","speakerId":"0","speakerRole":"UNKNOWN","startTimeMs":100,"endTimeMs":900},
                 {"text":"我负责核心模块","speakerId":"1","speakerRole":"UNKNOWN","startTimeMs":1000,"endTimeMs":4100}]
                """);

        new ScoreResultParser(interviewMapper, questionMapper).parseAndSave(interview, """
                {"dimensionContent":80,"dimensionLogic":80,"dimensionExpression":80,
                 "dimensionProfessional":80,"dimensionCommunication":80,
                 "questions":[{"questionIndex":1,"questionText":"请介绍项目","answerText":"我负责核心模块",
                   "questionSpeakerId":"0","answerSpeakerId":"1",
                   "questionSpeaker":"INTERVIEWER","answerSpeaker":"CANDIDATE",
                   "startTime":0,"endTime":5,"score":80}]}
                """);

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
