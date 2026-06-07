package com.interview.ai.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * ASR 语音转文字客户端接口
 * <p>
 * 定义语音识别的统一接口，不同厂商（DashScope、Whisper 等）实现此接口。
 * </p>
 */
public interface AsrClient {

    /**
     * 语音转文字
     *
     * @param audioFile 音频文件
     * @param language  语言代码（如 "zh"）
     * @return 转录后的文本
     */
    String transcribe(MultipartFile audioFile, String language);
}
