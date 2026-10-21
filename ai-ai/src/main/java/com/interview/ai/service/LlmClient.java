package com.interview.ai.service;

/**
 * LLM 文本生成客户端接口
 * <p>
 * 定义业务层稳定的大语言模型调用接口。所有生产实现必须使用 Spring AI
 * {@code ChatModel}，避免业务服务依赖厂商协议。
 * </p>
 */
public interface LlmClient {

    /**
     * 调用 LLM 进行文本生成
     *
     * @param prompt       用户提示词
     * @param systemPrompt 系统提示词
     * @param configId     配置 ID（可选）
     * @return AI 生成的文本
     */
    String call(String prompt, String systemPrompt, Long configId);
}
