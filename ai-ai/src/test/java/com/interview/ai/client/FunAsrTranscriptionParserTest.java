package com.interview.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.dto.TranscriptionResult;
import com.interview.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunAsrTranscriptionParserTest {

    private final FunAsrTranscriptionParser parser = new FunAsrTranscriptionParser(new ObjectMapper());

    @Test
    void keepsSentenceTimestampsAndOriginalSpeakerIds() throws Exception {
        TranscriptionResult result = parser.parse("""
                {"transcripts":[{"sentences":[
                  {"begin_time":120,"end_time":980,"speaker_id":0,"text":"请介绍一下自己"},
                  {"begin_time":1100,"end_time":4200,"speaker_id":1,"text":"我是一名后端工程师"}
                ]}]}
                """, true);

        assertEquals(2, result.sentences().size());
        assertEquals("0", result.sentences().get(0).speakerId());
        assertEquals(120, result.sentences().get(0).startTimeMs());
        assertEquals(4200, result.sentences().get(1).endTimeMs());
        assertTrue(result.toScoringTranscript().contains("startTimeMs=120,endTimeMs=980,speakerId=0"));
    }

    @Test
    void rejectsMissingTimestamp() {
        assertThrows(BusinessException.class, () -> parser.parse("""
                {"transcripts":[{"sentences":[{"speaker_id":0,"text":"缺少时间戳"}]}]}
                """, true));
    }

    @Test
    void rejectsMissingSpeakerWhenDiarizationEnabled() {
        assertThrows(BusinessException.class, () -> parser.parse("""
                {"transcripts":[{"sentences":[{"begin_time":0,"end_time":100,"text":"缺少说话人"}]}]}
                """, true));
    }
}
