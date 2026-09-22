package com.interview.ai.dto;

/**
 * ASR 返回的句子级转写结果。
 *
 * @param text        句子文本
 * @param speakerId   ASR 提供商返回的原始说话人标识
 * @param speakerRole 业务角色：INTERVIEWER、CANDIDATE 或 UNKNOWN
 * @param startTimeMs 句子开始时间（毫秒）
 * @param endTimeMs   句子结束时间（毫秒）
 */
public record AsrSentence(
        String text,
        String speakerId,
        String speakerRole,
        long startTimeMs,
        long endTimeMs
) {

    public static final String UNKNOWN_ROLE = "UNKNOWN";

    public AsrSentence withSpeakerRole(String role) {
        return new AsrSentence(text, speakerId, role, startTimeMs, endTimeMs);
    }
}
