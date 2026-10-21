package com.interview.ai.dto;

import java.util.List;

/**
 * 完整 ASR 转写结果，同时保留用于展示的文本和句子级结构化数据。
 */
public record TranscriptionResult(
        String text,
        List<AsrSentence> sentences,
        Integer audioDurationSeconds
) {

    public TranscriptionResult {
        sentences = sentences == null ? List.of() : List.copyOf(sentences);
    }

    public static TranscriptionResult textOnly(String text) {
        return new TranscriptionResult(text, List.of(), null);
    }

    public TranscriptionResult withAudioDurationSeconds(int durationSeconds) {
        return new TranscriptionResult(text, sentences, durationSeconds);
    }

    /**
     * 构造供问答识别模型使用的转写内容。时间戳和 speakerId 使用机器可读标记，
     * 使模型只能引用 ASR 已返回的边界，不能自行猜测时间。
     */
    public String toScoringTranscript() {
        if (sentences.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder();
        for (AsrSentence sentence : sentences) {
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append("[startTimeMs=").append(sentence.startTimeMs())
                    .append(",endTimeMs=").append(sentence.endTimeMs())
                    .append(",speakerId=").append(sentence.speakerId() == null ? "UNKNOWN" : sentence.speakerId())
                    .append(",speakerRole=").append(sentence.speakerRole() == null ? "UNKNOWN" : sentence.speakerRole())
                    .append("] ")
                    .append(sentence.text().replace('\n', ' ').replace('\r', ' '));
        }
        return result.toString();
    }
}
