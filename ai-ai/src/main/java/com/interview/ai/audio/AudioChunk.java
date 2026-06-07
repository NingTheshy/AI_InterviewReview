package com.interview.ai.audio;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 音频片段数据
 * <p>
 * 表示切片后的一个音频片段，包含 PCM WAV 数据和时间轴信息。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioChunk {

    /** 片段序列号（从 0 开始递增） */
    private int seq;

    /** PCM WAV 格式的音频字节数据 */
    private byte[] audioData;

    /** 在原始音频中的起始时间（秒） */
    private double startTime;

    /** 在原始音频中的结束时间（秒） */
    private double endTime;
}
