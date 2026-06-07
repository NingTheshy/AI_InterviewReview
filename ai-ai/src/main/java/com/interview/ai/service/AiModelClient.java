package com.interview.ai.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * AI 模型客户端接口
 * <p>
 * 定义与 AI 模型交互的统一接口，支持文本生成和语音转文字。
 * 不同厂商（DeepSeek、小米、FunASR 等）实现此接口。
 * </p>
 * @deprecated 已拆分为 {@link AsrClient} 和 {@link LlmClient}
 */
@Deprecated
public interface AiModelClient {

    /**
     * 调用 AI 模型进行文本生成
     *
     * @param prompt       用户提示词
     * @param systemPrompt 系统提示词
     * @param configId     配置 ID（可选）
     * @return AI 生成的文本
     */
    String call(String prompt, String systemPrompt, Long configId);

    /**
     * 调用 ASR 模型进行语音转文字
     *
     * @param audioFile 音频文件
     * @param language  语言代码（如 "zh"）
     * @return 转录后的文本
     */
    String transcribe(MultipartFile audioFile, String language);
}
