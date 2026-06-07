package com.interview.ai.client;

import com.interview.ai.config.AiProviderProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * 小米 MiMo LLM 客户端实现
 * <p>
 * 继承 DeepSeekLlmClient，小米 LLM API 与 OpenAI chat/completions 格式兼容。
 * 使用 Authorization: Bearer 认证（与小米 ASR 一致）。
 * </p>
 */
@Slf4j
public class XiaomiLlmClient extends DeepSeekLlmClient {

    public XiaomiLlmClient(AiProviderProperties properties) {
        super(properties);
    }
}
