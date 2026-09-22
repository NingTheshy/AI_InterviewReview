package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.ai.config.FunAsrProperties;
import com.interview.interview.entity.Interview;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.service.FileService.FileDownload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FileServiceImplTest {
    private final InterviewMapper mapper = mock(InterviewMapper.class);
    private final FunAsrProperties properties = mock(FunAsrProperties.class);
    private final FileServiceImpl service = new FileServiceImpl(mapper, properties);
    private Path directory;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(Path.of("target"));
        directory = Files.createTempDirectory(Path.of("target"), "file-service-");
        Files.writeString(directory.resolve("audio.mp3"), "0123456789");
        ReflectionTestUtils.setField(service, "uploadDir", directory.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(directory.resolve("audio.mp3"));
        Files.deleteIfExists(directory);
    }

    @Test
    void onlyOwnerCanDownload() {
        when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        ResponseStatusException denied = assertThrows(ResponseStatusException.class,
                () -> service.prepareUserDownload("audio.mp3", 2L, null));
        assertEquals(404, denied.getStatusCode().value());
        verify(mapper).selectCount(any(LambdaQueryWrapper.class));
    }

    @Test
    void streamsOpenEndedAndSuffixRanges() throws IOException {
        when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        FileDownload openEnded = service.prepareUserDownload("audio.mp3", 1L, "bytes=4-");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.copy(openEnded, output);
        assertEquals("456789", output.toString());
        assertEquals(6, openEnded.length());

        FileDownload suffix = service.prepareUserDownload("audio.mp3", 1L, "bytes=-3");
        output.reset();
        service.copy(suffix, output);
        assertEquals("789", output.toString());
    }

    @Test
    void rejectsInvalidRangeAndFilename() {
        when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertEquals(416, assertThrows(ResponseStatusException.class,
                () -> service.prepareUserDownload("audio.mp3", 1L, "bytes=10-")).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.prepareUserDownload("../audio.mp3", 1L, null)).getStatusCode().value());
    }
}
