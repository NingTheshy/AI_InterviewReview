package com.interview.ai.client;

import com.interview.ai.dto.AsrTranscriptionContext;
import com.interview.ai.dto.TranscriptionResult;
import com.interview.ai.model.dashscope.DashScopeFileTranscriptionModel;
import com.interview.ai.model.dashscope.DashScopeTranscriptionOptions;
import com.interview.ai.service.WholeFileAsrClient;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Duration;

/** 将项目 ASR 契约适配到 DashScope 的 Spring AI {@code TranscriptionModel}。 */
public class SpringAiDashScopeAsrClient implements WholeFileAsrClient {

    private final DashScopeFileTranscriptionModel transcriptionModel;
    private final String modelName;

    public SpringAiDashScopeAsrClient(DashScopeFileTranscriptionModel transcriptionModel, String modelName) {
        this.transcriptionModel = transcriptionModel;
        this.modelName = modelName;
    }

    @Override
    public String transcribe(MultipartFile audioFile, String language) {
        throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                "DashScope 录音文件识别需要完整音频路径，不能使用分片直传");
    }

    @Override
    public String transcribe(Path audioFile, String language) {
        return transcribeDetailed(audioFile, language).text();
    }

    @Override
    public TranscriptionResult transcribeDetailed(Path audioFile, String language) {
        return transcribeDetailed(audioFile, language, AsrTranscriptionContext.chinese());
    }

    @Override
    public TranscriptionResult transcribeDetailed(Path audioFile, String language, AsrTranscriptionContext context) {
        AudioTranscriptionResponse response = transcriptionModel.call(new AudioTranscriptionPrompt(
                new FileSystemResource(audioFile), new DashScopeTranscriptionOptions(modelName, context)));
        TranscriptionResult detailed = response.getMetadata().get(
                DashScopeFileTranscriptionModel.TRANSCRIPTION_RESULT_METADATA_KEY);
        return detailed != null ? detailed : TranscriptionResult.textOnly(response.getResult().getOutput());
    }

    @Override
    public Duration maxRecommendedDuration() {
        return transcriptionModel.maxRecommendedDuration();
    }
}
