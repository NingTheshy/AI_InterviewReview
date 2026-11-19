package com.interview.ai.dto;

import java.util.List;

/**
 * ASR 返回的句子级转写结果。
 *
 * @param text        句子文本
 * @param speakerId   ASR 提供商返回的原始说话人标识
 * @param speakerRole 业务角色：INTERVIEWER、CANDIDATE 或 UNKNOWN
 * @param startTimeMs 句子开始时间（毫秒）
 * @param endTimeMs   句子结束时间（毫秒）
 * @param words       词级时间戳；上游未返回时为空列表
 */
public record AsrSentence(
        String text,
        String speakerId,
        String speakerRole,
        long startTimeMs,
        long endTimeMs,
        List<AsrWord> words,
        Integer transcriptIndex
) {

    public static final String UNKNOWN_ROLE = "UNKNOWN";

    public AsrSentence {
        words = words == null ? List.of() : List.copyOf(words);
        transcriptIndex = transcriptIndex == null ? 0 : transcriptIndex;
    }

    public AsrSentence(String text, String speakerId, String speakerRole, long startTimeMs, long endTimeMs,
                       List<AsrWord> words) {
        this(text, speakerId, speakerRole, startTimeMs, endTimeMs, words, 0);
    }

    /** 保留旧调用点的兼容构造器；新增转写结果应优先传入词级时间戳。 */
    public AsrSentence(String text, String speakerId, String speakerRole, long startTimeMs, long endTimeMs) {
        this(text, speakerId, speakerRole, startTimeMs, endTimeMs, List.of());
    }

    public AsrSentence withSpeakerRole(String role) {
        return new AsrSentence(text, speakerId, role, startTimeMs, endTimeMs, words, transcriptIndex);
    }
}
