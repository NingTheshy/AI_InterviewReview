package com.interview.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 音频分片转录配置
 * <p>
 * 对应 application.yml 中的 ai.chunk 配置项。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.chunk")
public class AudioChunkProperties {

    /** 小于该值（字节）直接调用 ASR，超过则分片。默认 6MB */
    private long maxDirectSize = 6291456L;

    /** 最小片段时长（秒），防止过多碎片。默认 30 秒 */
    private int minSegmentDuration = 30;

    /** 最大片段时长（秒），防止超限。默认 180 秒（16kHz/16bit/mono 约 5.76MB，base64 后约 7.7MB，安全低于 ASR 10MB 限制） */
    private int maxSegmentDuration = 180;

    /** 最小静音间隙（秒），用于切分点检测。默认 1.0 秒（面试中的自然停顿通常较短） */
    private double minSilenceDuration = 1.0;

    /** 重叠窗口（秒），避免切分点词语断裂。默认 1.5 秒 */
    private double overlapDuration = 1.5;

    /** 并发发送窗口。默认 2 */
    private int concurrency = 2;

    /** 单片段最大重试次数。默认 3 */
    private int maxRetries = 3;

    /** 单次 ASR 请求超时（秒）。默认 30 */
    private int timeoutSeconds = 30;

    /** FFmpeg 可执行文件路径。默认 "ffmpeg"（从系统 PATH 查找） */
    private String ffmpegPath = "ffmpeg";

    /** 静音检测 RMS 能量阈值（16bit PCM），低于此值视为静音。默认 300.0（约 -60dBFS） */
    private double silenceThreshold = 300.0;
}
