package com.interview.ai.dto;

import java.util.List;

/** 连续同一说话人的完整发言；sourceSentenceIndexes 指向原始句子数组的零基位置。 */
public record DialogueTurn(int turnIndex, String speakerId, String role, long startTimeMs,
                           long endTimeMs, String text, List<Integer> sourceSentenceIndexes,
                           int transcriptIndex) {
}
