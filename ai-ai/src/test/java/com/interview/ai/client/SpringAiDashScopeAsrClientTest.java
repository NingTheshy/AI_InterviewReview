package com.interview.ai.client;

import com.interview.ai.dto.AsrSentence;
import com.interview.ai.dto.AsrTranscriptionContext;
import com.interview.ai.dto.TranscriptionResult;
import com.interview.ai.model.dashscope.DashScopeFileTranscriptionModel;
import com.interview.ai.model.dashscope.DashScopeTranscriptionOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponseMetadata;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringAiDashScopeAsrClientTest {

    @Mock
    private DashScopeFileTranscriptionModel transcriptionModel;

    @TempDir
    Path tempDir;

    @Test
    void transcribeDetailedUsesSpringAiPromptAndKeepsProviderMetadata() throws Exception {
        Path audio = tempDir.resolve("interview.wav");
        Files.write(audio, new byte[]{1, 2, 3});
        TranscriptionResult expected = new TranscriptionResult("测试文本",
                List.of(new AsrSentence("测试文本", "1", "UNKNOWN", 100, 900)), null);
        AudioTranscriptionResponseMetadata metadata = new AudioTranscriptionResponseMetadata();
        metadata.put(DashScopeFileTranscriptionModel.TRANSCRIPTION_RESULT_METADATA_KEY, expected);
        when(transcriptionModel.call(org.mockito.ArgumentMatchers.any())).thenReturn(
                new AudioTranscriptionResponse(new AudioTranscription(expected.text()), metadata));

        SpringAiDashScopeAsrClient client = new SpringAiDashScopeAsrClient(transcriptionModel,
                DashScopeFileTranscriptionModel.QWEN_MODEL);
        TranscriptionResult actual = client.transcribeDetailed(audio, "zh", AsrTranscriptionContext.chinese());

        assertEquals(expected, actual);
        ArgumentCaptor<AudioTranscriptionPrompt> promptCaptor = ArgumentCaptor.forClass(AudioTranscriptionPrompt.class);
        verify(transcriptionModel).call(promptCaptor.capture());
        DashScopeTranscriptionOptions options = (DashScopeTranscriptionOptions) promptCaptor.getValue().getOptions();
        assertEquals(DashScopeFileTranscriptionModel.QWEN_MODEL, options.getModel());
        assertEquals(List.of("zh"), options.context().languageHints());
    }

    @Test
    void maxRecommendedDurationComesFromSpringAiModel() {
        when(transcriptionModel.maxRecommendedDuration()).thenReturn(Duration.ofHours(2));
        SpringAiDashScopeAsrClient client = new SpringAiDashScopeAsrClient(transcriptionModel,
                DashScopeFileTranscriptionModel.QWEN_MODEL);

        assertEquals(Duration.ofHours(2), client.maxRecommendedDuration());
    }
}
