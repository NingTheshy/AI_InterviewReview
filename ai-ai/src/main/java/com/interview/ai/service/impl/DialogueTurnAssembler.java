package com.interview.ai.service.impl;

import com.interview.ai.dto.AsrSentence;
import com.interview.ai.dto.DialogueTurn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 从原始句子生成展示用发言，不修改原文，也不按静音时长或标点切分。 */
public final class DialogueTurnAssembler {
    private DialogueTurnAssembler() {
    }

    public static List<DialogueTurn> assemble(List<AsrSentence> sentences) {
        if (sentences == null || sentences.isEmpty()) return List.of();
        List<Integer> ordered = new ArrayList<>();
        for (int index = 0; index < sentences.size(); index++) {
            if (sentences.get(index) != null) ordered.add(index);
        }
        ordered.sort(Comparator.comparingInt((Integer index) -> sentences.get(index).transcriptIndex())
                .thenComparingLong(index -> sentences.get(index).startTimeMs())
                .thenComparingInt(index -> index));

        List<DialogueTurn> result = new ArrayList<>();
        String speakerId = null;
        String role = null;
        int transcriptIndex = -1;
        long start = 0;
        long end = 0;
        StringBuilder text = new StringBuilder();
        List<Integer> source = new ArrayList<>();
        for (int index : ordered) {
            AsrSentence sentence = sentences.get(index);
            String nextSpeaker = sentence.speakerId();
            String nextRole = sentence.speakerRole() == null ? AsrSentence.UNKNOWN_ROLE : sentence.speakerRole();
            boolean sameSpeaker = !source.isEmpty() && speakerId != null && !speakerId.isBlank()
                    && speakerId.equals(nextSpeaker) && role.equals(nextRole)
                    && transcriptIndex == sentence.transcriptIndex();
            if (!source.isEmpty() && !sameSpeaker) {
                result.add(new DialogueTurn(result.size() + 1, speakerId, role, start, end,
                        text.toString(), List.copyOf(source), transcriptIndex));
                source.clear();
                text.setLength(0);
            }
            if (source.isEmpty()) {
                speakerId = nextSpeaker;
                role = nextRole;
                transcriptIndex = sentence.transcriptIndex();
                start = sentence.startTimeMs();
                end = sentence.endTimeMs();
            } else {
                end = Math.max(end, sentence.endTimeMs());
            }
            text.append(sentence.text() == null ? "" : sentence.text());
            source.add(index);
        }
        if (!source.isEmpty()) {
            result.add(new DialogueTurn(result.size() + 1, speakerId, role, start, end,
                    text.toString(), List.copyOf(source), transcriptIndex));
        }
        return List.copyOf(result);
    }
}
