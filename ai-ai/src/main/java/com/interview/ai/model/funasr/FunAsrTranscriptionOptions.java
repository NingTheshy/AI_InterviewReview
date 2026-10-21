package com.interview.ai.model.funasr;

import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;

/**
 * FunASR 转写运行时选项。
 *
 * <p>模型名称遵循 Spring AI {@link AudioTranscriptionOptions} 契约，语言字段用于
 * DashScope 的 {@code language_hints} 参数。</p>
 */
public record FunAsrTranscriptionOptions(String model, String language)
        implements AudioTranscriptionOptions {

    @Override
    public String getModel() {
        return model;
    }

    public String getLanguage() {
        return language;
    }
}
