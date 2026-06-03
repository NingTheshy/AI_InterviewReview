package com.interview.ai.service.impl;

import com.interview.ai.entity.AiConfig;
import com.interview.ai.factory.AiClientFactory;
import com.interview.ai.service.AiConfigService;
import com.interview.ai.service.AiModelClient;
import com.interview.ai.service.TranscriptionService;
import com.interview.common.constant.ConfigType;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 语音转文字服务实现类
 * <p>
 * 通过 DashScope ASR 实现音频文件的语音识别，采用异步提交+轮询模式。
 * 支持网络重试（3次指数退避）和可配置的超时时间。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptionServiceImpl implements TranscriptionService {

    private final AiClientFactory aiClientFactory;
    private final AiConfigService aiConfigService;

    @Override
    public String transcribe(String audioFilePath, Long configId) {
        log.info("开始语音转文字: filePath={}, configId={}", audioFilePath, configId);

        Path path = Paths.get(audioFilePath);
        if (!Files.exists(path)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(),
                    "音频文件不存在: " + audioFilePath);
        }

        try {
            byte[] audioBytes = Files.readAllBytes(path);
            String filename = path.getFileName().toString();
            MultipartFile multipartFile = new SimpleMultipartFile(filename, audioBytes);

            AiModelClient client = resolveClient(configId);
            String transcript = client.transcribe(multipartFile, "zh");

            log.info("语音转文字完成: 长度={}", transcript.length());
            return transcript;

        } catch (IOException e) {
            log.error("读取音频文件失败: {}", audioFilePath, e);
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(),
                    "读取音频文件失败: " + e.getMessage());
        }
    }

    private AiModelClient resolveClient(Long configId) {
        if (configId != null && configId > 0) {
            AiConfig config = aiConfigService.getDetail(configId);
            return aiClientFactory.getClient(config.getProvider());
        }
        return aiClientFactory.getDefaultClient(ConfigType.ASR.getCode());
    }

    private static class SimpleMultipartFile implements MultipartFile {

        private final String filename;
        private final byte[] content;

        SimpleMultipartFile(String filename, byte[] content) {
            this.filename = filename;
            this.content = content;
        }

        @Override
        public String getName() { return "file"; }

        @Override
        public String getOriginalFilename() { return filename; }

        @Override
        public String getContentType() {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".mp3")) return "audio/mpeg";
            if (lower.endsWith(".m4a")) return "audio/mp4";
            if (lower.endsWith(".ogg")) return "audio/ogg";
            if (lower.endsWith(".flac")) return "audio/flac";
            return "audio/wav";
        }

        @Override
        public boolean isEmpty() { return content.length == 0; }

        @Override
        public long getSize() { return content.length; }

        @Override
        public byte[] getBytes() { return content; }

        @Override
        public InputStream getInputStream() { return new ByteArrayInputStream(content); }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}
