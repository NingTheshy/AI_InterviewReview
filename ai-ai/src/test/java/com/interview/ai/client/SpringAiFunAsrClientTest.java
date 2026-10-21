package com.interview.ai.client;

import com.interview.ai.dto.AsrSentence;
import com.interview.ai.dto.TranscriptionResult;
import com.interview.ai.model.funasr.FunAsrTranscriptionModel;
import com.interview.ai.model.funasr.FunAsrTranscriptionOptions;
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
class SpringAiFunAsrClientTest {

    @Mock
    private FunAsrTranscriptionModel transcriptionModel;

    @TempDir
    Path tempDir;

    @Test
    void transcribeDetailedUsesSpringAiPromptAndKeepsProviderMetadata() throws Exception {
        Path audio = tempDir.resolve("interview.wav");
        Files.write(audio, new byte[]{1, 2, 3});
        TranscriptionResult expected = new TranscriptionResult("测试文本",
                List.of(new AsrSentence("测试文本", "1", "UNKNOWN", 100, 900)), null);
        AudioTranscriptionResponseMetadata metadata = new AudioTranscriptionResponseMetadata();
        metadata.put(FunAsrTranscriptionModel.TRANSCRIPTION_RESULT_METADATA_KEY, expected);
        when(transcriptionModel.call(org.mockito.ArgumentMatchers.any())).thenReturn(
                new AudioTranscriptionResponse(new AudioTranscription(expected.text()), metadata));

        SpringAiFunAsrClient client = new SpringAiFunAsrClient(transcriptionModel, "fun-asr");
        TranscriptionResult actual = client.transcribeDetailed(audio, "zh");

        assertEquals(expected, actual);
        ArgumentCaptor<AudioTranscriptionPrompt> promptCaptor = ArgumentCaptor.forClass(AudioTranscriptionPrompt.class);
        verify(transcriptionModel).call(promptCaptor.capture());
        FunAsrTranscriptionOptions options = (FunAsrTranscriptionOptions) promptCaptor.getValue().getOptions();
        assertEquals("fun-asr", options.getModel());
        assertEquals("zh", options.getLanguage());
    }

    @Test
    void maxRecommendedDurationComesFromSpringAiModel() {
        when(transcriptionModel.maxRecommendedDuration()).thenReturn(Duration.ofHours(2));
        SpringAiFunAsrClient client = new SpringAiFunAsrClient(transcriptionModel, "fun-asr");

        assertEquals(Duration.ofHours(2), client.maxRecommendedDuration());
    }
}
