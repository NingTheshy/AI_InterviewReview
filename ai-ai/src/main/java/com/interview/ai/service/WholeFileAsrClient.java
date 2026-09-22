package com.interview.ai.service;

import com.interview.ai.dto.TranscriptionResult;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 需要由供应商拉取完整音频文件的 ASR 客户端。
 *
 * <p>该能力与按字节分片直传的 {@link AsrClient} 并存，避免把供应商限制泄漏到业务服务。</p>
 */
public interface WholeFileAsrClient extends AsrClient {

    String transcribe(Path audioFile, String language);

    /**
     * 返回包含句子时间戳和说话人标识的结构化结果。
     * 不支持结构化结果的旧客户端可继续返回纯文本。
     */
    default TranscriptionResult transcribeDetailed(Path audioFile, String language) {
        return TranscriptionResult.textOnly(transcribe(audioFile, language));
    }

    Duration maxRecommendedDuration();
}
