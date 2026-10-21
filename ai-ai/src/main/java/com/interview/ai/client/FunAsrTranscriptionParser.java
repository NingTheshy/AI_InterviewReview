package com.interview.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.dto.AsrSentence;
import com.interview.ai.dto.TranscriptionResult;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 DashScope FunASR 的转写文件解析为句子级结构化结果。
 */
public final class FunAsrTranscriptionParser {

    private final ObjectMapper objectMapper;

    public FunAsrTranscriptionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析转写文件，并保留评分和回放定位所需的原始句子元数据。
     *
     * <p>时间戳或开启分离后缺少 {@code speaker_id} 时直接失败，不能降级为纯文本；否则后续
     * 问答定位会把不完整数据误当作可信评分依据。</p>
     *
     * @param resultJson DashScope 返回的转写结果 JSON
     * @param diarizationEnabled 是否要求每句话都包含说话人编号
     * @return 文本摘要及按原始顺序排列的句子列表
     * @throws Exception JSON 无法解析时抛出；字段不完整时抛出业务异常
     */
    public TranscriptionResult parse(String resultJson, boolean diarizationEnabled) throws Exception {
        JsonNode transcripts = objectMapper.readTree(resultJson).path("transcripts");
        if (!transcripts.isArray()) {
            throw invalidResult("Fun-ASR 未返回 transcripts 数组");
        }

        List<AsrSentence> sentences = new ArrayList<>();
        StringBuilder readableText = new StringBuilder();
        for (JsonNode transcript : transcripts) {
            JsonNode sentenceNodes = transcript.path("sentences");
            if (!sentenceNodes.isArray()) {
                continue;
            }
            for (JsonNode sentence : sentenceNodes) {
                String text = sentence.path("text").asText().trim();
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                JsonNode beginTime = sentence.get("begin_time");
                JsonNode endTime = sentence.get("end_time");
                if (beginTime == null || !beginTime.canConvertToLong()
                        || endTime == null || !endTime.canConvertToLong()) {
                    throw invalidResult("Fun-ASR 句子缺少有效的 begin_time/end_time");
                }
                long startTimeMs = beginTime.asLong();
                long endTimeMs = endTime.asLong();
                if (startTimeMs < 0 || endTimeMs < startTimeMs) {
                    throw invalidResult("Fun-ASR 句子时间戳无效");
                }

                String speakerId = null;
                JsonNode speakerNode = sentence.get("speaker_id");
                if (speakerNode != null && !speakerNode.isNull() && StringUtils.hasText(speakerNode.asText())) {
                    speakerId = speakerNode.asText();
                }
                if (diarizationEnabled && !StringUtils.hasText(speakerId)) {
                    throw invalidResult("Fun-ASR 已开启说话人分离，但句子缺少 speaker_id");
                }

                sentences.add(new AsrSentence(text, speakerId, AsrSentence.UNKNOWN_ROLE,
                        startTimeMs, endTimeMs));
                if (readableText.length() > 0) {
                    readableText.append('\n');
                }
                if (StringUtils.hasText(speakerId)) {
                    readableText.append("[说话人 ").append(speakerId).append("] ");
                }
                readableText.append(text);
            }
        }
        if (sentences.isEmpty()) {
            throw invalidResult("Fun-ASR 未返回可用的句子级转写结果");
        }
        return new TranscriptionResult(readableText.toString(), sentences, null);
    }

    /** 将上游返回格式错误统一转换为可定位的模型调用失败。 */
    private BusinessException invalidResult(String message) {
        return new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(), message);
    }
}
