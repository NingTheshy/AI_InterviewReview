package com.interview.ai.service.impl;

import com.interview.ai.audio.AudioPreprocessor;
import com.interview.ai.audio.AudioSplitter;
import com.interview.ai.audio.ReliableSender;
import com.interview.ai.audio.TextMerger;
import com.interview.ai.config.AudioChunkProperties;
import com.interview.ai.factory.AiClientFactory;
import com.interview.ai.service.AsrClient;
import com.interview.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
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
    private AudioChunkProperties chunkProperties;

    @Mock
    private AudioPreprocessor audioPreprocessor;

    @Mock
    private AudioSplitter audioSplitter;

    @Mock
    private ReliableSender reliableSender;

    @Mock
    private TextMerger textMerger;

    @InjectMocks
    private TranscriptionServiceImpl transcriptionService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // 默认配置：小文件直传阈值 6MB
        lenient().when(chunkProperties.getMaxDirectSize()).thenReturn(6291456L);
    }

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
}
