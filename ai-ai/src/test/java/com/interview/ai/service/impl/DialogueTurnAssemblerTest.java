package com.interview.ai.service.impl;

import com.interview.ai.dto.AsrSentence;
import com.interview.ai.dto.DialogueTurn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DialogueTurnAssemblerTest {
    @Test
    void mergesOnlyConsecutiveSameSpeakerAndPreservesSourceIndexes() {
        List<AsrSentence> raw = List.of(
                sentence("后半句", "0", 10_000, 12_000, 0),
                sentence("开头", "0", 0, 1_000, 0),
                sentence("回答一", "1", 13_000, 14_000, 0),
                sentence("回答二", "1", 19_000, 21_000, 0),
                sentence("追问", "0", 22_000, 23_000, 0),
                sentence("再次回答", "1", 24_000, 25_000, 0));

        List<DialogueTurn> turns = DialogueTurnAssembler.assemble(raw);

        assertEquals(4, turns.size());
        assertEquals("开头后半句", turns.get(0).text());
        assertEquals(List.of(1, 0), turns.get(0).sourceSentenceIndexes());
        assertEquals(0, turns.get(0).startTimeMs());
        assertEquals(12_000, turns.get(0).endTimeMs());
        assertEquals("回答一回答二", turns.get(1).text());
        assertEquals("再次回答", turns.get(3).text());
        assertEquals(turns, DialogueTurnAssembler.assemble(raw));
    }

    @Test
    void unknownSpeakersAndDifferentTranscriptsNeverMerge() {
        List<DialogueTurn> turns = DialogueTurnAssembler.assemble(List.of(
                sentence("未知一", null, 0, 100, 0),
                sentence("未知二", null, 100, 200, 0),
                sentence("音轨一", "0", 200, 300, 0),
                sentence("音轨二", "0", 200, 300, 1)));
        assertEquals(4, turns.size());
        assertEquals(1, turns.get(3).transcriptIndex());
    }

    private AsrSentence sentence(String text, String speaker, long start, long end, int transcript) {
        return new AsrSentence(text, speaker, "UNKNOWN", start, end, List.of(), transcript);
    }
}
