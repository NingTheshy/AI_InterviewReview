package com.interview.ai.service.impl;

import com.interview.ai.audio.AudioPreprocessor;
import com.interview.ai.dto.TranscriptionResult;
import com.interview.ai.factory.AiClientFactory;
import com.interview.ai.service.AsrClient;
import com.interview.ai.service.TranscriptionService;
import com.interview.ai.service.WholeFileAsrClient;
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
import java.time.Duration;
import java.util.UUID;

/**
 * 语音转文字服务实现类
 * <p>
 * 通过 Spring AI ASR 适配客户端实现音频文件的语音识别。
 * 支持指定 ASR 配置或使用默认配置。
 * </p>
 * <p>
 * Fun-ASR 等长音频模型使用完整文件异步转写；其他模型由各自客户端处理输入限制。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptionServiceImpl implements TranscriptionService {

    private final AiClientFactory aiClientFactory;
    private final AudioPreprocessor audioPreprocessor;

    @Override
    public String transcribe(String audioFilePath, Long configId) {
        return transcribeDetailed(audioFilePath, configId).text();
    }

    /**
     * 根据当前 ASR 配置转写本地音频，并尽可能保留结构化转写信息。
     *
     * <p>完整文件客户端（Fun-ASR）走流式预处理路径，避免把长音频读入堆内存；传统直传客户端
     * 保持原有 {@link MultipartFile} 调用方式。调用方可统一使用返回对象，兼容只提供纯文本的模型。</p>
     *
     * @param audioFilePath 已保存的音频绝对或相对路径
     * @param configId 指定 AI 配置；为空时使用默认 ASR 配置
     * @return 转写文本、句子元数据和可获得的音频时长
     */
    @Override
    public TranscriptionResult transcribeDetailed(String audioFilePath, Long configId) {
        log.info("开始语音转文字: filePath={}, configId={}", audioFilePath, configId);
        return transcribeDetailed(audioFilePath, aiClientFactory.getAsrClientByConfigId(configId));
    }

    @Override
    public TranscriptionResult transcribeDetailedWithSnapshot(String audioFilePath, String encryptedSnapshot) {
        log.info("使用任务配置快照开始语音转文字: filePath={}", audioFilePath);
        return transcribeDetailed(audioFilePath, aiClientFactory.getAsrClientFromSnapshot(encryptedSnapshot));
    }

    private TranscriptionResult transcribeDetailed(String audioFilePath, AsrClient selectedClient) {

        Path path = Paths.get(audioFilePath);
        if (!Files.exists(path)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(),
                    "音频文件不存在: " + audioFilePath);
        }

        try {
            AsrClient client = selectedClient;

            // Fun-ASR 等长音频异步模型必须以完整文件提交。此分支必须在读取全部字节之前执行，
            // 避免将数百 MB 甚至 GB 级音频载入 JVM 堆内存。
            if (client instanceof WholeFileAsrClient wholeFileClient) {
                return transcribeWholeFile(path, wholeFileClient, "zh");
            }

            byte[] audioBytes = Files.readAllBytes(path);
            String filename = path.getFileName().toString();

            log.info("调用 ASR 直传客户端: 文件={}，大小={}KB", filename, audioBytes.length / 1024);
            MultipartFile multipartFile = new SimpleMultipartFile(filename, audioBytes);
            String transcript = client.transcribe(multipartFile, "zh");
            log.info("语音转文字完成: 长度={}", transcript.length());
            return TranscriptionResult.textOnly(transcript);

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
     * 将长音频预处理为 Fun-ASR 所需的单声道 WAV 后整体提交。
     *
     * <p>临时文件必须在所有成功、失败和中断路径中清理；音频时长以预处理结果计算，确保与实际
     * 上传给模型的文件一致。</p>
     */
    private TranscriptionResult transcribeWholeFile(Path source, WholeFileAsrClient client, String language) {
        Path preparedAudio = source.resolveSibling(".funasr-" + UUID.randomUUID() + ".wav");
        try {
            double durationSeconds = audioPreprocessor.preprocessToMonoWav(source, preparedAudio);
            Duration maxDuration = client.maxRecommendedDuration();
            if (durationSeconds > maxDuration.toSeconds()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(),
                        "开启说话人分离时音频建议不超过 " + maxDuration.toMinutes() + " 分钟，当前为 "
                                + Math.round(durationSeconds / 60.0) + " 分钟");
            }

            TranscriptionResult result = client.transcribeDetailed(preparedAudio, language)
                    .withAudioDurationSeconds((int) Math.ceil(durationSeconds));
            log.info("完整音频转写完成: 文件={}, 文本长度={}, 句子数={}",
                    source.getFileName(), result.text().length(), result.sentences().size());
            return result;
        } finally {
            try {
                Files.deleteIfExists(preparedAudio);
            } catch (IOException e) {
                log.warn("清理 Fun-ASR 临时单声道音频失败: {}", preparedAudio, e);
            }
        }
    }

    /** 为仍使用直传协议的 ASR 客户端提供最小化的内存型 {@link MultipartFile} 适配器。 */
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
