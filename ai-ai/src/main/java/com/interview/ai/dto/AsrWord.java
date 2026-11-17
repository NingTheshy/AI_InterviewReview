package com.interview.ai.dto;

/**
 * ASR 返回的词级转写结果。
 *
 * @param text        词文本
 * @param punctuation 词后的标点；上游未返回时为空
 * @param startTimeMs 词开始时间（毫秒）
 * @param endTimeMs   词结束时间（毫秒）
 */
public record AsrWord(String text, String punctuation, long startTimeMs, long endTimeMs) {
}
