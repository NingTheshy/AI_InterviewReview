package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.ai.config.FunAsrProperties;
import com.interview.common.utils.AsrFileUrlSigner;
import com.interview.interview.entity.Interview;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 上传文件下载服务。
 *
 * <p>用户下载以面试记录所有权为准；Fun-ASR 下载以短时 HMAC 签名为准。两条路径均在解析文件
 * 前限制文件名和规范化路径，防止路径穿越，并支持单区间 Range 以供音频播放器按需读取。</p>
 */
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final InterviewMapper interviewMapper;
    private final FunAsrProperties funAsrProperties;

    @Value("${interview.file.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    public FileDownload prepareUserDownload(String filename, Long userId, String rangeHeader) {
        validateFilename(filename);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        LambdaQueryWrapper<Interview> query = new LambdaQueryWrapper<Interview>()
                .eq(Interview::getUserId, userId)
                .and(files -> files.eq(Interview::getAudioFilePath, filename)
                        .or().eq(Interview::getResumeFilePath, filename));
        if (interviewMapper.selectCount(query) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return prepare(filename, rangeHeader);
    }

    /**
     * 为 Fun-ASR 准备签名授权的完整音频下载。
     *
     * <p>该接口不依赖 JWT，因为 DashScope 无法携带用户会话；必须先验签再解析路径，防止签名
     * 校验逻辑成为文件枚举接口。</p>
     */
    @Override
    public FileDownload prepareAsrDownload(String filename, long expires, String token) {
        if (!AsrFileUrlSigner.verify(filename, expires, token, funAsrProperties.getFileUrlSecret())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        validateFilename(filename);
        return prepare(filename, null);
    }

    /**
     * 解析文件路径和单区间 Range 请求，不接受多区间以保持响应流逻辑和资源使用可预测。
     */
    private FileDownload prepare(String filename, String rangeHeader) {
        Path directory = Path.of(uploadDir).toAbsolutePath().normalize();
        Path file = directory.resolve(filename).normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        try {
            long size = Files.size(file);
            long start = 0;
            long end = size - 1;
            boolean partial = rangeHeader != null;
            if (partial) {
                if (size == 0 || !rangeHeader.startsWith("bytes=") || rangeHeader.indexOf(',') >= 0) {
                    throw invalidRange();
                }
                String spec = rangeHeader.substring(6);
                int separator = spec.indexOf('-');
                if (separator < 0 || separator != spec.lastIndexOf('-')) {
                    throw invalidRange();
                }
                try {
                    String first = spec.substring(0, separator);
                    String last = spec.substring(separator + 1);
                    if (first.isEmpty()) {
                        long suffix = Long.parseLong(last);
                        if (suffix <= 0) throw invalidRange();
                        start = Math.max(0, size - suffix);
                    } else {
                        start = Long.parseLong(first);
                        end = last.isEmpty() ? size - 1 : Math.min(Long.parseLong(last), size - 1);
                    }
                } catch (NumberFormatException ex) {
                    throw invalidRange();
                }
                if (start < 0 || start >= size || end < start) {
                    throw invalidRange();
                }
            }
            return new FileDownload(file, filename, contentType(filename), size, start, end, partial);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不可读取", ex);
        }
    }

    /** 按已校验的范围复制文件，限制读取长度以避免 Range 请求越界返回后续内容。 */
    @Override
    public void copy(FileDownload download, OutputStream output) throws IOException {
        try (InputStream input = Files.newInputStream(download.path())) {
            input.skipNBytes(download.start());
            byte[] buffer = new byte[8192];
            long remaining = download.length();
            while (remaining > 0) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) throw new IOException("文件在传输时发生变化");
                output.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    /** 拒绝路径分隔符、控制字符及相对路径标记，文件名只能是上传目录中的单个文件。 */
    private void validateFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.equals(".") || filename.equals("..")
                || filename.contains("/") || filename.contains("\\") || filename.indexOf('\0') >= 0
                || filename.indexOf('"') >= 0 || filename.indexOf('\r') >= 0 || filename.indexOf('\n') >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效文件名");
        }
    }

    private ResponseStatusException invalidRange() {
        return new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "无效 Range");
    }

    private MediaType contentType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".wav")) return MediaType.parseMediaType("audio/wav");
        if (lower.endsWith(".mp3")) return MediaType.parseMediaType("audio/mpeg");
        if (lower.endsWith(".m4a")) return MediaType.parseMediaType("audio/mp4");
        if (lower.endsWith(".ogg")) return MediaType.parseMediaType("audio/ogg");
        if (lower.endsWith(".flac")) return MediaType.parseMediaType("audio/flac");
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".doc")) return MediaType.parseMediaType("application/msword");
        if (lower.endsWith(".docx")) return MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
