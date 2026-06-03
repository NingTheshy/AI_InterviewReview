package com.interview.ai.service.impl;

import com.interview.ai.entity.AiConfig;
import com.interview.ai.factory.AiClientFactory;
import com.interview.ai.service.AiConfigService;
import com.interview.ai.service.AiModelClient;
import com.interview.common.constant.ConfigType;
import com.interview.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

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
    private AiConfigService aiConfigService;

    @Mock
    private AiModelClient aiModelClient;

    @InjectMocks
    private TranscriptionServiceImpl transcriptionService;

    @TempDir
    Path tempDir;

    private AiConfig mockConfig;

    @BeforeEach
    void setUp() {
        mockConfig = new AiConfig();
        mockConfig.setId(1L);
        mockConfig.setProvider("funasr");
        mockConfig.setConfigType(ConfigType.ASR.getCode());
    }

    @Test
    @DisplayName("transcribe - 文件不存在抛出异常")
    void transcribe_fileNotExists_throwsException() {
        String nonExistentPath = tempDir.resolve("nonexistent.wav").toString();
        assertThrows(BusinessException.class, () -> transcriptionService.transcribe(nonExistentPath, null));
    }

    @Test
    @DisplayName("transcribe - 成功转录音频文件")
    void transcribe_validFile_returnsTranscript() throws IOException {
        Path audioFile = tempDir.resolve("test.wav");
        Files.write(audioFile, new byte[]{1, 2, 3, 4, 5});

        when(aiClientFactory.getDefaultClient(ConfigType.ASR.getCode())).thenReturn(aiModelClient);
        when(aiModelClient.transcribe(any(), eq("zh"))).thenReturn("转录结果文本");

        String result = transcriptionService.transcribe(audioFile.toString(), null);

        assertEquals("转录结果文本", result);
        verify(aiModelClient).transcribe(any(), eq("zh"));
    }

    @Test
    @DisplayName("transcribe - 指定 configId 时使用对应客户端")
    void transcribe_withConfigId_usesSpecificClient() throws IOException {
        Path audioFile = tempDir.resolve("test.wav");
        Files.write(audioFile, new byte[]{1, 2, 3, 4, 5});

        when(aiConfigService.getDetail(1L)).thenReturn(mockConfig);
        when(aiClientFactory.getClient("funasr")).thenReturn(aiModelClient);
        when(aiModelClient.transcribe(any(), eq("zh"))).thenReturn("转录结果");

        String result = transcriptionService.transcribe(audioFile.toString(), 1L);

        assertEquals("转录结果", result);
        verify(aiConfigService).getDetail(1L);
        verify(aiClientFactory).getClient("funasr");
    }
}
