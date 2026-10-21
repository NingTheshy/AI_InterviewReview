package com.interview.ai.model.funasr;

import com.interview.ai.config.AiProviderProperties;
import com.interview.ai.config.FunAsrProperties;
import com.interview.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FunAsrTranscriptionModelTest {

    @TempDir
    Path tempDir;

    @Test
    void implementsSpringAiTranscriptionModelAndPreservesDurationPolicy() {
        FunAsrProperties funAsr = validFunAsrProperties();
        funAsr.setMaxDiarizationDurationMinutes(120);
        FunAsrTranscriptionModel model = new FunAsrTranscriptionModel(providerProperties(), funAsr);

        assertInstanceOf(TranscriptionModel.class, model);
        assertEquals(Duration.ofHours(2), model.maxRecommendedDuration());
    }

    @Test
    void rejectsMissingPublicUrlBeforeRemoteCall() throws Exception {
        Path audio = tempDir.resolve("interview.wav");
        Files.write(audio, new byte[]{1});
        FunAsrProperties funAsr = validFunAsrProperties();
        funAsr.setPublicBaseUrl("");
        FunAsrTranscriptionModel model = new FunAsrTranscriptionModel(providerProperties(), funAsr);

        assertThrows(BusinessException.class, () -> model.call(new AudioTranscriptionPrompt(
                new FileSystemResource(audio), new FunAsrTranscriptionOptions("fun-asr", "zh"))));
    }

    private AiProviderProperties providerProperties() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setApiKey("test-key");
        properties.setApiEndpoint("https://dashscope.example.test/api/v1");
        properties.setModelName("fun-asr");
        properties.setTimeoutSeconds(5);
        return properties;
    }

    private FunAsrProperties validFunAsrProperties() {
        FunAsrProperties properties = new FunAsrProperties();
        properties.setPublicBaseUrl("https://audio.example.test");
        properties.setFileUrlSecret("test-file-url-secret-with-sufficient-length");
        properties.setDiarizationEnabled(true);
        return properties;
    }
}
