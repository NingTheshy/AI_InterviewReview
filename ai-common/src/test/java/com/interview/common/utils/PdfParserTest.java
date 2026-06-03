package com.interview.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PdfParser 工具类测试")
class PdfParserTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("extractText(File) - 文件不存在抛出异常")
    void extractText_fileNotExists_throwsIOException() {
        File nonExistentFile = new File(tempDir.toFile(), "nonexistent.pdf");
        assertThrows(IOException.class, () -> PdfParser.extractText(nonExistentFile));
    }

    @Test
    @DisplayName("extractText(File) - null 文件抛出异常")
    void extractText_nullFile_throwsIOException() {
        assertThrows(IOException.class, () -> PdfParser.extractText((File) null));
    }

    @Test
    @DisplayName("extractText(InputStream) - null 输入流抛出异常")
    void extractText_nullInputStream_throwsIOException() {
        assertThrows(IOException.class, () -> PdfParser.extractText((InputStream) null));
    }

    @Test
    @DisplayName("extractText(String) - 空路径抛出异常")
    void extractText_emptyPath_throwsIOException() {
        assertThrows(IOException.class, () -> PdfParser.extractText(""));
    }

    @Test
    @DisplayName("extractText(String) - null 路径抛出异常")
    void extractText_nullPath_throwsIOException() {
        assertThrows(IOException.class, () -> PdfParser.extractText((String) null));
    }
}
