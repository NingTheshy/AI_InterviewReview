package com.interview.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.dto.AsrSentence;
import com.interview.ai.dto.AsrWord;
import com.interview.ai.dto.TranscriptionResult;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 将 DashScope 录音文件识别结果解析为句子及词级结构化数据。 */
public final class DashScopeTranscriptionParser {

    private final ObjectMapper objectMapper;

    public DashScopeTranscriptionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TranscriptionResult parse(String resultJson, boolean diarizationEnabled) throws Exception {
        JsonNode transcripts = objectMapper.readTree(resultJson).path("transcripts");
        if (!transcripts.isArray()) {
            throw invalidResult("DashScope ASR 未返回 transcripts 数组");
        }
        List<AsrSentence> sentences = new ArrayList<>();
        StringBuilder readableText = new StringBuilder();
        for (JsonNode transcript : transcripts) {
            JsonNode sentenceNodes = transcript.path("sentences");
            if (!sentenceNodes.isArray()) continue;
            for (JsonNode sentence : sentenceNodes) {
                String text = sentence.path("text").asText().trim();
                if (!StringUtils.hasText(text)) continue;
                long start = requiredTime(sentence, "begin_time");
                long end = requiredTime(sentence, "end_time");
                if (start < 0 || end < start) throw invalidResult("DashScope ASR 句子时间戳无效");
                String speakerId = textValue(sentence.get("speaker_id"));
                if (diarizationEnabled && !StringUtils.hasText(speakerId)) {
                    throw invalidResult("DashScope ASR 已开启说话人分离，但句子缺少 speaker_id");
                }
                List<AsrWord> words = parseWords(sentence.path("words"));
                sentences.add(new AsrSentence(text, speakerId, AsrSentence.UNKNOWN_ROLE, start, end, words));
                if (!readableText.isEmpty()) readableText.append('\n');
                if (StringUtils.hasText(speakerId)) readableText.append("[说话人 ").append(speakerId).append("] ");
                readableText.append(text);
            }
        }
        if (sentences.isEmpty()) throw invalidResult("DashScope ASR 未返回可用的句子级转写结果");
        return new TranscriptionResult(readableText.toString(), sentences, null);
    }

    private List<AsrWord> parseWords(JsonNode nodes) {
        if (!nodes.isArray()) return List.of();
        List<AsrWord> words = new ArrayList<>();
        for (JsonNode node : nodes) {
            String text = textValue(node.get("text"));
            if (!StringUtils.hasText(text)) text = textValue(node.get("word"));
            JsonNode begin = node.get("begin_time");
            JsonNode end = node.get("end_time");
            if (!StringUtils.hasText(text) || begin == null || end == null
                    || !begin.canConvertToLong() || !end.canConvertToLong()) continue;
            long start = begin.asLong();
            long finish = end.asLong();
            if (start >= 0 && finish >= start) {
                words.add(new AsrWord(text, textValue(node.get("punctuation")), start, finish));
            }
        }
        return words;
    }

    private long requiredTime(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw invalidResult("DashScope ASR 句子缺少有效的 " + field);
        }
        return value.asLong();
    }

    private String textValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private BusinessException invalidResult(String message) {
        return new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(), message);
    }
}
