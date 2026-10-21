package com.interview.ai.client;

import com.interview.ai.dto.TranscriptionResult;
import com.interview.ai.model.funasr.FunAsrTranscriptionModel;
import com.interview.ai.model.funasr.FunAsrTranscriptionOptions;
import com.interview.ai.service.WholeFileAsrClient;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 将项目既有 ASR 业务契约适配到 Spring AI FunASR 转写模型。
 */
public class SpringAiFunAsrClient implements WholeFileAsrClient {

    private final FunAsrTranscriptionModel transcriptionModel;
    private final String modelName;

    public SpringAiFunAsrClient(FunAsrTranscriptionModel transcriptionModel, String modelName) {
        this.transcriptionModel = transcriptionModel;
        this.modelName = modelName;
    }

    @Override
    public String transcribe(MultipartFile audioFile, String language) {
        throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                "FunASR 非实时转写需要完整音频文件路径，不能使用分片直传");
    }

    @Override
    public String transcribe(Path audioFile, String language) {
        return transcribeDetailed(audioFile, language).text();
    }

    @Override
    public TranscriptionResult transcribeDetailed(Path audioFile, String language) {
        FunAsrTranscriptionOptions options = new FunAsrTranscriptionOptions(modelName, language);
        AudioTranscriptionResponse response = transcriptionModel.call(
                new AudioTranscriptionPrompt(new FileSystemResource(audioFile), options));
        TranscriptionResult detailed = response.getMetadata().get(
                FunAsrTranscriptionModel.TRANSCRIPTION_RESULT_METADATA_KEY);
        return detailed != null ? detailed : TranscriptionResult.textOnly(response.getResult().getOutput());
    }

    @Override
    public Duration maxRecommendedDuration() {
        return transcriptionModel.maxRecommendedDuration();
    }
}
