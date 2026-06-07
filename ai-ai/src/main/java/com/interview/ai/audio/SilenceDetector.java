package com.interview.ai.audio;

import com.interview.ai.config.AudioChunkProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 静音检测器
 * <p>
 * 基于 PCM 帧的 RMS（均方根）能量计算，检测音频中的静音区域。
 * 纯 Java 实现，无需 webrtcvad 等外部依赖。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SilenceDetector {

    private final AudioChunkProperties chunkProperties;

    /** 默认帧长（毫秒） */
    private static final int FRAME_DURATION_MS = 20;

    /**
     * 检测静音区域
     *
     * @param pcmBytes           PCM 16bit 小端字节数据
     * @param sampleRate         采样率（Hz）
     * @param minSilenceDuration 最小静音持续时间（秒），短于此的不记录
     * @return 静音区域列表
     */
    public List<SilenceRegion> detectSilence(byte[] pcmBytes, int sampleRate,
                                              double minSilenceDuration) {
        double threshold = chunkProperties.getSilenceThreshold();
        int frameSize = (int) (sampleRate * FRAME_DURATION_MS / 1000) * 2;
        int totalFrames = pcmBytes.length / frameSize;

        double[] rmsValues = new double[totalFrames];
        for (int i = 0; i < totalFrames; i++) {
            int offset = i * frameSize;
            rmsValues[i] = calculateRms(pcmBytes, offset, frameSize);
        }

        List<SilenceRegion> regions = new ArrayList<>();
        int silenceStart = -1;

        for (int i = 0; i < totalFrames; i++) {
            boolean isSilent = rmsValues[i] < threshold;

            if (isSilent && silenceStart == -1) {
                // 静音开始
                silenceStart = i;
            } else if (!isSilent && silenceStart != -1) {
                // 静音结束
                double duration = (i - silenceStart) * FRAME_DURATION_MS / 1000.0;
                if (duration >= minSilenceDuration) {
                    double startTime = silenceStart * FRAME_DURATION_MS / 1000.0;
                    double endTime = i * FRAME_DURATION_MS / 1000.0;
                    regions.add(new SilenceRegion(startTime, endTime));
                }
                silenceStart = -1;
            }
        }

        // 处理尾部静音
        if (silenceStart != -1) {
            double duration = (totalFrames - silenceStart) * FRAME_DURATION_MS / 1000.0;
            if (duration >= minSilenceDuration) {
                double startTime = silenceStart * FRAME_DURATION_MS / 1000.0;
                double endTime = totalFrames * FRAME_DURATION_MS / 1000.0;
                regions.add(new SilenceRegion(startTime, endTime));
            }
        }

        log.debug("静音检测完成: 总帧数={}, 检测到 {} 个静音区域", totalFrames, regions.size());
        return regions;
    }

    /**
     * 计算 PCM 帧的 RMS 能量
     * <p>
     * 16bit 有符号小端 PCM，每 2 字节一个采样点。
     * </p>
     */
    private double calculateRms(byte[] pcm, int offset, int length) {
        if (length <= 0) return 0;
        long sumSquares = 0;
        int sampleCount = 0;

        for (int i = offset; i + 1 < offset + length; i += 2) {
            // 16bit 小端有符号整数
            short sample = (short) ((pcm[i + 1] << 8) | (pcm[i] & 0xFF));
            sumSquares += (long) sample * sample;
            sampleCount++;
        }

        if (sampleCount == 0) return 0;
        return Math.sqrt((double) sumSquares / sampleCount);
    }

    /**
     * 静音区域
     */
    @Data
    @AllArgsConstructor
    public static class SilenceRegion {
        /** 起始时间（秒） */
        private double startTime;
        /** 结束时间（秒） */
        private double endTime;

        /** 静音时长（秒） */
        public double getDuration() {
            return endTime - startTime;
        }
    }
}
