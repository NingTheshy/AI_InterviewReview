package com.interview.ai.audio;

import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 音频预处理器
 * <p>
 * 将任意格式音频（mp3/wav/m4a/ogg/flac 等）转换为 16kHz 16bit 单声道 PCM WAV，
 * 供后续切片和 ASR 转录使用。
 * </p>
 * <p>
 * 实现策略：
 * <ul>
 *   <li>WAV 文件：直接使用 javax.sound.sampled 处理（零依赖）</li>
 *   <li>MP3/其他格式：调用 ffmpeg 进行格式转换（路径由 {@link FfmpegResolver} 智能探测）</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AudioPreprocessor {

    private final FfmpegResolver ffmpegResolver;

    /** 目标采样率 16kHz */
    private static final float TARGET_SAMPLE_RATE = 16000f;
    /** 目标位深 16bit */
    private static final int TARGET_SAMPLE_SIZE = 16;
    /** 目标声道数 1（单声道） */
    private static final int TARGET_CHANNELS = 1;

    /**
     * 将音频文件预处理为 16kHz 16bit 单声道 WAV
     *
     * @param audioBytes 原始音频字节
     * @param filename   文件名（用于判断格式）
     * @return WAV 格式字节数据
     */
    public byte[] preprocess(byte[] audioBytes, String filename) {
        String format = getFormat(filename);
        log.info("音频预处理开始: filename={}, format={}, size={}KB",
                filename, format, audioBytes.length / 1024);

        try {
            byte[] wavBytes;

            if ("wav".equals(format)) {
                wavBytes = preprocessWav(audioBytes);
            } else {
                wavBytes = preprocessWithFfmpeg(audioBytes, format);
            }

            log.info("音频预处理完成: 原始={}KB, 转换后={}KB",
                    audioBytes.length / 1024, wavBytes.length / 1024);
            return wavBytes;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("音频预处理失败: {}", filename, e);
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "音频预处理失败: " + e.getMessage());
        }
    }

    /**
     * WAV 文件预处理（纯 Java，零依赖）
     */
    private byte[] preprocessWav(byte[] wavBytes) throws UnsupportedAudioFileException, IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(wavBytes);
        AudioInputStream originalStream = AudioSystem.getAudioInputStream(bais);
        AudioFormat originalFormat = originalStream.getFormat();

        log.debug("原始 WAV 格式: {}Hz, {}bit, {}ch, encoding={}",
                originalFormat.getSampleRate(),
                originalFormat.getSampleSizeInBits(),
                originalFormat.getChannels(),
                originalFormat.getEncoding());

        AudioInputStream pcmStream = toPcmStream(originalStream);
        AudioFormat targetFormat = createTargetFormat();
        AudioInputStream convertedStream;

        if (pcmStream.getFormat().matches(targetFormat)) {
            convertedStream = pcmStream;
        } else {
            convertedStream = AudioSystem.getAudioInputStream(targetFormat, pcmStream);
        }

        ByteArrayOutputStream wavOut = new ByteArrayOutputStream();
        AudioSystem.write(convertedStream, AudioFileFormat.Type.WAVE, wavOut);

        originalStream.close();
        pcmStream.close();
        if (convertedStream != pcmStream) {
            convertedStream.close();
        }

        return wavOut.toByteArray();
    }

    /**
     * 使用 FFmpeg 预处理非 WAV 格式
     */
    private byte[] preprocessWithFfmpeg(byte[] audioBytes, String format) throws IOException {
        // 通过 FfmpegResolver 智能获取 FFmpeg 路径
        String ffmpegPath = ffmpegResolver.resolve();
        if (ffmpegPath == null) {
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "未找到 FFmpeg，无法处理 " + format + " 格式音频。"
                            + "请安装 FFmpeg 并配置 ai.chunk.ffmpeg-path，或使用 WAV 格式上传。");
        }

        Path tempDir = Files.createTempDirectory("audio_preprocess_");
        Path inputFile = tempDir.resolve("input." + format);
        Path outputFile = tempDir.resolve("output.wav");

        try {
            Files.write(inputFile, audioBytes);

            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath,
                    "-i", inputFile.toAbsolutePath().toString(),
                    "-ar", String.valueOf((int) TARGET_SAMPLE_RATE),
                    "-ac", String.valueOf(TARGET_CHANNELS),
                    "-sample_fmt", "s16",
                    "-y",
                    outputFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);

            log.debug("执行 FFmpeg 转换: {} -> WAV (path={})", format, ffmpegPath);
            Process process = pb.start();

            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("FFmpeg 转换失败: exitCode={}, output={}", exitCode, output);
                throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                        "FFmpeg 音频转换失败: " + output);
            }

            byte[] result = Files.readAllBytes(outputFile);
            log.debug("FFmpeg 转换完成: {}KB -> {}KB", audioBytes.length / 1024, result.length / 1024);
            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("FFmpeg 转换被中断", e);
        } finally {
            try {
                Files.deleteIfExists(inputFile);
                Files.deleteIfExists(outputFile);
                Files.deleteIfExists(tempDir);
            } catch (IOException ignored) {
            }
        }
    }

    private AudioInputStream toPcmStream(AudioInputStream stream) {
        AudioFormat format = stream.getFormat();
        AudioFormat.Encoding encoding = format.getEncoding();

        if (encoding == AudioFormat.Encoding.PCM_SIGNED
                || encoding == AudioFormat.Encoding.PCM_UNSIGNED) {
            return stream;
        }

        log.debug("解码音频: {} -> PCM_SIGNED", encoding);
        AudioFormat pcmFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                format.getSampleRate(),
                TARGET_SAMPLE_SIZE,
                format.getChannels(),
                format.getChannels() * (TARGET_SAMPLE_SIZE / 8),
                format.getSampleRate(),
                false
        );
        return AudioSystem.getAudioInputStream(pcmFormat, stream);
    }

    private AudioFormat createTargetFormat() {
        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                TARGET_SAMPLE_RATE,
                TARGET_SAMPLE_SIZE,
                TARGET_CHANNELS,
                TARGET_CHANNELS * (TARGET_SAMPLE_SIZE / 8),
                TARGET_SAMPLE_RATE,
                false
        );
    }

    private String getFormat(String filename) {
        if (filename == null) return "wav";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".mp3")) return "mp3";
        if (lower.endsWith(".m4a")) return "m4a";
        if (lower.endsWith(".ogg")) return "ogg";
        if (lower.endsWith(".flac")) return "flac";
        if (lower.endsWith(".webm")) return "webm";
        return "wav";
    }
}
