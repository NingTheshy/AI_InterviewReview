package com.interview.interview.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@RestController
public class FileController {

    @Value("${interview.file.upload-dir:./uploads}")
    private String uploadDir;

    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<?> serveFile(
            @PathVariable String filename,
            @RequestHeader(value = "Range", required = false) String rangeHeader) throws java.io.IOException {
        if (filename.contains("..") || filename.contains("\\")
                || filename.contains("/") || filename.contains("\0")) {
            return ResponseEntity.badRequest().build();
        }

        Path filePath = Paths.get(uploadDir, filename).normalize();
        if (!filePath.startsWith(Paths.get(uploadDir).normalize())) {
            return ResponseEntity.badRequest().build();
        }

        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = determineContentType(filename);
        long fileLength = resource.contentLength();

        // 支持 HTTP Range 请求（音频 seek）
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            try {
                String rangeSpec = rangeHeader.substring(6);
                String[] parts = rangeSpec.split("-");
                long start = Long.parseLong(parts[0]);
                long end = parts.length > 1 && !parts[1].isEmpty()
                        ? Long.parseLong(parts[1])
                        : fileLength - 1;

                if (start >= fileLength || end >= fileLength || start > end) {
                    return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
                }

                long contentLength = end - start + 1;
                byte[] data = new byte[(int) contentLength];
                try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
                    raf.seek(start);
                    raf.read(data, 0, (int) contentLength);
                }

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType(contentType));
                headers.set("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
                headers.setContentLength(contentLength);
                headers.set("Accept-Ranges", "bytes");
                headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"");

                return new ResponseEntity<>(data, headers, HttpStatus.PARTIAL_CONTENT);
            } catch (Exception e) {
                log.warn("Range 请求处理失败: {}", rangeHeader, e);
                // 回退到完整文件返回
            }
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .header("Accept-Ranges", "bytes")
                .contentLength(fileLength)
                .body(resource);
    }

    private String determineContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return "application/octet-stream";
    }
}
