package com.interview.ai.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * ASR 语音转文字客户端接口
 * <p>
 * 定义业务层稳定的语音识别接口。所有生产实现必须适配 Spring AI
 * {@code TranscriptionModel}，供应商协议不能直接泄漏到业务服务。
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
