package com.interview.ai.service.impl;

import com.interview.ai.audio.AudioChunk;
import com.interview.ai.audio.AudioPreprocessor;
import com.interview.ai.audio.AudioSplitter;
import com.interview.ai.audio.ReliableSender;
import com.interview.ai.audio.TextMerger;
import com.interview.ai.config.AudioChunkProperties;
import com.interview.ai.factory.AiClientFactory;
import com.interview.ai.service.AsrClient;
import com.interview.ai.service.TranscriptionService;
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
import java.util.List;
import java.util.Map;

/**
 * 语音转文字服务实现类
 * <p>
 * 通过 ASR 客户端实现音频文件的语音识别。
 * 支持指定 ASR 配置或使用默认配置。
 * </p>
 * <p>
 * 对于超过阈值的大音频文件，自动进行分片处理：
 * 预处理 → 静音检测切片 → 并发 ASR 调用 → 文本合并去重。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptionServiceImpl implements TranscriptionService {

    private final AiClientFactory aiClientFactory;
    private final AudioChunkProperties chunkProperties;
    private final AudioPreprocessor audioPreprocessor;
    private final AudioSplitter audioSplitter;
    private final ReliableSender reliableSender;
    private final TextMerger textMerger;

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
            AsrClient client = aiClientFactory.getAsrClientByConfigId(configId);

            // 小文件直传
            if (audioBytes.length <= chunkProperties.getMaxDirectSize()) {
                log.info("音频文件较小（{}KB），直接调用 ASR", audioBytes.length / 1024);
                MultipartFile multipartFile = new SimpleMultipartFile(filename, audioBytes);
                String transcript = client.transcribe(multipartFile, "zh");
                log.info("语音转文字完成: 长度={}", transcript.length());
                return transcript;
            }

            // 大文件：分片处理
            log.info("音频文件超过阈值（{}MB > {}MB），启动分片转录",
                    audioBytes.length / 1024 / 1024,
                    chunkProperties.getMaxDirectSize() / 1024 / 1024);

            // 1. 预处理：转换为 16kHz 16bit 单声道 WAV
            byte[] wavData = audioPreprocessor.preprocess(audioBytes, filename);

            // 2. 智能切片
            List<AudioChunk> chunks = audioSplitter.split(wavData);
            log.info("音频切片完成: {} 个片段", chunks.size());
            for (AudioChunk chunk : chunks) {
                long base64Estimate = chunk.getAudioData().length * 4L / 3;
                String sizeWarning = base64Estimate > 9_000_000 ? " ⚠️ 可能超限!" : "";
                log.info("  片段 {}: {}s - {}s, 大小={}KB, 预估base64={}KB{}",
                        chunk.getSeq(),
                        String.format("%.1f", chunk.getStartTime()),
                        String.format("%.1f", chunk.getEndTime()),
                        chunk.getAudioData().length / 1024,
                        base64Estimate / 1024,
                        sizeWarning);
            }

            // 3. 并发 ASR 转录（带重试和去重）
            Map<Integer, String> results = reliableSender.sendAll(chunks, client);

            // 检查是否有丢失的片段
            if (results.size() < chunks.size()) {
                log.warn("警告: 有 {} 个片段转录失败（总共 {} 个）",
                        chunks.size() - results.size(), chunks.size());
                for (AudioChunk chunk : chunks) {
                    if (!results.containsKey(chunk.getSeq())) {
                        log.warn("  丢失片段 {}: {}s - {}s",
                                chunk.getSeq(),
                                String.format("%.1f", chunk.getStartTime()),
                                String.format("%.1f", chunk.getEndTime()));
                    }
                }
            }

            // 4. 文本合并去重
            String transcript = textMerger.merge(results, audioSplitter.getOverlapDuration());
            log.info("分片转录完成: 成功片段={}/{}, 文本长度={}",
                    results.size(), chunks.size(), transcript.length());

            return transcript;

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("读取音频文件失败: {}", audioFilePath, e);
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(),
                    "读取音频文件失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("语音转文字异常: {}", audioFilePath, e);
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "语音转文字异常: " + e.getMessage());
        }
    }

    /**
     * 简单的 MultipartFile 实现
     */
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
