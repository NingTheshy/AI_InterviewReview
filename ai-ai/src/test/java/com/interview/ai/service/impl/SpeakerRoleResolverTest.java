package com.interview.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.dto.AsrSentence;
import com.interview.ai.dto.TranscriptionResult;
import com.interview.ai.factory.AiClientFactory;
import com.interview.ai.service.LlmClient;
import com.interview.ai.util.StructuredOutputInvoker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpeakerRoleResolverTest {
    private final AiClientFactory factory = mock(AiClientFactory.class);
    private final StructuredOutputInvoker invoker = mock(StructuredOutputInvoker.class);
    private final SpeakerRoleResolver resolver = new SpeakerRoleResolver(factory, invoker);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsTwoSpeakersByConversationEvidenceNotFirstSpeaker() throws Exception {
        TranscriptionResult input = new TranscriptionResult("", List.of(
                new AsrSentence("我先介绍一下项目", "candidate", "UNKNOWN", 0, 1000),
                new AsrSentence("你负责哪一部分？", "interviewer", "UNKNOWN", 1000, 2000)), 2);
        when(factory.getLlmClientFromSnapshot("snapshot")).thenReturn(mock(LlmClient.class));
        when(invoker.invoke(any(), anyString(), anyString(), isNull())).thenReturn(mapper.readTree(
                "{\"interviewerSpeakerId\":\"interviewer\",\"candidateSpeakerId\":\"candidate\","
                + "\"interviewerEvidence\":\"你负责哪一部分\",\"candidateEvidence\":\"我先介绍一下项目\"}"));

        TranscriptionResult result = resolver.resolve(input, "snapshot");

        assertEquals("CANDIDATE", result.sentences().get(0).speakerRole());
        assertEquals("INTERVIEWER", result.sentences().get(1).speakerRole());
        assertEquals("[候选人] 我先介绍一下项目\n[面试官] 你负责哪一部分？", result.text());
    }

    @Test
    void keepsUnknownWhenEvidenceCannotResolveBothRoles() throws Exception {
        TranscriptionResult input = new TranscriptionResult("原文", List.of(
                new AsrSentence("你好", "0", "UNKNOWN", 0, 1000),
                new AsrSentence("你好", "1", "UNKNOWN", 1000, 2000)), 2);
        when(factory.getLlmClientFromSnapshot("snapshot")).thenReturn(mock(LlmClient.class));
        when(invoker.invoke(any(), anyString(), anyString(), isNull())).thenReturn(mapper.readTree(
                "{\"interviewerSpeakerId\":\"\",\"candidateSpeakerId\":\"\"}"));

        assertEquals(input, resolver.resolve(input, "snapshot"));
    }
}
