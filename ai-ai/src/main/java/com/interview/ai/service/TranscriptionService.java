package com.interview.ai.service;

/**
 * 语音转文字服务接口
 * <p>
 * 提供音频文件的语音识别功能，将音频转换为文本。
 * 支持指定 ASR 配置或使用默认配置。
 * </p>
 */
public interface TranscriptionService {

    /**
     * 转录音频文件为文本
     *
     * @param audioFilePath 音频文件路径
     * @param configId      ASR 配置 ID（null 使用默认）
     * @return 转录后的文本内容
     */
    String transcribe(String audioFilePath, Long configId);
}
