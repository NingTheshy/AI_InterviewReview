package com.interview.common.utils;

import com.interview.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileUtil 工具类测试")
class FileUtilTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("validateAudioFile - 有效 MP3 文件通过校验")
    void validateAudioFile_validMp3_noException() {
        MockMultipartFile file = new MockMultipartFile(
                "audio", "test.mp3", "audio/mpeg", new byte[1024]);
        assertDoesNotThrow(() -> FileUtil.validateAudioFile(file));
    }

    @Test
    @DisplayName("validateAudioFile - 有效 WAV 文件通过校验")
    void validateAudioFile_validWav_noException() {
        MockMultipartFile file = new MockMultipartFile(
                "audio", "test.wav", "audio/wav", new byte[1024]);
        assertDoesNotThrow(() -> FileUtil.validateAudioFile(file));
    }

    @Test
    @DisplayName("validateAudioFile - 空文件抛出异常")
    void validateAudioFile_emptyFile_throwsException() {
        MockMultipartFile file = new MockMultipartFile(
                "audio", "test.mp3", "audio/mpeg", new byte[0]);
        assertThrows(BusinessException.class, () -> FileUtil.validateAudioFile(file));
    }

    @Test
    @DisplayName("validateAudioFile - null 文件抛出异常")
    void validateAudioFile_nullFile_throwsException() {
        assertThrows(BusinessException.class, () -> FileUtil.validateAudioFile(null));
    }

    @Test
    @DisplayName("validateAudioFile - 不支持的格式抛出异常")
    void validateAudioFile_unsupportedFormat_throwsException() {
        MockMultipartFile file = new MockMultipartFile(
                "audio", "test.txt", "text/plain", new byte[1024]);
        assertThrows(BusinessException.class, () -> FileUtil.validateAudioFile(file));
    }

    @Test
    @DisplayName("generateFileName - 生成唯一文件名")
    void generateFileName_returnsUniqueName() {
        String name1 = FileUtil.generateFileName("test.mp3");
        String name2 = FileUtil.generateFileName("test.mp3");
        assertNotEquals(name1, name2);
        assertTrue(name1.endsWith(".mp3"));
        assertTrue(name2.endsWith(".mp3"));
    }

    @Test
    @DisplayName("saveFile - 保存文件成功")
    void saveFile_validFile_saved() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "audio", "test.mp3", "audio/mpeg", new byte[1024]);
        Path result = FileUtil.saveFile(file, tempDir.toString(), "saved.mp3");
        assertNotNull(result);
        assertTrue(result.toFile().exists());
    }
}
