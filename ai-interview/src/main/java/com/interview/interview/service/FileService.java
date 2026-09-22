package com.interview.interview.service;

import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

public interface FileService {
    FileDownload prepareUserDownload(String filename, Long userId, String rangeHeader);

    FileDownload prepareAsrDownload(String filename, long expires, String token);

    void copy(FileDownload download, OutputStream output) throws IOException;

    record FileDownload(Path path, String filename, MediaType contentType, long fileSize,
                        long start, long end, boolean partial) {
        public long length() {
            return partial ? end - start + 1 : fileSize;
        }
    }
}
