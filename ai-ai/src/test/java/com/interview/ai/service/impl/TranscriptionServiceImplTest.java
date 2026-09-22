package com.interview.ai.service.impl;

import com.interview.ai.audio.AudioPreprocessor;
import com.interview.ai.dto.AsrSentence;
import com.interview.ai.dto.TranscriptionResult;
import com.interview.ai.factory.AiClientFactory;
import com.interview.ai.service.AsrClient;
import com.interview.ai.service.WholeFileAsrClient;
import com.interview.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TranscriptionServiceImpl 测试")
class TranscriptionServiceImplTest {

    @Mock
    private AiClientFactory aiClientFactory;

    @Mock
    private AsrClient asrClient;

    @Mock
    private WholeFileAsrClient wholeFileAsrClient;

    @Mock
    private AudioPreprocessor audioPreprocessor;

    @InjectMocks
    private TranscriptionServiceImpl transcriptionService;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("transcribe - 文件不存在抛出异常")
    void transcribe_fileNotExists_throwsException() {
        String nonExistentPath = tempDir.resolve("nonexistent.wav").toString();
        assertThrows(BusinessException.class, () -> transcriptionService.transcribe(nonExistentPath, null));
    }

    @Test
    @DisplayName("transcribe - 小文件直传成功")
    void transcribe_validFile_returnsTranscript() throws IOException {
        Path audioFile = tempDir.resolve("test.wav");
        Files.write(audioFile, new byte[]{1, 2, 3, 4, 5});

        when(aiClientFactory.getAsrClientByConfigId(null)).thenReturn(asrClient);
        when(asrClient.transcribe(any(), eq("zh"))).thenReturn("转录结果文本");

        String result = transcriptionService.transcribe(audioFile.toString(), null);

        assertEquals("转录结果文本", result);
        verify(asrClient).transcribe(any(), eq("zh"));
    }

    @Test
    @DisplayName("transcribe - 指定 configId 时使用对应客户端")
    void transcribe_withConfigId_usesSpecificClient() throws IOException {
        Path audioFile = tempDir.resolve("test.wav");
        Files.write(audioFile, new byte[]{1, 2, 3, 4, 5});

        when(aiClientFactory.getAsrClientByConfigId(1L)).thenReturn(asrClient);
        when(asrClient.transcribe(any(), eq("zh"))).thenReturn("转录结果");

        String result = transcriptionService.transcribe(audioFile.toString(), 1L);

        assertEquals("转录结果", result);
        verify(aiClientFactory).getAsrClientByConfigId(1L);
    }

    @Test
    @DisplayName("transcribe - 完整文件客户端绕过通用直传路径")
    void transcribe_wholeFileClient_usesWholeFilePath() throws IOException {
        Path audioFile = tempDir.resolve("interview.wav");
        Files.write(audioFile, new byte[]{1, 2, 3, 4, 5});

        when(aiClientFactory.getAsrClientByConfigId(null)).thenReturn(wholeFileAsrClient);
        when(audioPreprocessor.preprocessToMonoWav(eq(audioFile), any(Path.class))).thenReturn(60.0);
        when(wholeFileAsrClient.maxRecommendedDuration()).thenReturn(Duration.ofHours(2));
        when(wholeFileAsrClient.transcribeDetailed(any(Path.class), eq("zh"))).thenReturn(
                new TranscriptionResult("[说话人 0] 测试文本",
                        List.of(new AsrSentence("测试文本", "0", "UNKNOWN", 0, 1000)), null));

        String result = transcriptionService.transcribe(audioFile.toString(), null);

        assertEquals("[说话人 0] 测试文本", result);
        verify(wholeFileAsrClient).transcribeDetailed(any(Path.class), eq("zh"));
        verify(asrClient, never()).transcribe(any(), anyString());
    }

    @Test
    @DisplayName("transcribeDetailed - 保留句子时间戳并写入音频时长")
    void transcribeDetailed_wholeFile_keepsStructuredResult() throws IOException {
        Path audioFile = tempDir.resolve("interview.wav");
        Files.write(audioFile, new byte[]{1, 2, 3});
        TranscriptionResult providerResult = new TranscriptionResult("文本",
                List.of(new AsrSentence("文本", "1", "UNKNOWN", 100, 2200)), null);

        when(aiClientFactory.getAsrClientByConfigId(null)).thenReturn(wholeFileAsrClient);
        when(audioPreprocessor.preprocessToMonoWav(eq(audioFile), any(Path.class))).thenReturn(60.2);
        when(wholeFileAsrClient.maxRecommendedDuration()).thenReturn(Duration.ofHours(2));
        when(wholeFileAsrClient.transcribeDetailed(any(Path.class), eq("zh"))).thenReturn(providerResult);

        TranscriptionResult result = transcriptionService.transcribeDetailed(audioFile.toString(), null);

        assertEquals(61, result.audioDurationSeconds());
        assertEquals(100, result.sentences().get(0).startTimeMs());
    }
}
