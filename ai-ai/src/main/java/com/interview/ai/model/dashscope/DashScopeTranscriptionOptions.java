package com.interview.ai.model.dashscope;

import com.interview.ai.dto.AsrTranscriptionContext;
import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;

/** DashScope 录音文件识别的 Spring AI 运行时选项。 */
public record DashScopeTranscriptionOptions(String model, AsrTranscriptionContext context)
        implements AudioTranscriptionOptions {

    @Override
    public String getModel() {
        return model;
    }
}
