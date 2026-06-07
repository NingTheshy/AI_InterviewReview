package com.interview.ai.audio;

import com.interview.ai.config.AudioChunkProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 智能音频切片器（SmartChunker）
 * <p>
 * 融合静音检测 + 最小/最大时长约束 + 重叠窗口，将长音频切分为多个片段。
 * 切分点选择在自然停顿处，避免破坏词语完整性。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AudioSplitter {

    private final SilenceDetector silenceDetector;
    private final AudioChunkProperties chunkProperties;

    /** 目标采样率 */
    private static final int SAMPLE_RATE = 16000;
    /** 每样本字节数（16bit） */
    private static final int BYTES_PER_SAMPLE = 2;
    /** 每帧字节数（单声道 16bit） */
    private static final int BYTES_PER_FRAME = BYTES_PER_SAMPLE;

    /**
     * 将 WAV 音频切分为多个片段
     *
     * @param wavBytes 16kHz 16bit 单声道 WAV 字节数据
     * @return 切片后的音频片段列表
     */
    public List<AudioChunk> split(byte[] wavBytes) {
        int minSeg = chunkProperties.getMinSegmentDuration();
        int maxSeg = chunkProperties.getMaxSegmentDuration();
        double minSilence = chunkProperties.getMinSilenceDuration();
        double overlap = chunkProperties.getOverlapDuration();

        log.info("音频切片开始: size={}KB, minSeg={}s, maxSeg={}s, minSilence={}s, overlap={}s",
                wavBytes.length / 1024, minSeg, maxSeg, minSilence, overlap);

        // 1. 提取 PCM 数据（跳过 WAV 头部）
        byte[] pcmData = extractPcmData(wavBytes);
        double totalDuration = (double) pcmData.length / (SAMPLE_RATE * BYTES_PER_SAMPLE);

        // 2. 检测静音区域
        List<SilenceDetector.SilenceRegion> silenceRegions =
                silenceDetector.detectSilence(pcmData, SAMPLE_RATE, minSilence);

        // 3. 计算切分点
        List<Double> cutPoints = calculateCutPoints(pcmData.length, silenceRegions, minSeg, maxSeg);
        log.info("计算得到 {} 个切分点: {}", cutPoints.size(), cutPoints);

        // 4. 按切分点切片，附加重叠窗口
        List<AudioChunk> chunks = new ArrayList<>();
        int overlapBytes = (int) (overlap * SAMPLE_RATE * BYTES_PER_SAMPLE);
        int seq = 0;

        for (int i = 0; i <= cutPoints.size(); i++) {
            // 计算本段的起止位置（PCM 字节偏移）
            int segStart = (i == 0) ? 0 : secondsToBytes(cutPoints.get(i - 1));
            int segEnd = (i == cutPoints.size()) ? pcmData.length : secondsToBytes(cutPoints.get(i));

            // 添加重叠前缀（上一片段的尾部）
            int prefixStart = Math.max(0, segStart - overlapBytes);
            int actualStart = prefixStart;

            byte[] chunkPcm = new byte[segEnd - actualStart];
            System.arraycopy(pcmData, actualStart, chunkPcm, 0, chunkPcm.length);

            // 转换为 WAV 格式
            byte[] chunkWav = pcmToWav(chunkPcm);

            double startTime = (double) actualStart / (SAMPLE_RATE * BYTES_PER_SAMPLE);
            double endTime = (double) segEnd / (SAMPLE_RATE * BYTES_PER_SAMPLE);

            chunks.add(new AudioChunk(seq, chunkWav, startTime, endTime));
            seq++;
        }

        log.info("音频切片完成: 总时长={}s, 切分为 {} 个片段", totalDuration, chunks.size());
        for (AudioChunk chunk : chunks) {
            log.debug("  片段 {}: {}s - {}s, size={}KB",
                    chunk.getSeq(),
                    String.format("%.1f", chunk.getStartTime()),
                    String.format("%.1f", chunk.getEndTime()),
                    chunk.getAudioData().length / 1024);
        }

        return chunks;
    }

    /**
     * 计算切分点（秒）
     * <p>
     * 策略：
     * 1. 当缓冲时长 >= minSegmentDuration 且遇到静音区域时，在静音中点切分
     * 2. 当缓冲时长 >= maxSegmentDuration 时，强制切分
     * </p>
     */
    private List<Double> calculateCutPoints(int pcmLength,
                                             List<SilenceDetector.SilenceRegion> silenceRegions,
                                             int minSeg, int maxSeg) {
        List<Double> cutPoints = new ArrayList<>();
        double totalDuration = (double) pcmLength / (SAMPLE_RATE * BYTES_PER_SAMPLE);
        double lastCutTime = 0.0;

        // 遍历静音区域，寻找合适的切分点
        for (SilenceDetector.SilenceRegion region : silenceRegions) {
            double regionMid = (region.getStartTime() + region.getEndTime()) / 2.0;
            double bufferedDuration = regionMid - lastCutTime;

            if (bufferedDuration >= minSeg) {
                cutPoints.add(regionMid);
                lastCutTime = regionMid;
            }
        }

        // 检查是否有超长片段需要强制切分
        List<Double> finalCutPoints = new ArrayList<>();
        double prevCut = 0.0;
        for (Double cutPoint : cutPoints) {
            // 在 prevCut 和 cutPoint 之间检查是否需要强制切分
            while (cutPoint - prevCut > maxSeg) {
                double forceCut = prevCut + maxSeg;
                finalCutPoints.add(forceCut);
                prevCut = forceCut;
            }
            finalCutPoints.add(cutPoint);
            prevCut = cutPoint;
        }

        // 最后一段
        while (totalDuration - prevCut > maxSeg) {
            double forceCut = prevCut + maxSeg;
            finalCutPoints.add(forceCut);
            prevCut = forceCut;
        }

        return finalCutPoints;
    }

    /**
     * 从 WAV 字节中提取 PCM 数据（跳过头部）
     */
    private byte[] extractPcmData(byte[] wavBytes) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(wavBytes);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(bais);
            ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = audioStream.read(buffer)) != -1) {
                pcmOut.write(buffer, 0, read);
            }
            audioStream.close();
            return pcmOut.toByteArray();
        } catch (UnsupportedAudioFileException | IOException e) {
            throw new RuntimeException("提取 PCM 数据失败", e);
        }
    }

    /**
     * 将 PCM 字节转换为 WAV 格式
     */
    private byte[] pcmToWav(byte[] pcmData) {
        AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                SAMPLE_RATE, 16, 1, 2, SAMPLE_RATE, false);
        ByteArrayInputStream pcmIn = new ByteArrayInputStream(pcmData);
        AudioInputStream audioIn = new AudioInputStream(pcmIn, format, pcmData.length / 2);
        ByteArrayOutputStream wavOut = new ByteArrayOutputStream();
        try {
            AudioSystem.write(audioIn, AudioFileFormat.Type.WAVE, wavOut);
            audioIn.close();
        } catch (IOException e) {
            throw new RuntimeException("PCM 转 WAV 失败", e);
        }
        return wavOut.toByteArray();
    }

    /**
     * 秒数转 PCM 字节偏移
     */
    private int secondsToBytes(double seconds) {
        return (int) (seconds * SAMPLE_RATE * BYTES_PER_SAMPLE);
    }

    /**
     * 获取重叠窗口时长（供 TextMerger 使用）
     */
    public double getOverlapDuration() {
        return chunkProperties.getOverlapDuration();
    }
}
